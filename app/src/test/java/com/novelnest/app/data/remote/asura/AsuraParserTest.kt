package com.novelnest.app.data.remote.asura

import com.novelnest.app.data.model.*
import org.json.JSONObject
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.io.File

internal fun asuraFixture(name: String): String = listOf(File("../docs/sources/asura/fixtures/$name"), File("docs/sources/asura/fixtures/$name"))
    .first { it.isFile }.readText()
internal const val ASURA_SERIES = "childhood-friend-of-the-zenith"
internal val ASURA_URL = AsuraUrls.seriesUrl(ASURA_SERIES)
internal val ASURA_CHAPTER = AsuraUrls.chapterUrl(ASURA_SERIES, "1")

class AsuraParserTest {
    @Test fun allEntryRoutesProduceTheSameStableIdentityAndSource() {
        val id = "asura-$ASURA_SERIES"
        val search = AsuraParser.search(asuraFixture("search.json")).single()
        val browse = AsuraParser.browse(asuraFixture("browse.html"), AsuraUrls.browse()).novels.first { it.id == id }
        val details = AsuraParser.details(asuraFixture("detail.html"), ASURA_URL).novel
        listOf(search, browse, details).forEach {
            assertEquals(id, it.id); assertEquals(ASURA_URL, it.sourceUrl); assertEquals(NovelSourceType.ASURA_SCANS, it.source)
            assertTrue(it.hasRating()); assertTrue(it.chapterCount >= 112); assertTrue(it.coverUrl.startsWith("https://cdn.asurascans.com/"))
        }
        val canonical = NovelIdentity.normalize(details.copy(id = "asura-s-0", sourceUrl = "https://asuracomic.net/series/$ASURA_SERIES"))
        assertEquals(details.id, canonical.id)
        assertEquals(details.sourceUrl, canonical.sourceUrl)
    }
    @Test fun fullDetailMetadataAndChronologicalChaptersAreReal() {
        val result = AsuraParser.details(asuraFixture("detail.html"), ASURA_URL)
        assertEquals("Childhood Friend of the Zenith", result.novel.title)
        assertTrue(result.novel.synopsis.length > 800)
        assertEquals("Dubu (i)", result.novel.author); assertEquals("Daum", result.artist)
        assertEquals("Ongoing", result.novel.status); assertTrue(result.novel.rank > 0)
        assertEquals(9.3f, result.sourceRating!!, .01f); assertEquals(4.65f, result.novel.rating, .01f)
        assertEquals(2128L, result.sourceSeriesId); assertTrue(result.alternativeTitles.size > 10)
        assertTrue(result.novel.genres.contains("Murim")); assertEquals(112, result.chapters.size)
        assertEquals("1", result.chapters.first().number); assertEquals("112", result.chapters.last().number)
        assertTrue(result.chapters.last().isPremium)
        assertEquals(112, result.chapters.map { it.id }.distinct().size)
    }
    @Test fun completedSeriesDoesNotDefaultToOngoing() {
        val result = AsuraParser.details(asuraFixture("completed.html"), AsuraUrls.seriesUrl("the-greatest-estate-developer"))
        assertEquals("Completed", result.novel.status); assertTrue(result.chapters.size >= 220)
    }
    @Test fun homeUsesComicSectionsAndNeverImportsTheSeparateNovelFeed() {
        val result = AsuraParser.home(asuraFixture("home.html"))
        assertEquals(setOf("asura-live-hero", "asura-live-trending", "asura-live-popular", "asura-live-latest"), result.map { it.id }.toSet())
        result.forEach { section -> assertFalse(section.novels.isEmpty()); section.novels.forEach { novel ->
            assertTrue(novel.sourceUrl.contains("/comics/")); assertFalse(novel.coverUrl.contains("unsplash"))
            assertEquals(NovelSourceType.ASURA_SCANS, novel.source)
        } }
    }
    @Test fun actualBrowseCategorySlugsAndPaginationArePreserved() {
        val all = AsuraParser.browse(asuraFixture("browse.html"), AsuraUrls.browse())
        assertEquals(20, all.novels.size)
        assertEquals("https://asurascans.com/browse?genres=fantasy", all.categories.first { it.name == "Fantasy" }.url)
        assertEquals("https://asurascans.com/browse?page=2", all.nextPageUrl)
        val fantasy = AsuraParser.browse(asuraFixture("fantasy.html"), AsuraUrls.genre("fantasy"))
        assertEquals(20, fantasy.novels.size); assertTrue(fantasy.novels.all { it.genres.contains("Fantasy") })
        assertTrue(fantasy.nextPageUrl!!.contains("genres=fantasy"))
        val second = AsuraParser.browse(asuraFixture("browse-page-2.html"), all.nextPageUrl!!)
        assertNotEquals(all.novels.first().id, second.novels.first().id)
    }
    @Test fun genuineEmptySearchIsDifferentFromBrokenResponse() {
        assertTrue(AsuraParser.search(asuraFixture("search-empty.json")).isEmpty())
        assertTrue(runCatching { AsuraParser.search("{}") }.exceptionOrNull() is AsuraException.Parse)
        assertTrue(runCatching { AsuraParser.home("<html><body>Unavailable</body></html>") }.isFailure)
    }
    @Test fun imageOrderDimensionsAndQueryStringsMatchHtmlExactly() {
        val api = AsuraParser.chapterContent(asuraFixture("chapter.json"), ASURA_CHAPTER)
        val html = AsuraParser.chapterHtml(asuraFixture("chapter.html"), ASURA_CHAPTER)
        assertEquals(14, api.pages.size); assertEquals(api.pages, html.pages)
        assertEquals((0..13).toList(), api.pages.map { it.index })
        assertTrue(api.pages.first().url.contains("001.webp?v=")); assertTrue(api.pages.last().url.contains("014.webp?v="))
        assertEquals(15522, api.pages[1].height); assertEquals(800, api.pages[1].width)
        assertEquals(mapOf("Referer" to ASURA_CHAPTER), api.imageHeaders)
        assertNull(api.previousChapterUrl); assertEquals(AsuraUrls.chapterUrl(ASURA_SERIES, "2"), api.nextChapterUrl)
        val dom = Jsoup.parse(asuraFixture("chapter.html")).select(AsuraSelectors.PAGE_IMAGES).map { it.attr("src") }
        assertEquals(api.pages.map { it.url }, dom)
    }
    @Test fun repeatedImagesKeepTheirPositionsAndAreNeverFilenameSorted() {
        val data = JSONObject(asuraFixture("chapter.json")); val pages = data.getJSONObject("data").getJSONObject("chapter").getJSONArray("pages")
        val first = pages.getJSONObject(0).getString("url"); val second = pages.getJSONObject(1).getString("url")
        pages.getJSONObject(0).put("url", second); pages.getJSONObject(1).put("url", first); pages.getJSONObject(2).put("url", first)
        val result = AsuraParser.chapterContent(data.toString(), ASURA_CHAPTER)
        assertEquals(listOf(second, first, first), result.pages.take(3).map { it.url }); assertEquals(14, result.pages.size)
    }
    @Test fun lockedChaptersCannotBeReadEvenIfPreviewPagesExist() {
        assertTrue(runCatching { AsuraParser.chapterContent(asuraFixture("locked.json"), AsuraUrls.chapterUrl(ASURA_SERIES, "112")) }.exceptionOrNull() is AsuraException.Locked)
        val json = JSONObject(asuraFixture("chapter.json")); json.getJSONObject("data").put("is_locked", true)
        assertTrue(runCatching { AsuraParser.chapterContent(json.toString(), ASURA_CHAPTER) }.exceptionOrNull() is AsuraException.Locked)
        json.getJSONObject("data").put("access_gate", "account_required")
        assertTrue(runCatching { AsuraParser.chapterContent(json.toString(), ASURA_CHAPTER) }.exceptionOrNull() is AsuraException.AccessRequired)
        json.getJSONObject("data").remove("is_locked")
        assertTrue(runCatching { AsuraParser.chapterContent(json.toString(), ASURA_CHAPTER) }.exceptionOrNull() is AsuraException.Parse)
    }
    @Test fun incompleteOrWrongChapterCannotBecomeASuccessfulCacheEntry() {
        val json = JSONObject(asuraFixture("chapter.json")); val chapter = json.getJSONObject("data").getJSONObject("chapter")
        chapter.put("page_count", 15)
        assertTrue(runCatching { AsuraParser.chapterContent(json.toString(), ASURA_CHAPTER) }.isFailure)
        chapter.put("page_count", 14).put("number", 2)
        assertTrue(runCatching { AsuraParser.chapterContent(json.toString(), ASURA_CHAPTER) }.isFailure)
        assertTrue(runCatching { AsuraParser.details(asuraFixture("detail.html"), AsuraUrls.seriesUrl("other-series")) }.isFailure)
    }
    @Test fun semanticSelectorsSurviveHashedBundleAndCssChanges() {
        val html = asuraFixture("browse.html").replace("BrowseFilters.Cc6KW6T8.js", "BrowseFilters.new-deploy.js")
        assertEquals(20, AsuraParser.browse(html, AsuraUrls.browse()).novels.size)
        val blocked = "<html><head><title>Just a moment...</title></head><body><form id='challenge-form'></form></body></html>"
        assertTrue(runCatching { AsuraParser.home(blocked) }.exceptionOrNull() is AsuraException.Blocked)
    }
    @Test fun decimalChaptersAndZeroNormalizeWithoutLosingOrder() {
        assertEquals("1.5", AsuraUrls.number("01.500")); assertEquals("0", AsuraUrls.number("0"))
        assertEquals("asura-$ASURA_SERIES", AsuraUrls.novelId("https://asurascans.com/comics/$ASURA_SERIES-3ebcc5a9"))
        assertEquals(ASURA_URL, AsuraUrls.seriesUrl("asura-$ASURA_SERIES"))
    }
    @Test fun urlsRejectForeignOriginsPortsCredentialsAndInvalidImages() {
        listOf("https://evil.example/comics/book", "https://asurascans.com.evil.example/comics/book", "https://u@asurascans.com/comics/book", "https://asurascans.com:8443/comics/book")
            .forEach { assertTrue(runCatching { AsuraUrls.seriesUrl(it) }.isFailure) }
        listOf("http://cdn.asurascans.com/asura-images/a.webp", "https://evil.example/asura-images/a.webp", "data:image/png,x")
            .forEach { assertTrue(runCatching { AsuraUrls.image(it) }.isFailure) }
        assertTrue(runCatching { AsuraUrls.browse("https://asurascans.com/browse?page=-1") }.isFailure)
    }
}
