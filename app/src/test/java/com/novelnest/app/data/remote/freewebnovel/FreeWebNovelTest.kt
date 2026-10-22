package com.novelnest.app.data.remote.freewebnovel

import android.content.ContextWrapper
import android.content.SharedPreferences
import com.novelnest.app.data.bookmark.BookmarkManager
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.remote.network.HostRateLimiter
import com.novelnest.app.data.repository.NovelRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

class FreeWebNovelTest {
    private val base = FreeWebNovelUrls.BASE_URL
    private fun fixture(name: String): String {
        val path = "docs/sources/freewebnovel/fixtures/$name"
        return listOf(File(path), File("../$path")).first { it.exists() }.readText()
    }

    @Test fun canonicalIdentityMatchesEveryEntryAndLegacyUrl() {
        val values = listOf("shadow-slave", "fwn-shadow-slave", "/novel/shadow-slave",
            "$base/novel/shadow-slave/?ref=search#chapters", "$base/shadow-slave.html")
        values.forEach { assertEquals("fwn-shadow-slave", FreeWebNovelUrls.novelId(it)) }
        assertEquals("$base/novel/martial-world", FreeWebNovelUrls.novelUrl("fwn-1"))
        assertTrue(runCatching { FreeWebNovelUrls.novelUrl("https://example.com/novel/shadow-slave") }.isFailure)
    }

    @Test fun liveHomeHasRealRecommendedPopularAndReleasedSections() {
        val sections = FreeWebNovelParser.parseHome(fixture("home.html"), fixture("popular.html"))
        assertEquals(4, sections.size)
        assertTrue(sections.all { it.id.startsWith("fwn-live-") && it.novels.isNotEmpty() })
        assertEquals("Shadow Slave", sections[2].novels.first().title)
        assertEquals("fwn-shadow-slave", sections[2].novels.first().id)
        assertTrue(sections.flatMap { it.novels }.all {
            it.source == NovelSourceType.FREE_WEB_NOVEL && it.sourceUrl.startsWith("$base/novel/") && !it.coverUrl.contains("unsplash")
        })
        assertTrue(sections.last().novels.first().latestChapter.contains("1289"))
    }

    @Test fun searchHomeAndDetailsUseTheSameIdAndRealMetadata() {
        val home = FreeWebNovelParser.parseListing(fixture("popular.html"), "$base/sort/most-popular").first()
        val search = FreeWebNovelParser.parseListing(fixture("search_shadow.html"), "$base/search?keyword=shadow").first()
        val detail = FreeWebNovelParser.parseDetails(fixture("detail_shadow_slave.html"), "$base/novel/shadow-slave")
        assertEquals(home.id, search.id)
        assertEquals(home.id, detail.id)
        assertEquals("Guiltythree", detail.author)
        assertEquals(4.6f, detail.rating, 0.01f)
        assertTrue(detail.synopsis.contains("Nightmare Spell"))
        assertTrue(detail.genres.contains("Fantasy"))
        assertTrue(detail.coverUrl.endsWith("1991s.jpg"))
    }

    @Test fun chapterPagesUseActualServerLinksAndPagination() {
        val first = FreeWebNovelParser.parseChapterPage(fixture("chapters_ajax_1.json"), "$base/novel/shadow-slave")
        val second = FreeWebNovelParser.parseChapterPage(fixture("chapters_ajax_2.json"), "$base/novel/shadow-slave")
        val htmlSecond = FreeWebNovelParser.parseChapterPage(fixture("chapters_page_2.html"), "$base/novel/shadow-slave?page=2")
        assertEquals(100, first.chapters.size)
        assertEquals(33, first.totalPages)
        assertEquals(3222, first.totalChapters)
        assertEquals("chapter-101", second.chapters.first().id)
        assertEquals("chapter-200", second.chapters.last().id)
        assertEquals(40, htmlSecond.chapters.size)
        assertEquals("chapter-41", htmlSecond.chapters.first().id)
        assertEquals("$base/novel/shadow-slave/chapter-1", first.chapters.first().url)
    }

    @Test fun validEmptySearchStaysEmpty() {
        assertTrue(FreeWebNovelParser.parseListing(fixture("search_empty.html"), "$base/search?keyword=missing").isEmpty())
    }

    @Test fun unknownListingLayoutIsAnErrorRatherThanAnEmptyResult() {
        assertTrue(runCatching { FreeWebNovelParser.parseListing("<div class='ul-list1'>Unexpected markup</div>", base) }.isFailure)
    }

    @Test fun readerHasRealParagraphsAndCorrectNavigation() {
        val chapter = FreeWebNovelParser.parseContent(fixture("chapter_1_shadow_slave.html"), "$base/novel/shadow-slave/chapter-1")
        assertEquals("Chapter 1: Nightmare Begins", chapter.title)
        assertTrue(chapter.paragraphs.size > 20)
        assertTrue(chapter.paragraphs.first().contains("police station"))
        assertNull(chapter.prevUrl)
        assertEquals("$base/novel/shadow-slave/chapter-2", chapter.nextUrl)
        assertFalse(chapter.paragraphs.any { it.contains("parser is currently") })
    }

