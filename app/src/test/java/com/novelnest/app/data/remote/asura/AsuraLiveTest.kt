package com.novelnest.app.data.remote.asura

import kotlinx.coroutines.runBlocking
import com.novelnest.app.data.manhwa.ManhwaImageStore
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/** Explicit opt-in only. Public read-only requests, no API keys, account cookies or unlocks. */
class AsuraLiveTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun publicFetchingAndOriginalImageAccess() = runBlocking {
        assumeTrue("Set ASURA_LIVE_SMOKE=1 for public source verification", System.getenv("ASURA_LIVE_SMOKE") == "1")
        val source = AsuraSource()
        val home = source.getHomeSections().getOrThrow()
        assertTrue(home.size >= 3)
        val browse = source.getBrowsePage().getOrThrow()
        val fantasy = browse.categories.first { it.name == "Fantasy" }
        assertTrue(source.getBrowsePage(fantasy.url).getOrThrow().novels.all { it.genres.contains("Fantasy") })
        assertTrue(source.getBrowsePage(browse.nextPageUrl!!).getOrThrow().novels.isNotEmpty())
        val novel = source.search("zenith").getOrThrow().first { it.id == "asura-$ASURA_SERIES" }
        val details = source.getManhwaDetails(novel.id).getOrThrow()
        assertEquals(novel.id, details.novel.id); assertTrue(details.novel.rank > 0)
        assertTrue(details.novel.synopsis.isNotBlank()); assertTrue(details.novel.rating > 0f)
        assertEquals(details.chapters.size, source.getChapters(novel.sourceUrl).getOrThrow().size)
        val content = source.getManhwaChapter(details.chapters.first().url).getOrThrow()
        assertTrue(content.pages.size > 1); assertEquals(content.pages.indices.toList(), content.pages.map { it.index })
        val images = ManhwaImageStore(temporary.newFolder())
        images.acquire(content.pages.first(), content.chapter.url).use { lease ->
            assertTrue(lease.file.length() > 32)
            val header = ByteArray(12)
            lease.file.inputStream().use { assertEquals(12, it.read(header)) }
            assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII)); assertEquals("WEBP", String(header, 8, 4, Charsets.US_ASCII))
        }
        assertTrue(images.clear())
        println("ASURA LIVE: ${home.size} sections, ${browse.novels.size} browse results, ${details.chapters.size} chapters, ${content.pages.size} ordered pages; public WebP image verified")
    }
}
