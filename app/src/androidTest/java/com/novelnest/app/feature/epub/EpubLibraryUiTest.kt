package com.novelnest.app.feature.epub

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelnest.app.core.designsystem.NovelNestTheme
import com.novelnest.app.data.local.epub.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EpubLibraryUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun epubLetterButtonOpensLibraryAction() {
        var opens = 0
        compose.setContent { NovelNestTheme { EpubLibraryButton { opens++ } } }
        compose.onNodeWithContentDescription("Open EPUB library").assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, opens) }
    }
    @Test fun createSeriesAndMoveAnExistingBookWithoutOpeningAReader() {
        EpubLibraryTestStore().use { store ->
            val book = store.book(1)
            runBlocking { store.dao.insert(book) }
            val models = ViewModelStore()
            lateinit var model: EpubLibraryViewModel
            var seriesOpened: String? = null
            try {
                compose.runOnIdle { model = EpubLibraryViewModel(store.repository); models.put("library", model) }
                compose.setContent { NovelNestTheme { EpubLibraryScreen(model, {}, {}, { seriesOpened = it }) } }
                compose.waitUntil(5000) { model.state.value.loaded }
                compose.onNodeWithContentDescription("Create series").performClick()
                compose.onNodeWithTag("EpubLibraryNameInput").performTextInput("My series")
                compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
                compose.waitUntil(5000) { model.state.value.collections.size == 1 && !model.operation.value.busy }
                val series = model.state.value.collections.single()
                compose.onNodeWithTag("EpubShelf:" + book.id).performTouchInput { longClick() }
                compose.onNodeWithContentDescription("Selected item actions").performClick()
                compose.onNodeWithText("Edit selected item").performClick()
                compose.onNodeWithText("Move to series").performClick()
                compose.onNode(hasText("My series") and hasAnyAncestor(isDialog()), useUnmergedTree = true).performClick()
                compose.onNodeWithText("Move", useUnmergedTree = true).performClick()
                compose.waitUntil(5000) { model.state.value.items.size == 1 && model.state.value.items.single() is EpubShelfItem.Series }
                compose.onNodeWithTag("EpubShelf:" + series.id).performClick()
                compose.runOnIdle { assertEquals(series.id, seriesOpened) }
                runBlocking { assertEquals(series.id, store.dao.book(book.id)!!.collectionId) }
            } finally { compose.runOnIdle { models.clear() } }
        }
    }
    @Test fun recentlyReadSeriesIsGroupedAndContinueTargetsTheActualVolume() {
        EpubLibraryTestStore().use { store ->
            val series = runBlocking { store.repository.createCollection("Moon chronicles") }
            val first = runBlocking { store.dao.insertOrganized(store.book(1), series) }
            val current = runBlocking { store.dao.insertOrganized(store.book(2), series) }
            runBlocking {
                store.dao.insert(store.book(3))
                store.repository.savePosition(EpubPositionEntity(first.id, "{}", 0.8, "Chapter 4", 10))
                store.repository.savePosition(EpubPositionEntity(current.id, "{}", 0.3, "Chapter 2", 20))
            }
            val models = ViewModelStore()
            lateinit var model: EpubLibraryViewModel
            var opened: String? = null
            try {
                compose.runOnIdle { model = EpubLibraryViewModel(store.repository); models.put("library", model) }
                compose.setContent { NovelNestTheme { EpubLibraryScreen(model, {}, { opened = it }, {}) } }
                compose.waitUntil(5000) { model.state.value.continueBook?.book?.id == current.id }
                compose.onNodeWithText("Continue", useUnmergedTree = true).performClick()
                compose.runOnIdle {
                    assertEquals(current.id, opened)
                    assertEquals(listOf(series), model.state.value.recentlyRead.map { it.key })
                    assertEquals(2, model.state.value.items.size)
                }
                val screenshot = File(ApplicationProvider.getApplicationContext<Context>().externalCacheDir, "epub-library-overview.png")
                screenshot.outputStream().buffered().use { output ->
                    compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                }
            } finally { compose.runOnIdle { models.clear() } }
        }
    }
    @Test fun folderReorderingIsPersistedAndDoesNotReplaceProgress() {
        EpubLibraryTestStore().use { store ->
            val series = runBlocking { store.repository.createCollection("Series") }
            val first = runBlocking { store.dao.insertOrganized(store.book(1), series) }
            val second = runBlocking { store.dao.insertOrganized(store.book(2), series) }
            runBlocking { store.repository.savePosition(EpubPositionEntity(first.id, "{}", 0.5, "Saved", 10)) }
            val models = ViewModelStore()
            lateinit var model: EpubLibraryViewModel
            try {
                compose.runOnIdle { model = EpubLibraryViewModel(store.repository, series); models.put("library", model) }
                compose.setContent { NovelNestTheme { EpubLibraryScreen(model, {}, {}, {}) } }
                compose.waitUntil(5000) { model.state.value.items.size == 2 }
                compose.onNodeWithContentDescription("Series options").performClick()
                compose.onNodeWithText("Reorder volumes").performClick()
                compose.onAllNodesWithContentDescription("Move volume down").onFirst().performClick()
                compose.onNodeWithText("Save order").performClick()
                compose.waitUntil(5000) { model.state.value.items.map { it.key } == listOf(second.id, first.id) }
                runBlocking { assertEquals(0.5, store.repository.position(first.id)!!.progression, 0.0) }
            } finally { compose.runOnIdle { models.clear() } }
        }
    }
    @Test fun newDraftAfterAnEarlierSaveSurvivesStateRestoration() {
        EpubLibraryTestStore().use { store ->
            val models = ViewModelStore()
            lateinit var model: EpubLibraryViewModel
            val restoration = StateRestorationTester(compose)
            try {
                compose.runOnIdle { model = EpubLibraryViewModel(store.repository); models.put("library", model) }
                restoration.setContent { NovelNestTheme { EpubLibraryScreen(model, {}, {}, {}) } }
                compose.waitUntil(5000) { model.state.value.loaded }
                compose.runOnIdle { model.createSeries("Already saved") }
                compose.waitUntil(5000) { model.operation.value.revision == 1L }
                compose.waitForIdle()
                compose.onNodeWithContentDescription("Create series").performClick()
                compose.onNodeWithTag("EpubLibraryNameInput").performTextInput("Keep this draft")
                restoration.emulateSavedInstanceStateRestore()
                compose.onNodeWithTag("EpubLibraryNameInput").assertTextContains("Keep this draft")
            } finally { compose.runOnIdle { models.clear() } }
        }
    }
}
