package com.novelnest.app.data.remote.freewebnovel

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt in with FWN_LIVE_SMOKE=1; excluded from normal offline test runs. */
class FreeWebNovelLiveSmokeTest {
    @Test fun liveHomeSearchDetailsChaptersAndReading() = runBlocking {
        assumeTrue(System.getenv("FWN_LIVE_SMOKE") == "1")
        val source = FreeWebNovelSource()
        val home = source.getHomeSections().getOrThrow()
        assertTrue(home.all { it.novels.isNotEmpty() })
        val search = source.search("Shadow Slave").getOrThrow()
        val novel = search.first { it.id == "fwn-shadow-slave" }
        val detail = source.getNovelDetails(novel.sourceUrl).getOrThrow()
        assertEquals(novel.id, detail.id)
        assertTrue(detail.synopsis.isNotBlank())
        val chapters = source.getChaptersFlow(detail.sourceUrl, false).first().getOrThrow()
        assertEquals("chapter-1", chapters.first().id)
        val content = source.getChapterContent(chapters.first().url, false).getOrThrow()
        assertTrue(content.paragraphs.size > 10)
        assertNotNull(content.nextUrl)
    }
}
