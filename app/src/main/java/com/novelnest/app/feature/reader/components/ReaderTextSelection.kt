package com.novelnest.app.feature.reader.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.novelnest.app.R
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.data.preferences.ReaderTheme
import com.novelnest.app.feature.dictionary.DictionaryLookupActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private class SelectionRow {
    var layout: TextLayoutResult? = null
    var bounds: Rect? = null
    fun publish(selection: ReaderChapterSelection, index: Int) {
        val text = layout ?: return
        val rect = bounds ?: return
        if (text.layoutInput.text.text != selection.paragraphs[index]) return
        selection.layouts[index] = ReaderSelectionLayout(text, rect)
    }
}

/** Draw ranges in the existing Text rows; selection never changes text measurement or lazy keys. */
@Composable
internal fun ReaderTextSelection(selection: ReaderChapterSelection, index: Int, theme: ReaderTheme, enabled: () -> Boolean,
    content: @Composable (Modifier, (TextLayoutResult) -> Unit) -> Unit) {
    val row = remember(index) { SelectionRow() }
    val highlight = NovelPrimary.copy(alpha = if (theme.isDark) 0.28f else 0.35f)
    DisposableEffect(selection, index) { onDispose { selection.layouts.remove(index) } }
    SideEffect { row.publish(selection, index) }
    content(Modifier
        .onGloballyPositioned {
            // Keep the actual text origin when its first lines are clipped by the lazy viewport.
            row.bounds = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
            row.publish(selection, index)
        }
        .drawWithContent {
            val offsets = selection.selectedOffsets(index)
            if (offsets != null) row.layout?.let { drawPath(it.getPathForRange(offsets.first, offsets.last + 1), highlight) }
            drawContent()
        }
        .semantics {
            onLongClick(label = "Select text") {
                if (!enabled()) return@onLongClick false
                val bounds = row.bounds
                val layout = row.layout
                val started = if (bounds != null && layout != null && layout.lineCount > 0)
                    selection.beginWord(bounds.topLeft + Offset(1f, layout.getLineBottom(0) / 2f)) else false
                if (started) selection.endDrag()
                started
            }
        }, { row.layout = it; row.publish(selection, index) })
}

/** A stable viewport listener survives row recycling during long-press selection dragging. */
internal fun Modifier.readerSelectionGesture(selection: ReaderChapterSelection, enabled: () -> Boolean): Modifier =
    pointerInput(selection) {
        detectDragGesturesAfterLongPress(
            onDragStart = { if (enabled()) selection.beginWord(selection.hostBounds.topLeft + it) },
            onDrag = { change, _ ->
                if (selection.dragging) { change.consume(); selection.dragTo(selection.hostBounds.topLeft + change.position) }
            },
            onDragEnd = selection::endDrag,
            onDragCancel = selection::endDrag
        )
    }

