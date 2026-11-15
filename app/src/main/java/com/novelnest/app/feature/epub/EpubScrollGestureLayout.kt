package com.novelnest.app.feature.epub

import android.content.Context
import android.view.ActionMode
import android.view.View
import android.webkit.WebView
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max

/**
 * Readium 3.0.3 can interpret the curved tail of a vertical scroll as a chapter swipe.
 * Remember the initial intent for the whole touch sequence. Only the release X is
 * normalized for a non-horizontal drag; all vertical motion/fling samples stay intact.
 * No child interception, synthetic cancellation, WebView listener replacement, or JS is used.
 */
internal class EpubScrollGestureLayout(context: Context) : FrameLayout(context) {
    var continuousScrolling: Boolean = false
    var isTextSelectionActive: () -> Boolean = { false }

    var onDefineSelection: (WebView, ActionMode, String?) -> Unit = { _, _, _ -> }

    // Wrap the callback supplied by the actual WebView, retaining all native actions.
    override fun startActionModeForChild(child: View, callback: ActionMode.Callback): ActionMode? =
        super.startActionModeForChild(child, dictionaryCallback(child, callback))

    override fun startActionModeForChild(child: View, callback: ActionMode.Callback, type: Int): ActionMode? =
        super.startActionModeForChild(child, dictionaryCallback(child, callback), type)

    private fun dictionaryCallback(child: View, callback: ActionMode.Callback): ActionMode.Callback =
        if (child is WebView && callback !is EpubDictionarySelectionCallback)
            EpubDictionarySelectionCallback(callback, child) { view, mode, word -> onDefineSelection(view, mode, word) }
        else callback

    private enum class Intent { UNDECIDED, VERTICAL, HORIZONTAL, BYPASS }
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val minimumSwipe = 48f * resources.displayMetrics.density
    private val accessibility = context.getSystemService(AccessibilityManager::class.java)
    private var intent = Intent.UNDECIDED
    private var tracking = false
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var startX = 0f
    private var startY = 0f
    private var downTime = 0L
    private var moved = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            tracking = continuousScrolling && event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) &&
                !isTextSelectionActive() && accessibility?.isTouchExplorationEnabled != true
            intent = Intent.UNDECIDED
            moved = false
            startX = event.x
            startY = event.y
            downTime = event.downTime
            pointerId = event.getPointerId(0)
        }
        if (!tracking) return super.dispatchTouchEvent(event)

        if (!continuousScrolling || isTextSelectionActive() || event.pointerCount != 1 ||
            event.getPointerId(0) != pointerId || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            // Once multi-touch/selection starts, leave the rest of the sequence to the reader.
            intent = Intent.BYPASS
        }
        if (intent != Intent.BYPASS && (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_UP)) {
            // Batched events may already contain the curve. Classify the earlier samples first.
            for (index in 0 until event.historySize) {
                classify(event.getHistoricalX(0, index), event.getHistoricalY(0, index), event.getHistoricalEventTime(index))
            }
            classify(event.x, event.y, event.eventTime)
        }
        val suppressChapterSwipe = event.actionMasked == MotionEvent.ACTION_UP && moved && intent != Intent.BYPASS &&
            (intent != Intent.HORIZONTAL || abs(event.x - startX) < minimumSwipe)
        try {
            if (!suppressChapterSwipe) return super.dispatchTouchEvent(event)
            // Readium checks release X against ACTION_DOWN X to choose the chapter direction.
            // Keep them equal for a vertical/ambiguous drag. Never mutate the caller's event.
            val release = MotionEvent.obtain(event)
            return try {
                release.setLocation(startX, event.y)
                super.dispatchTouchEvent(release)
            } finally { release.recycle() }
        } finally {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                tracking = false
                pointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
    }

    private fun classify(x: Float, y: Float, time: Long) {
        val dx = abs(x - startX)
        val dy = abs(y - startY)
        if (max(dx, dy) > slop) moved = true
        if (intent != Intent.UNDECIDED) return
        if (time - downTime >= ViewConfiguration.getLongPressTimeout()) {
            intent = Intent.BYPASS
            return
        }
        intent = when {
            dy >= slop && dy >= dx * 0.75f -> Intent.VERTICAL
            dx >= slop * 2f && dx >= dy * 1.8f -> Intent.HORIZONTAL
            max(dx, dy) >= slop * 3f -> Intent.VERTICAL // ambiguous drags favor reading scroll
            else -> Intent.UNDECIDED
        }
    }
}
