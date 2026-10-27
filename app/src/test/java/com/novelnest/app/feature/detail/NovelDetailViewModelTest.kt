package com.novelnest.app.feature.detail

import androidx.lifecycle.ViewModelStore
import com.novelnest.app.data.bookmark.BookmarkManager
import com.novelnest.app.data.database.entity.ReadingProgressEntity
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.progress.ReadingProgressManager
import com.novelnest.app.feature.reader.Chapter
import com.novelnest.app.testsupport.ReaderTestProgressDao
import com.novelnest.app.testsupport.ReaderTestRepository
import com.novelnest.app.testsupport.readerTestContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NovelDetailViewModelTest {
    private fun novel(source: NovelSourceType, slug: String = "book"): Novel {
        val prefix = when (source) {
            NovelSourceType.NOVEL_PHOENIX -> "np"
            NovelSourceType.FREE_WEB_NOVEL -> "fwn"
            NovelSourceType.ASURA_SCANS -> "asura"
        }
        val path = if (source.isManhwa) "comics" else "novel"
        return Novel("$prefix-$slug", "Title", "", source, "${source.baseUrl}/$path/$slug", synopsis = "Synopsis")
    }

    @Test fun canonicalProgressIsObservedFromLegacyRoutesAcrossSources() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val progress = ReadingProgressManager(ReaderTestProgressDao(), this)
            for (source in NovelSourceType.entries) {
                val canonical = novel(source)
                val alias = canonical.id + "-old"
                progress.markChapterRead(alias, "old", 2, "Old chapter")
                val detail = NovelDetailViewModel(alias, ReaderTestRepository(canonical), readingProgressManager = progress)
                store.put(source.name, detail)
                advanceUntilIdle()
                assertEquals(2, detail.uiState.value.readingProgress?.lastReadChapterIndex)
                progress.markChapterRead(canonical.id, "current", 82, "Current chapter")
                progress.markChapterRead(novel(source, "other").id, "unrelated", 99, "Other novel")
                advanceUntilIdle()
                assertEquals(canonical.id, detail.uiState.value.readingProgress?.novelId)
                assertEquals(82, detail.uiState.value.readingProgress?.lastReadChapterIndex)
                assertEquals(setOf("old", "current"), detail.uiState.value.readChapterIds)
            }
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun identityChangeRebindsExistingProgressWithoutWaitingForAnotherRead() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val canonical = novel(NovelSourceType.NOVEL_PHOENIX)
            val gate = CompletableDeferred<Novel?>()
            val repo = object : ReaderTestRepository(canonical) {
                override suspend fun getNovelById(novelId: String) = gate.await()
            }
            val progress = ReadingProgressManager(ReaderTestProgressDao(), this)
            progress.markChapterRead(canonical.id, "current", 55, "Already read")
            val bookmarks = BookmarkManager(readerTestContext())
            bookmarks.addBookmark(canonical)
            val detail = NovelDetailViewModel("np-legacy", repo, bookmarks, progress)
            store.put("detail", detail)
            runCurrent()
            assertNull(detail.uiState.value.readingProgress)
            assertFalse(detail.uiState.value.isBookmarked)
            gate.complete(canonical)
            advanceUntilIdle()
            assertEquals(55, detail.uiState.value.readingProgress?.lastReadChapterIndex)
            assertTrue(detail.uiState.value.isBookmarked)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun bookmarkChangesInReaderUpdateTheRetainedDetailAcrossSources() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val bookmarks = BookmarkManager(readerTestContext())
            for (source in NovelSourceType.entries) {
                val book = novel(source)
                val detail = NovelDetailViewModel(book.id + "-old", ReaderTestRepository(book), bookmarks)
                store.put(source.name, detail)
                advanceUntilIdle()
                assertFalse(detail.uiState.value.isBookmarked)
                bookmarks.addBookmark(book)
                advanceUntilIdle()
                assertTrue(detail.uiState.value.isBookmarked)
                bookmarks.removeBookmark(book.id)
                advanceUntilIdle()
                assertFalse(detail.uiState.value.isBookmarked)
                detail.toggleBookmark()
                advanceUntilIdle()
                assertTrue(detail.uiState.value.isBookmarked)
                assertTrue(bookmarks.isBookmarked(book.id))
            }
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun sortingAndFilteringPreserveTheChronologicalReaderIndexAcrossSources() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val chapters = listOf(Chapter("zero", "Prologue"), Chapter("one", "First"), Chapter("two", "Second"))
            for (source in NovelSourceType.entries) {
                val book = novel(source)
                val repo = object : ReaderTestRepository(book) {
                    override suspend fun getChapters(source: NovelSourceType, novelSlugOrUrl: String, forceRefresh: Boolean) = Result.success(chapters)
                }
                val detail = NovelDetailViewModel(book.id, repo)
                store.put(source.name, detail)
                advanceUntilIdle()
                detail.toggleSortOrder()
                detail.onChapterSearch("Second")
                assertEquals(listOf(chapters[2]), detail.uiState.value.filteredChapters)
                assertEquals(2, detail.readerChapterIndex("two"))
                detail.onChapterSearch("")
                assertEquals(chapters.reversed(), detail.uiState.value.filteredChapters)
                detail.toggleSortOrder()
                assertEquals(chapters, detail.uiState.value.filteredChapters)
                assertEquals(0, detail.readerChapterIndex("zero"))
            }
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun newerLegacyProgressIsNotHiddenByOlderCanonicalProgress() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val book = novel(NovelSourceType.ASURA_SCANS)
            val alias = book.id + "-old"
            val dao = ReaderTestProgressDao()
            dao.insertOrUpdate(ReadingProgressEntity(book.id, "one", 0, "One", 100, listOf("one")))
            dao.insertOrUpdate(ReadingProgressEntity(alias, "two", 1, "Two", 200, listOf("two")))
            val progress = ReadingProgressManager(dao, this)
            val detail = NovelDetailViewModel(alias, ReaderTestRepository(book), readingProgressManager = progress)
            store.put("detail", detail)
            advanceUntilIdle()
            assertEquals(book.id, detail.uiState.value.readingProgress?.novelId)
            assertEquals(1, detail.uiState.value.readingProgress?.lastReadChapterIndex)
            assertEquals(setOf("one", "two"), detail.uiState.value.readChapterIds)
            assertEquals(0, progress.getProgress(book.id)?.lastReadChapterIndex) // storage is not rewritten
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
