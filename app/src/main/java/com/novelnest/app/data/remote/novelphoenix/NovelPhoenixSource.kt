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
import kotlinx.coroutines.delay
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
    private val circuitBreaker: CircuitBreaker = CircuitBreaker(failureThreshold = 3, resetTimeoutMillis = 180_000L)
) : NovelSource {

    override val sourceType: NovelSourceType = NovelSourceType.NOVEL_PHOENIX

    private val host = "novelphoenix.com"
    private val defaultUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    // Simple in-memory response caches with timestamps
    private val detailCache = mutableMapOf<String, Pair<Long, PhoenixNovelDetail>>()
    private val chapterListCache = mutableMapOf<String, Pair<Long, List<PhoenixChapterItem>>>()
    private val cacheTtlMillis = 10 * 60 * 1000L // 10 minutes

    override suspend fun getHomeSections(): Result<List<NovelSection>> = withContext(Dispatchers.IO) {
        runCatching {
            val html = executePoliteGet("${sourceType.baseUrl}/ranking")
            val listingItems = NovelPhoenixParser.parseListingHtml(html)

            val parsedNovels = listingItems.mapIndexed { idx, item ->
                Novel(
                    id = "np-${item.slug}",
                    title = item.title,
                    coverUrl = item.coverUrl,
                    source = sourceType,
                    sourceUrl = "${sourceType.baseUrl}/novel/${item.slug}",
                    latestChapter = item.latestChapter,
                    rating = item.rating,
                    status = "Ongoing"
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
                Novel(
                    id = "np-${item.slug}",
                    title = item.title,
                    coverUrl = item.coverUrl,
                    source = sourceType,
                    sourceUrl = "${sourceType.baseUrl}/novel/${item.slug}",
                    latestChapter = item.latestChapter,
                    rating = item.rating,
                    status = "Ongoing"
                )
            }
            if (mapped.isEmpty()) SeedData.search(sourceType, query) else mapped
        }.recover { err ->
            SeedData.search(sourceType, query)
        }
    }

    override suspend fun getNovelDetails(novelUrl: String): Result<Novel> = withContext(Dispatchers.IO) {
        runCatching {
            val detail = getPhoenixDetail(novelUrl)
            Novel(
                id = detail.id,
                title = detail.title,
                coverUrl = detail.coverUrl,
                source = sourceType,
                sourceUrl = "${sourceType.baseUrl}/novel/${detail.slug}",
                synopsis = detail.synopsis,
                author = detail.author,
                rating = detail.rating,
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
        return detail
    }

    /**
     * Fetches all chapters of a novel by paging through /novel/{slug}/chapters?page=N starting at 1
     * until an empty page returns.
     */
    suspend fun getAllChapters(novelSlug: String, forceRefresh: Boolean = false): List<PhoenixChapterItem> {
        val cacheKey = "slug:$novelSlug"
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = chapterListCache[cacheKey]
            if (cached != null && (now - cached.first) < cacheTtlMillis) {
                return cached.second
            }
        }

        val allChapters = mutableListOf<PhoenixChapterItem>()
        var page = 1
        val maxPagesSafetyLimit = 100 // up to 10,000 chapters

        while (page <= maxPagesSafetyLimit) {
            val pageUrl = "${sourceType.baseUrl}/novel/$novelSlug/chapters?page=$page"
            val html = executePoliteGet(pageUrl)
            val batch = NovelPhoenixParser.parseChapterList(html)
            if (batch.isEmpty()) {
                break
            }
            allChapters.addAll(batch)
            page++
        }

        chapterListCache[cacheKey] = Pair(now, allChapters)
        return allChapters
    }

    /**
     * Fetches and parses a single chapter reader content.
     */
    suspend fun getChapterContent(chapterUrl: String): PhoenixChapterContent {
        val html = executePoliteGet(chapterUrl)
        return NovelPhoenixParser.parseChapterContent(html)
    }

    /**
     * Executes an HTTP GET request with rate limiting, retries, and blocked detection.
     */
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
                        circuitBreaker.recordFailure(host)
                        throw PhoenixException.SourceBlockedError(statusCode = code)
                    }
                    code == 404 -> {
                        throw PhoenixException.ResourceNotFoundError(url)
                    }
                    code >= 500 -> {
                        if (attempt >= maxRetries) {
                            circuitBreaker.recordFailure(host)
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
                if (attempt >= maxRetries) throw e
                delay(backoffMs)
                backoffMs *= 2
            }
        }

        throw IOException("Failed to fetch $url after $maxRetries attempts")
    }
}
