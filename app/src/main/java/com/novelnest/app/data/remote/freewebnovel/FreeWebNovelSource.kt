package com.novelnest.app.data.remote.freewebnovel

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.remote.NovelSource
import com.novelnest.app.data.remote.network.HostRateLimiter
import com.novelnest.app.data.remote.network.HttpFactory
import com.novelnest.app.feature.reader.Chapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import com.novelnest.app.data.cache.MemoryLruCache
import com.novelnest.app.data.cache.MutexPool

class FreeWebNovelSource(
    private val client: OkHttpClient = HttpFactory.getClient(),
    private val rateLimiter: HostRateLimiter = HostRateLimiter(1_000L)
) : NovelSource {
    override val sourceType = NovelSourceType.FREE_WEB_NOVEL
    private val cacheTtl = 15 * 60 * 1_000L
    private val detailCache = MemoryLruCache<String, FreeWebNovelCached<Novel>>(128)
    private val pageCache = MemoryLruCache<String, FreeWebNovelCached<FreeWebNovelChapterPage>>(64)
    private val listCache = MemoryLruCache<String, FreeWebNovelCached<List<Chapter>>>(16)
    private val contentCache = MemoryLruCache<String, Chapter>(64)
    private val detailLocks = MutexPool()
    private val listLocks = MutexPool()
    private val pageLocks = MutexPool()
    private val contentLocks = MutexPool()

    private suspend fun <T> result(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun getHomeSections(): Result<List<NovelSection>> = withContext(Dispatchers.IO) {
        result {
            val home = request(FreeWebNovelUrls.BASE_URL + "/home")
            val popular = request(FreeWebNovelUrls.BASE_URL + "/sort/most-popular")
            FreeWebNovelParser.parseHome(home, popular)
        }
    }

    override suspend fun search(query: String): Result<List<Novel>> = withContext(Dispatchers.IO) {
        result {
            if (query.isBlank()) emptyList() else {
                val url = FreeWebNovelUrls.BASE_URL + "/search?keyword=" + URLEncoder.encode(query.trim(), "UTF-8")
                FreeWebNovelParser.parseListing(request(url), url)
            }
        }
    }

    override suspend fun getBrowsePage(url: String?): Result<com.novelnest.app.data.model.BrowsePage> = withContext(Dispatchers.IO) {
        try {
            val target = com.novelnest.app.data.remote.BrowseParser.validateUrl(sourceType, url ?: com.novelnest.app.data.remote.BrowseParser.initialUrl(sourceType))
            Result.success(com.novelnest.app.data.remote.BrowseParser.parse(sourceType, request(target), target))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun getPopularNovels(): Result<List<Novel>> = withContext(Dispatchers.IO) {
        result {
            val url = FreeWebNovelUrls.BASE_URL + "/sort/most-popular"
            FreeWebNovelParser.parseListing(request(url), url).take(12)
        }
    }

    override suspend fun getNovelDetails(novelUrl: String): Result<Novel> = withContext(Dispatchers.IO) {
        result {
            val url = FreeWebNovelUrls.novelUrl(novelUrl)
            detailLocks.forKey(url).withLock {
                detailCache[url]?.takeIf { it.fresh(cacheTtl) }?.value ?: run {
                    val detail = FreeWebNovelParser.parseDetails(request(url), url)
                    detailCache[url] = FreeWebNovelCached(detail)
                    detail
                }
            }
        }
    }

    override fun getChaptersFlow(novelSlugOrUrl: String, forceRefresh: Boolean): Flow<Result<List<Chapter>>> = flow {
        val url = FreeWebNovelUrls.novelUrl(novelSlugOrUrl)
        listLocks.forKey(url).withLock {
            val cached = listCache[url]
            if (!forceRefresh && cached != null && cached.fresh(cacheTtl)) {
                emit(Result.success(cached.value))
                return@withLock
            }
            val first = chapterPage(url, 1, forceRefresh)
            if (first.totalChapters == 0 && first.chapters.isEmpty()) {
                emit(Result.success(emptyList()))
                return@withLock
            }
            if (first.chapters.isEmpty() || first.totalPages !in 1..10_000) {
                throw FreeWebNovelException.IncompleteChapterList("FreeWebNovel returned no valid chapter list")
            }
            val chapters = first.chapters.toMutableList()
            emit(Result.success(chapters.toList()))
            for (page in 2..first.totalPages) {
                val next = chapterPage(url, page, forceRefresh)
                if (next.page != page || next.chapters.isEmpty()) throw FreeWebNovelException.IncompleteChapterList("FreeWebNovel chapter page $page could not be loaded")
                chapters.addAll(next.chapters)
                emit(Result.success(chapters.distinctBy { it.url }))
            }
            val complete = chapters.distinctBy { it.url }
            if (complete.size < first.totalChapters) throw FreeWebNovelException.IncompleteChapterList("Some FreeWebNovel chapters are missing. Retry to finish loading.")
            listCache[url] = FreeWebNovelCached(complete)
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(Result.failure(error))
    }.flowOn(Dispatchers.IO)

    override suspend fun getChapters(novelSlugOrUrl: String, forceRefresh: Boolean): Result<List<Chapter>> =
        getChaptersFlow(novelSlugOrUrl, forceRefresh).last()

    private suspend fun chapterPage(novelUrl: String, page: Int, forceRefresh: Boolean): FreeWebNovelChapterPage {
        val url = "$novelUrl?ajax=chapters&page=$page&pageSize=100"
        return pageLocks.forKey(url).withLock {
            pageCache[url]?.takeIf { !forceRefresh && it.fresh(cacheTtl) }?.value ?: run {
                val parsed = FreeWebNovelParser.parseChapterPage(request(url, ajax = true), novelUrl)
                if (parsed.page != page) throw FreeWebNovelException.IncompleteChapterList("FreeWebNovel returned an unexpected chapter page")
                pageCache[url] = FreeWebNovelCached(parsed)
                parsed
            }
        }
    }

    override suspend fun getChapterContent(chapterUrlOrId: String, forceRefresh: Boolean): Result<Chapter> = withContext(Dispatchers.IO) {
        result {
            val url = FreeWebNovelUrls.resolve(chapterUrlOrId)
            FreeWebNovelUrls.chapterId(url)
            contentLocks.forKey(url).withLock {
                contentCache[url]?.takeIf { !forceRefresh } ?: run {
                    val chapter = FreeWebNovelParser.parseContent(request(url), url)
                    contentCache[url] = chapter
                    chapter
                }
            }
        }
    }


    private suspend fun request(url: String, ajax: Boolean = false): String {
        var lastFailure: IOException? = null
        repeat(3) { attempt ->
            rateLimiter.acquire("freewebnovel.com")
            val builder = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Accept", if (ajax) "application/json" else "text/html,application/xhtml+xml")
            if (ajax) builder.header("X-Requested-With", "XMLHttpRequest")
            try {
                client.newCall(builder.build()).execute().use { response ->
                    when {
                        response.code == 403 -> throw FreeWebNovelException.Blocked()
                        response.code == 404 -> throw FreeWebNovelException.NotFound()
                        !response.isSuccessful -> throw FreeWebNovelException.HttpError(response.code)
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) throw IOException("FreeWebNovel returned an empty response")
                    FreeWebNovelParser.document(body, url)
                    return body
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                if (error is FreeWebNovelException && (error !is FreeWebNovelException.HttpError || !error.retryable)) throw error
                lastFailure = if (error is FreeWebNovelException) error else FreeWebNovelException.NetworkError(error)
                if (attempt < 2) delay(1_000L * (1L shl attempt))
            }
        }
        throw lastFailure ?: IOException("Unable to connect to FreeWebNovel")
    }
}
