package com.novelnest.app.data.repository

import com.novelnest.app.data.cache.MemoryLruCache
import com.novelnest.app.data.database.NovelNestDatabase
import com.novelnest.app.data.database.entity.NovelEntity
import com.novelnest.app.data.database.entity.SectionEntity
import com.novelnest.app.data.database.mapper.toDomain
import com.novelnest.app.data.database.mapper.toEntity
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelIdentity
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.model.SectionType
import com.novelnest.app.data.remote.AsuraSource
import com.novelnest.app.data.remote.FreeWebNovelSource
import com.novelnest.app.data.remote.NovelPhoenixSource
import com.novelnest.app.data.remote.NovelSource
import com.novelnest.app.data.remote.SeedData
import com.novelnest.app.data.remote.freewebnovel.FreeWebNovelUrls
import com.novelnest.app.data.remote.network.CircuitBreaker
import com.novelnest.app.data.remote.network.HostRateLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.io.IOException

class NovelRepositoryImpl(
    private val database: NovelNestDatabase? = null,
    private val sources: Map<NovelSourceType, NovelSource> = mapOf(
        NovelSourceType.NOVEL_PHOENIX to NovelPhoenixSource(),
        NovelSourceType.FREE_WEB_NOVEL to FreeWebNovelSource(),
        NovelSourceType.ASURA_SCANS to AsuraSource()
    ),
    private val rateLimiter: HostRateLimiter = HostRateLimiter(minIntervalMillis = 600L),
    private val circuitBreaker: CircuitBreaker = CircuitBreaker(),
    private val browseClockMillis: () -> Long = { System.currentTimeMillis() }
) : NovelRepository {

    // L1: In-memory LRU cache (15-minute validity)
    private val l1MemoryCache = MemoryLruCache<String, Pair<Long, List<NovelSection>>>(10)
    private val L1_TTL_MILLIS = 15 * 60 * 1000L // 15 mins
    private val L2_TTL_MILLIS = 2 * 60 * 60 * 1000L // 2 hours

    // Repository scope for background revalidation
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Tracks last sync times per source
    private val lastSyncTimestamps = ConcurrentHashMap<NovelSourceType, Long>()

    // Persistent in-memory chapter cache across the entire application lifecycle
    private val memoryChapterCache = ConcurrentHashMap<String, List<com.novelnest.app.feature.reader.Chapter>>()

    // Progressive navigation snapshots are separate from the cache of complete lists.
    private val chapterSnapshots = ConcurrentHashMap<String, MutableStateFlow<List<com.novelnest.app.feature.reader.Chapter>>>()
    private val prefetchedNovels = ConcurrentHashMap.newKeySet<String>()
    private val memoryNovels = ConcurrentHashMap<String, Novel>()
    private val popularCache = ConcurrentHashMap<NovelSourceType, Pair<Long, List<Novel>>>()

    private val browseCache = MemoryLruCache<String, Pair<Long, com.novelnest.app.data.model.BrowsePage>>(40)
    private val browseLocks = List(16) { kotlinx.coroutines.sync.Mutex() }

    override suspend fun getBrowsePage(source: NovelSourceType, url: String?, forceRefresh: Boolean): Result<com.novelnest.app.data.model.BrowsePage> {
        val canonicalUrl = if (source == NovelSourceType.ASURA_SCANS) url else
            runCatching { com.novelnest.app.data.remote.BrowseParser.validateUrl(source, url ?: com.novelnest.app.data.remote.BrowseParser.initialUrl(source)) }
                .getOrElse { return Result.failure(it) }
        val key = source.name + ":" + (canonicalUrl ?: "initial")
        val lock = browseLocks[(key.hashCode() and Int.MAX_VALUE) % browseLocks.size]
        return lock.withLock {
            val cached = browseCache.get(key)
            if (!forceRefresh && cached != null && browseClockMillis() - cached.first < L1_TTL_MILLIS) {
                return@withLock Result.success(cached.second)
            }
            val remote = sources[source] ?: return@withLock Result.failure(IOException("Source is unavailable"))
            val result = remote.getBrowsePage(canonicalUrl).map { page ->
                page.copy(novels = page.novels.filter { it.source == source }.distinctBy { it.id })
            }
            result.onSuccess { page ->
                page.novels.forEach(::rememberNovel)
                browseCache.put(key, browseClockMillis() to page)
            }
            if (result.isFailure && cached != null) Result.success(cached.second.copy(isStale = true)) else result
        }
    }

    override suspend fun getPopularNovels(source: NovelSourceType, forceRefresh: Boolean): Result<List<Novel>> {
        val cached = popularCache[source]
        if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.first < L1_TTL_MILLIS) {
            return Result.success(cached.second)
        }
        val remote = sources[source] ?: return Result.failure(IOException("Source is unavailable"))
        val result = remote.getPopularNovels().map { novels -> novels.filter { it.source == source }.distinctBy { it.id }.take(12) }
        result.onSuccess { novels ->
            novels.forEach(::rememberNovel)
            popularCache[source] = System.currentTimeMillis() to novels
        }
        return if (result.isFailure && cached != null) Result.success(cached.second) else result
    }

    override fun rememberNovel(novel: Novel) {
        val canonical = if (novel.source == NovelSourceType.FREE_WEB_NOVEL) {
            runCatching { novel.copy(id = FreeWebNovelUrls.novelId(novel.sourceUrl), sourceUrl = FreeWebNovelUrls.novelUrl(novel.sourceUrl)) }.getOrDefault(novel)
        } else novel
        val existing = memoryNovels[canonical.id]
        val merged = if (existing != null && canonical.synopsis.isBlank()) canonical.copy(
            chapterCount = canonical.chapterCount.takeIf { it > 0 } ?: existing.chapterCount,
            genres = canonical.genres.ifEmpty { existing.genres },
            synopsis = existing.synopsis, author = existing.author.takeUnless { it == "Unknown" } ?: canonical.author
        ) else canonical
        memoryNovels[novel.id] = merged
        memoryNovels[canonical.id] = merged
    }

    private fun isLiveFreeWebNovel(novel: Novel): Boolean = novel.source == NovelSourceType.FREE_WEB_NOVEL &&
        runCatching { novel.id == FreeWebNovelUrls.novelId(novel.sourceUrl) }.getOrDefault(false)

    private fun chapterSnapshot(source: NovelSourceType, novelSlugOrUrl: String) =
        chapterSnapshots.getOrPut(buildChapterCacheKey(source, novelSlugOrUrl)) { MutableStateFlow(emptyList()) }

    override fun observeChapterSnapshots(source: NovelSourceType, novelSlugOrUrl: String): StateFlow<List<com.novelnest.app.feature.reader.Chapter>> =
        chapterSnapshot(source, novelSlugOrUrl).asStateFlow()

    override fun getHomeSections(
        source: NovelSourceType,
        forceRefresh: Boolean
    ): Flow<Result<List<NovelSection>>> = flow {
        val now = System.currentTimeMillis()
        val cacheKey = "sections_${source.name}"

        // 1. Check L1 Memory Cache
        val l1Entry = l1MemoryCache.get(cacheKey)
        if (!forceRefresh && l1Entry != null && (now - l1Entry.first < L1_TTL_MILLIS)) {
            emit(Result.success(l1Entry.second))
            return@flow
        }

        // 2. Check L2 Room Database
        val cachedFromDb = readSectionsFromDatabase(source)
        val lastSync = lastSyncTimestamps[source] ?: 0L
        val isDbFresh = cachedFromDb.isNotEmpty() && (now - lastSync < L2_TTL_MILLIS)

        if (cachedFromDb.isNotEmpty()) {
            // Immediately emit cached DB data (Instant UI rendering)
            emit(Result.success(cachedFromDb))
            l1MemoryCache.put(cacheKey, Pair(now, cachedFromDb))

            if (!forceRefresh && isDbFresh) {
                return@flow
            }
        }

        // 3. Stale-While-Revalidate: Fetch from remote source with rate limiting & circuit breaker
        if (!circuitBreaker.canExecute(source.name)) {
            if (cachedFromDb.isNotEmpty()) return@flow
            if (source == NovelSourceType.FREE_WEB_NOVEL) emit(Result.failure(IOException("FreeWebNovel is temporarily unavailable")))
            else emit(Result.success(SeedData.getSectionsFor(source)))
            return@flow
        }

        rateLimiter.acquire(source.name)

        val remoteSource = sources[source]
        if (remoteSource == null) {
            if (source == NovelSourceType.FREE_WEB_NOVEL && cachedFromDb.isEmpty()) {
                emit(Result.failure(IOException("FreeWebNovel source is unavailable")))
                return@flow
            }
            val fallback = if (cachedFromDb.isNotEmpty()) cachedFromDb else SeedData.getSectionsFor(source)
            emit(Result.success(fallback))
            return@flow
        }

        val remoteResult = remoteSource.getHomeSections()
        remoteResult.fold(
            onSuccess = { sections ->
                sections.flatMap { it.novels }.forEach(::rememberNovel)
                circuitBreaker.recordSuccess(source.name)
                if (remoteSource is NovelPhoenixSource) {
                    remoteSource.resetCircuitBreaker()
                }
                lastSyncTimestamps[source] = System.currentTimeMillis()

                // Update L1 Cache
                l1MemoryCache.put(cacheKey, Pair(System.currentTimeMillis(), sections))

                // Persist asynchronously into L2 Room DB
                saveSectionsToDatabase(source, sections)

                emit(Result.success(sections))
            },
            onFailure = { error ->
                // Only trip circuit breaker on genuine block errors (403/429/Cloudflare)
                if (error is com.novelnest.app.data.remote.novelphoenix.PhoenixException.SourceBlockedError) {
                    circuitBreaker.recordFailure(source.name)
                }
                if (cachedFromDb.isNotEmpty()) {
                    // Do not break UI if stale data exists
                    emit(Result.success(cachedFromDb))
                } else {
                    // Ultimate graceful fallback
                    if (source == NovelSourceType.FREE_WEB_NOVEL) emit(Result.failure(error))
                    else emit(Result.success(SeedData.getSectionsFor(source)))
                }
            }
        )
    }

    override suspend fun searchNovels(source: NovelSourceType, query: String): Result<List<Novel>> {
        val q = query.trim()
        if (q.isBlank()) {
            return Result.success(if (source == NovelSourceType.FREE_WEB_NOVEL) emptyList() else SeedData.search(source, ""))
        }

        // Check local DB first for matches to reduce scraper stress
        var cachedMatches = NovelIdentity.distinct(memoryNovels.values.toList()).filter {
            it.source == source && it.title.contains(q, ignoreCase = true) &&
                (source != NovelSourceType.FREE_WEB_NOVEL || isLiveFreeWebNovel(it))
        }
        if (database != null) {
            val localMatches = database.novelDao().searchNovels(source.name, q).firstOrNull().orEmpty()
            cachedMatches = NovelIdentity.distinct(cachedMatches + localMatches.map { it.toDomain() })
                .filter { source != NovelSourceType.FREE_WEB_NOVEL || isLiveFreeWebNovel(it) }
            if (source != NovelSourceType.FREE_WEB_NOVEL && localMatches.isNotEmpty()) {
                return Result.success(cachedMatches)
            }
        }

        // Polite remote fetch
        if (!circuitBreaker.canExecute(source.name)) {
            if (source == NovelSourceType.FREE_WEB_NOVEL) return if (cachedMatches.isNotEmpty()) Result.success(cachedMatches)
                else Result.failure(IOException("FreeWebNovel is temporarily unavailable"))
            return Result.success(SeedData.search(source, q))
        }

        rateLimiter.acquire(source.name)
        val remoteSource = sources[source] ?: return if (source == NovelSourceType.FREE_WEB_NOVEL)
            Result.failure(IOException("FreeWebNovel source is unavailable")) else Result.success(SeedData.search(source, q))

        val result = remoteSource.search(q).map { NovelIdentity.distinct(it.filter { novel -> novel.source == source }) }
        result.onSuccess { novels ->
            novels.forEach(::rememberNovel)
            circuitBreaker.recordSuccess(source.name)
            if (remoteSource is NovelPhoenixSource) {
                remoteSource.resetCircuitBreaker()
            }
            // Cache found novels in DB for future queries
            database?.let { db ->
                repositoryScope.launch {
                    val entities = novels.map { it.toEntity() }
                    db.novelDao().upsertNovels(entities)
                }
            }
        }.onFailure { err ->
            if (err is com.novelnest.app.data.remote.novelphoenix.PhoenixException.SourceBlockedError) {
                circuitBreaker.recordFailure(source.name)
            }
        }

        return if (source == NovelSourceType.FREE_WEB_NOVEL && result.isFailure && cachedMatches.isNotEmpty()) Result.success(cachedMatches) else result
    }

    override suspend fun getNovelById(novelId: String): Novel? {
        memoryNovels[novelId]?.let { return it }
        if (database != null) {
            val cached = database.novelDao().getNovelById(novelId).firstOrNull()
            if (cached != null) {
                rememberNovel(cached.toDomain())
                return memoryNovels[novelId]
            }
        }
        return SeedData.findNovelById(novelId)
    }

    override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String): Result<Novel> {
        val remoteSource = sources[source] ?: return Result.failure(IllegalArgumentException("Unknown source $source"))
        val result = remoteSource.getNovelDetails(novelUrl)
        result.onSuccess { liveNovel ->
            rememberNovel(liveNovel)
            circuitBreaker.recordSuccess(source.name)
            if (remoteSource is NovelPhoenixSource) {
                remoteSource.resetCircuitBreaker()
            }
            database?.let { db ->
                repositoryScope.launch {
                    db.novelDao().upsertNovel(liveNovel.toEntity())
                }
            }
        }
        return result
    }

    private fun buildChapterCacheKey(source: NovelSourceType, slugOrUrl: String): String {
        if (source == NovelSourceType.FREE_WEB_NOVEL) return "${source.name}_${FreeWebNovelUrls.slug(slugOrUrl)}"
        val trimmed = slugOrUrl.trim()
        val path = runCatching { java.net.URI(trimmed).path }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: trimmed.substringBefore('?').substringBefore('#')
        val lastSegment = path.trimEnd('/').substringAfterLast('/')
        val mappedLegacy = when (if (source == NovelSourceType.NOVEL_PHOENIX) lastSegment else "") {
            "np-1", "1" -> "shadow-slave"
            "np-2", "2" -> "full-star-player-returns-as-a-godslayer"
            "np-3", "3" -> "genius-martial-arts-trainer"
            "np-4", "4" -> "the-mine-lord-a-dwarven-survival-base-builder"
            "np-5", "5" -> "lord-of-the-mysteries"
            "np-6", "6" -> "reverend-insanity"
            "np-7", "7" -> "collecting-powerful-girlfriends-my-invincible-harem"
            "np-8", "8" -> "tyrant-no-i-am-the-villain"
            "np-9", "9" -> "the-beginning-after-the-end"
            "np-10", "10" -> "mother-of-learning"
            else -> null
        }
        val cleanSlug = mappedLegacy ?: lastSegment
            .removePrefix("np-").removePrefix("fwn-").removePrefix("asura-")
        return "${source.name}_$cleanSlug"
    }

    override suspend fun getChapters(
        source: NovelSourceType,
        novelSlugOrUrl: String,
        forceRefresh: Boolean
    ): Result<List<com.novelnest.app.feature.reader.Chapter>> {
        val cacheKey = buildChapterCacheKey(source, novelSlugOrUrl)
        val useRepositoryCache = source !in setOf(NovelSourceType.NOVEL_PHOENIX, NovelSourceType.FREE_WEB_NOVEL)
        val cached = if (useRepositoryCache) memoryChapterCache[cacheKey] else null
        if (useRepositoryCache && !forceRefresh && !cached.isNullOrEmpty()) {
            return Result.success(cached)
        }

        val remoteSource = sources[source] ?: return Result.failure(IllegalArgumentException("Unknown source $source"))
        val result = remoteSource.getChapters(novelSlugOrUrl, forceRefresh)
        result.onSuccess { chapters ->
            if (chapters.isNotEmpty()) chapterSnapshot(source, novelSlugOrUrl).value = chapters
            if (useRepositoryCache && chapters.isNotEmpty()) {
                memoryChapterCache[cacheKey] = chapters
            }
        }.onFailure {
            if (!cached.isNullOrEmpty()) {
                return Result.success(cached)
            }
        }
        return result
    }

    override fun getChaptersFlow(
        source: NovelSourceType,
        novelSlugOrUrl: String,
        forceRefresh: Boolean
    ): Flow<Result<List<com.novelnest.app.feature.reader.Chapter>>> = flow {
        val cacheKey = buildChapterCacheKey(source, novelSlugOrUrl)
        val useRepositoryCache = source !in setOf(NovelSourceType.NOVEL_PHOENIX, NovelSourceType.FREE_WEB_NOVEL)
        val cached = if (useRepositoryCache) memoryChapterCache[cacheKey] else null
        if (useRepositoryCache && !forceRefresh && !cached.isNullOrEmpty()) {
            emit(Result.success(cached))
            if (cached.size > 100) {
                return@flow
            }
        }

        val remoteSource = sources[source]
        if (remoteSource == null) {
            emit(Result.failure(IllegalArgumentException("Unknown source $source")))
            return@flow
        }

        remoteSource.getChaptersFlow(novelSlugOrUrl, forceRefresh).collect { result ->
            result.onSuccess { chapters ->
                if (chapters.isNotEmpty()) {
                    chapterSnapshot(source, novelSlugOrUrl).value = chapters
                    val firstChapter = chapters.first()
                    if (source in setOf(NovelSourceType.NOVEL_PHOENIX, NovelSourceType.FREE_WEB_NOVEL) && firstChapter.url.isNotBlank() && prefetchedNovels.add(cacheKey)) {
                        // Warm one chapter while details are visible; selecting it uses the same source cache.
                        repositoryScope.launch {
                            remoteSource.getChapterContent(firstChapter.url, forceRefresh = false).onFailure {
                                prefetchedNovels.remove(cacheKey)
                            }
                        }
                    }
                    circuitBreaker.recordSuccess(source.name)
                    if (remoteSource is NovelPhoenixSource) {
                        remoteSource.resetCircuitBreaker()
                    }
                    if (useRepositoryCache) memoryChapterCache[cacheKey] = chapters
                }
            }
            emit(result)
        }
    }

    override suspend fun getChapterContent(
        source: NovelSourceType,
        chapterUrlOrId: String,
        forceRefresh: Boolean
    ): Result<com.novelnest.app.feature.reader.Chapter> {
        val remoteSource = sources[source] ?: return Result.failure(IllegalArgumentException("Unknown source $source"))
        return remoteSource.getChapterContent(chapterUrlOrId, forceRefresh)
    }

    private suspend fun readSectionsFromDatabase(source: NovelSourceType): List<NovelSection> {
        if (database == null) return emptyList()

        val sectionEntities = database.sectionDao().getSectionsForSource(source.name).firstOrNull().orEmpty()
        if (sectionEntities.isEmpty()) return emptyList()

        return sectionEntities.map { sectionEntity ->
            val novels = database.sectionDao().getNovelsForSection(sectionEntity.sectionId)
                .firstOrNull()
                .orEmpty()
                .map { it.toDomain() }

            sectionEntity.toDomain(novels)
        }.filter { it.novels.isNotEmpty() && (source != NovelSourceType.FREE_WEB_NOVEL ||
            (it.id.startsWith("fwn-live-") && it.novels.all(::isLiveFreeWebNovel))) }
    }

    private fun saveSectionsToDatabase(source: NovelSourceType, sections: List<NovelSection>) {
        if (database == null) return

        repositoryScope.launch {
            sections.forEachIndexed { index, section ->
                val sectionEntity = section.toEntity(source = source, displayOrder = index)
                val novelEntities = section.novels.map { it.toEntity() }

                // Upsert novels into master table
                database.novelDao().upsertNovels(novelEntities)

                // Save section with ordered cross-references
                database.sectionDao().saveSectionWithNovels(sectionEntity, novelEntities)
            }
        }
    }

    override fun resetSourceCircuitBreaker(source: NovelSourceType) {
        // Reset the repository-level circuit breaker
        circuitBreaker.reset(source.name)

        // Also reset the source-level circuit breaker (for NovelPhoenix's internal breaker)
        val remoteSource = sources[source]
        if (remoteSource is com.novelnest.app.data.remote.novelphoenix.NovelPhoenixSource) {
            remoteSource.resetCircuitBreaker()
        }
    }
}