    @Test fun blockedPagesAreErrorsRatherThanSeedData() = runBlocking {
        val source = mockSource { _ -> 403 to "<title>Just a moment...</title>" }
        val repository = NovelRepositoryImpl(sources = mapOf(NovelSourceType.FREE_WEB_NOVEL to source))
        assertTrue(repository.getHomeSections(NovelSourceType.FREE_WEB_NOVEL).first().isFailure)
        assertTrue(repository.searchNovels(NovelSourceType.FREE_WEB_NOVEL, "shadow").isFailure)
        assertTrue(source.getChapters("fwn-shadow-slave", false).isFailure)
        assertTrue(runCatching { FreeWebNovelParser.parseDetails("<title>Just a moment...</title>", base) }.isFailure)
    }

    @Test fun clickedRecordIsAvailableBeforeDatabasePersistence() = runBlocking {
        val repository = NovelRepositoryImpl(sources = emptyMap())
        val novel = FreeWebNovelParser.parseListing(fixture("search_shadow.html"), base).first()
        repository.rememberNovel(novel)
        assertEquals(novel, repository.getNovelById(novel.id))
        val legacy = novel.copy(id = "fwn-s-0", sourceUrl = "$base/shadow-slave.html")
        repository.rememberNovel(legacy)
        assertEquals(novel.id, repository.getNovelById(legacy.id)?.id)
    }

    @Test fun partialChapterFailureRemainsRetryableAndNeverBecomesACompleteCache() = runBlocking {
        var failSecond = true
        val page1Calls = AtomicInteger()
        val source = mockSource { path ->
            if (path.contains("page=1&")) {
                page1Calls.incrementAndGet()
                200 to twoPageJson(1)
            } else if (failSecond) 403 to "Blocked" else 200 to twoPageJson(2)
        }
        val first = source.getChaptersFlow("fwn-shadow-slave", false).toList()
        assertEquals(100, first.first().getOrThrow().size)
        assertTrue(first.last().isFailure)
        failSecond = false
        val retried = source.getChaptersFlow("fwn-shadow-slave", true).toList()
        assertEquals(200, retried.last().getOrThrow().size)
        assertEquals(2, page1Calls.get())
        assertEquals(200, source.getChapters("fwn-shadow-slave", false).getOrThrow().size)
        assertEquals(2, page1Calls.get())
    }

    @Test fun concurrentReadAndPrefetchFetchOnlyOneContentResponse() = runBlocking {
        val calls = AtomicInteger()
        val source = mockSource {
            calls.incrementAndGet()
            Thread.sleep(40)
            200 to fixture("chapter_1_shadow_slave.html")
        }
        val chapters = (1..8).map { index -> async(Dispatchers.Default) {
            source.getChapterContent(if (index % 2 == 0) "$base/novel/shadow-slave/chapter-1" else "/novel/shadow-slave/chapter-1", false).getOrThrow()
        } }.awaitAll()
        assertEquals(1, calls.get())
        assertTrue(chapters.all { it.paragraphs.isNotEmpty() })
    }

    @Test fun bookmarksPersistAcrossEntryPointsAndStaySeparateFromPhoenix() {
        val values = mutableMapOf<String, String?>()
        val context = memoryContext(values)
        val home = FreeWebNovelParser.parseListing(fixture("popular.html"), base).first()
        val search = FreeWebNovelParser.parseListing(fixture("search_shadow.html"), base).first()
        val manager = BookmarkManager(context)
        assertTrue(manager.toggleBookmark(home))
        assertTrue(manager.isBookmarked(search.id))
        assertFalse(manager.isBookmarked("np-shadow-slave"))
        manager.addBookmark(search)
        assertEquals(1, manager.bookmarks.value.size)
        val restored = BookmarkManager(context)
        assertTrue(restored.isBookmarked(search.id))
        assertFalse(restored.toggleBookmark(search))
        assertTrue(restored.bookmarks.value.isEmpty())
        manager.addBookmark(home.copy(id = "fwn-s-0", sourceUrl = "$base/shadow-slave.html"))
        assertEquals(home.id, BookmarkManager(context).bookmarks.value.single().id)
    }

    private fun twoPageJson(page: Int) = JSONObject(fixture("chapters_ajax_$page.json"))
        .put("totalPage", 2).put("totalChapters", 200).toString()

    private fun mockSource(responder: (String) -> Pair<Int, String>): FreeWebNovelSource {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val (code, body) = responder(chain.request().url.encodedPath + "?" + chain.request().url.encodedQuery.orEmpty())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
                .body(body.toResponseBody("text/html".toMediaType())).build()
        }.build()
        return FreeWebNovelSource(client, HostRateLimiter(0))
    }

    private fun memoryContext(values: MutableMap<String, String?>): ContextWrapper {
        val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putString" -> { values[args[0] as String] = args[1] as String?; proxy }
                "apply" -> null
                "commit" -> true
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SharedPreferences.Editor
        val preferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> values[args[0] as String] ?: args[1]
                "edit" -> editor
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SharedPreferences
        return object : ContextWrapper(null) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = preferences
        }
    }
}
