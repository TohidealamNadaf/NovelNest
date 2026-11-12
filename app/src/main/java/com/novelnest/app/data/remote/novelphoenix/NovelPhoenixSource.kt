package com.novelnest.app.data.remote.novelphoenix

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.model.SectionType
import com.novelnest.app.data.remote.NovelSource
import com.novelnest.app.data.remote.network.CircuitBreaker
import com.novelnest.app.data.remote.network.HostRateLimiter
import com.novelnest.app.data.remote.network.HttpFactory
import com.novelnest.app.data.remote.awaitTextResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Robust, polite remote source implementation for NovelPhoenix.
 *
 * Politeness & Resilience:
 * - Request throttling: HostRateLimiter enforcing >= 1000ms between requests.
 * - Pause after blocks and retry transient 5xx/network errors with bounded backoff.
 * - CircuitBreaker preventing hammer attacks when blocked.
 * - Applies a host cooldown for 403, 429, and detected Cloudflare challenges.
 * - In-memory cache for detail and chapter list requests.
 */
class NovelPhoenixSource(
    private val client: OkHttpClient = HttpFactory.getClient(),
    private val rateLimiter: HostRateLimiter = HostRateLimiter(minIntervalMillis = 1000L),
    private val circuitBreaker: CircuitBreaker = CircuitBreaker(failureThreshold = 5, resetTimeoutMillis = 60_000L)
) : NovelSource {

    override val sourceType: NovelSourceType = NovelSourceType.NOVEL_PHOENIX
    override fun isCoolingDown(): Boolean = rateLimiter.cooldown(host) != null

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
    private val chapterCatalog = NovelPhoenixChapterLoader(fetchPage = { slug, page ->
        val html = executePoliteGet(sourceType.baseUrl + "/novel/" + slug + "/chapters?page=" + page,
            maxRetries = 2, callTimeoutMillis = 15_000L)
        NovelPhoenixParser.parseChapterListPage(html, defaultPage = page)
    })
    private val chapterContentCache = com.novelnest.app.data.cache.MemoryLruCache<String, PhoenixChapterContent>(64)
    private val chapterContentLocks = com.novelnest.app.data.cache.MutexPool()
    private val cacheTtlMillis = 15 * 60 * 1000L // Refresh mutable details and chapter lists every 15 minutes

    override suspend fun getHomeSections(): Result<List<NovelSection>> = withContext(Dispatchers.IO) {
        try {
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
                throw PhoenixException.ParseError("home", "No live novels were found")
            } else {
                val heroList = parsedNovels.take(3)
                val trendingList = parsedNovels.drop(3).take(7)
                val latestList = parsedNovels.drop(10)

                Result.success(listOf(
                    NovelSection(
                        id = "np-live-hero-v1",
                        title = "Phoenix Top Ranked",
                        iconEmoji = "",
                        subtitle = "Top picks on NovelPhoenix",
                        type = SectionType.HERO_SLIDER,
                        novels = heroList
                    ),
                    NovelSection(
                        id = "np-live-trending-v1",
                        title = "Top Trending Novels",
                        iconEmoji = "",
                        subtitle = "Most read on NovelPhoenix this week",
                        type = SectionType.HORIZONTAL_LIST,
                        novels = trendingList
                    ),
                    NovelSection(
                        id = "np-live-latest-v1",
                        title = "Recently Added & Updates",
                        iconEmoji = "",
                        subtitle = "Fresh novel updates",
                        type = SectionType.HORIZONTAL_LIST,
                        novels = latestList
                    )
                ).filter { it.novels.isNotEmpty() })
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
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
    ): Result<List<com.novelnest.app.data.model.Chapter>> = withContext(Dispatchers.IO) {
        try {
            val items = getChapters(extractCleanSlug(novelSlugOrUrl), fetchAll = true, forceRefresh = forceRefresh)
            Result.success(items.map { it.toDomainChapter() })
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
    }

    override fun getChaptersFlow(
        novelSlugOrUrl: String,
        forceRefresh: Boolean
    ): Flow<Result<List<com.novelnest.app.data.model.Chapter>>> =
        chapterCatalog.chapters(extractCleanSlug(novelSlugOrUrl), forceRefresh).map { result ->
            result.map { chapters -> chapters.map { it.toDomainChapter() } }
        }.flowOn(Dispatchers.IO)

    override suspend fun getChapterContent(
        chapterUrlOrId: String,
        forceRefresh: Boolean
    ): Result<com.novelnest.app.data.model.Chapter> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedUrl = NovelPhoenixUtils.resolveUrl(chapterUrlOrId)
            val content = fetchChapterContent(resolvedUrl, forceRefresh)
            com.novelnest.app.data.model.Chapter(
                id = chapterUrlOrId,
                title = content.title,
                paragraphs = content.paragraphs,
                url = resolvedUrl,
                prevUrl = content.prevChapterUrl,
                nextUrl = content.nextChapterUrl
            )
        }
    }

    /** The same resumable loader serves both progressive and complete catalog requests. */
    suspend fun getChapters(
        novelSlug: String,
        fetchAll: Boolean = true,
        forceRefresh: Boolean = false
    ): List<PhoenixChapterItem> {
        val slug = extractCleanSlug(novelSlug)
        return if (fetchAll) chapterCatalog.chapters(slug, forceRefresh).last().getOrThrow()
        else chapterCatalog.firstPage(slug, forceRefresh)
    }

    private fun PhoenixChapterItem.toDomainChapter() = com.novelnest.app.data.model.Chapter(
        id = id,
        title = title,
        paragraphs = emptyList(),
        url = urlPath
    )

    /**
     * Fetches validated pages through the advertised final range; never probes past the catalog.
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

    private suspend fun executePoliteGet(url: String, isAjax: Boolean = false, maxRetries: Int = 3, callTimeoutMillis: Long? = null): String {
        rateLimiter.cooldown(host)?.let { throw it }
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
                val call = client.newCall(reqBuilder.build())
                if (callTimeoutMillis != null) call.timeout().timeout(callTimeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                val response = call.awaitTextResponse()
                val code = response.code

                when {
                    code == 403 || code == 429 || response.cloudflareChallenge -> {
                        circuitBreaker.recordFailure(host)
                        throw rateLimiter.block(host, code, response.cloudflareChallenge, response.retryAfter)
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
                        val bodyString = response.body
                        if (NovelPhoenixUtils.isCloudflareChallenge(bodyString)) {
                            circuitBreaker.recordFailure(host)
                            throw rateLimiter.block(host, code, true, response.retryAfter)
                        }
                        circuitBreaker.recordSuccess(host)
                        return bodyString
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: com.novelnest.app.data.remote.network.SourceCooldownException) {
                throw e
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
