package com.novelnest.app.feature.reader.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val thumbPx = with(density) { 48.dp.toPx() }
    val thumbWidth = with(density) { 4.dp.toPx() }
    val rightMargin = with(density) { 4.dp.toPx() }
    val requests = remember(listState) { Channel<ScrollTarget>(Channel.CONFLATED) }
    val draggingChanged by rememberUpdatedState(onDraggingChanged)
    var dragFraction by remember(listState) { mutableStateOf<Float?>(null) }
    val thumbAlpha by animateFloatAsState(
        targetValue = if (dragFraction != null) 0.85f else 0.35f,
        label = "fast_scroller_thumb_alpha"
    )
    var trackHeight by remember { mutableIntStateOf(0) }
    var gesture by remember { mutableIntStateOf(0) }
    var gestureRange by remember { mutableFloatStateOf(1f) }
    val percent by remember(listState) {
        derivedStateOf { ((dragFraction ?: listState.scrollFraction()) * 100).roundToInt() }
    }

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

    Box(modifier.width(100.dp).fillMaxHeight().onSizeChanged { trackHeight = it.height }) {
        Canvas(
            Modifier.align(Alignment.CenterEnd).width(48.dp).fillMaxHeight()
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
                    var grabOffset = thumbPx / 2f
                    fun fraction(y: Float) = ((y - grabOffset) /
                        (size.height - thumbPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    fun finish() {
                        dragFraction?.let { requests.trySend(ScrollTarget(it, true, gesture, gestureRange)) }
                    }
                    try {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                draggingChanged(true)
                                gesture++
                                // Keep the mapping stable as differently sized paragraphs enter view.
                                gestureRange = listState.scrollRange()
                                val top = (size.height - thumbPx).coerceAtLeast(0f) * listState.scrollFraction()
                                grabOffset = if (offset.y in top..(top + thumbPx)) offset.y - top else thumbPx / 2f
                                dragFraction = fraction(offset.y)
                                requests.trySend(ScrollTarget(checkNotNull(dragFraction), false, gesture, gestureRange))
                            },
                            onDragEnd = { finish() },
                            onDragCancel = { finish() },
                            onVerticalDrag = { change, _ ->
                                change.consume()
                                dragFraction = fraction(change.position.y)
                                requests.trySend(ScrollTarget(checkNotNull(dragFraction), false, gesture, gestureRange))
                            }
                        )
                    } finally {
                        dragFraction = null
                        draggingChanged(false)
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
        if (dragFraction != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 20.dp)
                    .offset {
                        IntOffset(
                            0,
                            (((trackHeight - thumbPx).coerceAtLeast(0f) *
                                (dragFraction ?: 0f)) + thumbPx / 2 - 12.dp.toPx()).roundToInt()
                        )
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$percent%",
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
