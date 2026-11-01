package com.novelnest.app.feature.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withText
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelnest.app.data.ai.*
import com.novelnest.app.data.preferences.ReaderSettings
import com.novelnest.app.data.preferences.ReaderTheme
import com.novelnest.app.data.study.*
import com.novelnest.app.feature.reader.components.ReaderQuickSettingsSheet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** No repository, provider or narration action is invoked by these interaction tests. */
@RunWith(AndroidJUnit4::class)
class ReaderQuickSettingsSheetTest {
    @get:Rule val compose = createComposeRule()
    private val chapter = Chapter("one", "Chapter 1", listOf("Qi gathers."))
    private val study = mutableStateOf(ReaderStudyState(key = StudyChapterKey("NOVEL_PHOENIX", "book", chapter.id)))
    private val ai = mutableStateOf(ReaderAiState())
    private var dismissCount = 0
    private var retryCollection: String? = null
    private var navigationInsetPx = 0f

    @Composable private fun content() {
        val inset = WindowInsets.navigationBars.getBottom(LocalDensity.current).toFloat()
        SideEffect { navigationInsetPx = inset }
        MaterialTheme {
            val theme = ReaderTheme.DARK
            ReaderQuickSettingsSheet(
                settings = ReaderSettings(), textColor = theme.textColor, secondaryColor = theme.secondaryTextColor,
                reader = ReaderUiState(chapters = listOf(chapter)), studyState = study.value, aiState = ai.value,
                onDismiss = { dismissCount++ }, onSetTheme = {}, onSetFontSize = {}, onSetLineHeight = {}, onSetFontFamily = {},
                onSelectCollection = { study.value = study.value.copy(selectedCollectionId = it) },
                onExtractTerms = { retryCollection = it }
            )
        }
    }

    private fun openNotes() {
        compose.onNodeWithText("Notes").performClick()
        compose.onNodeWithText("Novel Notes").assertIsDisplayed()
    }

    private fun editNewNote() {
        openNotes()
        compose.onNodeWithContentDescription("Create new note").performClick()
        compose.onNodeWithTag("ReaderNoteTitle").performTextInput("Keep this draft")
        onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog()).perform(replaceText("**Important note**"))
        closeSoftKeyboard()
    }

    @Test fun scrimKeepsDirtyNoteUntilDiscardIsConfirmed() {
        compose.setContent { content() }
        editNewNote()
        compose.onNodeWithContentDescription("Close sheet").performClick()
        compose.onNodeWithText("Discard note changes?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, dismissCount) }
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("ReaderNoteTitle").assertTextEquals("Keep this draft")
        onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog()).check(matches(withText("**Important note**")))
        compose.onNodeWithContentDescription("Close sheet").performClick()
        compose.onNodeWithText("Discard changes").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, dismissCount) }
    }

    @Test fun systemBackUsesNoteGuardAndThenReturnsThroughSubpages() {
        compose.setContent { content() }
        editNewNote()
        pressBack()
        compose.onNodeWithText("Discard note changes?").assertIsDisplayed()
        compose.onNodeWithText("Discard changes").performClick()
        compose.onNodeWithText("Novel Notes").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, dismissCount) }
        pressBack()
        compose.onNodeWithText("Notes").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, dismissCount) }
        pressBack()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, dismissCount) }
    }

    @Test fun cleanSubpageStillDismissesFromScrim() {
        compose.setContent { content() }
        openNotes()
        compose.onNodeWithContentDescription("Close sheet").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, dismissCount) }
    }

    @Test fun footerPaddingFlingCannotDismissSubpage() {
        compose.setContent { content() }
        openNotes()
        val padding = with(compose.density) { 15.dp.toPx() }
        compose.onNodeWithTag("ReaderQuickSettingsContent").performTouchInput {
            // Fast enough to exceed the sheet's fling threshold, entirely inside its bottom padding.
            val footerBottom = height - navigationInsetPx
            swipe(Offset(centerX, footerBottom - padding), Offset(centerX, footerBottom - 1f), durationMillis = 32)
        }
        compose.onNodeWithText("Novel Notes").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, dismissCount) }
    }

    @Test fun dirtyTitleSurvivesSavedStateRestorationAndStillRequiresConfirmation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { content() }
        editNewNote()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("ReaderNoteTitle").assertTextEquals("Keep this draft")
        onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog()).check(matches(withText("**Important note**")))
        compose.onNodeWithContentDescription("Back to notes").performClick()
        compose.onNodeWithText("Discard note changes?").assertIsDisplayed()
    }

    @Test fun collectionDetailHidesOtherCollectionsWorkAndRetriesItsOwnFailure() {
        fun collection(id: String, name: String) = TermCollectionRow(
            TermCollectionEntity(id, "NOVEL_PHOENIX", "book", name, name.lowercase(), name, 1L, ""), 0
        )
        study.value = study.value.copy(collections = listOf(collection("a", "Cultivation levels"), collection("b", "World building")))
        ai.value = ReaderAiState(
            chapterId = chapter.id, sourceTitle = chapter.title, sourceParagraphs = chapter.paragraphs,
            action = AiAction.EXTRACT, extractionCollectionId = "a", progress = AiProgress(AiPhase.GENERATING)
        )
        compose.setContent { content() }
        compose.onNodeWithText("Extract").performClick()
        compose.onNodeWithText("World building").performClick()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.runOnIdle {
            ai.value = ai.value.copy(extractionCollectionId = "b", progress = AiProgress(AiPhase.FAILED),
                failure = AiFailure(AiErrorCode.UNAVAILABLE, "Provider temporarily unavailable"))
        }
        compose.onNodeWithText("Provider temporarily unavailable").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals("b", retryCollection) }
    }
}
