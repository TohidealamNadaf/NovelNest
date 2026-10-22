package com.novelnest.app.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/** Adjacent source navigation from unclaimed touches in inset side gutters.
 * Uses the existing screen and ViewModel; no offscreen pages are composed or fetched.
 */
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
    val gutter = with(density) { 32.dp.toPx() }
    val threshold = with(density) { 48.dp.toPx() }
    val travel = with(density) { 36.dp.toPx() }
    val reducedMotion = rememberAreAnimationsDisabled()
    val onSelect by rememberUpdatedState(onSourceSelected)
    val scope = rememberCoroutineScope()
    val playHaptic = rememberNovelNestHaptic()
    val translation = remember { Animatable(0f) }
    var previousSource by remember { mutableStateOf(selectedSource) }
    var switching by remember { mutableStateOf(false) }

    // Tab taps and swipes use the same incoming transition without duplicating page state.
    LaunchedEffect(selectedSource, reducedMotion, orderSign) {
        val direction = selectedSource.ordinal.compareTo(previousSource.ordinal)
        previousSource = selectedSource
        try {
            if (direction != 0 && !reducedMotion) {
                translation.snapTo(direction * orderSign * travel)
                switching = true
                translation.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            } else translation.snapTo(0f)
        } finally {
            switching = false
        }
    }

    return clipToBounds()
        .pointerInput(selectedSource, leftInset, rightInset, gutter, threshold, reducedMotion, orderSign) {
            awaitEachGesture {
                // Clickable cards, text fields and buttons keep their touches.
                val down = awaitFirstDown(requireUnconsumed = true)
                if (switching) return@awaitEachGesture
                val fromLeft = down.position.x in leftInset..(leftInset + gutter)
                val fromRight = down.position.x in (size.width - rightInset - gutter)..(size.width - rightInset)
                if (!fromLeft && !fromRight) return@awaitEachGesture
                val direction = if (fromRight) 1 else -1
                val target = NovelSourceType.entries.getOrNull(selectedSource.ordinal + direction * orderSign)
                    ?: return@awaitEachGesture
                var distance = Offset.Zero
                var claimed = false
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.size != 1) return@awaitEachGesture
                    val change = event.changes.first()
                    if (change.id != down.id || change.isConsumed) return@awaitEachGesture
                    distance += change.positionChange()
                    if (!claimed) {
                        // Vertical scrolling and outward/system gestures always win.
                        if (abs(distance.y) > viewConfiguration.touchSlop && abs(distance.y) >= abs(distance.x)) return@awaitEachGesture
                        if (abs(distance.x) > viewConfiguration.touchSlop) {
                            if (distance.x * direction >= 0f) return@awaitEachGesture
                            claimed = true
                        }
                    }
                    if (claimed) change.consume()
                    if (!change.pressed) {
                        if (claimed && -distance.x * direction >= threshold) {
                            switching = true
                            scope.launch {
                                try {
                                    if (!reducedMotion) translation.animateTo(-direction * travel, tween(100))
                                    playHaptic(NovelNestHapticType.LIGHT_TICK)
                                    onSelect(target)
                                } catch (cancelled: CancellationException) {
                                    // A tab tap can supersede the outgoing animation.
                                    // Its incoming transition owns the layer from this point.
                                    switching = false
                                    throw cancelled
                                }
                            }
                        }
                        return@awaitEachGesture
                    }
                }
            }
        }
        .graphicsLayer {
            translationX = translation.value
            alpha = 1f - (abs(translation.value) / travel).coerceIn(0f, 1f) * 0.25f
        }
}
