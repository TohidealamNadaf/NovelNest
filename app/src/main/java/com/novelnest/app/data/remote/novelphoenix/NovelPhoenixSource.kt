package com.novelnest.app.data.remote.novelphoenix

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.model.SectionType
import com.novelnest.app.data.remote.NovelSource
import com.novelnest.app.data.remote.SeedData
import com.novelnest.app.data.remote.network.CircuitBreaker
import com.novelnest.app.data.remote.network.HostRateLimiter
import com.novelnest.app.data.remote.network.HttpFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Robust, polite remote source implementation for NovelPhoenix.
 *
 * Politeness & Resilience:
 * - Request throttling: HostRateLimiter enforcing >= 1000ms between requests.
 * - Retry with exponential backoff on 429/5xx (up to 3 attempts).
 * - CircuitBreaker preventing hammer attacks when blocked.
 * - Detects 403, 429, and Cloudflare challenge pages, rethrowing [PhoenixException.SourceBlockedError].
 * - In-memory cache for detail and chapter list requests.
 */
class NovelPhoenixSource(
    private val client: OkHttpClient = HttpFactory.getClient(),
    private val rateLimiter: HostRateLimiter = HostRateLimiter(minIntervalMillis = 1000L),
    private val circuitBreaker: CircuitBreaker = CircuitBreaker(failureThreshold = 5, resetTimeoutMillis = 60_000L)
) : NovelSource {

    override val sourceType: NovelSourceType = NovelSourceType.NOVEL_PHOENIX

    override suspend fun getBrowsePage(url: String?): Result<com.novelnest.app.data.model.BrowsePage> = withContext(Dispatchers.IO) {
        try {
            val target = com.novelnest.app.data.remote.BrowseParser.validateUrl(sourceType, url ?: com.novelnest.app.data.remote.BrowseParser.initialUrl(sourceType))
            Result.success(com.novelnest.app.data.remote.BrowseParser.parse(sourceType, executePoliteGet(target), target))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun getPopularNovels(): Result<List<Novel>> = withContext(Dispatchers.IO) {
        try {
            val items = NovelPhoenixParser.parseListingHtml(executePoliteGet("${sourceType.baseUrl}/ranking"))
            Result.success(items.take(12).mapIndexed { index, item ->
                val slug = extractCleanSlug(item.slug)
                Novel(id = "np-$slug", title = item.title, coverUrl = item.coverUrl,
                    source = sourceType, sourceUrl = "${sourceType.baseUrl}/novel/$slug",
                    latestChapter = item.latestChapter, rating = item.rating,
                    rank = item.rankBadge?.filter { it.isDigit() }?.toIntOrNull() ?: index + 1)
            })
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private val host = "novelphoenix.com"
    private val defaultUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    // Simple in-memory response caches with timestamps
    private val detailLocks = com.novelnest.app.data.cache.MutexPool()
    private val detailCache = com.novelnest.app.data.cache.MemoryLruCache<String, Pair<Long, PhoenixNovelDetail>>(128)
    private val chapterListCache = com.novelnest.app.data.cache.MemoryLruCache<String, Pair<Long, List<PhoenixChapterItem>>>(16)
    private val chapterContentCache = com.novelnest.app.data.cache.MemoryLruCache<String, PhoenixChapterContent>(64)
    private val chapterContentLocks = com.novelnest.app.data.cache.MutexPool()
    private val cacheTtlMillis = 15 * 60 * 1000L // Refresh mutable details and chapter lists every 15 minutes

    override suspend fun getHomeSections(): Result<List<NovelSection>> = withContext(Dispatchers.IO) {
        runCatching {
            val html = executePoliteGet("${sourceType.baseUrl}/ranking")
            val listingItems = NovelPhoenixParser.parseListingHtml(html)

            val parsedNovels = listingItems.mapIndexed { idx, item ->
                val rankInt = item.rankBadge?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: (idx + 1)
                Novel(
                    id = "np-${item.slug}",
                    title = item.title,
                    coverUrl = item.coverUrl,
                    source = sourceType,
                    sourceUrl = "${sourceType.baseUrl}/novel/${item.slug}",
                    latestChapter = item.latestChapter,
                    rating = item.rating,
                    status = "Ongoing",
                    rank = rankInt
                )
            }

            if (parsedNovels.isEmpty()) {
                SeedData.getSectionsFor(sourceType)
            } else {
                val heroList = parsedNovels.take(3)
                val trendingList = parsedNovels.drop(3).take(7)
                val latestList = parsedNovels.drop(10)

                listOf(
                    NovelSection(
                        id = "np-hero",
                        title = "Phoenix Top Ranked",
                        iconEmoji = "",
                        subtitle = "Top picks on NovelPhoenix",
                        type = SectionType.HERO_SLIDER,
                        novels = heroList.ifEmpty { SeedData.novelPhoenixHeroSlides }
                    ),
                    NovelSection(
                        id = "np-trending",
                        title = "Top Trending Novels",
                        iconEmoji = "",
                        subtitle = "Most read on NovelPhoenix this week",
                        type = SectionType.HORIZONTAL_LIST,
                        novels = trendingList.ifEmpty { SeedData.novelPhoenixTrending }
                    ),
                    NovelSection(
                        id = "np-latest",
                        title = "Recently Added & Updates",
                        iconEmoji = "",
                        subtitle = "Fresh novel updates",
                        type = SectionType.HORIZONTAL_LIST,
                        novels = latestList.ifEmpty { SeedData.novelPhoenixLatest }
                    )
                )
            }
        }.recover { err ->
            SeedData.getSectionsFor(sourceType)
        }
    }

    override suspend fun search(query: String): Result<List<Novel>> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@runCatching emptyList()

            // Try fast JSON search endpoint first
            val searchApiUrl = "${sourceType.baseUrl}/ajax/searchLive?keyword=${java.net.URLEncoder.encode(trimmed, "UTF-8")}&type=title"
            val jsonResponse = runCatching {
                executePoliteGet(searchApiUrl, isAjax = true)
            }.getOrNull()

            val items = if (!jsonResponse.isNullOrBlank()) {
                NovelPhoenixParser.parseLiveSearchJson(jsonResponse)
            } else {
                // Fallback to HTML search page
                val htmlSearchUrl = "${sourceType.baseUrl}/search?keyword=${java.net.URLEncoder.encode(trimmed, "UTF-8")}&type=title"
                val html = executePoliteGet(htmlSearchUrl)
                NovelPhoenixParser.parseListingHtml(html)
            }

            val mapped = items.map { item ->
                val slug = extractCleanSlug(item.slug)
                val rankInt = item.rankBadge?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0
                Novel(
                    id = "np-$slug",
                    title = item.title,
                    coverUrl = item.coverUrl,
                    source = sourceType,
                    sourceUrl = "${sourceType.baseUrl}/novel/$slug",
                    latestChapter = item.latestChapter,
                    rating = item.rating,
                    status = "Ongoing",
                    rank = rankInt
                )
            }
            com.novelnest.app.data.model.NovelIdentity.distinct(mapped)
        }
    }

    override suspend fun getNovelDetails(novelUrl: String): Result<Novel> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedUrl = "${sourceType.baseUrl}/novel/${extractCleanSlug(novelUrl)}"
            val detail = getPhoenixDetail(resolvedUrl)
            Novel(
                id = detail.id,
                title = detail.title,
                coverUrl = detail.coverUrl,
                source = sourceType,
                sourceUrl = "${sourceType.baseUrl}/novel/${detail.slug}",
                synopsis = detail.synopsis,
                author = detail.author,
                rating = detail.rating,
                chapterCount = detail.totalChapters,
                latestChapter = detail.latestChapterTitle.ifBlank { "${detail.totalChapters} Chapters" },
                genres = detail.genres,
                status = detail.status,
                views = detail.viewsRaw,
                rank = detail.rank
            )
        }
    }

    /**
     * Retrieves full detail model, utilizing cache.
     */
    suspend fun getPhoenixDetail(novelUrl: String, forceRefresh: Boolean = false): PhoenixNovelDetail {
        return detailLocks.forKey(novelUrl).withLock {
            val now = System.currentTimeMillis()
            if (!forceRefresh) {
                val cached = detailCache[novelUrl]
                if (cached != null && (now - cached.first) < cacheTtlMillis) {
                    return cached.second
                }
            }

            val html = executePoliteGet(novelUrl)
            val detail = NovelPhoenixParser.parseNovelDetail(html, novelUrl)
            detailCache[novelUrl] = Pair(now, detail)
            detail
        }
    }

    override suspend fun getChapters(
        novelSlugOrUrl: String,
        forceRefresh: Boolean
    ): Result<List<com.novelnest.app.feature.reader.Chapter>> = withContext(Dispatchers.IO) {
        runCatching {
            val slug = extractCleanSlug(novelSlugOrUrl)
            val items = getChapters(slug, fetchAll = true, forceRefresh = forceRefresh)
            items.map { it.toDomainChapter() }
        }
    }

    override fun getChaptersFlow(
        novelSlugOrUrl: String,
        forceRefresh: Boolean
    ): Flow<Result<List<com.novelnest.app.feature.reader.Chapter>>> = flow {
        val slug = extractCleanSlug(novelSlugOrUrl)
        val cacheKey = "slug:$slug"
        val now = System.currentTimeMillis()

        // 1. Check in-memory cache first (instant 0ms response)
        val cached = chapterListCache[cacheKey]
        if (!forceRefresh && cached != null && (now - cached.first) < cacheTtlMillis) {
            emit(Result.success(cached.second.map { it.toDomainChapter() }))
            return@flow
        }

        // 2. Fetch Page 1 immediately for instant display (<800ms)
        val page1Url = "${sourceType.baseUrl}/novel/$slug/chapters?page=1"
        val page1Result = runCatching {
            val page1Html = executePoliteGet(page1Url)
            NovelPhoenixParser.parseChapterListPage(page1Html, defaultPage = 1)
        }

        if (page1Result.isFailure) {
            if (cached != null) {
                emit(Result.success(cached.second.map { it.toDomainChapter() }))
            } else {
                emit(Result.failure(page1Result.exceptionOrNull() ?: IOException("Failed to fetch chapter list")))
            }
            return@flow
        }

        val firstPage = page1Result.getOrThrow()
        if (firstPage.chapters.isEmpty()) {
            if (cached != null) {
                emit(Result.success(cached.second.map { it.toDomainChapter() }))
            } else {
                emit(Result.success(emptyList()))
            }
            return@flow
        }

        val allChapters = ArrayList<PhoenixChapterItem>(firstPage.chapters)
        if (firstPage.totalPages <= 1) {
            chapterListCache[cacheKey] = Pair(now, allChapters.toList())
        }

        // Emit Page 1 chapters immediately so UI is interactive without waiting for all 2000+ chapters
        emit(Result.success(allChapters.map { it.toDomainChapter() }))

        if (firstPage.totalPages <= 1) {
            return@flow
        }

        // 3. Optimized concurrent progressive fetch for remaining batches (pages 2..totalPages)
        val totalPages = firstPage.totalPages
        val semaphore = Semaphore(3) // 3 concurrent requests max to remain polite and fast
        val remainingBatches = java.util.concurrent.ConcurrentHashMap<Int, List<PhoenixChapterItem>>()
        val failedPages = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

        coroutineScope {
            val channel = kotlinx.coroutines.channels.Channel<Pair<Int, List<PhoenixChapterItem>>>(capacity = totalPages)

            val workers = (2..totalPages).map { pageNum ->
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        delay(120L)
                        val pageUrl = "${sourceType.baseUrl}/novel/$slug/chapters?page=$pageNum"
                        val result = runCatching {
                            val html = executePoliteGet(pageUrl)
                            NovelPhoenixParser.parseChapterList(html)
                        }
                        result.onFailure { failedPages += pageNum }
                        result.onSuccess { batch ->
                            if (batch.isEmpty()) failedPages += pageNum
                            else remainingBatches[pageNum] = batch
                        }
                        channel.send(pageNum to result.getOrDefault(emptyList()))
                    }
                }
            }

            launch {
                workers.forEach { it.join() }
                channel.close()
            }

            var lastEmitTime = System.currentTimeMillis()
            var completedCount = 0

            for ((pageNum, batch) in channel) {
                completedCount++

                val currentTime = System.currentTimeMillis()
                // Emit progressive updates periodically (every 750ms) as pages complete
                if (currentTime - lastEmitTime >= 750L || completedCount == totalPages - 1) {
                    lastEmitTime = currentTime
                    val progressList = ArrayList<PhoenixChapterItem>(firstPage.chapters)
                    for (p in 2..totalPages) {
                        // Keep indices stable while pages finish out of order.
                        val page = remainingBatches[p] ?: break
                        progressList.addAll(page)
                    }
                    if (progressList.size > firstPage.chapters.size) {
                        emit(Result.success(progressList.map { it.toDomainChapter() }))
                    }
                }
            }
        }

        // Final assemble in exact page order and cache
        val fullList = ArrayList<PhoenixChapterItem>(firstPage.chapters)
        for (p in 2..totalPages) {
            remainingBatches[p]?.let { fullList.addAll(it) }
        }

        if (failedPages.isNotEmpty()) {
            emit(Result.failure(IOException("Failed to fetch NovelPhoenix chapter pages: ${failedPages.sorted().joinToString()}")))
            return@flow
        }

        chapterListCache[cacheKey] = Pair(now, fullList)
        emit(Result.success(fullList.map { it.toDomainChapter() }))
    }.flowOn(Dispatchers.IO)

    override suspend fun getChapterContent(
        chapterUrlOrId: String,
        forceRefresh: Boolean
    ): Result<com.novelnest.app.feature.reader.Chapter> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedUrl = NovelPhoenixUtils.resolveUrl(chapterUrlOrId)
            val content = fetchChapterContent(resolvedUrl, forceRefresh)
            com.novelnest.app.feature.reader.Chapter(
                id = chapterUrlOrId,
                title = content.title,
                paragraphs = content.paragraphs,
                url = resolvedUrl,
                prevUrl = content.prevChapterUrl,
                nextUrl = content.nextChapterUrl
            )
        }
    }

    /**
     * Optimized chapter fetching:
     * - Fetches Page 1 immediately to determine total pages and yield initial 100 chapters.
     * - When [fetchAll] is true, fetches remaining pages (2..totalPages) concurrently using
     *   a polite semaphore-bounded worker pool, caching all chapters permanently.
     */
    suspend fun getChapters(
        novelSlug: String,
        fetchAll: Boolean = true,
        forceRefresh: Boolean = false
    ): List<PhoenixChapterItem> {
        val cacheKey = "slug:$novelSlug"
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = chapterListCache[cacheKey]
            if (cached != null && (now - cached.first) < cacheTtlMillis && cached.second.isNotEmpty()) {
                return cached.second
            }
        }

        // 1. Fetch Page 1
        val page1Url = "${sourceType.baseUrl}/novel/$novelSlug/chapters?page=1"
        val page1Html = executePoliteGet(page1Url)
        val page1Result = NovelPhoenixParser.parseChapterListPage(page1Html, defaultPage = 1)
        if (page1Result.chapters.isEmpty()) {
            return emptyList()
        }

        val allChapters = ArrayList<PhoenixChapterItem>(page1Result.chapters.size * page1Result.totalPages.coerceAtLeast(1))
        allChapters.addAll(page1Result.chapters)

        if (!fetchAll || page1Result.totalPages <= 1) {
            if (fetchAll) chapterListCache[cacheKey] = Pair(now, allChapters.toList())
            return allChapters
        }

        // 2. Fetch remaining pages concurrently
        val totalPages = page1Result.totalPages
        val semaphore = Semaphore(3)
        val remainingBatches = mutableMapOf<Int, List<PhoenixChapterItem>>()

        coroutineScope {
            val deferredList = (2..totalPages).map { pageNum ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        delay(120L)
                        val pageUrl = "${sourceType.baseUrl}/novel/$novelSlug/chapters?page=$pageNum"
                        val result = runCatching {
                            val html = executePoliteGet(pageUrl)
                            NovelPhoenixParser.parseChapterList(html)
                        }
                        pageNum to result
                    }
                }
            }

            for (deferred in deferredList) {
                val (pageNum, result) = deferred.await()
                val batch = result.getOrElse { throw IOException("Failed to fetch NovelPhoenix chapter page $pageNum", it) }
                if (batch.isEmpty()) throw IOException("NovelPhoenix chapter page $pageNum returned no chapters")
                remainingBatches[pageNum] = batch
            }
        }

        val fullList = ArrayList<PhoenixChapterItem>(page1Result.chapters)
        for (p in 2..totalPages) {
            remainingBatches[p]?.let { fullList.addAll(it) }
        }

        if (fullList.isNotEmpty()) {
            chapterListCache[cacheKey] = Pair(now, fullList)
        }
        return fullList
    }

    private fun PhoenixChapterItem.toDomainChapter() = com.novelnest.app.feature.reader.Chapter(
        id = id,
        title = title,
        paragraphs = emptyList(),
        url = urlPath
    )

    /**
     * Fetches all chapters of a novel by paging through /novel/{slug}/chapters?page=N starting at 1
     * until an empty page returns.
     */
    suspend fun getAllChapters(novelSlug: String, forceRefresh: Boolean = false): List<PhoenixChapterItem> {
        return getChapters(novelSlug, fetchAll = true, forceRefresh = forceRefresh)
    }

    /**
     * Fetches and parses a single chapter reader content.
     */
    suspend fun fetchChapterContent(chapterUrl: String, forceRefresh: Boolean = false): PhoenixChapterContent = withContext(Dispatchers.IO) {
        val resolvedUrl = NovelPhoenixUtils.resolveUrl(chapterUrl)
        // Prefetch and a foreground read can arrive together. Fetch a URL only once.
        chapterContentLocks.forKey(resolvedUrl).withLock {
            if (!forceRefresh) {
                val cached = chapterContentCache[resolvedUrl]
                if (cached != null && cached.paragraphs.isNotEmpty()) {
                    return@withLock cached
                }
            }
            val html = executePoliteGet(resolvedUrl)
            val parsed = NovelPhoenixParser.parseChapterContent(html)
            if (parsed.paragraphs.isNotEmpty()) {
                chapterContentCache[resolvedUrl] = parsed
            }
            parsed
        }
    }

    /**
     * Executes an HTTP GET request with rate limiting, retries, and blocked detection.
     */
    /**
     * Resets the circuit breaker, allowing requests to proceed.
     * Call this when the user manually retries.
     */
    fun resetCircuitBreaker() {
        circuitBreaker.reset(host)
    }

    private fun extractCleanSlug(slugOrUrl: String): String {
        val trimmed = slugOrUrl.trim()
        val mappedLegacy = mapLegacyIdToSlug(trimmed)
        if (mappedLegacy != null) return mappedLegacy

        val path = runCatching { java.net.URI(trimmed).path }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: trimmed.substringBefore('?').substringBefore('#')
        val withoutPrefix = path.removePrefix("np-").removePrefix("fwn-").removePrefix("asura-").trimEnd('/')
        val lastSegment = withoutPrefix.substringAfterLast('/')
        val mappedFromLast = mapLegacyIdToSlug(lastSegment)
        if (mappedFromLast != null) return mappedFromLast

        return lastSegment.removePrefix("np-").removePrefix("fwn-").removePrefix("asura-")
    }

    private fun mapLegacyIdToSlug(id: String): String? = when (id) {
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

    private suspend fun executePoliteGet(url: String, isAjax: Boolean = false, maxRetries: Int = 3): String {
        if (!circuitBreaker.canExecute(host)) {
            throw PhoenixException.SourceBlockedError(
                message = "Circuit breaker open: NovelPhoenix is temporarily paused after consecutive blocks."
            )
        }

        var attempt = 0
        var backoffMs = 1000L

        while (attempt < maxRetries) {
            attempt++
            rateLimiter.acquire(host)

            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", defaultUserAgent)
                .header("Accept", if (isAjax) "application/json, text/javascript, */*; q=0.01" else "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

            if (isAjax) {
                reqBuilder.header("X-Requested-With", "XMLHttpRequest")
            }

            try {
                val response = client.newCall(reqBuilder.build()).execute()
                val code = response.code

                when {
                    code == 403 || code == 429 -> {
                        // Only genuine blocks should trip the circuit breaker
                        circuitBreaker.recordFailure(host)
                        throw PhoenixException.SourceBlockedError(statusCode = code)
                    }
                    code == 404 -> {
                        // 404 is NOT a block — don't trip breaker
                        throw PhoenixException.ResourceNotFoundError(url)
                    }
                    code >= 500 -> {
                        // Server errors: retry but do NOT trip the circuit breaker
                        // (transient server issues shouldn't block all future requests)
                        if (attempt >= maxRetries) {
                            throw IOException("HTTP Server Error $code on $url")
                        }
                        delay(backoffMs)
                        backoffMs *= 2
                        continue
                    }
                    !response.isSuccessful -> {
                        throw IOException("Unexpected HTTP code $code on $url")
                    }
                    else -> {
                        val bodyString = response.body?.string().orEmpty()
                        if (NovelPhoenixUtils.isCloudflareChallenge(bodyString)) {
                            circuitBreaker.recordFailure(host)
                            throw PhoenixException.SourceBlockedError(isCloudflareChallenge = true)
                        }
                        circuitBreaker.recordSuccess(host)
                        return bodyString
                    }
                }
            } catch (e: PhoenixException) {
                throw e
            } catch (e: Exception) {
                // Transient network errors (timeout, DNS, connection reset) — do NOT trip breaker
                if (attempt >= maxRetries) throw e
                delay(backoffMs)
                backoffMs *= 2
            }
        }

        throw IOException("Failed to fetch $url after $maxRetries attempts")
    }
}
