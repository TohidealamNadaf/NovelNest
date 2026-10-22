package com.novelnest.app.data.remote

import android.content.ContextWrapper
import android.content.SharedPreferences
import com.novelnest.app.data.bookmark.BookmarkManager
import com.novelnest.app.data.model.*
import com.novelnest.app.data.remote.freewebnovel.*
import com.novelnest.app.data.remote.novelphoenix.NovelPhoenixSource
import com.novelnest.app.data.remote.network.HostRateLimiter
import com.novelnest.app.data.repository.NovelRepositoryImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.last
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

fun sourceFixture(source: NovelSourceType, name: String): String {
    val folder = if (source == NovelSourceType.FREE_WEB_NOVEL) "freewebnovel" else "novelphoenix"
    val path = "docs/sources/$folder/fixtures/$name"
    return listOf(File(path), File("../$path")).first { it.exists() }.readText()
}

fun bookmarkContext(): ContextWrapper {
    val values = mutableMapOf<String, String?>()
    val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
        when (method.name) {
            "putString" -> { values[args[0] as String] = args[1] as String?; proxy }
            "apply" -> null
            "commit" -> true
            else -> error(method.name)
        }
    } as SharedPreferences.Editor
    val prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
        when (method.name) {
            "getString" -> values[args[0] as String] ?: args[1]
            "edit" -> editor
            else -> error(method.name)
        }
    } as SharedPreferences
    return object : ContextWrapper(null) {
        override fun getSharedPreferences(name: String, mode: Int) = prefs
    }
}

/**
 * Real source/repository/parser stack with deterministic HTTP fixtures.
 * Detail and reader fixtures are adapted to the selected card identity; chapter
 * pagination is shortened to one real fixture page so these tests need no network.
 */
abstract class SourceRouteContract {
    abstract val kind: NovelSourceType
    private val fwn get() = kind == NovelSourceType.FREE_WEB_NOVEL

    @Test fun discoverRecordsReachMetadataChaptersAndReader() = exercise("discover")
    @Test fun browseAllRecordsReachMetadataChaptersAndReader() = exercise("browse")
    @Test fun sourceCategoryRecordsReachMetadataChaptersAndReader() = exercise("category")
    @Test fun searchRecordsReachMetadataChaptersAndReader() = exercise("search")
    @Test fun restoredHomeBookmarksReachMetadataChaptersAndReader() = exercise("bookmark")

