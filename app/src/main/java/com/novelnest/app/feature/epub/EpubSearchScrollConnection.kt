package com.novelnest.app.feature.epub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/**
 * Observe user intent without consuming any grid movement. Header resizing can clamp a short
 * grid's scroll position, so layout offsets must never decide whether to reverse the animation.
 * The accumulator is ordinary state: scrolling does not recompose the page on every pixel.
 */
internal class EpubSearchScrollConnection(
    private val thresholdPx: Float,
    private val atStart: () -> Boolean,
    private val onCollapseChange: (Boolean) -> Unit
) : NestedScrollConnection {
    init { require(thresholdPx.isFinite() && thresholdPx > 0f) }
    private var accumulatedY = 0f

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // Ignore fling/programmatic movement; horizontal Recently Read gestures are independent.
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        val requested = consumed + available
        if (abs(requested.y) <= abs(requested.x)) return Offset.Zero
        val delta = when {
            consumed.y != 0f -> consumed.y
            // A collapsed header must still be revealable if expanding the viewport made the
            // entire shelf fit. Downward pull at the start is an explicit reveal gesture.
            available.y > 0f && atStart() -> available.y
            else -> return Offset.Zero
        }
        if ((delta > 0f && accumulatedY < 0f) || (delta < 0f && accumulatedY > 0f)) accumulatedY = 0f
        accumulatedY = (accumulatedY + delta).coerceIn(-thresholdPx, thresholdPx)
        if (abs(accumulatedY) >= thresholdPx) {
            onCollapseChange(accumulatedY < 0f)
            accumulatedY = 0f
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // A short, fast flick can release before the distance threshold. Commit its already
        // observed drag direction once; subsequent fling/layout movement cannot reverse it.
        if ((available.y > 0f && accumulatedY > 0f) || (available.y < 0f && accumulatedY < 0f)) {
            onCollapseChange(accumulatedY < 0f)
        }
        accumulatedY = 0f
        return Velocity.Zero
    }
}
