package com.novelnest.app.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.rememberAreAnimationsDisabled
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic
import com.novelnest.app.data.model.NovelSourceType
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private class SourceSwipeMotion {
    // Read only in the graphics layer: finger movement does not recompose the list.
    var offset by mutableFloatStateOf(0f)
    var busy = false
    var committedSource: NovelSourceType? = null
    var job: Job? = null
}

/** Apply to the persistent content viewport below the search bar, never the screen root. */
@Composable
fun Modifier.sourceSwipeNavigation(
    selectedSource: NovelSourceType,
    onSourceSelected: (NovelSourceType) -> Unit
): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val orderSign = if (layoutDirection == LayoutDirection.Rtl) -1 else 1
    val systemGestures = WindowInsets.systemGestures
    val edgePadding = with(density) { 8.dp.toPx() }
    val minimumInset = with(density) { 24.dp.toPx() }
    val leftInset = max(minimumInset, systemGestures.getLeft(density, layoutDirection) + edgePadding)
    val rightInset = max(minimumInset, systemGestures.getRight(density, layoutDirection) + edgePadding)
    val maximumGutter = with(density) { 112.dp.toPx() }
    val threshold = with(density) { 40.dp.toPx() }
    val reducedMotion = rememberAreAnimationsDisabled()
    val onSelect by rememberUpdatedState(onSourceSelected)
    val currentSource by rememberUpdatedState(selectedSource)
    val scope = rememberCoroutineScope()
    val playHaptic = rememberNovelNestHaptic()
    val motion = remember { SourceSwipeMotion() }
    var previousSource by remember { mutableStateOf(selectedSource) }

    suspend fun moveTo(target: Float, duration: Int) {
        if (reducedMotion) motion.offset = target
        else animate(motion.offset, target, animationSpec = tween(duration, easing = FastOutSlowInEasing)) { value, _ ->
            motion.offset = value
        }
    }

    fun settle(target: NovelSourceType?, direction: Int) {
        motion.busy = true
        motion.job = scope.launch {
            try {
                if (target == null) {
                    moveTo(0f, 160)
                } else {
                    // Switch only when the outgoing list is fully outside the viewport.
                    // The old short-distance snap could occur while content was still visible.
                    if (!reducedMotion) moveTo(-direction.toFloat(), 140)
                    motion.committedSource = target
                    playHaptic(NovelNestHapticType.LIGHT_TICK)
                    onSelect(target)
                    if (!reducedMotion) {
                        motion.offset = direction.toFloat()
                        moveTo(0f, 240)
                    }
                }
            } finally {
                motion.offset = 0f
                motion.busy = false
            }
        }
    }

    // A tab tap can supersede a swipe. Swipes already own their incoming animation.
    LaunchedEffect(selectedSource, reducedMotion, orderSign) {
        val direction = selectedSource.ordinal.compareTo(previousSource.ordinal) * orderSign
        previousSource = selectedSource
        if (motion.committedSource == selectedSource) {
            motion.committedSource = null
            return@LaunchedEffect
        }
        if (direction != 0) {
            motion.job?.cancelAndJoin()
            motion.busy = true
            motion.job = scope.launch {
                try {
                    if (!reducedMotion) motion.offset = direction.toFloat()
                    moveTo(0f, 240)
                } finally {
                    motion.offset = 0f
                    motion.busy = false
                }
            }
        }
    }

    return clipToBounds()
        .pointerInput(leftInset, rightInset, maximumGutter, threshold, reducedMotion, orderSign) {
            awaitEachGesture {
                // A clickable card consumes down, but a horizontal drag can still cancel
                // its click. Consumed movement (carousels/vertical scrolling) keeps priority.
                val down = awaitFirstDown(requireUnconsumed = false)
                if (motion.busy) return@awaitEachGesture
                val sourceAtDown = currentSource
                val gutter = min(maximumGutter, (size.width - leftInset - rightInset).coerceAtLeast(0f) * 0.4f)
                val fromLeft = down.position.x in leftInset..(leftInset + gutter)
                val fromRight = down.position.x in (size.width - rightInset - gutter)..(size.width - rightInset)
                if (!fromLeft && !fromRight) return@awaitEachGesture
                val direction = if (fromRight) 1 else -1
                val target = NovelSourceType.entries.getOrNull(sourceAtDown.ordinal + direction * orderSign)
                    ?: return@awaitEachGesture
                var distance = Offset.Zero
                var claimed = false
                var settling = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (currentSource != sourceAtDown || event.changes.size != 1) return@awaitEachGesture
                        val change = event.changes.first()
                        if (change.id != down.id || change.isConsumed) return@awaitEachGesture
                        distance += change.positionChange()
                        if (!claimed) {
                            if (abs(distance.y) > viewConfiguration.touchSlop && abs(distance.y) >= abs(distance.x)) return@awaitEachGesture
                            if (abs(distance.x) > viewConfiguration.touchSlop && abs(distance.x) > abs(distance.y) * 1.35f) {
                                if (distance.x * direction >= 0f) return@awaitEachGesture
                                claimed = true
                                motion.busy = true
                            }
                        }
                        if (claimed) {
                            if (!reducedMotion) motion.offset = (distance.x / size.width.coerceAtLeast(1)).coerceIn(-0.9f, 0.9f)
                            change.consume()
                        }
                        if (!change.pressed) {
                            if (claimed) {
                                settling = true
                                settle(target.takeIf { -distance.x * direction >= threshold }, direction)
                            }
                            return@awaitEachGesture
                        }
                    }
                } finally {
                    if (claimed && !settling && currentSource == sourceAtDown) settle(null, direction)
                }
            }
        }
        .graphicsLayer {
            translationX = motion.offset * size.width
        }
}