    private fun exercise(route: String) = runBlocking {
        val calls = ConcurrentHashMap<String, AtomicInteger>()
        var chosen: Novel? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            calls.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            fun fixture(name: String) = sourceFixture(kind, name)
            fun adapt(html: String) = html.replace("shadow-slave", chosen?.sourceUrl?.substringAfterLast('/') ?: "shadow-slave")
                .replace("Shadow Slave", chosen?.title ?: "Shadow Slave")
            val body = when {
                path == "/home" -> fixture("home.html")
                path == "/ranking" -> fixture("ranking.html")
                path.startsWith("/sort/") -> fixture(if (path == "/sort/most-popular") "popular.html" else "latest.html")
                path.startsWith("/genre/") -> fixture("popular.html")
                path.startsWith("/genre-") -> fixture(if (route == "category") "browse_fantasy.html" else "browse_new.html")
                path == "/ajax/searchLive" -> fixture("search_live_shadow.json")
                path == "/search" -> fixture(if (fwn) "search_shadow.html" else "search_page_shadow.html")
                path.contains("/chapter-") -> adapt(fixture("chapter_1_shadow_slave.html")).let {
                    if (path.endsWith("chapter-2")) it.replace("chapter-1", "chapter-2") else it
                }
                path.endsWith("/chapters") -> {
                    val doc = Jsoup.parse(adapt(fixture("chapters_shadow_slave.html")))
                    doc.select("select[aria-label='Chapter range'], .chaprange select").remove()
                    doc.outerHtml()
                }
                request.url.queryParameter("ajax") == "chapters" -> {
                    JSONObject(adapt(fixture("chapters_ajax_1.json"))).put("totalPage", 1).put("totalChapters", 100).toString()
                }
                path.startsWith("/novel/") -> adapt(fixture("detail_shadow_slave.html"))
                else -> error("Unexpected request: $request")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body(body.toResponseBody("text/html".toMediaType())).build()
        }.build()
        val source: NovelSource = if (fwn) FreeWebNovelSource(client, HostRateLimiter(0))
            else NovelPhoenixSource(client, HostRateLimiter(0))
        val repository = NovelRepositoryImpl(sources = mapOf(kind to source), rateLimiter = HostRateLimiter(0))
        val candidates = when (route) {
            "discover" -> repository.getHomeSections(kind).last().getOrThrow().flatMap { it.novels }
            "browse" -> repository.getBrowsePage(kind).getOrThrow().novels
            "category" -> {
                val page = repository.getBrowsePage(kind).getOrThrow()
                val category = page.categories.first { it.name == "Fantasy" }
                repository.getBrowsePage(kind, category.url).getOrThrow().novels
            }
            else -> repository.searchNovels(kind, "Shadow Slave").getOrThrow()
        }
        assertTrue(candidates.isNotEmpty())
        var entry = candidates.first()
        chosen = entry
        val context = bookmarkContext()
        val bookmarks = BookmarkManager(context)
        if (route == "bookmark") {
            bookmarks.addBookmark(entry)
            entry = BookmarkManager(context).bookmarks.value.single()
        }
        repository.rememberNovel(entry) // Same shared callback used by every screen.
        assertEquals(entry.id, repository.getNovelById(entry.id)?.id)
        val detail = repository.getNovelDetails(kind, entry.sourceUrl).getOrThrow()
        assertEquals(entry.id, detail.id)
        assertEquals(entry.title, detail.title)
        assertEquals(kind, detail.source)
        assertEquals(entry.sourceUrl, detail.sourceUrl)
        assertTrue(detail.author.isNotBlank())
        assertTrue(detail.coverUrl.startsWith("https://"))
        assertTrue(detail.synopsis.isNotBlank())
        assertTrue(detail.genres.isNotEmpty())
        assertTrue(detail.chapterCount > 0)
        assertEquals("${detail.chapterCount} Chapters", detail.chapterCountLabel())
        bookmarks.addBookmark(detail)
        val restored = BookmarkManager(context).bookmarks.value.single()
        assertEquals(detail, restored)
        assertFalse(bookmarks.isBookmarked(if (fwn) "np-${entry.id.removePrefix("fwn-")}" else "fwn-${entry.id.removePrefix("np-")}"))
        val chapters = repository.getChaptersFlow(kind, restored.sourceUrl).last().getOrThrow()
        assertTrue(chapters.size >= 40)
        assertEquals(chapters.size, chapters.distinctBy { it.url }.size)
        assertTrue(chapters.first().url.contains(restored.sourceUrl.substringAfterLast('/')))
        val first = chapters.first()
        val content = repository.getChapterContent(kind, first.url, false).getOrThrow()
        assertTrue(content.title.isNotBlank())
        assertTrue(content.paragraphs.size > 10)
        assertNotNull(content.nextUrl)
        assertTrue(repository.getChapterContent(kind, content.nextUrl!!, false).getOrThrow().paragraphs.isNotEmpty())
        val detailPath = java.net.URI(restored.sourceUrl).path
        val before = calls[detailPath]?.get()
        repository.getNovelDetails(kind, restored.sourceUrl).getOrThrow()
        assertEquals("Repeated details must use the source cache", before, calls[detailPath]?.get())
        val contentPath = java.net.URI(first.url).path
        assertEquals("Prefetch and reading must share a response", 1, calls[contentPath]?.get())
        val listRequests = calls.values.sumOf { it.get() }
        repository.getChapters(kind, restored.sourceUrl, false).getOrThrow()
        assertEquals("Complete chapter list must be cached", listRequests, calls.values.sumOf { it.get() })
        repository.getChaptersFlow(kind, restored.sourceUrl).last().getOrThrow()
        assertEquals("Progressive route must also reuse a short complete list", listRequests, calls.values.sumOf { it.get() })
    }

    @Test fun simultaneousMetadataRequestsShareOneResponse() = runBlocking {
        val count = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            count.incrementAndGet()
            Thread.sleep(25)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body(sourceFixture(kind, "detail_shadow_slave.html").toResponseBody("text/html".toMediaType())).build()
        }.build()
        val source: NovelSource = if (fwn) FreeWebNovelSource(client, HostRateLimiter(0))
            else NovelPhoenixSource(client, HostRateLimiter(0))
        val details = (1..8).map { async(Dispatchers.Default) {
            source.getNovelDetails("${kind.baseUrl}/novel/shadow-slave").getOrThrow()
        } }.awaitAll()
        assertEquals(1, count.get())
        assertTrue(details.all { it.chapterCount > 0 && it.synopsis.isNotBlank() })
    }
}
