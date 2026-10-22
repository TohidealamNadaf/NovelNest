package com.novelnest.app.data.remote

import com.novelnest.app.data.model.*
import com.novelnest.app.data.cache.MemoryLruCache
import com.novelnest.app.data.repository.NovelRepositoryImpl
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class BrowseSourceTest {
    private val np = NovelSourceType.NOVEL_PHOENIX
    private val fwn = NovelSourceType.FREE_WEB_NOVEL

    @Test fun phoenixActualAllAndFantasyPagesExposeSourceCategoriesAndNextPage() {
        for (file in listOf("browse_new.html", "browse_fantasy.html")) {
            val url = if (file == "browse_new.html") BrowseParser.initialUrl(np) else "https://novelphoenix.com/genre-fantasy/sort-new/status-all/all-novel"
            val page = BrowseParser.parse(np, sourceFixture(np, file), url)
            assertTrue(page.novels.isNotEmpty())
            assertTrue(page.novels.all { it.id.startsWith("np-") && it.sourceUrl.startsWith(np.baseUrl + "/novel/") })
            assertTrue(page.categories.any { it.name == "Fantasy" && it.url.contains("genre-fantasy/") })
            assertTrue(page.nextPageUrl!!.endsWith("?page=2"))
        }
    }

    @Test fun fwnRealListingNavigationYieldsCategoriesAndPathPagination() {
        val page = BrowseParser.parse(fwn, sourceFixture(fwn, "latest.html"), BrowseParser.initialUrl(fwn))
        assertTrue(page.novels.isNotEmpty())
        assertTrue(page.categories.any { it.name == "Fantasy" && it.url.endsWith("/genre/Fantasy") })
        // latest.html is a latest-release capture, so its next link is rejected
        // when it does not belong to the supported catalog paths.
        val html = sourceFixture(fwn, "latest.html").replace("/sort/latest-release", "/sort/latest-novel")
        assertEquals("https://freewebnovel.com/sort/latest-novel/2",
            BrowseParser.parse(fwn, html, BrowseParser.initialUrl(fwn)).nextPageUrl)
    }

    @Test fun fwnCategoryScoresSurviveDecorativeSpansAndLocalizedNumbers() {
        val doc = org.jsoup.Jsoup.parse(sourceFixture(fwn, "popular.html"))
        val score = doc.selectFirst(".core span")!!
        score.before("<span class='rating-icon'></span>")
        score.text("4,6 / 5")
        val page = BrowseParser.parse(fwn, doc.html(), "https://freewebnovel.com/genre/Fantasy")
        assertEquals(4.6f, page.novels.first().rating, 0.001f)
        score.text("0.0")
        val unrated = BrowseParser.parse(fwn, doc.html(), BrowseParser.initialUrl(fwn)).novels.first()
        assertFalse(unrated.hasRating())
        assertEquals("—", unrated.ratingLabel())
    }

    @Test fun phoenixUsesRatingBadgeInsteadOfRankAndDoesNotInventMissingScores() {
        val doc = org.jsoup.Jsoup.parse(sourceFixture(np, "browse_fantasy.html"))
        val score = doc.selectFirst(".novel-item .badge._br")!!
        score.text("4.2")
        val url = "https://novelphoenix.com/genre-fantasy/sort-new/status-all/all-novel"
        assertEquals(4.2f, BrowseParser.parse(np, doc.html(), url).novels.first().rating, 0.001f)
        score.text("")
        assertEquals(0f, BrowseParser.parse(np, doc.html(), url).novels.first().rating, 0f)
    }

    @Test fun externalAndNonCatalogLinksAreRejected() {
        for (source in listOf(np, fwn)) {
            for (url in listOf("https://evil.example/genre/Fantasy", "${source.baseUrl}/novel/shadow-slave", "${source.baseUrl}/genre/Fantasy?admin=1")) {
                assertTrue(runCatching { BrowseParser.validateUrl(source, url) }.isFailure)
            }
        }
    }

    @Test fun boundedLruEvictsLeastRecentlyUsedPage() {
        val cache = MemoryLruCache<String, Int>(2)
        cache.put("a", 1); cache.put("b", 2); cache.get("a"); cache.put("c", 3)
        assertNull(cache.get("b"))
        assertEquals(1, cache.get("a"))
    }

    @Test fun cacheCoalescesConcurrentRequestsSeparatesSourcesAndRetainsStaleOnFailure() = runBlocking {
        val count = AtomicInteger()
        var offline = false
        fun stub(source: NovelSourceType) = object : NovelSource {
            override val sourceType = source
            override suspend fun getHomeSections() = Result.success(emptyList<NovelSection>())
            override suspend fun search(query: String) = Result.success(emptyList<Novel>())
            override suspend fun getNovelDetails(novelUrl: String) = Result.failure<Novel>(IOException())
            override suspend fun getBrowsePage(url: String?): Result<BrowsePage> {
                count.incrementAndGet()
                delay(20)
                return if (offline) Result.failure(IOException("offline")) else
                    Result.success(BrowsePage(listOf(Novel(source.name, "Book", "", source, "${source.baseUrl}/novel/book", rating = 0f)), emptyList()))
            }
        }
        val repository = NovelRepositoryImpl(sources = mapOf(np to stub(np), fwn to stub(fwn)))
        (1..12).map { async { repository.getBrowsePage(np).getOrThrow() } }.awaitAll()
        assertEquals(1, count.get())
        val sparse = repository.getBrowsePage(np).getOrThrow().novels.single()
        repository.rememberNovel(sparse.copy(rating = 4.8f, synopsis = "Known detail"))
        assertEquals(4.8f, repository.getBrowsePage(np).getOrThrow().novels.single().rating, 0.001f)
        repository.rememberNovel(sparse)
        assertEquals(4.8f, repository.getBrowsePage(np).getOrThrow().novels.single().rating, 0.001f)
        repository.getBrowsePage(np, BrowseParser.initialUrl(np)).getOrThrow()
        assertEquals(1, count.get())
        assertEquals(fwn, repository.getBrowsePage(fwn).getOrThrow().novels.single().source)
        assertEquals(2, count.get())
        offline = true
        val stale = repository.getBrowsePage(np, forceRefresh = true).getOrThrow()
        assertTrue(stale.isStale)
        assertEquals(np, stale.novels.single().source)
        assertTrue(repository.getBrowsePage(np, "https://novelphoenix.com/genre-action/sort-new/status-all/all-novel").isFailure)
        offline = false
        assertFalse(repository.getBrowsePage(np, forceRefresh = true).getOrThrow().isStale)
    }

    @Test fun chapterBadgeNeverUsesTitleOrAmbiguousChapterNumberAsTotal() {
        val novel = Novel("np-book", "Book", "", np, "", latestChapter = "Chapter 3245: The Last Battle")
        assertEquals("", novel.chapterCountLabel())
        assertEquals("100 Chapters", novel.copy(chapterCount = 100).chapterCountLabel())
        assertEquals("3245 Chapters", novel.copy(latestChapter = "3,245 Chapters").chapterCountLabel())
        assertEquals("", novel.copy(latestChapter = "Chapter 1").chapterCountLabel())
    }

    @Test fun browseCacheExpiresAndEvictsWithoutDiscardingBookmarks() = runBlocking {
        var now = 1_000L
        var requests = 0
        val source = object : NovelSource {
            override val sourceType = np
            override suspend fun getHomeSections() = Result.success(emptyList<NovelSection>())
            override suspend fun search(query: String) = Result.success(emptyList<Novel>())
            override suspend fun getNovelDetails(novelUrl: String) = Result.failure<Novel>(IOException())
            override suspend fun getBrowsePage(url: String?): Result<BrowsePage> {
                requests++
                return Result.success(BrowsePage(emptyList(), emptyList()))
            }
        }
        val repository = NovelRepositoryImpl(sources = mapOf(np to source), browseClockMillis = { now })
        repository.getBrowsePage(np).getOrThrow()
        now += 15 * 60 * 1_000L - 1
        repository.getBrowsePage(np).getOrThrow()
        assertEquals(1, requests)
        now++
        repository.getBrowsePage(np).getOrThrow()
        assertEquals(2, requests)
        for (page in 2..41) repository.getBrowsePage(np, BrowseParser.initialUrl(np) + "?page=$page").getOrThrow()
        val before = requests
        repository.getBrowsePage(np).getOrThrow()
        assertEquals("The 41st cached catalog page evicts the oldest", before + 1, requests)
    }

    @Test fun asuraRetainsExistingFeedGenreFiltering() = runBlocking {
        val source = object : NovelSource {
            override val sourceType = NovelSourceType.ASURA_SCANS
            override suspend fun getHomeSections() = Result.success(listOf(
                NovelSection("asura", "Asura", "", "", SectionType.HORIZONTAL_LIST, listOf(
                    Novel("asura-a", "Action", "", sourceType, "", genres = listOf("Action")),
                    Novel("asura-b", "Comedy", "", sourceType, "", genres = listOf("Comedy"))
                ))))
            override suspend fun search(query: String) = Result.success(emptyList<Novel>())
            override suspend fun getNovelDetails(novelUrl: String) = Result.failure<Novel>(IOException())
        }
        val repository = NovelRepositoryImpl(sources = mapOf(source.sourceType to source))
        val page = repository.getBrowsePage(source.sourceType).getOrThrow()
        val action = page.categories.first { it.name == "Action" }
        assertEquals("asura-a", repository.getBrowsePage(source.sourceType, action.url).getOrThrow().novels.single().id)
    }
}
