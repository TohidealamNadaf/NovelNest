package com.novelnest.app.feature.epub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelnest.app.core.designsystem.NovelNestTheme
import com.novelnest.app.data.local.epub.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubReorderEditorUiTest {
    @get:Rule val compose = createComposeRule()

    // Avoid draining the Compose rule's instrumentation-thread event loop during Room work.
    private fun <T> blocking(block: suspend () -> T): T {
        var result: Result<T>? = null
        kotlin.concurrent.thread { result = runCatching { runBlocking { block() } } }.join()
        return result!!.getOrThrow()
    }

    private fun withEditor(test: (EpubLibraryTestStore, EpubLibraryViewModel,
        List<LocalBookEntity>, StateRestorationTester) -> Unit) {
        EpubLibraryTestStore().use { store ->
            val series = blocking { store.repository.createCollection("Reorder regression") }
            val books = blocking { (1..3).map { store.dao.insertOrganized(store.book(it), series) } }
            blocking { store.repository.savePosition(EpubPositionEntity(books.first().id, "{}", 0.4, "Saved", 10)) }
            val models = ViewModelStore()
            lateinit var model: EpubLibraryViewModel
            val restoration = StateRestorationTester(compose)
            try {
                compose.runOnIdle { model = EpubLibraryViewModel(store.repository, series); models.put("library", model) }
                restoration.setContent { NovelNestTheme { EpubLibraryScreen(model, {}, {}, {}) } }
                compose.waitUntil(10_000) { model.state.value.items.size == 3 }
                compose.onNodeWithContentDescription("Series options").performClick()
                compose.onNodeWithText("Reorder volumes").performClick()
                test(store, model, books, restoration)
                blocking { assertEquals(0.4, store.repository.position(books.first().id)!!.progression, 0.0) }
            } finally { compose.runOnIdle { models.clear() } }
        }
    }

    private fun assertBefore(first: String, second: String) {
        val a = compose.onNodeWithContentDescription("Drag handle for " + first).getUnclippedBoundsInRoot()
        val b = compose.onNodeWithContentDescription("Drag handle for " + second).getUnclippedBoundsInRoot()
        assertTrue("Expected $first above $second: $a / $b", a.top < b.top)
    }

    @Test fun positionInputRejectsOutOfRangeAndSavesExactlyOnce() = withEditor { store, model, books, _ ->
        compose.onNodeWithContentDescription("Position 1. Tap to move to position").performClick()
        val input = compose.onNodeWithTag("EpubMoveToPositionInput")
        input.performTextReplacement("0")
        compose.onNode(hasText("Move") and hasClickAction()).assertIsNotEnabled()
        input.performTextReplacement("4")
        compose.onNode(hasText("Move") and hasClickAction()).assertIsNotEnabled()
        input.performTextReplacement("2")
        compose.onNode(hasText("Move") and hasClickAction()).performClick()
        assertBefore("Book 2", "Book 1")
        // Editing a draft must not write the repository before Save order.
        blocking { assertEquals(books.map { it.id }, store.dao.collectionBookIds(model.collectionId!!)) }
        compose.onNodeWithText("Save order").performClick()
        compose.waitUntil(10_000) { model.operation.value.revision == 1L && !model.operation.value.busy }
        blocking { assertEquals(listOf(books[1].id, books[0].id, books[2].id), store.dao.collectionBookIds(model.collectionId!!)) }
    }

    @Test fun quickPlacementUndoAndCancelKeepTheSavedOrder() = withEditor { store, model, books, _ ->
        compose.onNodeWithContentDescription("Position 1. Tap to move to position").performClick()
        compose.onNodeWithText("Move to last").performClick()
        assertBefore("Book 3", "Book 1")
        compose.onNodeWithText("Undo").performClick()
        assertBefore("Book 1", "Book 2")
        compose.onAllNodesWithContentDescription("Move volume down").onFirst().performClick()
        compose.onNodeWithContentDescription("Cancel").performClick()
        blocking { assertEquals(books.map { it.id }, store.dao.collectionBookIds(model.collectionId!!)) }
        compose.runOnIdle { assertEquals(0L, model.operation.value.revision) }
    }

    @Test fun movedDraftSurvivesStateRestorationBeforeSaving() = withEditor { store, model, books, restoration ->
        compose.onAllNodesWithContentDescription("Move volume down").onFirst().performClick()
        restoration.emulateSavedInstanceStateRestore()
        assertBefore("Book 2", "Book 1")
        compose.onNodeWithText("Save order").performClick()
        compose.waitUntil(10_000) { model.operation.value.revision == 1L && !model.operation.value.busy }
        blocking { assertEquals(listOf(books[1].id, books[0].id, books[2].id), store.dao.collectionBookIds(model.collectionId!!)) }
    }

    @Test fun dragMoveHasUndoAndDoesNotWriteUntilSaved() = withEditor { store, model, books, _ ->
        val distance = compose.onNodeWithContentDescription("Drag handle for Book 2").fetchSemanticsNode().boundsInRoot.top -
            compose.onNodeWithContentDescription("Drag handle for Book 1").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithContentDescription("Drag handle for Book 1").performTouchInput {
            swipe(center, center + Offset(0f, distance), durationMillis = 500)
        }
        assertBefore("Book 2", "Book 1")
        compose.onNodeWithText("Undo").performClick()
        assertBefore("Book 1", "Book 2")
        blocking { assertEquals(books.map { it.id }, store.dao.collectionBookIds(model.collectionId!!)) }
    }

    @Test fun cancelledDragRestoresTheOrderBeforeTheGesture() = withEditor { store, model, books, _ ->
        val distance = compose.onNodeWithContentDescription("Drag handle for Book 2").fetchSemanticsNode().boundsInRoot.top -
            compose.onNodeWithContentDescription("Drag handle for Book 1").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithContentDescription("Drag handle for Book 1").performTouchInput {
            down(center)
            moveBy(Offset(0f, distance / 2f), delayMillis = 100)
            moveBy(Offset(0f, distance / 2f), delayMillis = 100)
        }
        // Let the live insertion settle before Android cancels this still-active gesture.
        compose.waitForIdle()
        assertBefore("Book 2", "Book 1")
        compose.onNode(isDialog()).performTouchInput { cancel() }
        assertBefore("Book 1", "Book 2")
        blocking { assertEquals(books.map { it.id }, store.dao.collectionBookIds(model.collectionId!!)) }
    }

    @Test fun changedMembershipRequiresReloadBeforeSave() = withEditor { store, model, books, _ ->
        blocking { store.repository.moveBook(books.last().id, null) }
        compose.waitUntil(10_000) { model.state.value.items.size == 2 }
        compose.onNodeWithText("Volumes changed while this editor was open.").assertIsDisplayed()
        compose.onNodeWithText("Save order").assertIsNotEnabled()
        compose.onNodeWithText("Reload volumes").performClick()
        compose.onNodeWithText("Save order").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { model.operation.value.revision == 1L && !model.operation.value.busy }
        blocking { assertEquals(books.take(2).map { it.id }, store.dao.collectionBookIds(model.collectionId!!)) }
    }
}
