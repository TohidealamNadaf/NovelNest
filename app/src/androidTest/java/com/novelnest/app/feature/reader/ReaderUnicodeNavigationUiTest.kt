package com.novelnest.app.feature.reader

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelnest.app.core.designsystem.NovelNestTheme
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.preferences.ReaderPreferencesManager
import com.novelnest.app.data.repository.NovelRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real reader scroll/pull gestures with offline chapter data and a manually released decoder. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderUnicodeNavigationUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun phoenixLatePreparationKeepsThePositionAlreadyRead() = latePreparation(NovelSourceType.NOVEL_PHOENIX)
    @Test fun freeWebNovelLatePreparationKeepsThePositionAlreadyRead() = latePreparation(NovelSourceType.FREE_WEB_NOVEL)
    @Test fun phoenixDecodeChangesDoNotCancelNextOrPreviousPull() = pullNavigation(NovelSourceType.NOVEL_PHOENIX)
    @Test fun freeWebNovelDecodeChangesDoNotCancelNextOrPreviousPull() = pullNavigation(NovelSourceType.FREE_WEB_NOVEL)

    private fun latePreparation(source: NovelSourceType) = withReader(source, enabled = true) { model, _, scheduler, chapters ->
        compose.onNodeWithTag("NovelChapterText").performScrollToIndex(20)
        var anchor = 0
        compose.runOnIdle {
            anchor = model.scrollPosition(1).first
            assertTrue("Raw text should be immediately scrollable and tracked", anchor >= 18)
        }
        awaitPrepared(model, scheduler, chapters[1])
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(anchor, model.scrollPosition(1).first) }
    }

    private fun pullNavigation(source: NovelSourceType) = withReader(source, enabled = false) { model, prefs, scheduler, chapters ->
        compose.onNodeWithTag("NovelChapterText").performScrollToIndex(chapters[1].paragraphs.size + 1)
        compose.onNodeWithTag("NovelChapterText").performTouchInput {
            down(Offset(centerX, height * 0.78f))
            moveTo(Offset(centerX, height * 0.12f), delayMillis = 600)
        }
        compose.runOnIdle { prefs.updateUnicodeEscapeDecoding(true) }
        awaitPrepared(model, scheduler, chapters[1])
        compose.runOnIdle { assertEquals(1, model.uiState.value.currentChapterIndex) }
        compose.onNodeWithTag("NovelChapterText").performTouchInput { up() }
        compose.waitUntil(5000) { model.uiState.value.currentChapterIndex == 2 }
        awaitPrepared(model, scheduler, chapters[2])
        compose.waitForIdle()
        compose.onNodeWithTag("NovelChapterText").performScrollToIndex(0)
        compose.onNodeWithTag("NovelChapterText").performTouchInput {
            down(Offset(centerX, height * 0.22f))
            moveTo(Offset(centerX, height * 0.85f), delayMillis = 600)
        }
        compose.runOnIdle { prefs.updateUnicodeEscapeDecoding(false) }
        compose.waitForIdle()
        compose.onNodeWithTag("NovelChapterText").performTouchInput { up() }
        compose.waitUntil(5000) { model.uiState.value.currentChapterIndex == 1 }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(chapters[1].paragraphs, model.displayedChapterParagraphs(chapters[1])) }
    }

    private fun awaitPrepared(model: ReaderViewModel, scheduler: TestCoroutineScheduler, chapter: Chapter) {
        compose.waitUntil(5000) {
            scheduler.runCurrent()
            model.preparedChapter(chapter)?.paragraphs?.first()?.contains("<Ability: None>") == true
        }
    }

    private fun withReader(source: NovelSourceType, enabled: Boolean,
        action: (ReaderViewModel, ReaderPreferencesManager, TestCoroutineScheduler, List<Chapter>) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val prefName = "unicode-navigation-" + java.util.UUID.randomUUID()
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(prefName, mode)
        }
        val prefs = ReaderPreferencesManager(context).apply { updateUnicodeEscapeDecoding(enabled) }
        val scheduler = TestCoroutineScheduler()
        val novel = Novel("book", "Book", "", source, source.baseUrl + "/novel/book")
        val chapters = (0..2).map { chapter -> Chapter("chapter-" + chapter, "Chapter " + chapter,
            (0 until 60).map { "Paragraph " + it + ": \\u003cAbility: None\\u003e with enough text to wrap across the screen." },
            source.baseUrl + "/novel/book/chapter-" + chapter) }
        var fetches = 0
        val repository = object : NovelRepository {
            override fun getHomeSections(source: NovelSourceType, forceRefresh: Boolean) = flowOf(Result.success(emptyList<com.novelnest.app.data.model.NovelSection>()))
            override suspend fun searchNovels(source: NovelSourceType, query: String) = Result.success(emptyList<Novel>())
            override suspend fun getNovelById(novelId: String) = novel
            override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String) = Result.success(novel)
            override suspend fun getChapters(source: NovelSourceType, novelSlugOrUrl: String, forceRefresh: Boolean) = Result.success(chapters)
            override suspend fun getChapterContent(source: NovelSourceType, chapterUrlOrId: String, forceRefresh: Boolean): Result<Chapter> {
                fetches++
                return Result.failure(IllegalStateException("All chapter text is already available"))
            }
        }
        val store = ViewModelStore()
        lateinit var model: ReaderViewModel
        try {
            compose.runOnIdle {
                model = ReaderViewModel(novel.id, repository, prefs, initialChapterIndex = 1,
                    // Hold preparation across real gestures without racing emulator cold-start time.
                    // The production two-second fallback is verified with virtual time in unit tests.
                    textDispatcher = StandardTestDispatcher(scheduler), textPreparationTimeoutMs = 30_000L)
                store.put("reader", model)
            }
            compose.setContent { NovelNestTheme { ReaderScreen(model, {}) } }
            compose.waitUntil(5000) { !model.uiState.value.isLoading }
            action(model, prefs, scheduler, chapters)
            compose.runOnIdle { assertEquals(0, fetches) }
        } finally {
            compose.runOnIdle { store.clear() }
            scheduler.runCurrent()
            app.deleteSharedPreferences(prefName)
        }
    }
}
