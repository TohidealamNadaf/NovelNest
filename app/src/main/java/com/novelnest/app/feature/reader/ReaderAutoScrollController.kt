package com.novelnest.app.feature.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ReaderAutoScrollTarget(val source: String, val novelId: String, val chapterId: String, val contentKey: String)
enum class ReaderAutoScrollPhase { STOPPED, RUNNING, PAUSED, FINISHED }
data class ReaderAutoScrollState(
    val target: ReaderAutoScrollTarget? = null,
    val phase: ReaderAutoScrollPhase = ReaderAutoScrollPhase.STOPPED,
    val runId: Long = 0
)

/** Session intent only. Owns no view, list state, timer or saved running flag. */
class ReaderAutoScrollController {
    private val mutable = MutableStateFlow(ReaderAutoScrollState())
    val state = mutable.asStateFlow()

    fun start(target: ReaderAutoScrollTarget) {
        mutable.update {
            if (it.target == target && it.phase == ReaderAutoScrollPhase.RUNNING) it
            else ReaderAutoScrollState(target, ReaderAutoScrollPhase.RUNNING, it.runId + 1)
        }
    }

    fun pause(runId: Long? = null) {
        mutable.update {
            if (it.phase == ReaderAutoScrollPhase.RUNNING && (runId == null || runId == it.runId))
                it.copy(phase = ReaderAutoScrollPhase.PAUSED) else it
        }
    }

    fun finish(runId: Long) {
        mutable.update {
            if (it.runId == runId && it.phase == ReaderAutoScrollPhase.RUNNING)
                it.copy(phase = ReaderAutoScrollPhase.FINISHED) else it
        }
    }

    fun stop() { mutable.update { ReaderAutoScrollState(runId = it.runId + 1) } }

    fun sourceChanged(target: ReaderAutoScrollTarget?) {
        if (mutable.value.target != null && mutable.value.target != target) stop()
    }
}

internal fun readerAutoScrollTarget(reader: ReaderUiState, ai: ReaderAiState): ReaderAutoScrollTarget? {
    if (reader.isLoading || reader.isChapterLoading) return null
    val novel = reader.novel ?: return null
    val chapter = reader.chapters.getOrNull(reader.currentChapterIndex) ?: return null
    if (chapter.paragraphs.none { it.isNotBlank() }) return null
    return ReaderAutoScrollTarget(novel.source.name, novel.id, chapter.id,
        if (ai.matches(chapter)) ai.contentKey else "original")
}

/** No catch-up movement after a stalled frame, background interval or debugger pause. */
internal fun autoScrollFrameDistance(elapsedNanos: Long, pixelsPerSecond: Float): Float =
    elapsedNanos.coerceIn(0L, 50_000_000L) / 1_000_000_000f * pixelsPerSecond
