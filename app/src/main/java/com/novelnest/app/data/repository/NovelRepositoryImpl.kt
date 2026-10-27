package com.novelnest.app.data.repository

import com.novelnest.app.data.cache.ReaderCacheStore
import com.novelnest.app.data.cache.MutexPool
import com.novelnest.app.feature.reader.Chapter
import com.novelnest.app.data.cache.MemoryLruCache
import com.novelnest.app.data.cache.ChapterSnapshotStore
import com.novelnest.app.data.database.NovelNestDatabase
import com.novelnest.app.data.database.entity.NovelEntity
import com.novelnest.app.data.database.entity.SectionEntity
import com.novelnest.app.data.database.mapper.toDomain
import com.novelnest.app.data.database.mapper.toEntity
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.reuseRatings
import com.novelnest.app.data.model.hasRating
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
import com.novelnest.app.data.remote.asura.AsuraUrls
import com.novelnest.app.data.remote.ManhwaSource
import com.novelnest.app.data.remote.network.CircuitBreaker
import com.novelnest.app.data.remote.network.HostRateLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
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
    private val browseClockMillis: () -> Long = { System.currentTimeMillis() },
    private val readerCache: ReaderCacheStore? = null
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
    private val memoryChapterCache = MemoryLruCache<String, List<Chapter>>(16)

    // Progressive navigation snapshots are separate from the cache of complete lists.
    private val chapterSnapshots = ChapterSnapshotStore(16)
    private val prefetchedNovels = MemoryLruCache<String, Boolean>(64)
    private val memoryNovels = MemoryLruCache<String, Novel>(256)
    private val popularCache = ConcurrentHashMap<NovelSourceType, Pair<Long, List<Novel>>>()

    private val readerContent = MemoryLruCache<String, Chapter>(8)
    private val readerContentLocks = MutexPool()

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
                return@withLock Result.success(cached.second.copy(novels = cached.second.novels.map(::withKnownRating)))
            }
            val remote = sources[source] ?: return@withLock Result.failure(IOException("Source is unavailable"))
            val fetched = if (remote is AsuraSource) remote.getBrowsePage(canonicalUrl, forceRefresh) else remote.getBrowsePage(canonicalUrl)
            val result = fetched.map { page ->
                page.copy(novels = page.novels.filter { it.source == source }.distinctBy { it.id }.map(::withKnownRating))
            }
            result.onSuccess { page ->
                page.novels.forEach(::rememberNovel)
                browseCache.put(key, browseClockMillis() to page)
            }
            if (result.isFailure && cached != null) Result.success(cached.second.copy(isStale = true,
                novels = cached.second.novels.map(::withKnownRating))) else result
        }
    }

    override suspend fun getPopularNovels(source: NovelSourceType, forceRefresh: Boolean): Result<List<Novel>> {
        val cached = popularCache[source]
        if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.first < L1_TTL_MILLIS) {
            return Result.success(cached.second)
        }
        val remote = sources[source] ?: return Result.failure(IOException("Source is unavailable"))
        val fetched = if (remote is AsuraSource && forceRefresh) remote.getHomeSections(true).map { sections ->
            sections.firstOrNull { it.id == "asura-live-popular" }?.novels.orEmpty()
        } else remote.getPopularNovels()
        val result = fetched.map { novels -> novels.filter { it.source == source }.distinctBy { it.id }.take(12) }
        result.onSuccess { novels ->
            novels.forEach(::rememberNovel)
            popularCache[source] = System.currentTimeMillis() to novels
        }
        return if (result.isFailure && cached != null) Result.success(cached.second) else result
    }

    /** Sparse catalog cards must not erase a score already fetched from this source. */
    private fun withKnownRating(novel: Novel): Novel {
        if (novel.hasRating()) return novel
        val known = memoryNovels[novel.id]?.takeIf { it.source == novel.source && it.hasRating() }
        return if (known != null) novel.copy(rating = known.rating) else novel
    }

    override fun rememberNovel(novel: Novel) {
        val canonical = if (novel.source == NovelSourceType.FREE_WEB_NOVEL) {
            runCatching { novel.copy(id = FreeWebNovelUrls.novelId(novel.sourceUrl), sourceUrl = FreeWebNovelUrls.novelUrl(novel.sourceUrl)) }.getOrDefault(novel)
        } else if (novel.source == NovelSourceType.ASURA_SCANS) NovelIdentity.normalize(novel) else novel
        val existing = memoryNovels[canonical.id]
        val merged = if (existing != null && canonical.synopsis.isBlank()) canonical.copy(
            rating = withKnownRating(canonical).rating,
            chapterCount = canonical.chapterCount.takeIf { it > 0 } ?: existing.chapterCount,
            genres = canonical.genres.ifEmpty { existing.genres },
            synopsis = existing.synopsis, author = existing.author.takeUnless { it == "Unknown" } ?: canonical.author
        ) else canonical
        memoryNovels[novel.id] = merged
        memoryNovels[canonical.id] = merged
    }

    private fun isLiveFreeWebNovel(novel: Novel): Boolean = novel.source == NovelSourceType.FREE_WEB_NOVEL &&
        runCatching { novel.id == FreeWebNovelUrls.novelId(novel.sourceUrl) }.getOrDefault(false)

    private fun liveOnly(source: NovelSourceType) = source in setOf(NovelSourceType.FREE_WEB_NOVEL, NovelSourceType.ASURA_SCANS)
    private fun genuine(novel: Novel): Boolean = when (novel.source) {
        NovelSourceType.FREE_WEB_NOVEL -> isLiveFreeWebNovel(novel)
        NovelSourceType.ASURA_SCANS -> runCatching {
            novel.id == AsuraUrls.novelId(novel.sourceUrl) &&
                (novel.coverUrl.isBlank() || AsuraUrls.image(novel.coverUrl).isNotBlank())
        }.getOrDefault(false)
        else -> true
    }

    private fun chapterSnapshot(source: NovelSourceType, novelSlugOrUrl: String) =
        chapterSnapshots.get(buildChapterCacheKey(source, novelSlugOrUrl))

    override fun observeChapterSnapshots(source: NovelSourceType, novelSlugOrUrl: String): StateFlow<List<com.novelnest.app.feature.reader.Chapter>> =
        chapterSnapshot(source, novelSlugOrUrl).asStateFlow()

    private fun availableSectionRatings(sections: List<NovelSection>): List<NovelSection> =
        sections.map { section -> section.copy(novels = section.novels.map(NovelIdentity::normalize)
            .distinctBy { it.source to it.id }.map(::withKnownRating)) }.reuseRatings()

    override fun getHomeSections(
        source: NovelSourceType,
        forceRefresh: Boolean
    ): Flow<Result<List<NovelSection>>> = flow {
        val now = System.currentTimeMillis()
        val cacheKey = "sections_${source.name}"

        // 1. Check L1 Memory Cache
        val l1Entry = l1MemoryCache.get(cacheKey)
        if (!forceRefresh && l1Entry != null && (now - l1Entry.first < L1_TTL_MILLIS)) {
            emit(Result.success(availableSectionRatings(l1Entry.second)))
            return@flow
        }

        // 2. Check L2 Room Database
        val cachedFromDb = availableSectionRatings(readSectionsFromDatabase(source))
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
            if (liveOnly(source)) emit(Result.failure(IOException("${source.displayName} is temporarily unavailable")))
            else emit(Result.success(SeedData.getSectionsFor(source)))
            return@flow
        }

        rateLimiter.acquire(source.name)

        val remoteSource = sources[source]
        if (remoteSource == null) {
            if (liveOnly(source) && cachedFromDb.isEmpty()) {
                emit(Result.failure(IOException("${source.displayName} source is unavailable")))
                return@flow
            }
            val fallback = if (cachedFromDb.isNotEmpty()) cachedFromDb else SeedData.getSectionsFor(source)
            emit(Result.success(fallback))
            return@flow
        }

        val fetched = if (remoteSource is AsuraSource) remoteSource.getHomeSections(forceRefresh) else remoteSource.getHomeSections()
        val remoteResult = fetched.map(::availableSectionRatings)
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
                    if (liveOnly(source)) emit(Result.failure(error))
                    else emit(Result.success(SeedData.getSectionsFor(source)))
                }
            }
        )
    }

    override suspend fun searchNovels(source: NovelSourceType, query: String): Result<List<Novel>> {
        val q = query.trim()
        if (q.isBlank()) {
            return Result.success(if (liveOnly(source)) emptyList() else SeedData.search(source, ""))
        }

        // Check local DB first for matches to reduce scraper stress
        var cachedMatches = NovelIdentity.distinct(memoryNovels.valuesSnapshot()).filter {
            it.source == source && it.title.contains(q, ignoreCase = true) &&
                (!liveOnly(source) || genuine(it))
        }
        if (database != null) {
            val localMatches = database.novelDao().searchNovels(source.name, q).firstOrNull().orEmpty()
            cachedMatches = NovelIdentity.distinct(cachedMatches + localMatches.map { it.toDomain() })
                .filter { !liveOnly(source) || genuine(it) }
            if (!liveOnly(source) && localMatches.isNotEmpty()) {
                return Result.success(cachedMatches)
            }
        }

        // Polite remote fetch
        if (!circuitBreaker.canExecute(source.name)) {
            if (liveOnly(source)) return if (cachedMatches.isNotEmpty()) Result.success(cachedMatches)
                else Result.failure(IOException("${source.displayName} is temporarily unavailable"))
            return Result.success(SeedData.search(source, q))
        }

        rateLimiter.acquire(source.name)
        val remoteSource = sources[source] ?: return if (liveOnly(source))
            Result.failure(IOException("${source.displayName} source is unavailable")) else Result.success(SeedData.search(source, q))

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

        return if (liveOnly(source) && result.isFailure && cachedMatches.isNotEmpty()) Result.success(cachedMatches) else result
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
        return SeedData.findNovelById(novelId)?.takeUnless { it.source == NovelSourceType.ASURA_SCANS }
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

    override suspend fun getManhwaDetails(source: NovelSourceType, seriesUrlOrId: String, forceRefresh: Boolean): Result<com.novelnest.app.data.model.ManhwaDetails> {
        val remote = sources[source] as? ManhwaSource
            ?: return Result.failure(UnsupportedOperationException("This source does not support image chapters"))
        return remote.getManhwaDetails(seriesUrlOrId, forceRefresh).onSuccess { details ->
            rememberNovel(details.novel)
            database?.let { db -> repositoryScope.launch {
                try { db.novelDao().upsertNovel(details.novel.toEntity()) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { android.util.Log.w("NovelRepository", "Unable to cache manhwa metadata", error) }
            } }
        }
    }

    override suspend fun getManhwaChapter(source: NovelSourceType, chapterUrl: String, forceRefresh: Boolean): Result<com.novelnest.app.data.model.ManhwaChapterContent> {
        val remote = sources[source] as? ManhwaSource
            ?: return Result.failure(UnsupportedOperationException("This source does not support image chapters"))
        return remote.getManhwaChapter(chapterUrl, forceRefresh)
    }

    private fun buildChapterCacheKey(source: NovelSourceType, slugOrUrl: String): String {
        if (source == NovelSourceType.ASURA_SCANS) return "${source.name}_${AsuraUrls.slug(slugOrUrl)}"
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
        val useRepositoryCache = source !in setOf(NovelSourceType.NOVEL_PHOENIX, NovelSourceType.FREE_WEB_NOVEL, NovelSourceType.ASURA_SCANS)
        val cached = if (useRepositoryCache) memoryChapterCache[cacheKey] else null
        if (useRepositoryCache && !forceRefresh && !cached.isNullOrEmpty()) {
            return Result.success(cached)
        }

        val remoteSource = sources[source] ?: return Result.failure(IllegalArgumentException("Unknown source $source"))
        val result = remoteSource.getChapters(novelSlugOrUrl, forceRefresh)
        result.onSuccess { chapters ->
            if (chapters.isNotEmpty()) {
                chapterSnapshot(source, novelSlugOrUrl).value = chapters
                readerCache?.write("catalog:" + cacheKey, chapters, includeText = false)
            }
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
    ): Flow<Result<List<Chapter>>> = chapterFlow(source, novelSlugOrUrl, forceRefresh, warmFirstChapter = true)

    override fun getReaderChaptersFlow(source: NovelSourceType, novelSlugOrUrl: String): Flow<Result<List<Chapter>>> =
        chapterFlow(source, novelSlugOrUrl, forceRefresh = false, warmFirstChapter = false)

    private fun chapterFlow(
        source: NovelSourceType,
        novelSlugOrUrl: String,
        forceRefresh: Boolean,
        warmFirstChapter: Boolean
    ): Flow<Result<List<Chapter>>> = flow {
        val cacheKey = buildChapterCacheKey(source, novelSlugOrUrl)
        val useRepositoryCache = source !in setOf(NovelSourceType.NOVEL_PHOENIX, NovelSourceType.FREE_WEB_NOVEL, NovelSourceType.ASURA_SCANS)
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

        val snapshot = chapterSnapshot(source, novelSlugOrUrl)
        var storedCatalogSize = 0
        if (!forceRefresh) {
            val stored = readerCache?.read("catalog:" + cacheKey)
            if (!stored.isNullOrEmpty()) {
                storedCatalogSize = stored.size
                snapshot.update { mergePartialCatalog(it, stored) }
                emit(Result.success(snapshot.value))
            } else if (snapshot.value.isNotEmpty()) {
                emit(Result.success(snapshot.value))
            }
        }

        var latestRemote: List<Chapter>? = null
        var failed = false
        var lastWriteNanos = 0L
        remoteSource.getChaptersFlow(novelSlugOrUrl, forceRefresh).catch { error ->
            if (error is CancellationException) throw error
            emit(Result.failure(error))
        }.collect { result ->
            if (result.isFailure) {
                failed = true
                emit(result)
            } else {
                val chapters = result.getOrThrow()
                latestRemote = chapters
                if (chapters.isNotEmpty()) {
                    snapshot.update { mergePartialCatalog(it, chapters) }
                    val firstChapter = chapters.first()
                    if (warmFirstChapter && source in setOf(NovelSourceType.NOVEL_PHOENIX, NovelSourceType.FREE_WEB_NOVEL) && firstChapter.url.isNotBlank() && prefetchedNovels.putIfAbsent(cacheKey, true) == null) {
                        // Warm one chapter while details are visible; selecting it uses the same source cache.
                        repositoryScope.launch {
                            getChapterContent(source, firstChapter.url, forceRefresh = false).onFailure {
                                prefetchedNovels.remove(cacheKey)
                            }
                        }
                    }
                    circuitBreaker.recordSuccess(source.name)
                    if (remoteSource is NovelPhoenixSource) {
                        remoteSource.resetCircuitBreaker()
                    }
                }
                emit(Result.success(snapshot.value))
                val now = System.nanoTime()
                // Checkpoint useful growth, without serializing the whole catalog on every emission.
                if (snapshot.value.size > storedCatalogSize &&
                    (lastWriteNanos == 0L || now - lastWriteNanos >= 1_000_000_000L)) {
                    readerCache?.write("catalog:" + cacheKey, snapshot.value, includeText = false)
                    storedCatalogSize = snapshot.value.size
                    lastWriteNanos = now
                }
            }
        }
        // Flow cancellation (e.g. first()) never reaches this point. Only a normally
        // completed, successful source refresh may remove chapters from the old list.
        val complete = latestRemote
        if (!failed && !complete.isNullOrEmpty()) {
            snapshot.value = complete
            if (useRepositoryCache) memoryChapterCache[cacheKey] = complete
            emit(Result.success(complete))
            readerCache?.write("catalog:" + cacheKey, complete, includeText = false)
        } else if (!failed && complete != null && snapshot.value.isNotEmpty()) {
            emit(Result.failure(IOException("Source returned no chapters; keeping the cached catalog.")))
        }
    }

    private fun mergePartialCatalog(existing: List<Chapter>, incoming: List<Chapter>): List<Chapter> {
        if (existing.isEmpty()) return incoming
        if (incoming.isEmpty()) return existing
        val fresh = incoming.associateBy { it.id }
        val known = existing.mapTo(HashSet()) { it.id }
        return existing.map { old -> fresh[old.id]?.let { updated ->
            if (old.paragraphs.isNotEmpty()) updated.copy(paragraphs = old.paragraphs) else updated
        } ?: old } + incoming.filter { it.id !in known }
    }

    override suspend fun getChapterContent(
        source: NovelSourceType,
        chapterUrlOrId: String,
        forceRefresh: Boolean
    ): Result<com.novelnest.app.feature.reader.Chapter> {
        val remoteSource = sources[source] ?: return Result.failure(IllegalArgumentException("Unknown source $source"))
        val url = runCatching {
            when (source) {
                NovelSourceType.NOVEL_PHOENIX -> com.novelnest.app.data.remote.novelphoenix.NovelPhoenixUtils.resolveUrl(chapterUrlOrId)
                NovelSourceType.FREE_WEB_NOVEL -> FreeWebNovelUrls.resolve(chapterUrlOrId)
                else -> java.net.URI(source.baseUrl + "/").resolve(chapterUrlOrId).toString()
            }
        }.getOrDefault(chapterUrlOrId)
        val key = source.name + ":content:" + url
        return readerContentLocks.forKey(key).withLock {
            if (!forceRefresh) {
                readerContent[key]?.let { return@withLock Result.success(it) }
                readerCache?.read(key)?.singleOrNull()?.takeIf { it.paragraphs.isNotEmpty() }?.let {
                    readerContent[key] = it
                    return@withLock Result.success(it)
                }
            }
            val result = remoteSource.getChapterContent(chapterUrlOrId, forceRefresh)
            result.getOrNull()?.takeIf { it.paragraphs.isNotEmpty() }?.let {
                readerContent[key] = it
                readerCache?.write(key, listOf(it), includeText = true)
            }
            result
        }
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
        }.filter { it.novels.isNotEmpty() && when (source) {
            NovelSourceType.FREE_WEB_NOVEL -> it.id.startsWith("fwn-live-") && it.novels.all(::isLiveFreeWebNovel)
            NovelSourceType.ASURA_SCANS -> it.id.startsWith("asura-live-") && it.novels.all(::genuine)
            else -> true
        } }
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
