package com.novelnest.app.feature.reader.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt

/** Variable-height lazy content: approximate item-space progress, exact boundary jumps. */
private fun LazyListState.scrollRange(): Float {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    val averageHeight = (visible.sumOf { it.size }.toFloat() / visible.size.coerceAtLeast(1)).coerceAtLeast(1f)
    val viewport = (info.viewportEndOffset - info.viewportStartOffset -
        info.beforeContentPadding - info.afterContentPadding).coerceAtLeast(1)
    return (info.totalItemsCount - viewport / averageHeight).coerceAtLeast(1f)
}

private fun LazyListState.scrollFraction(): Float {
    if (!canScrollBackward) return 0f
    if (!canScrollForward) return 1f
    val height = layoutInfo.visibleItemsInfo.firstOrNull { it.index == firstVisibleItemIndex }
        ?.size?.coerceAtLeast(1) ?: 1
    return ((firstVisibleItemIndex + firstVisibleItemScrollOffset.toFloat() / height) / scrollRange())
        .coerceIn(0f, 1f)
}

private data class ScrollTarget(val fraction: Float, val finished: Boolean, val gesture: Int, val range: Float)

@Composable
fun ReaderFastScroller(
    listState: LazyListState,
    enabled: Boolean,
    color: Color,
    onDraggingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollable by remember(listState) {
        derivedStateOf { listState.canScrollBackward || listState.canScrollForward }
    }
    if (!enabled || !scrollable) return

    val density = LocalDensity.current
    val thumbPx = with(density) { 28.dp.toPx() }
    val thumbWidth = with(density) { 3.5.dp.toPx() }
    val rightMargin = with(density) { 4.dp.toPx() }
    val requests = remember(listState) { Channel<ScrollTarget>(Channel.CONFLATED) }
    val draggingChanged by rememberUpdatedState(onDraggingChanged)
    var dragFraction by remember(listState) { mutableStateOf<Float?>(null) }
    val thumbAlpha by animateFloatAsState(
        targetValue = if (dragFraction != null) 0.90f else 0.20f,
        animationSpec = tween(durationMillis = 200),
        label = "fast_scroller_thumb_alpha"
    )
    var gesture by remember { mutableIntStateOf(0) }
    var gestureRange by remember { mutableFloatStateOf(1f) }

    DisposableEffect(requests) {
        onDispose {
            requests.close()
            draggingChanged(false)
        }
    }
    LaunchedEffect(requests) {
        // One consumer; no coroutine/animation per pointer event. Coalesce to display cadence.
        for (initial in requests) {
            withFrameNanos { }
            var target = initial
            while (true) target = requests.tryReceive().getOrNull() ?: break
            val count = listState.layoutInfo.totalItemsCount
            try {
                if (count > 0) {
                    when {
                        target.fraction <= 0f -> listState.scrollToItem(0)
                        target.fraction >= 1f -> listState.scrollToItem(count - 1)
                        else -> {
                            val position = target.fraction * target.range
                            val index = position.toInt().coerceIn(0, count - 1)
                            val visible = listState.layoutInfo.visibleItemsInfo
                            val height = visible.firstOrNull { it.index == index }?.size
                                ?: (visible.sumOf { it.size } / visible.size.coerceAtLeast(1)).coerceAtLeast(1)
                            listState.scrollToItem(index, ((position - index) * height).roundToInt())
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                // A direct list gesture may interrupt a programmatic jump. It must not
                // permanently kill this consumer; disposal still cancels it normally.
                currentCoroutineContext().ensureActive()
            }
            if (target.finished && target.gesture == gesture) {
                dragFraction = null
                draggingChanged(false)
            }
        }
    }

    Box(modifier.width(48.dp).fillMaxHeight()) {
        Canvas(
            Modifier.fillMaxSize()
                .semantics {
                    contentDescription = "Chapter scroll position, approximate"
                    progressBarRangeInfo = ProgressBarRangeInfo(listState.scrollFraction(), 0f..1f)
                    setProgress { value ->
                        if (!value.isFinite()) false else {
                            draggingChanged(true)
                            gesture++
                            requests.trySend(ScrollTarget(value.coerceIn(0f, 1f), true, gesture, listState.scrollRange())).isSuccess
                        }
                    }
                }
                .pointerInput(listState, thumbPx) {
                    val verticalPadding = 16.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val thumb = thumbPx.coerceAtMost(size.height.toFloat())
                        val currentFraction = dragFraction ?: listState.scrollFraction()
                        val top = (size.height - thumb) * currentFraction
                        val bottom = top + thumb
                        val hitRange = (top - verticalPadding)..(bottom + verticalPadding)

                        // Only activate if the initial touch is directly on the scroll handle
                        if (down.position.y !in hitRange) {
                            return@awaitEachGesture
                        }

                        val grabOffset = (down.position.y - top).coerceIn(0f, thumb)
                        fun fraction(y: Float) = ((y - grabOffset) /
                            (size.height - thumb).coerceAtLeast(1f)).coerceIn(0f, 1f)

                        var dragStarted = false
                        try {
                            val dragChange = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                            }

                            if (dragChange != null) {
                                dragStarted = true
                                draggingChanged(true)
                                gesture++
                                gestureRange = listState.scrollRange()
                                dragFraction = fraction(dragChange.position.y)
                                requests.trySend(ScrollTarget(checkNotNull(dragFraction), false, gesture, gestureRange))

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val currentChange = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!currentChange.pressed) {
                                        break
                                    }
                                    if (currentChange.positionChange().y != 0f) {
                                        currentChange.consume()
                                        dragFraction = fraction(currentChange.position.y)
                                        requests.trySend(ScrollTarget(checkNotNull(dragFraction), false, gesture, gestureRange))
                                    }
                                }
                            }
                        } finally {
                            if (dragStarted) {
                                dragFraction?.let { requests.trySend(ScrollTarget(it, true, gesture, gestureRange)) }
                                dragFraction = null
                                draggingChanged(false)
                            }
                        }
                    }
                }
        ) {
            val thumb = thumbPx.coerceAtMost(size.height)
            val fraction = dragFraction ?: listState.scrollFraction()
            val thumbX = size.width - rightMargin - thumbWidth
            // Track line removed to provide a clean reading experience without background line distraction
            drawRoundRect(
                color = color.copy(alpha = thumbAlpha),
                topLeft = Offset(thumbX, (size.height - thumb) * fraction),
                size = Size(thumbWidth, thumb),
                cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f)
            )
        }
    }
}
