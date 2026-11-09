package com.novelnest.app.data.progress

import com.novelnest.app.data.database.dao.ReadingProgressDao
import com.novelnest.app.data.database.entity.ReadingProgressEntity
import com.novelnest.app.testsupport.ReaderTestProgressDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressPersistenceTest {
    @Test fun transientWriteFailureRetriesWithoutLosingNovelReadIds() = runTest {
        val backing = ReaderTestProgressDao()
        var attempts = 0
        val dao = object : ReadingProgressDao by backing {
            override suspend fun insertOrUpdate(progress: ReadingProgressEntity) {
                attempts++
                if (attempts < 3) throw IOException("Test failure")
                backing.insertOrUpdate(progress)
            }
        }
        val manager = ReadingProgressManager(dao, this)
        manager.awaitLoaded()
        manager.markChapterRead("np-book", "chapter-87", 86, "Chapter 87")
        advanceUntilIdle()
        assertEquals(3, attempts)
        assertEquals(listOf("chapter-87"), backing.getProgressOnce("np-book")!!.readChapterIds)
        assertTrue(manager.persistenceState.value.failedNovelIds.isEmpty())
    }

    @Test fun failedHydrationNeverOverwritesPreviouslySavedHistory() = runTest {
        val backing = ReaderTestProgressDao()
        backing.insertOrUpdate(ReadingProgressEntity("np-book", "old", 0, "Old", 1L, listOf("old")))
        var readsFail = true
        var writes = 0
        val dao = object : ReadingProgressDao by backing {
            override suspend fun getAllProgressOnce(): List<ReadingProgressEntity> {
                if (readsFail) throw IOException("Test read failure")
                return backing.getAllProgressOnce()
            }
            override suspend fun insertOrUpdate(progress: ReadingProgressEntity) {
                writes++; backing.insertOrUpdate(progress)
            }
        }
        val manager = ReadingProgressManager(dao, this)
        manager.awaitLoaded()
        manager.markChapterRead("np-book", "new", 1, "New")
        advanceUntilIdle()
        assertEquals(0, writes)
        assertEquals(listOf("old"), backing.getProgressOnce("np-book")!!.readChapterIds)
        assertTrue(manager.persistenceState.value.loadFailed)
        readsFail = false
        manager.retryPendingWrites(); advanceUntilIdle()
        assertEquals(setOf("old", "new"), backing.getProgressOnce("np-book")!!.readChapterIds.toSet())
        assertFalse(manager.persistenceState.value.loadFailed)
        assertTrue(manager.persistenceState.value.failedNovelIds.isEmpty())
    }

    @Test fun permanentWriteFailureRemainsObservableUntilExplicitRecovery() = runTest {
        val backing = ReaderTestProgressDao()
        var fails = true
        var writes = 0
        val dao = object : ReadingProgressDao by backing {
            override suspend fun insertOrUpdate(progress: ReadingProgressEntity) {
                writes++
                if (fails) throw IOException("Test write failure")
                backing.insertOrUpdate(progress)
            }
        }
        val manager = ReadingProgressManager(dao, this)
        manager.awaitLoaded()
        manager.markChapterRead("np-book", "one", 0, "One")
        advanceUntilIdle()
        assertEquals(3, writes)
        assertEquals(setOf("np-book"), manager.persistenceState.value.failedNovelIds)
        fails = false; manager.retryPendingWrites(); advanceUntilIdle()
        assertNotNull(backing.getProgressOnce("np-book"))
        assertTrue(manager.persistenceState.value.failedNovelIds.isEmpty())
    }
}
