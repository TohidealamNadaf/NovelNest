package com.novelnest.app.navigation

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.testsupport.ReaderTestRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderEntrySourceTest {
    @Test fun storedSourceTakesPriorityOverPrefixWithoutStartingEitherReader() = runTest {
        for (source in NovelSourceType.entries) {
            val repo = ReaderTestRepository(Novel("asura-legacy-id", "Title", "", source, ""))
            assertEquals(source, resolveReaderSource("asura-legacy-id", repo, null))
            assertEquals(0, repo.chapterCalls)
            assertEquals(0, repo.contentCalls)
        }
    }

    @Test fun coldIdsSelectTheCorrectSourceWithoutCatalogOrContentRequests() = runTest {
        val repo = ReaderTestRepository()
        for ((id, source) in listOf(
            "asura-childhood-friend" to NovelSourceType.ASURA_SCANS,
            "np-shadow-slave" to NovelSourceType.NOVEL_PHOENIX,
            "fwn-shadow-slave" to NovelSourceType.FREE_WEB_NOVEL,
            "np-1" to NovelSourceType.NOVEL_PHOENIX
        )) assertEquals(source, resolveReaderSource(id, repo, null))
        assertEquals(0, repo.chapterCalls)
        assertEquals(0, repo.contentCalls)
    }

    @Test fun leavingDuringSourceResolutionCancelsWithoutStartingReaders() = runTest {
        val gate = CompletableDeferred<Novel?>()
        val repo = object : ReaderTestRepository() {
            override suspend fun getNovelById(novelId: String) = gate.await()
        }
        val lookup = async { resolveReaderSource("asura-book", repo, null) }
        runCurrent()
        assertFalse(lookup.isCompleted)
        lookup.cancelAndJoin()
        gate.complete(Novel("asura-book", "Title", "", NovelSourceType.ASURA_SCANS, ""))
        assertTrue(lookup.isCancelled)
        assertEquals(0, repo.chapterCalls)
        assertEquals(0, repo.contentCalls)
    }

    @Test fun storageFailureDoesNotFallThroughToTheWrongReader() = runTest {
        val repo = object : ReaderTestRepository() {
            override suspend fun getNovelById(novelId: String): Novel? = throw IOException("Storage unavailable")
        }
        try {
            resolveReaderSource("legacy-book", repo, null)
            fail("A failed lookup must reach the entry's Retry state")
        } catch (_: IOException) { }
        assertEquals(0, repo.chapterCalls)
    }
}
