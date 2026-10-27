package com.novelnest.app.feature.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelnest.app.data.preferences.ReaderSettings
import com.novelnest.app.data.preferences.ReaderTheme
import com.novelnest.app.feature.reader.components.ReaderQuickSettingsSheet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Compose list/mutation tests; no provider, narration or network request is involved. */
@RunWith(AndroidJUnit4::class)
class ReaderAutoScrollUiTest {
    @get:Rule val compose = createComposeRule()
    private val controller = ReaderAutoScrollController()
    private val target = ReaderAutoScrollTarget("NOVEL_PHOENIX", "book", "one", "original")
    private val speed = mutableFloatStateOf(1f)
    private val eligible = mutableStateOf(true)
    private lateinit var list: LazyListState
    private var rowHeightPx = 1f

    @Composable private fun reader() {
        val session by controller.state.collectAsState()
        val listState = rememberLazyListState()
        val rowHeight = with(LocalDensity.current) { 40.dp.toPx() }
        SideEffect { list = listState; rowHeightPx = rowHeight }
        ReaderAutoScrollEffect(listState, target, session, eligible.value, speed.floatValue,
            onInterrupted = { controller.pause(it) }, onFinished = controller::finish)
        LazyColumn(state = listState, modifier = Modifier.size(300.dp, 220.dp).testTag("AutoScrollReader")) {
            items(200) { index -> Box(Modifier.fillMaxWidth().height(40.dp)) { Text("Paragraph $index") } }
        }
    }

    private fun position() = list.firstVisibleItemIndex * rowHeightPx + list.firstVisibleItemScrollOffset

    @Test fun changingSpeedDoesNotRestartSessionAndPauseLeavesPositionStable() {
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { reader() } }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { controller.start(target) }
        compose.mainClock.advanceTimeBy(1_000)
        var first = 0f
        var runId = 0L
        compose.runOnIdle { first = position(); runId = controller.state.value.runId; speed.floatValue = 2f }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            assertTrue(first > 0f)
            val second = position() - first
            assertTrue(second > first * 1.7f && second < first * 2.4f)
            assertEquals(runId, controller.state.value.runId)
            controller.pause()
        }
        compose.mainClock.advanceTimeByFrame()
        var paused = 0f
        compose.runOnIdle { paused = position() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertEquals(paused, position(), 0f) }
    }

    @Test fun manualListGestureInterruptsRunnerWithoutBlockingTheGesture() {
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { reader() } }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { controller.start(target) }
        compose.mainClock.advanceTimeBy(500)
        var before = 0f
        compose.runOnIdle { before = position() }
        compose.onNodeWithTag("AutoScrollReader").performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            assertEquals(ReaderAutoScrollPhase.PAUSED, controller.state.value.phase)
            assertTrue(position() > before)
        }
    }

    @Test fun ineligibleReaderDoesNotMoveAndEndOfChapterFinishesInsteadOfLooping() {
        eligible.value = false
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { reader() } }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { controller.start(target) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertEquals(0f, position(), 0f); eligible.value = true }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertTrue(position() > 0f); eligible.value = false }
        compose.mainClock.advanceTimeByFrame()
        // Exact bottom jump uses the same scroll mutation arbitration as the fast scroller.
        compose.onNodeWithTag("AutoScrollReader").performScrollToIndex(199)
        compose.runOnIdle { eligible.value = true }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertEquals(ReaderAutoScrollPhase.FINISHED, controller.state.value.phase) }
    }

    @Test fun quickSheetControlsAdjustSpeedAndStartOnlyOnceBeforeDismissal() {
        var savedSpeed = 1f
        var starts = 0
        var dismissals = 0
        compose.setContent {
            MaterialTheme {
                ReaderQuickSettingsSheet(
                    settings = ReaderSettings(), textColor = ReaderTheme.DARK.textColor,
                    secondaryColor = ReaderTheme.DARK.secondaryTextColor,
                    reader = ReaderUiState(chapters = listOf(Chapter("one", "Chapter 1", listOf("Text")))),
                    onDismiss = { dismissals++ }, onSetTheme = {}, onSetFontSize = {},
                    onSetLineHeight = {}, onSetFontFamily = {},
                    onSetAutoScrollSpeed = { savedSpeed = it }, onStartAutoScroll = { starts++ }
                )
            }
        }
        compose.onNodeWithText("Auto Scroll").performClick()
        compose.onNodeWithContentDescription("Faster auto scroll").performClick()
        compose.runOnIdle { assertEquals(1.25f, savedSpeed, 0f) }
        compose.onNodeWithText("Start").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, starts); assertEquals(1, dismissals) }
    }

    @Test fun overlappingReaderRequestsDoNotClearEachOthersScreenOnFlag() {
        val first = mutableStateOf(false)
        val second = mutableStateOf(false)
        lateinit var rootView: android.view.View
        compose.setContent {
            val view = LocalView.current
            SideEffect { rootView = view }
            ReaderKeepScreenOn(first.value)
            ReaderKeepScreenOn(second.value)
        }
        var baseline = false
        compose.runOnIdle { baseline = rootView.keepScreenOn; first.value = true; second.value = true }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(rootView.keepScreenOn); first.value = false }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(rootView.keepScreenOn); second.value = false }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(baseline, rootView.keepScreenOn) }
    }
}
