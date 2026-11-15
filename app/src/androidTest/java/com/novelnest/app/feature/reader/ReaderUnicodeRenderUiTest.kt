package com.novelnest.app.feature.reader

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
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
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderUnicodeRenderUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun decodingVisibleTextPreservesParagraphAnchorAndOriginalInput() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val prefName = "unicode-render-" + java.util.UUID.randomUUID()
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(prefName, mode)
        }
        val manager = ReaderPreferencesManager(context)
        val source = NovelSourceType.NOVEL_PHOENIX
        val novel = Novel("book", "Book", "", source, source.baseUrl + "/novel/book")
        val original = Chapter("one", "Chapter 1", (0 until 60).map { "Paragraph " + it + ": It\\u2019s readable text with enough words to wrap across the screen." }, source.baseUrl + "/novel/book/chapter-1")
        var fetches = 0
        val repository = object : NovelRepository {
            override fun getHomeSections(source: NovelSourceType, forceRefresh: Boolean) = flowOf(Result.success(emptyList<com.novelnest.app.data.model.NovelSection>()))
            override suspend fun searchNovels(source: NovelSourceType, query: String) = Result.success(emptyList<Novel>())
            override suspend fun getNovelById(novelId: String) = novel
            override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String) = Result.success(novel)
            override suspend fun getChapters(source: NovelSourceType, novelSlugOrUrl: String, forceRefresh: Boolean) = Result.success(listOf(original))
            override suspend fun getChapterContent(source: NovelSourceType, chapterUrlOrId: String, forceRefresh: Boolean): Result<Chapter> { fetches++; return Result.success(original) }
        }
        val store = ViewModelStore()
        lateinit var model: ReaderViewModel
        try {
            compose.runOnIdle { model = ReaderViewModel(novel.id, repository, manager); store.put("reader", model) }
            compose.setContent { NovelNestTheme { ReaderScreen(model, {}) } }
            compose.waitUntil(5000) { !model.uiState.value.isLoading }
            compose.onNodeWithTag("NovelChapterText").performScrollToIndex(20)
            var anchor = 0
            compose.runOnIdle { anchor = model.scrollPosition(0).first; assertTrue(anchor >= 15); manager.updateUnicodeEscapeDecoding(true) }
            compose.waitUntil(5000) { model.preparedChapter(original)?.paragraphs?.first()?.contains("It\u2019s") == true }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(anchor, model.scrollPosition(0).first)
                assertEquals(original.paragraphs, model.uiState.value.chapters.single().paragraphs)
                assertEquals(0, fetches)
                manager.updateUnicodeEscapeDecoding(false)
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(anchor, model.scrollPosition(0).first)
                assertEquals(original.paragraphs, model.displayedChapterParagraphs(original))
            }
        } finally {
            compose.runOnIdle { store.clear() }
            app.deleteSharedPreferences(prefName)
        }
    }
}
