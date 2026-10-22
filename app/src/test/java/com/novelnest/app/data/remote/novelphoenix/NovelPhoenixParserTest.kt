package com.novelnest.app.data.remote.novelphoenix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NovelPhoenixParserTest {

    private val fixturesDir = File("docs/sources/novelphoenix/fixtures")

    private fun loadFixture(fileName: String): String {
        val file = File(fixturesDir, fileName)
        if (!file.exists()) {
            // Also check alternate path if tests are run from app subdirectory
            val altFile = File("../docs/sources/novelphoenix/fixtures", fileName)
            if (altFile.exists()) return altFile.readText(Charsets.UTF_8)
            throw IllegalArgumentException("Fixture file not found: $fileName at ${file.absolutePath}")
        }
        return file.readText(Charsets.UTF_8)
    }

    // --- Novel Detail Tests ---

    @Test
    fun parseNovelDetail_geneticAscension() {
        val html = loadFixture("detail_genetic_ascension.html")
        val detail = NovelPhoenixParser.parseNovelDetail(html, "https://novelphoenix.com/novel/genetic-ascension")

        assertEquals("Genetic Ascension", detail.title)
        assertEquals("Awespec", detail.author)
        assertEquals(112, detail.rank)
        assertEquals(3.5f, detail.rating, 0.01f)
        assertEquals(37, detail.ratingCount)
        assertEquals("2.5M", detail.viewsRaw)
        assertEquals(2_500_000L, detail.viewsApprox)
        assertEquals("2.8K", detail.bookmarksRaw)
        assertEquals(2_800L, detail.bookmarksApprox)
        assertEquals(2451, detail.totalChapters)
        assertEquals("Ongoing", detail.status)
        assertEquals("https://novelphoenix.com/server-1/genetic-ascension.jpg", detail.coverUrl)
        assertTrue(detail.genres.contains("Fantasy"))
        assertEquals(9, detail.tags.size)
        assertTrue(detail.tags.contains("Action"))
        assertTrue(detail.tags.contains("Weaktostrong"))
        assertTrue(detail.synopsis.contains("The people of Earth have been summoned once again"))
        assertEquals("Chapter 2452 Heaven’s Answer (3) [Bonus]", detail.latestChapterTitle)
    }

    @Test
    fun parseNovelDetail_shadowSlave() {
        val html = loadFixture("detail_shadow_slave.html")
        val detail = NovelPhoenixParser.parseNovelDetail(html, "https://novelphoenix.com/novel/shadow-slave")

        assertEquals("Shadow Slave", detail.title)
        assertEquals("Guiltythree", detail.author)
        assertEquals(1, detail.rank)
        assertEquals("Ongoing", detail.status)
        assertEquals(3221, detail.totalChapters)
        assertEquals("72.4M", detail.viewsRaw)
        assertEquals(72_400_000L, detail.viewsApprox)
        assertEquals("https://novelphoenix.com/server-1/shadow-slave.jpg", detail.coverUrl)
    }

    @Test
    fun parseNovelDetail_completed_lordOfTheMysteries() {
        val html = loadFixture("detail_lord_of_the_mysteries_completed.html")
        val detail = NovelPhoenixParser.parseNovelDetail(html, "https://novelphoenix.com/novel/lord-of-the-mysteries")

        assertEquals("Lord of the Mysteries", detail.title)
        assertEquals("Completed", detail.status)
        assertEquals(1432, detail.totalChapters)
        assertEquals("16.8M", detail.viewsRaw)
        assertEquals(16_800_000L, detail.viewsApprox)
        assertEquals("18.5K", detail.bookmarksRaw)
        assertEquals(18_500L, detail.bookmarksApprox)
        assertTrue(detail.genres.contains("Xuanhuan"))
        assertTrue(detail.genres.contains("Mystery"))
    }

    @Test
    fun parseNovelDetail_emptyTags_killTheSun() {
        val html = loadFixture("detail_kill_the_sun_no_tags.html")
        val detail = NovelPhoenixParser.parseNovelDetail(html, "https://novelphoenix.com/novel/kill-the-sun")

        assertEquals("Kill the Sun", detail.title)
        assertTrue("Expected empty or zero tags for Kill the Sun", detail.tags.isEmpty())
        assertEquals(992, detail.totalChapters)
    }

    // --- Chapter List Tests ---

    @Test
    fun parseChapterList_page1_geneticAscension() {
        val html = loadFixture("chapters_genetic_ascension.html")
        val chapters = NovelPhoenixParser.parseChapterList(html)

        assertEquals(100, chapters.size)
        val first = chapters.first()
        assertEquals("1", first.chapterNumber)
        assertEquals("Chapter 1 Worst-Case Scenario", first.title)
        assertEquals("/novel/genetic-ascension/chapter-1", first.urlPath)

        val ch100 = chapters[99]
        assertEquals("100", ch100.chapterNumber)
        assertEquals("Chapter 100 Exploded", ch100.title)
        assertEquals("/novel/genetic-ascension/chapter-100", ch100.urlPath)
    }

    @Test
    fun parseChapterList_middlePage17_shadowSlave() {
        val html = loadFixture("chapters_shadow_slave_page_17.html")
        val chapters = NovelPhoenixParser.parseChapterList(html)

        assertEquals(100, chapters.size)
        val first = chapters.first()
        assertEquals("1601", first.chapterNumber)
        assertTrue(first.urlPath.contains("chapter-1601"))

        val last = chapters.last()
        assertEquals("1700", last.chapterNumber)
        assertTrue(last.urlPath.contains("chapter-1700"))
    }

    @Test
    fun parseChapterList_lastPage33_shadowSlave() {
        val html = loadFixture("chapters_shadow_slave_page_33.html")
        val chapters = NovelPhoenixParser.parseChapterList(html)

        assertEquals(21, chapters.size)
        val first = chapters.first()
        assertEquals("3201", first.chapterNumber)
        val last = chapters.last()
        assertEquals("3221", last.chapterNumber)
    }

    @Test
    fun parseChapterListPage_extractsTotalPagesFromRangeDropdown() {
        val htmlWithDropdown = """
            <select aria-label="Chapter range">
                <option value="http://novelphoenix.com/novel/shadow-slave/chapters?page=1" selected>1-100</option>
                <option value="http://novelphoenix.com/novel/shadow-slave/chapters?page=2">101-200</option>
                <option value="http://novelphoenix.com/novel/shadow-slave/chapters?page=33">3201-3221</option>
            </select>
            <ul class="chapter-list">
                <li><a href="/novel/shadow-slave/chapter-1" title="Chapter 1"><span class="chapter-no">1</span><strong class="chapter-title">Chapter 1</strong><time class="chapter-update">1 day ago</time></a></li>
            </ul>
        """.trimIndent()

        val pageResult = NovelPhoenixParser.parseChapterListPage(htmlWithDropdown)
        assertEquals(33, pageResult.totalPages)
        assertEquals(1, pageResult.chapters.size)
        assertEquals("1", pageResult.chapters.first().chapterNumber)
    }

    // --- Chapter Content & Reader Tests ---

    @Test
    fun parseChapterContent_chapter1_geneticAscension() {
        val html = loadFixture("chapter_1_genetic_ascension.html")
        val content = NovelPhoenixParser.parseChapterContent(html)

        assertEquals("Chapter 1 Worst-Case Scenario", content.title)
        assertNull("Chapter 1 prev should be null", content.prevChapterUrl)
        assertEquals("https://novelphoenix.com/novel/genetic-ascension/chapter-2", content.nextChapterUrl)
        assertTrue(content.paragraphs.size > 20)
        assertTrue(content.paragraphs.first().contains("Sylas shrugged off his lab coat"))

        // Assert that ad injected elements (.nf-ads, pubadx, iframe) were removed
        for (para in content.paragraphs) {
            assertFalse(para.contains("ad.a-ads.com"))
            assertFalse(para.contains("pubadx-slot"))
        }
    }

    @Test
    fun parseChapterContent_bonusChapter_geneticAscension2452() {
        val html = loadFixture("chapter_2451_bonus_genetic_ascension.html")
        val content = NovelPhoenixParser.parseChapterContent(html)

        assertTrue(content.title.contains("Chapter 2452"))
        assertTrue(content.title.contains("[Bonus]"))
        assertEquals("https://novelphoenix.com/novel/genetic-ascension/chapter-2450", content.prevChapterUrl)
        // Next link on latest chapter is disabled/null
        assertNull(content.nextChapterUrl)
        assertTrue(content.paragraphs.isNotEmpty())
    }

    @Test
    fun parseChapterContent_lastChapter_lotm1432() {
        val html = loadFixture("chapter_1432_last_lotm.html")
        val content = NovelPhoenixParser.parseChapterContent(html)

        assertEquals("Chapter 1432-END - 1432 Bonus Chapter: That Corner (2)", content.title)
        assertNull("Final chapter of completed novel next should be null", content.nextChapterUrl)
        assertEquals("https://novelphoenix.com/novel/lord-of-the-mysteries/chapter-1431", content.prevChapterUrl)
        assertTrue(content.paragraphs.isNotEmpty())
    }

    // --- Search Tests ---

    @Test
    fun parseLiveSearchJson_shadowSlave() {
        val json = loadFixture("search_live_shadow.json")
        val results = NovelPhoenixParser.parseLiveSearchJson(json)

        assertEquals(6, results.size)
        val first = results.first()
        assertEquals("Shadow Slave", first.title)
        assertEquals("shadow-slave", first.slug)
        assertEquals("https://novelphoenix.com/server-1/shadow-slave.jpg", first.coverUrl)
        assertEquals("3221 Chapters", first.latestChapter)
    }

    @Test
    fun parseLiveSearchJson_zeroResults() {
        val json = loadFixture("search_live_zero_results.json")
        val results = NovelPhoenixParser.parseLiveSearchJson(json)

        assertTrue(results.isEmpty())
    }

    @Test
    fun parseLiveSearchJson_shortKeyword() {
        val json = loadFixture("search_live_short_keyword.json")
        val results = NovelPhoenixParser.parseLiveSearchJson(json)

        assertEquals(6, results.size)
        assertTrue(results.any { it.title.contains("Circle of Inevitability") })
    }

    @Test
    fun parseListingHtml_searchPageShadow() {
        val html = loadFixture("search_page_shadow.html")
        val results = NovelPhoenixParser.parseListingHtml(html)

        assertTrue(results.isNotEmpty())
        val first = results.first()
        assertEquals("Shadow Slave", first.title)
        assertEquals("shadow-slave", first.slug)
        assertEquals("3221 Chapters", first.latestChapter)
        assertEquals("1", first.rankBadge?.replace(Regex("[^0-9]"), ""))
    }

    // --- Error & Blocked Response Tests ---

    @Test(expected = PhoenixException.SourceBlockedError::class)
    fun parseNovelDetail_cloudflare403ThrowsBlockedError() {
        val html = loadFixture("response_403_cloudflare.html")
        NovelPhoenixParser.parseNovelDetail(html)
    }

    @Test(expected = PhoenixException.ResourceNotFoundError::class)
    fun parseNovelDetail_404ThrowsNotFoundError() {
        val html = loadFixture("response_404.html")
        NovelPhoenixParser.parseNovelDetail(html, "https://novelphoenix.com/novel/missing-slug")
    }

    // --- Utility Resolver Tests ---

    @Test
    fun testUrlResolver_allVariants() {
        // 1. Absolute URL
        assertEquals("https://novelphoenix.com/server-1/cover.jpg",
            NovelPhoenixUtils.resolveUrl("https://novelphoenix.com/server-1/cover.jpg"))

        // 2. Leading slash
        assertEquals("https://novelphoenix.com/server-1/cover.jpg",
            NovelPhoenixUtils.resolveUrl("/server-1/cover.jpg"))

        // 3. No leading slash (e.g. from live search API)
        assertEquals("https://novelphoenix.com/server-1/cover.jpg",
            NovelPhoenixUtils.resolveUrl("server-1/cover.jpg"))

        // 4. Data URI / blank returns fallback
        assertEquals("https://fallback.com/placeholder.jpg",
            NovelPhoenixUtils.resolveUrl("data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==", "https://fallback.com/placeholder.jpg"))
        assertEquals("https://fallback.com/placeholder.jpg",
            NovelPhoenixUtils.resolveUrl("", "https://fallback.com/placeholder.jpg"))
    }

    @Test
    fun parseChapterContent_brSeparatedFallback() {
        val htmlWithoutP = """
            <html>
            <head><title>Chapter 10 - Test Novel - Novel Phoenix</title></head>
            <body>
                <div id="content">
                    Line one of the story.<br><br>
                    Line two of the story.<br>
                    Line three of the story.
                </div>
            </body>
            </html>
        """.trimIndent()

        val parsed = NovelPhoenixParser.parseChapterContent(htmlWithoutP)
        assertEquals(3, parsed.paragraphs.size)
        assertEquals("Line one of the story.", parsed.paragraphs[0])
        assertEquals("Line two of the story.", parsed.paragraphs[1])
        assertEquals("Line three of the story.", parsed.paragraphs[2])
    }
}
