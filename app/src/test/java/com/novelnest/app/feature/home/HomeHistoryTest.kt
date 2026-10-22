package com.novelnest.app.feature.home

import androidx.lifecycle.ViewModelStore
import com.novelnest.app.data.bookmark.BookmarkManager
import com.novelnest.app.data.database.dao.ReadingProgressDao
import com.novelnest.app.data.database.entity.ReadingProgressEntity
import com.novelnest.app.data.model.*
import com.novelnest.app.data.progress.*
import com.novelnest.app.data.remote.bookmarkContext
import com.novelnest.app.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeHistoryTest {
    private val np = NovelSourceType.NOVEL_PHOENIX
    private val fwn = NovelSourceType.FREE_WEB_NOVEL
    private fun novel(index: Int, source: NovelSourceType = np): Novel {
        val id = (if (source == fwn) "fwn-" else "np-") + "novel-$index"
        return Novel(id, "Novel $index", "https://example.com/$index.jpg", source,
            "${source.baseUrl}/novel/novel-$index", chapterCount = 100)
    }
    private fun progress(novel: Novel, time: Long) = NovelProgress(novel.id, "chapter-83", 82, "Chapter 83", time, setOf("chapter-83"))

    @Test fun currentAndRecentAreOrderedByActualReadTimeAndLimitedToTen() {
        val entries = (0..14).map { index -> novel(index).let { HomeReadingEntry(it, progress(it, index.toLong())) } }
        val state = HomeUiState(readingHistory = entries.reversed())
        assertEquals("np-novel-14", state.currentReading!!.novel.id)
        assertEquals((14 downTo 5).map { "np-novel-$it" }, state.recentlyRead.map { it.novel.id })
        assertEquals(82, state.currentReading!!.progress.lastReadChapterIndex)
    }

    @Test fun sourceSelectionKeepsCurrentHistoryAndBookmarksInSameSource() {
        val phoenix = novel(1)
        val free = novel(2, fwn)
        val entries = listOf(HomeReadingEntry(phoenix, progress(phoenix, 100)),
            HomeReadingEntry(free, progress(free, 200)))
        val state = HomeUiState(selectedSource = fwn, bookmarkedNovels = listOf(phoenix, free), readingHistory = entries)
        assertEquals(free, state.currentReading!!.novel)
        assertEquals(listOf(free), state.visibleBookmarks)
        assertEquals(listOf(free), state.recentlyRead.map { it.novel })
    }

    @Test fun bookmarksUseReadRecencyThenKeepUnreadBookmarkOrder() {
        val books = (0..4).map { novel(it) }
        val history = listOf(progress(books[1], 50), progress(books[3], 100)).associateBy { it.novelId }
        val state = HomeUiState(bookmarkedNovels = books, progress = history)
        assertEquals(listOf(books[3], books[1], books[0], books[2], books[4]), state.visibleBookmarks)
    }

    @Test fun recentHistoryDeduplicatesAliasesAndDoesNotRequireBookmark() {
        val book = novel(1)
        val entries = listOf(HomeReadingEntry(book.copy(id = "np-old"), progress(book.copy(id = "np-old"), 10)),
            HomeReadingEntry(book, progress(book, 20)))
        val state = HomeUiState(readingHistory = entries)
        assertEquals(1, state.recentlyRead.size)
        assertEquals(book, state.currentReading!!.novel)
        assertTrue(state.visibleBookmarks.isEmpty())
    }

    @Test fun restoredLocalHistoryResolvesOnlyTopTenPerSourceAndReusesMetadata() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val books = (0..14).map { novel(it) } + listOf(novel(20, fwn), novel(21, fwn))
            val dao = MemoryProgressDao()
            books.forEachIndexed { index, book ->
                dao.records[book.id] = ReadingProgressEntity(book.id, "chapter-83", 82, "Chapter 83",
                    index.toLong(), listOf("chapter-83"))
            }
            val manager = ReadingProgressManager(dao, this)
            val bookmarks = BookmarkManager(bookmarkContext())
            bookmarks.addBookmark(books[14])
            val base = NovelRepositoryImpl(sources = emptyMap())
            books.forEach(base::rememberNovel)
            var lookups = 0
            val repository = object : NovelRepository by base {
                override suspend fun getNovelById(novelId: String): Novel? {
                    lookups++
                    return base.getNovelById(novelId)
                }
                override fun getHomeSections(source: NovelSourceType, forceRefresh: Boolean) =
                    error("Home must not fetch discovery feeds")
            }
            val vm = HomeViewModel(bookmarks, manager, repository)
            store.put("home", vm)
            advanceUntilIdle()
            assertEquals(10, vm.uiState.value.recentlyRead.size)
            assertEquals(books[14], vm.uiState.value.currentReading!!.novel)
            assertEquals(11, lookups) // One of the twelve records is already a bookmark.
            val before = lookups
            manager.markChapterRead(books[7].id, "chapter-84", 83, "Chapter 84")
            advanceUntilIdle()
            assertEquals(books[7], vm.uiState.value.currentReading!!.novel)
            assertEquals(83, vm.uiState.value.currentReading!!.progress.lastReadChapterIndex)
            assertEquals(before, lookups)
            bookmarks.removeBookmark(books[14].id)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.visibleBookmarks.isEmpty())
            assertTrue(vm.uiState.value.recentlyRead.any { it.novel.id == books[14].id })
            vm.selectSource(fwn)
            assertEquals(2, vm.uiState.value.recentlyRead.size)
            assertEquals(books.last(), vm.uiState.value.currentReading!!.novel)
            assertEquals(before, lookups)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    private class MemoryProgressDao : ReadingProgressDao {
        val records = linkedMapOf<String, ReadingProgressEntity>()
        override fun getProgress(novelId: String) = flowOf(records[novelId])
        override suspend fun getProgressOnce(novelId: String) = records[novelId]
        override fun getAllProgress() = flowOf(records.values.toList())
        override suspend fun getAllProgressOnce() = records.values.toList()
        override suspend fun insertOrUpdate(progress: ReadingProgressEntity) { records[progress.novelId] = progress }
        override suspend fun deleteProgress(novelId: String) { records.remove(novelId) }
    }
}
