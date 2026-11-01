package com.novelnest.app.feature.reader

import com.novelnest.app.data.preferences.AutoScrollSpeed
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.ai.AiAction
import com.novelnest.app.data.ai.AiProviderId
import com.novelnest.app.data.ai.AiResult
import com.novelnest.app.data.ai.AiRoute
import org.junit.Assert.*
import org.junit.Test

class ReaderAutoScrollTest {
    private val target = ReaderAutoScrollTarget("NOVEL_PHOENIX", "book", "chapter-83", "original")

    @Test fun repeatedStartCannotCreateCompetingRuns() {
        val controller = ReaderAutoScrollController()
        controller.start(target)
        val active = controller.state.value
        repeat(100) { controller.start(target) }
        assertEquals(active, controller.state.value)
    }

    @Test fun staleCompletionOrInterruptionCannotChangeResumedRun() {
        val controller = ReaderAutoScrollController()
        controller.start(target)
        val previous = controller.state.value.runId
        controller.pause()
        controller.start(target)
        val resumed = controller.state.value
        controller.finish(previous)
        controller.pause(previous)
        assertEquals(resumed, controller.state.value)
        controller.finish(resumed.runId)
        assertEquals(ReaderAutoScrollPhase.FINISHED, controller.state.value.phase)
    }

    @Test fun sourceChangesInvalidateBothRunningAndPausedSessions() {
        for (other in listOf(null, target.copy(chapterId = "chapter-84"), target.copy(novelId = "other"),
            target.copy(source = "FREE_WEB_NOVEL"), target.copy(contentKey = "rewrite:version"))) {
            val controller = ReaderAutoScrollController()
            controller.start(target)
            controller.sourceChanged(target)
            assertEquals(ReaderAutoScrollPhase.RUNNING, controller.state.value.phase)
            controller.pause()
            controller.sourceChanged(other)
            assertEquals(ReaderAutoScrollPhase.STOPPED, controller.state.value.phase)
            assertNull(controller.state.value.target)
        }
    }

    @Test fun stopThenStartDoesNotAllowOldRunToFinishNewOne() {
        val controller = ReaderAutoScrollController()
        controller.start(target)
        val previous = controller.state.value.runId
        controller.stop()
        controller.start(target)
        controller.finish(previous)
        assertEquals(ReaderAutoScrollPhase.RUNNING, controller.state.value.phase)
        assertTrue(controller.state.value.runId > previous)
        assertEquals(ReaderAutoScrollPhase.STOPPED, ReaderAutoScrollController().state.value.phase)
    }

    @Test fun elapsedTimeProducesConsistentMovementAcrossRefreshRates() {
        val pixelsPerSecond = 60f
        for (hz in listOf(60, 90, 120)) {
            val frame = 1_000_000_000L / hz
            val distance = (1..hz).sumOf { autoScrollFrameDistance(frame, pixelsPerSecond).toDouble() }.toFloat()
            assertEquals(60f, distance, 0.01f)
        }
        assertTrue(autoScrollFrameDistance(8_333_333, 5f) > 0f)
        assertTrue(autoScrollFrameDistance(8_333_333, 5f) < 1f)
    }

    @Test fun stalledFramesCannotCauseCatchUpJumps() {
        assertEquals(3f, autoScrollFrameDistance(10_000_000_000L, 60f), 0.001f)
        assertEquals(0f, autoScrollFrameDistance(-1, 60f), 0f)
    }

    @Test fun invalidSavedSpeedsRecoverToSafeDefaultAndBounds() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY))
            assertEquals(AutoScrollSpeed.DEFAULT, AutoScrollSpeed.validated(value), 0f)
        assertEquals(AutoScrollSpeed.MIN, AutoScrollSpeed.validated(-10f), 0f)
        assertEquals(AutoScrollSpeed.MAX, AutoScrollSpeed.validated(100f), 0f)
        assertEquals(1.5f, AutoScrollSpeed.validated(1.5f), 0f)
    }

    @Test fun targetUsesOnlyLoadedCurrentChapterAndMatchingDisplayVersion() {
        val chapter = Chapter("chapter-83", "Chapter 83", listOf("Original text"))
        val novel = Novel("book", "Book", "", NovelSourceType.NOVEL_PHOENIX, "")
        val reader = ReaderUiState(novel = novel, chapters = listOf(chapter))
        val ai = ReaderAiState(chapterId = chapter.id, sourceTitle = chapter.title, sourceParagraphs = chapter.paragraphs,
            version = ReaderContentVersion.REWRITE, rewrite = AiResult(AiAction.REWRITE, "fingerprint",
                AiRoute(AiProviderId.GROQ, "test"), rewrittenParagraphs = listOf("Rewritten text"), createdAtMs = 12L))
        assertEquals(target.copy(contentKey = ai.contentKey), readerAutoScrollTarget(reader, ai))
        assertEquals(target, readerAutoScrollTarget(reader, ai.copy(chapterId = "outgoing-chapter")))
        assertNull(readerAutoScrollTarget(reader.copy(isChapterLoading = true), ai))
        assertNull(readerAutoScrollTarget(reader.copy(isLoading = true), ai))
        assertNull(readerAutoScrollTarget(reader.copy(novel = null), ai))
        assertNull(readerAutoScrollTarget(reader.copy(chapters = listOf(chapter.copy(paragraphs = emptyList()))), ai))
    }

    @Test fun continuousMovementBoundsBundleWritesAndFlushesFinalPosition() {
        val checkpoints = ReaderScrollCheckpoint()
        var writes = 0
        for (time in 0L..999L) {
            if (checkpoints.record(ReaderScrollPosition(83, 2, time.toInt(), "original"), time) != null) writes++
        }
        assertEquals(4, writes)
        assertEquals(999, checkpoints.flush(1_000)?.offset)
        assertNull(checkpoints.flush(1_001))
    }

    @Test fun checkpointsKeepContentIdentityAndDoNotWriteIdleDuplicates() {
        val checkpoints = ReaderScrollCheckpoint()
        val original = ReaderScrollPosition(83, 10, 27, "original")
        assertEquals(original, checkpoints.record(original, 0))
        assertNull(checkpoints.record(original, 1_000))
        val rewritten = original.copy(item = 6, content = "rewrite:new")
        assertEquals(rewritten, checkpoints.record(rewritten, 1_001))
        val next = original.copy(chapter = 84, item = 0, offset = 0)
        assertNull(checkpoints.record(next, 1_002))
        assertEquals(next, checkpoints.flush(1_003))
    }
}
