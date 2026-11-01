package com.novelnest.app.feature.reader

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import com.novelnest.app.data.preferences.AutoScrollSpeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One scroll mutation for this run; manual scrolling takes priority and cancels it. */
@Composable
internal fun ReaderAutoScrollEffect(
    listState: LazyListState,
    target: ReaderAutoScrollTarget?,
    session: ReaderAutoScrollState,
    eligible: Boolean,
    speed: Float,
    onInterrupted: (Long) -> Unit,
    onFinished: (Long) -> Unit
) {
    val pixelsPerSecond by rememberUpdatedState(
        AutoScrollSpeed.BASE_DP_PER_SECOND * AutoScrollSpeed.validated(speed) * LocalDensity.current.density
    )
    val interrupted by rememberUpdatedState(onInterrupted)
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(listState, target, session.runId, session.phase, eligible) {
        if (!eligible || target == null || session.target != target || session.phase != ReaderAutoScrollPhase.RUNNING)
            return@LaunchedEffect
        try {
            listState.scroll(MutatePriority.Default) {
                var previousFrame = withFrameNanos { it }
                while (true) {
                    val frame = withFrameNanos { it }
                    currentCoroutineContext().ensureActive()
                    if (!listState.canScrollForward) {
                        finished(session.runId)
                        break
                    }
                    // ScrollScope preserves fractional pixels; do not round slow speeds to zero.
                    scrollBy(autoScrollFrameDistance(frame - previousFrame, pixelsPerSecond))
                    previousFrame = frame
                }
            }
        } catch (cancelled: CancellationException) {
            // A competing gesture cancels the mutation's child scope, not this effect's owner.
            // Disposal must propagate cancellation without pausing a newer run.
            currentCoroutineContext().ensureActive()
            interrupted(session.runId)
        }
    }
}
