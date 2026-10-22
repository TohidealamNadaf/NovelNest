package com.novelnest.app.data.remote.novelphoenix

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Live smoke test for NovelPhoenix against real production servers.
 *
 * NOTE: Ignored by default in CI/offline unit test suites to prevent test flakiness,
 * Cloudflare IP blocks, and unmetered requests.
 *
 * To run manually:
 * ./gradlew.bat testDebugUnitTest --tests "com.novelnest.app.data.remote.novelphoenix.NovelPhoenixLiveSmokeTest"
 */
@Ignore("Live smoke test hits real network. Run manually for source verification.")
class NovelPhoenixLiveSmokeTest {

    private val source = NovelPhoenixSource()

    @Test
    fun liveSmokeTest_shadowSlaveFullFlow() = runBlocking {
        // 1. Live search
        val searchResult = source.search("Shadow Slave")
        assertTrue("Live search should succeed", searchResult.isSuccess)
        val searchItems = searchResult.getOrNull().orEmpty()
        assertTrue("Search items should contain Shadow Slave", searchItems.any { it.title.contains("Shadow Slave", ignoreCase = true) })

        // 2. Novel details
        val detailResult = source.getNovelDetails("https://novelphoenix.com/novel/shadow-slave")
        assertTrue("Detail fetch should succeed", detailResult.isSuccess)
        val novel = detailResult.getOrThrow()
        assertTrue("Title should match", novel.title.contains("Shadow Slave", ignoreCase = true))
        assertTrue("Cover URL should be resolved", novel.coverUrl.startsWith("http"))
        assertTrue("Total chapters should be substantial", (novel.latestChapter.filter { it.isDigit() }.toIntOrNull() ?: 0) > 2000)

        // 3. Middle & Late chapter list pagination
        val allChapters = source.getAllChapters("shadow-slave")
        assertTrue("Should have collected at least 3000 chapters across pages", allChapters.size >= 3000)

        // Pick a late chapter
        val lateChapter = allChapters.find { it.chapterNumber == "3000" } ?: allChapters[allChapters.size - 50]
        assertNotNull("Late chapter should exist", lateChapter)

        // 4. Read late chapter
        val chapterUrl = if (lateChapter.urlPath.startsWith("http")) lateChapter.urlPath else "https://novelphoenix.com${lateChapter.urlPath}"
        val content = source.fetchChapterContent(chapterUrl)
        assertTrue("Chapter title should not be blank", content.title.isNotBlank())
        assertTrue("Paragraphs should be extracted", content.paragraphs.size >= 5)

        // 5. Follow next chapter link
        val nextUrl = content.nextChapterUrl
        assertNotNull("Next chapter URL should be present for a middle/late chapter", nextUrl)
        assertFalse("Next chapter URL should not be javascript", nextUrl!!.startsWith("javascript:"))

        // Verify next chapter loads cleanly
        val nextContent = source.fetchChapterContent(nextUrl)
        assertTrue("Next chapter should have readable content", nextContent.paragraphs.isNotEmpty())
    }
}
