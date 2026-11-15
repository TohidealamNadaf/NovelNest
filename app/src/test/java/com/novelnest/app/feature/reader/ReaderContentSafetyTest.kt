package com.novelnest.app.feature.reader

import androidx.lifecycle.ViewModelStore
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
class ReaderContentSafetyTest {
    @Test fun emptySuccessfulResponseShowsRetryAndAllowsLeavingUsingControls() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            for (source in listOf(NovelSourceType.NOVEL_PHOENIX, NovelSourceType.FREE_WEB_NOVEL)) {
                val chapters = listOf(Chapter("one", "One"), Chapter("two", "Two"))
                val repo = object : ReaderTestRepository(Novel("book", "Book", "", source, source.baseUrl + "/novel/book")) {
                    override suspend fun getChapters(source: NovelSourceType, novelSlugOrUrl: String, forceRefresh: Boolean) = Result.success(chapters)
                    override suspend fun getChapterContent(source: NovelSourceType, chapterUrlOrId: String, forceRefresh: Boolean) = Result.success(chapters.first().copy(paragraphs = listOf("  ", "")))
                }
                val prefs = ReaderPreferencesManager(readerTestContext()).apply { updateUnicodeEscapeDecoding(true) }
                val vm = ReaderViewModel("book", repo, prefs, textDispatcher = dispatcher)
                store.put(source.name, vm)
                advanceUntilIdle()
                assertFalse(vm.uiState.value.isChapterLoading)
                assertNotNull(vm.uiState.value.errorMessage)
                assertFalse(vm.uiState.value.canNavigateFrom(0))
                assertTrue(vm.uiState.value.canNavigateUsingControls(0))
            }
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun olderRefreshCannotOverwriteANewerRefreshOrItsPreparedText() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val old = CompletableDeferred<Result<Chapter>>()
            val fresh = CompletableDeferred<Result<Chapter>>()
            var abandonedLoadFinished = false
            val first = Chapter("one", "One", listOf("Original"))
            var calls = 0
            val source = NovelSourceType.NOVEL_PHOENIX
            val repo = object : ReaderTestRepository(Novel("book", "Book", "", source, source.baseUrl + "/novel/book")) {
                override suspend fun getChapters(source: NovelSourceType, novelSlugOrUrl: String, forceRefresh: Boolean) = Result.success(listOf(first))
                override suspend fun getChapterContent(source: NovelSourceType, chapterUrlOrId: String, forceRefresh: Boolean) = if (++calls == 1) {
                    try { old.await() } finally { abandonedLoadFinished = true }
                } else fresh.await()
            }
            val prefs = ReaderPreferencesManager(readerTestContext()).apply { updateUnicodeEscapeDecoding(true) }
            val vm = ReaderViewModel("book", repo, prefs, textDispatcher = dispatcher)
            store.put("reader", vm)
            advanceUntilIdle()
            vm.resyncChapter(); runCurrent()
            vm.resyncChapter(); runCurrent()
            assertTrue("A superseded foreground fetch must be cancelled", abandonedLoadFinished)
            fresh.complete(Result.success(first.copy(paragraphs = listOf("New \\u003cAbility: None\\u003e"))))
            runCurrent()
            old.complete(Result.success(first.copy(paragraphs = listOf("Outdated"))))
            advanceUntilIdle()
            assertEquals(listOf("New <Ability: None>"), vm.displayedChapterParagraphs(vm.uiState.value.chapters.single()))
            assertFalse(vm.uiState.value.isChapterLoading)
            assertNull(vm.uiState.value.errorMessage)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun latePrefetchDoesNotReplaceNewerForegroundContent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val prefetch = CompletableDeferred<Result<Chapter>>()
            val foreground = CompletableDeferred<Result<Chapter>>()
            val chapters = listOf(Chapter("one", "One", listOf("First")), Chapter("two", "Two"))
            var calls = 0
            val source = NovelSourceType.FREE_WEB_NOVEL
            val repo = object : ReaderTestRepository(Novel("book", "Book", "", source, source.baseUrl + "/novel/book")) {
                override suspend fun getChapters(source: NovelSourceType, novelSlugOrUrl: String, forceRefresh: Boolean) = Result.success(chapters)
                override suspend fun getChapterContent(source: NovelSourceType, chapterUrlOrId: String, forceRefresh: Boolean) = if (++calls == 1) prefetch.await() else foreground.await()
            }
            val vm = ReaderViewModel("book", repo, ReaderPreferencesManager(readerTestContext()), textDispatcher = dispatcher)
            store.put("reader", vm)
            runCurrent()
            assertEquals(1, calls)
            vm.nextChapter(0)
            assertTrue(vm.uiState.value.isChapterLoading)
            runCurrent()
            foreground.complete(Result.success(chapters[1].copy(paragraphs = listOf("Fresh"))))
            runCurrent()
            prefetch.complete(Result.success(chapters[1].copy(paragraphs = listOf("Stale prefetch"))))
            advanceUntilIdle()
            assertEquals(listOf("Fresh"), vm.uiState.value.chapters[1].paragraphs)
            assertFalse(vm.uiState.value.isChapterLoading)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