@Composable
internal fun BoxScope.ReaderSelectionOverlay(selection: ReaderChapterSelection, listState: LazyListState, enabled: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val eligible by rememberUpdatedState(enabled && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    val toolbar = remember(selection, view, owner) {
        ReaderSelectionToolbar(view, selection, canUse = { eligible }, onCopy = {
            val expected = selection.range
            scope.launch {
                val copied = withContext(Dispatchers.Default) { selection.selectedText(selected = expected) }
                if (eligible && expected != null && selection.range == expected && copied != null) {
                    try {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Novel Nest", copied))
                        selection.clear()
                    } catch (_: RuntimeException) {
                        Toast.makeText(context, "Could not copy this selection. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }, onDefine = { word ->
            if (eligible && view.isAttachedToWindow) {
                try {
                    // Explicit same-app component: this action cannot appear in external applications.
                    context.startActivity(Intent(context, DictionaryLookupActivity::class.java)
                        .setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
                        .putExtra(Intent.EXTRA_PROCESS_TEXT, word).putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true))
                    selection.clear()
                } catch (_: RuntimeException) {
                    Toast.makeText(context, R.string.dictionary_cannot_open, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
    DisposableEffect(toolbar, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { toolbar.close(); selection.clear() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); toolbar.close(); selection.clear() }
    }
    LaunchedEffect(enabled) { if (!enabled) selection.clear() }
    BackHandler(enabled = enabled && selection.active) { selection.clear() }
    // One event-driven observer tracks range and geometry; no polling or coroutine per pointer event.
    LaunchedEffect(toolbar, enabled) {
        snapshotFlow {
            Triple(selection.range, selection.dragging, if (selection.dragging) null else selection.selectionRect())
        }.collect { toolbar.update() }
    }
    val density = LocalDensity.current
    val edge = with(density) { 48.dp.toPx() }
    val speed = with(density) { 520.dp.toPx() }
    val atEdge by remember(selection, edge, speed, enabled) { derivedStateOf { enabled && selection.edgeVelocity(edge, speed) != 0f } }
    LaunchedEffect(selection, listState, atEdge) {
        if (!atEdge) return@LaunchedEffect
        listState.scroll(MutatePriority.UserInput) {
            var previous = withFrameNanos { it }
            while (selection.dragging) {
                val frame = withFrameNanos { it }
                val velocity = selection.edgeVelocity(edge, speed)
                if (velocity == 0f) break
                val distance = velocity * ((frame - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
                previous = frame
                if (scrollBy(distance) == 0f) break
                selection.pointer?.let(selection::dragTo)
            }
        }
    }
    if (enabled && selection.active) {
        SelectionHandle(selection, start = true)
        SelectionHandle(selection, start = false)
    }
}

@Composable
private fun BoxScope.SelectionHandle(selection: ReaderChapterSelection, start: Boolean) {
    val rawPosition = selection.handle(start)
    val held = selection.dragging && selection.draggedHandle == start
    // Keep the active drag node alive while a row is clipped/recycled during edge scrolling.
    val position = if (held) {
        val point = rawPosition ?: selection.pointer ?: return
        Offset(point.x.coerceIn(selection.viewport.left, selection.viewport.right),
            point.y.coerceIn(selection.viewport.top, selection.viewport.bottom))
    } else rawPosition?.takeIf { it.y >= selection.viewport.top && it.y <= selection.viewport.bottom } ?: return
    val density = LocalDensity.current
    val width = with(density) { 48.dp.toPx() }
    val boxLeft = (position.x - selection.hostBounds.left - if (start) width else 0f)
        .coerceIn(0f, (selection.hostBounds.width - width).coerceAtLeast(0f))
    val tipX = position.x - selection.hostBounds.left - boxLeft
    Box(Modifier
        .offset { IntOffset(boxLeft.roundToInt(),
            (position.y - selection.hostBounds.top - width / 4f).roundToInt()) }
        .size(48.dp)
        .semantics {
            contentDescription = if (start) "Selection start" else "Selection end"
            customActions = listOf(
                CustomAccessibilityAction("Move backward") { selection.nudge(start, false); true },
                CustomAccessibilityAction("Move forward") { selection.nudge(start, true); true })
        }
        .pointerInput(selection, start) {
            var pointer = Offset.Zero
            detectDragGestures(
                onDragStart = { pointer = selection.handle(start) ?: position; selection.beginHandle(start, pointer) },
                onDrag = { change, delta -> pointer += delta; change.consume(); selection.dragTo(pointer) },
                onDragEnd = selection::endDrag, onDragCancel = selection::endDrag)
        }) {
        Canvas(Modifier.size(48.dp)) {
            val radius = 6.dp.toPx()
            val tip = Offset(tipX, size.height / 4)
            drawLine(NovelPrimary, tip, tip + Offset(0f, radius), 2.dp.toPx())
            drawCircle(NovelPrimary, radius, tip + Offset(if (start) -radius else radius, radius * 1.8f))
        }
    }
}
