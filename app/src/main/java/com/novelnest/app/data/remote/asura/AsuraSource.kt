package com.novelnest.app.data.remote.asura

import com.novelnest.app.data.cache.MemoryLruCache
import com.novelnest.app.data.cache.MutexPool
import com.novelnest.app.data.model.*
import com.novelnest.app.data.remote.ManhwaSource
import com.novelnest.app.data.remote.network.HttpFactory
import com.novelnest.app.feature.reader.Chapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class AsuraSource(
    client: OkHttpClient = HttpFactory.getClient(),
    private val http: AsuraHttp = AsuraHttpClient(client),
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val cachedChapterBudget: Int = 12_000
) : ManhwaSource {
    override val sourceType = NovelSourceType.ASURA_SCANS
    private val locks = MutexPool()
    private val homes = MemoryLruCache<String, AsuraCached<List<NovelSection>>>(1)
    private val details = MemoryLruCache<String, AsuraCached<ManhwaDetails>>(24)
    private val browse = MemoryLruCache<String, AsuraCached<BrowsePage>>(24)
    private val searches = MemoryLruCache<String, AsuraCached<List<Novel>>>(24)
    private val content = MemoryLruCache<String, AsuraCached<ManhwaChapterContent>>(8)
    private val catalogTrim = Any()
    init { require(cachedChapterBudget > 0) }

    private fun trimCatalogs() = synchronized(catalogTrim) {
        val entries = details.valuesSnapshot()
        var count = entries.sumOf { it.value.chapters.size }
        for (entry in entries) {
            if (count <= cachedChapterBudget) break
            details.remove(entry.value.novel.sourceUrl)?.let { count -= it.value.chapters.size }
        }
    }

    private suspend fun <T> result(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try { Result.success(block()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: AsuraException) { Result.failure(error) }
        catch (error: Exception) { Result.failure(AsuraException.Parse(error.message ?: "Unsupported response", error)) }
    }
    private suspend fun <T> cached(cache: MemoryLruCache<String, AsuraCached<T>>, key: String, force: Boolean,
        ttl: Long = AsuraCachePolicy.METADATA_TTL_MS, load: suspend () -> T): T = locks.forKey(key).withLock {
        cache[key]?.takeIf { !force && clock() - it.fetchedAt in 0 until ttl }?.value
            ?: load().also {
                cache[key] = AsuraCached(it, clock())
                if (it is ManhwaDetails) trimCatalogs()
            }
    }
    override suspend fun getHomeSections(): Result<List<NovelSection>> = getHomeSections(false)
    suspend fun getHomeSections(forceRefresh: Boolean): Result<List<NovelSection>> = result {
        cached(homes, "home", forceRefresh) { AsuraParser.home(http.get(AsuraUrls.BASE + "/", forceRefresh)) }
    }
    override suspend fun getPopularNovels(): Result<List<Novel>> = getHomeSections().map { sections ->
        sections.firstOrNull { it.id == "asura-live-popular" }?.novels.orEmpty()
    }
    override suspend fun getBrowsePage(url: String?): Result<BrowsePage> = getBrowsePage(url, false)
    suspend fun getBrowsePage(url: String?, forceRefresh: Boolean): Result<BrowsePage> = result {
        val canonical = AsuraUrls.browse(url)
        cached(browse, canonical, forceRefresh) { AsuraParser.browse(http.get(canonical, forceRefresh), canonical) }
    }
    override suspend fun search(query: String): Result<List<Novel>> = result {
        if (query.isBlank()) emptyList() else {
            val url = AsuraUrls.search(query)
            cached(searches, url, false) { AsuraParser.search(http.get(url, false)) }
        }
    }
    override suspend fun getManhwaDetails(seriesUrlOrId: String, forceRefresh: Boolean): Result<ManhwaDetails> = result {
        val url = AsuraUrls.seriesUrl(seriesUrlOrId)
        cached(details, url, forceRefresh) { AsuraParser.details(http.get(url, forceRefresh), url) }
    }
    override suspend fun getNovelDetails(novelUrl: String): Result<Novel> = getManhwaDetails(novelUrl).map { it.novel }
    override suspend fun getChapters(novelSlugOrUrl: String, forceRefresh: Boolean): Result<List<Chapter>> =
        getManhwaDetails(novelSlugOrUrl, forceRefresh).map { details ->
            details.chapters.map { Chapter(id = it.id, title = it.title, paragraphs = emptyList(), url = it.url) }
        }
    override suspend fun getManhwaChapter(chapterUrl: String, forceRefresh: Boolean): Result<ManhwaChapterContent> = result {
        val (slug, number) = AsuraUrls.chapterParts(chapterUrl)
        val url = AsuraUrls.chapterUrl(slug, number)
        cached(content, url, forceRefresh, AsuraCachePolicy.PAGE_URL_TTL_MS) {
            try { AsuraParser.chapterContent(http.get(AsuraUrls.chapterApi(url), forceRefresh), url) }
            catch (_: AsuraException.NotFound) { AsuraParser.chapterHtml(http.get(url, forceRefresh), url) }
        }
    }
    override suspend fun getChapterContent(chapterUrlOrId: String, forceRefresh: Boolean): Result<Chapter> =
        Result.failure(UnsupportedOperationException("Asura chapters contain images. Open the dedicated manhwa reader."))
}
