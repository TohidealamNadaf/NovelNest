package com.novelnest.app.data.bookmark

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkSnapshotWriterTest {
    private fun book(id: String) = Novel(id, id, "", NovelSourceType.NOVEL_PHOENIX, "https://novelphoenix.com/novel/" + id)

    @Test fun queuedSnapshotsCoalesceWithoutMutatingTheSubmittedList() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val writes = mutableListOf<List<Novel>>()
            val writer = BookmarkSnapshotWriter(scope, dispatcher) { writes += it }
            writer.submit(listOf(book("old")))
            val latest = mutableListOf(book("new"))
            writer.submit(latest)
            latest.clear()
            assertTrue(writer.state.value.pending)
            advanceUntilIdle()
            assertEquals(listOf(listOf(book("new"))), writes)
            assertEquals(BookmarkWriteState(), writer.state.value)
        } finally { scope.cancel() }
    }

    @Test fun failureHasBoundedRetriesAndExplicitRecoveryUsesLatestSnapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            var calls = 0
            var fail = true
            var saved = emptyList<Novel>()
            val writer = BookmarkSnapshotWriter(scope, dispatcher) {
                calls++
                if (fail) throw IOException("Unavailable test storage")
                saved = it
            }
            writer.submit(listOf(book("one")))
            advanceUntilIdle()
            assertEquals(3, calls)
            assertTrue(writer.state.value.failed)
            advanceTimeBy(60_000); runCurrent()
            assertEquals(3, calls)
            fail = false
            writer.submit(listOf(book("two")))
            advanceUntilIdle()
            writer.retry(); advanceUntilIdle()
            assertEquals(listOf(book("two")), saved)
            assertEquals(4, calls)
            assertFalse(writer.state.value.pending)
        } finally { scope.cancel() }
    }

    @Test fun explicitRetryRecoversTheFailedSnapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            var fail = true
            var saved = emptyList<Novel>()
            val writer = BookmarkSnapshotWriter(scope, dispatcher) {
                if (fail) throw IOException("Unavailable test storage")
                saved = it
            }
            writer.submit(listOf(book("one"))); advanceUntilIdle()
            fail = false
            writer.retry(); advanceUntilIdle()
            assertEquals(listOf(book("one")), saved)
            assertFalse(writer.state.value.failed)
        } finally { scope.cancel() }
    }
}
