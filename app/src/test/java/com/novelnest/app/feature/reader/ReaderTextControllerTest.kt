package com.novelnest.app.feature.reader

import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.text.ChapterEscapeDecoder
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderTextControllerTest {
    private val source = NovelSourceType.NOVEL_PHOENIX
    private fun chapter(id: String) = Chapter(id, "Chapter", listOf("It\\u2019s " + id))
    @Test fun disabledAndOtherSourcesBypassBackgroundWork() = runTest {
        var calls = 0
        val controller = ReaderTextController(this, StandardTestDispatcher(testScheduler)) { s, c, e, check ->
            calls++; ChapterEscapeDecoder.prepare(s, c, e, check)
        }
        val raw = chapter("one")
        controller.submit(source, raw, false)
        assertSame(raw, controller.state.value.resolve(source, raw, false))
        controller.submit(NovelSourceType.ASURA_SCANS, raw, true)
        advanceUntilIdle()
        assertSame(raw, controller.state.value.resolve(NovelSourceType.ASURA_SCANS, raw, true))
        assertEquals(0, calls)
    }
    @Test fun repeatedRequestsAndReturningToCachedChapterReusePreparedText() = runTest {
        var calls = 0
        val controller = ReaderTextController(this, StandardTestDispatcher(testScheduler)) { s, c, e, check ->
            calls++; ChapterEscapeDecoder.prepare(s, c, e, check)
        }
        val first = chapter("one"); val second = chapter("two")
        controller.submit(source, first, true)
        advanceUntilIdle()
        val prepared = controller.state.value.resolve(source, first, true)!!
        repeat(100) { controller.submit(source, first, true) }
        controller.submit(source, second, true)
        advanceUntilIdle()
        controller.submit(source, first, true)
        advanceUntilIdle()
        assertSame(prepared, controller.state.value.resolve(source, first, true))
        assertEquals(2, calls)
    }
    @Test fun dataEqualRefreshResolvesEvenWhenStateFlowSuppressesItsEmission() = runTest {
        var calls = 0
        val controller = ReaderTextController(this, StandardTestDispatcher(testScheduler)) { s, c, e, check ->
            calls++; ChapterEscapeDecoder.prepare(s, c, e, check)
        }
        val first = chapter("one")
        controller.submit(source, first, true); advanceUntilIdle()
        val fresh = first.copy(paragraphs = first.paragraphs.toList())
        assertNotNull(controller.state.value.resolve(source, fresh, true))
        controller.submit(source, fresh, true); advanceUntilIdle()
        assertEquals(1, calls)
        assertSame(fresh, controller.state.value.entries.first().original)
    }
    @Test fun staleUncooperativeWorkCannotOverwriteTheNewChapter() = runTest {
        val gate = CompletableDeferred<Unit>()
        val first = chapter("one"); val second = chapter("two")
        val controller = ReaderTextController(this, StandardTestDispatcher(testScheduler)) { s, c, e, check ->
            if (c === first) withContext(NonCancellable) { gate.await() }
            ChapterEscapeDecoder.prepare(s, c, e, check)
        }
        controller.submit(source, first, true)
        runCurrent()
        controller.submit(source, second, true)
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(controller.state.value.resolve(source, first, true))
        assertEquals("It\u2019s two", controller.state.value.resolve(source, second, true)!!.paragraphs.single())
    }
    @Test fun switchingOffDuringPreparationRestoresOriginalAndDropsLateResult() = runTest {
        val gate = CompletableDeferred<Unit>()
        val raw = chapter("one")
        val controller = ReaderTextController(this, StandardTestDispatcher(testScheduler)) { s, c, e, check ->
            withContext(NonCancellable) { gate.await() }
            ChapterEscapeDecoder.prepare(s, c, e, check)
        }
        controller.submit(source, raw, true)
        runCurrent()
        controller.submit(source, raw, false)
        assertSame(raw, controller.state.value.resolve(source, raw, false))
        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(controller.state.value.resolve(source, raw, true))
    }
    @Test fun refreshWithNewChapterObjectAndSameIdUsesNewContent() = runTest {
        val controller = ReaderTextController(this, StandardTestDispatcher(testScheduler))
        val first = chapter("one")
        controller.submit(source, first, true); advanceUntilIdle()
        val refreshed = first.copy(paragraphs = listOf("New &#65;"))
        controller.submit(source, refreshed, true); advanceUntilIdle()
        assertNull(controller.state.value.resolve(source, first, true))
        assertEquals(listOf("New A"), controller.state.value.resolve(source, refreshed, true)!!.paragraphs)
    }
    @Test fun preparationFailureShowsOriginalAndHasAnActionableMessage() = runTest {
        val controller = ReaderTextController(this, StandardTestDispatcher(testScheduler)) { _, _, _, _ -> error("fault") }
        val raw = chapter("one")
        controller.submit(source, raw, true); advanceUntilIdle()
        assertSame(raw, controller.state.value.resolve(source, raw, true))
        assertNotNull(controller.state.value.failure)
        controller.submit(source, raw, false)
        assertNull(controller.state.value.failure)
    }
    @Test fun allocationFailureFallsBackToOriginalInsteadOfEscapingTheReaderCoroutine() = runTest {
        val controller = ReaderTextController(this, StandardTestDispatcher(testScheduler)) { _, _, _, _ -> throw OutOfMemoryError("simulated allocation failure") }
        val raw = chapter("one")
        controller.submit(source, raw, true); advanceUntilIdle()
        assertSame(raw, controller.state.value.resolve(source, raw, true))
        assertTrue(controller.state.value.failure!!.contains("memory"))
    }
    @Test fun cacheIsBoundedAndOversizedResultsAreNotRetainedForReuse() = runTest {
        val controller = ReaderTextController(this, StandardTestDispatcher(testScheduler))
        repeat(20) { index ->
            controller.submit(source, chapter(index.toString()), true); advanceUntilIdle()
            assertTrue(controller.state.value.entries.size <= 2)
        }
        val large = Chapter("huge", "Title", listOf("a".repeat(300000)))
        controller.submit(source, large, true); advanceUntilIdle()
        assertNotNull(controller.state.value.resolve(source, large, true))
        assertEquals(1, controller.state.value.entries.size)
        controller.submit(source, chapter("after-large"), true); advanceUntilIdle()
        assertNull(controller.state.value.resolve(source, large, true))
    }
    @Test fun slowPreparationFallsBackWithoutLeavingTheReaderPending() = runTest {
        val controller = ReaderTextController(this, StandardTestDispatcher(testScheduler)) { _, chapter, _, _ ->
            delay(60_000)
            chapter
        }
        val raw = chapter("one")
        controller.submit(source, raw, true)
        runCurrent()
        assertNull(controller.state.value.resolve(source, raw, true))
        advanceTimeBy(2_001)
        runCurrent()
        assertSame(raw, controller.state.value.resolve(source, raw, true))
        assertTrue(controller.state.value.failure!!.contains("too long"))
    }
    @Test fun decodingWhitespaceOnlyCannotMakeTheVisibleChapterDisappear() = runTest {
        val controller = ReaderTextController(this, StandardTestDispatcher(testScheduler))
        val raw = Chapter("one", "One", listOf("\\u0020&#32;"))
        controller.submit(source, raw, true)
        advanceUntilIdle()
        assertSame(raw, controller.state.value.resolve(source, raw, true))
        assertTrue(controller.state.value.failure!!.contains("no readable text"))
    }
}
