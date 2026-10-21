package com.novelnest.app.data.repository

import com.novelnest.app.data.cache.MemoryLruCache
import com.novelnest.app.data.database.NovelNestDatabase
import com.novelnest.app.data.database.entity.NovelEntity
import com.novelnest.app.data.database.entity.SectionEntity
import com.novelnest.app.data.database.mapper.toDomain
import com.novelnest.app.data.database.mapper.toEntity
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.model.SectionType
import com.novelnest.app.data.remote.AsuraSource
import com.novelnest.app.data.remote.FreeWebNovelSource
import com.novelnest.app.data.remote.NovelPhoenixSource
import com.novelnest.app.data.remote.NovelSource
import com.novelnest.app.data.remote.SeedData
import com.novelnest.app.data.remote.network.CircuitBreaker
import com.novelnest.app.data.remote.network.HostRateLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class NovelRepositoryImpl(
    private val database: NovelNestDatabase? = null,
    private val sources: Map<NovelSourceType, NovelSource> = mapOf(
        NovelSourceType.NOVEL_PHOENIX to NovelPhoenixSource(),
        NovelSourceType.FREE_WEB_NOVEL to FreeWebNovelSource(),
        NovelSourceType.ASURA_SCANS to AsuraSource()
    ),
    private val rateLimiter: HostRateLimiter = HostRateLimiter(minIntervalMillis = 600L),
    private val circuitBreaker: CircuitBreaker = CircuitBreaker()
) : NovelRepository {

    // L1: In-memory LRU cache (15-minute validity)
    private val l1MemoryCache = MemoryLruCache<String, Pair<Long, List<NovelSection>>>(10)
    private val L1_TTL_MILLIS = 15 * 60 * 1000L // 15 mins
    private val L2_TTL_MILLIS = 2 * 60 * 60 * 1000L // 2 hours

    // Repository scope for background revalidation
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Tracks last sync times per source
    private val lastSyncTimestamps = ConcurrentHashMap<NovelSourceType, Long>()

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
            emit(Result.success(SeedData.getSectionsFor(source)))
            return@flow
        }

        rateLimiter.acquire(source.name)

        val remoteSource = sources[source]
        if (remoteSource == null) {
            val fallback = if (cachedFromDb.isNotEmpty()) cachedFromDb else SeedData.getSectionsFor(source)
            emit(Result.success(fallback))
            return@flow
        }

        val remoteResult = remoteSource.getHomeSections()
        remoteResult.fold(
            onSuccess = { sections ->
                circuitBreaker.recordSuccess(source.name)
                lastSyncTimestamps[source] = System.currentTimeMillis()

                // Update L1 Cache
                l1MemoryCache.put(cacheKey, Pair(System.currentTimeMillis(), sections))

                // Persist asynchronously into L2 Room DB
                saveSectionsToDatabase(source, sections)

                emit(Result.success(sections))
            },
            onFailure = { error ->
                circuitBreaker.recordFailure(source.name)
                if (cachedFromDb.isNotEmpty()) {
                    // Do not break UI if stale data exists
                    emit(Result.success(cachedFromDb))
                } else {
                    // Ultimate graceful fallback
                    emit(Result.success(SeedData.getSectionsFor(source)))
                }
            }
        )
    }

    override suspend fun searchNovels(source: NovelSourceType, query: String): Result<List<Novel>> {
        val q = query.trim()
        if (q.isBlank()) {
            return Result.success(SeedData.search(source, ""))
        }

        // Check local DB first for matches to reduce scraper stress
        if (database != null) {
            val localMatches = database.novelDao().searchNovels(source.name, q).firstOrNull().orEmpty()
            if (localMatches.isNotEmpty()) {
                return Result.success(localMatches.map { it.toDomain() })
            }
        }

        // Polite remote fetch
        if (!circuitBreaker.canExecute(source.name)) {
            return Result.success(SeedData.search(source, q))
        }

        rateLimiter.acquire(source.name)
        val remoteSource = sources[source] ?: return Result.success(SeedData.search(source, q))

        val result = remoteSource.search(q)
        result.onSuccess { novels ->
            circuitBreaker.recordSuccess(source.name)
            // Cache found novels in DB for future queries
            database?.let { db ->
                repositoryScope.launch {
                    val entities = novels.map { it.toEntity() }
                    db.novelDao().upsertNovels(entities)
                }
            }
        }.onFailure {
            circuitBreaker.recordFailure(source.name)
        }

        return result
    }

    override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String): Result<Novel> {
        val remoteSource = sources[source] ?: return Result.failure(IllegalArgumentException("Unknown source $source"))
        return remoteSource.getNovelDetails(novelUrl)
    }

    override suspend fun getChapters(
        source: NovelSourceType,
        novelSlugOrUrl: String,
        forceRefresh: Boolean
    ): Result<List<com.novelnest.app.feature.reader.Chapter>> {
        val remoteSource = sources[source] ?: return Result.failure(IllegalArgumentException("Unknown source $source"))
        return remoteSource.getChapters(novelSlugOrUrl, forceRefresh)
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
        }.filter { it.novels.isNotEmpty() }
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
}
