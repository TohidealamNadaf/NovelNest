package com.novelnest.app.feature.reader

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelnest.app.feature.reader.components.ScrollableNoteEditText
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native editor/Compose interop regression checks; no storage or network is used. */
@RunWith(AndroidJUnit4::class)
class NoteEditorSelectionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var editor: ScrollableNoteEditText
    private lateinit var host: TouchHost

    private class TouchHost(context: Context) : FrameLayout(context) {
        var disallowIntercept = false
        override fun requestDisallowInterceptTouchEvent(disallow: Boolean) {
            disallowIntercept = disallow
            super.requestDisallowInterceptTouchEvent(disallow)
        }
    }

    private fun mount(text: String) {
        compose.setContent {
            AndroidView(factory = { context ->
                TouchHost(context).also { container ->
                    host = container
                    editor = ScrollableNoteEditText(context).apply {
                        showSoftInputOnFocus = false
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        gravity = android.view.Gravity.TOP
                        setText(text)
                        setSelection(0)
                    }
                    container.addView(editor, FrameLayout.LayoutParams(-1, -1))
                }
            }, modifier = Modifier.size(300.dp, 180.dp))
        }
        compose.runOnIdle { editor.requestFocus() }
        frames()
    }

    private fun frames() {
        val latch = CountDownLatch(1)
        compose.runOnIdle { editor.postOnAnimation { editor.postOnAnimation { latch.countDown() } } }
        assertTrue("Native frame callbacks did not complete", latch.await(3, TimeUnit.SECONDS))
        compose.waitForIdle()
    }

    private fun touch(action: Int, downTime: Long, eventTime: Long, y: Float) {
        val event = MotionEvent.obtain(downTime, eventTime, action, editor.width / 2f, y, 0)
        try { editor.dispatchTouchEvent(event) } finally { event.recycle() }
    }

    @Test fun draggingRangeDoesNotRelocateFormOrCollapseSelection() {
        val text = (1..80).joinToString("\n") { "Line $it of a long note" }
        mount(text)
        var requests = 0
        var cancellations = 0
        compose.runOnIdle {
            editor.onCursorRectangleRequested = { requests++ }
            editor.onCursorRevealCancelled = { cancellations++ }
            editor.requestCursorVisibility()
        }
        frames()
        compose.runOnIdle {
            assertTrue(requests > 0)
            requests = 0
            editor.setSelection(2, text.length - 3)
            editor.requestCursorVisibility()
            editor.scrollTo(0, 100)
            editor.requestLayout()
        }
        frames()
        compose.runOnIdle {
            assertEquals(0, requests)
            assertTrue(cancellations > 0)
            assertEquals(2, editor.selectionStart)
            assertEquals(text.length - 3, editor.selectionEnd)
            assertEquals(text, editor.text.toString())
        }
    }

    @Test fun shortNoteOwnsLongPressSelectionAtItsScrollBoundary() {
        mount("A short note for selection")
        compose.runOnIdle {
            assertFalse(editor.canScrollVertically(-1))
            assertFalse(editor.canScrollVertically(1))
            val down = SystemClock.uptimeMillis()
            val y = editor.height / 2f
            touch(MotionEvent.ACTION_DOWN, down, down, y)
            assertTrue(host.disallowIntercept)
            editor.setSelection(2, 7)
            val slop = ViewConfiguration.get(editor.context).scaledTouchSlop
            touch(MotionEvent.ACTION_MOVE, down, down + ViewConfiguration.getLongPressTimeout() + 1L, y - slop * 3)
            assertTrue(host.disallowIntercept)
            assertFalse(editor.canRevealCursor)
            touch(MotionEvent.ACTION_CANCEL, down, down + ViewConfiguration.getLongPressTimeout() + 2L, y - slop * 3)
            assertFalse(host.disallowIntercept)
        }
    }

    @Test fun ordinaryEdgeScrollWaitsForTouchSlopBeforeYielding() {
        mount("A short note")
        compose.runOnIdle {
            val down = SystemClock.uptimeMillis()
            val y = editor.height / 2f
            val slop = ViewConfiguration.get(editor.context).scaledTouchSlop
            touch(MotionEvent.ACTION_DOWN, down, down, y)
            assertTrue(host.disallowIntercept)
            touch(MotionEvent.ACTION_MOVE, down, down + 10L, y - slop / 2f)
            assertTrue(host.disallowIntercept)
            touch(MotionEvent.ACTION_MOVE, down, down + 20L, y - slop * 3)
            assertFalse(host.disallowIntercept)
            touch(MotionEvent.ACTION_CANCEL, down, down + 30L, y - slop * 3)
        }
    }

    @Test fun tapDefersKeyboardRevealAndCancelledTouchReleasesOwnership() {
        mount("A short note")
        var requests = 0
        compose.runOnIdle {
            editor.onCursorRectangleRequested = { requests++ }
            val down = SystemClock.uptimeMillis()
            val y = editor.height / 2f
            touch(MotionEvent.ACTION_DOWN, down, down, y)
            editor.requestCursorVisibility()
            assertEquals(0, requests)
            touch(MotionEvent.ACTION_UP, down, down + 20L, y)
            assertFalse(host.disallowIntercept)
        }
        frames()
        compose.runOnIdle {
            assertTrue(requests > 0)
            requests = 0
            val down = SystemClock.uptimeMillis()
            val y = editor.height / 2f
            touch(MotionEvent.ACTION_DOWN, down, down, y)
            editor.requestCursorVisibility()
            touch(MotionEvent.ACTION_CANCEL, down, down + 20L, y)
            assertFalse(host.disallowIntercept)
        }
        frames()
        compose.runOnIdle { assertEquals(0, requests) }
    }
}
