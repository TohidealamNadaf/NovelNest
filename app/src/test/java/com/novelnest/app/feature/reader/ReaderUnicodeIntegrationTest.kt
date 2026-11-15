package com.novelnest.app.feature.reader

import androidx.lifecycle.ViewModelStore
import com.novelnest.app.data.ai.*
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.preferences.ReaderPreferencesManager
import com.novelnest.app.testsupport.ReaderTestRepository
import com.novelnest.app.testsupport.readerTestContext
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderUnicodeIntegrationTest {
    private class Repository(source: NovelSourceType) : ReaderTestRepository(Novel("book", "Book", "", source,
        source.baseUrl + "/novel/book")) {
        val original = Chapter("one", "Chapter \\u0031", listOf("It\\u2019s &#65;."), source.baseUrl + "/novel/book/chapter-1")
        override suspend fun getChapters(source: NovelSourceType, novelSlugOrUrl: String, forceRefresh: Boolean) = Result.success(listOf(original))
        override suspend fun getChapterContent(source: NovelSourceType, chapterUrlOrId: String, forceRefresh: Boolean): Result<Chapter> {
            contentCalls++
            return Result.success(original.copy())
        }
    }
    @Test fun bothSourcesUseDecodedDisplayAndAiInputWithoutMutatingOrRefetchingCachedText() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            for (source in listOf(NovelSourceType.NOVEL_PHOENIX, NovelSourceType.FREE_WEB_NOVEL)) {
                val prefs = ReaderPreferencesManager(readerTestContext())
                prefs.updateUnicodeEscapeDecoding(true)
                val repo = Repository(source)
                val aiRepo = ChapterAiRepository(FakeConfiguration(configured(freeRoute)), FakeCache(), emptyList())
                val vm = ReaderViewModel("book", repo, prefs, aiRepository = aiRepo, textDispatcher = dispatcher)
                store.put(source.name, vm)
                advanceUntilIdle()
                val raw = vm.uiState.value.chapters.single()
                assertEquals(repo.original, raw)
                assertEquals("Chapter 1", vm.preparedChapter(raw)!!.title)
                assertEquals(listOf("It\u2019s A."), vm.displayedChapterParagraphs(raw))
                assertEquals(vm.preparedChapter(raw)!!.paragraphs, vm.aiState.value.sourceParagraphs)
                assertEquals(0, repo.contentCalls)
                prefs.updateUnicodeEscapeDecoding(false)
                advanceUntilIdle()
                assertSame(raw, vm.preparedChapter(raw))
                assertEquals(raw.paragraphs, vm.displayedChapterParagraphs(raw))
                assertEquals(raw.paragraphs, vm.aiState.value.sourceParagraphs)
                assertEquals(0, repo.contentCalls)
                assertTrue(vm.uiState.value.showQuickSettingsSheet.not())
                assertEquals(0, vm.uiState.value.currentChapterIndex)
            }
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun identicalRefreshCannotLeavePreparedChapterUnavailable() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val prefs = ReaderPreferencesManager(readerTestContext()).apply { updateUnicodeEscapeDecoding(true) }
            val repo = Repository(NovelSourceType.NOVEL_PHOENIX)
            val vm = ReaderViewModel("book", repo, prefs, textDispatcher = dispatcher)
            store.put("reader", vm)
            advanceUntilIdle()
            vm.resyncChapter()
            advanceUntilIdle()
            val current = vm.uiState.value.chapters.single()
            assertNotNull(vm.preparedChapter(current))
            assertEquals(listOf("It\u2019s A."), vm.displayedChapterParagraphs(current))
            assertEquals(1, repo.contentCalls)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun autoscrollMatchesRewritesAgainstPreparedSourceAndRejectsUnreadyText() {
        val raw = Chapter("one", "Chapter", listOf("It\\u2019s A."))
        val prepared = com.novelnest.app.data.text.ChapterEscapeDecoder.prepare(NovelSourceType.NOVEL_PHOENIX, raw, true)
        val reader = ReaderUiState(novel = Novel("book", "Book", "", NovelSourceType.NOVEL_PHOENIX, ""), chapters = listOf(raw))
        val ai = ReaderAiState(chapterId = raw.id, sourceTitle = prepared.title, sourceParagraphs = prepared.paragraphs,
            version = ReaderContentVersion.REWRITE, rewrite = AiResult(AiAction.REWRITE, "fingerprint", freeRoute,
                rewrittenParagraphs = listOf("Rewritten."), createdAtMs = 1))
        assertEquals(ai.contentKey, readerAutoScrollTarget(reader, ai, prepared)?.contentKey)
        assertNull(readerAutoScrollTarget(reader, ai, null))
        assertNull(readerAutoScrollTarget(reader, ai, prepared.copy(id = "another")))
    }
}
