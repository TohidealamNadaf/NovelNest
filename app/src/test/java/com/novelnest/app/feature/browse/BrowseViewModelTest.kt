package com.novelnest.app.feature.browse

import androidx.lifecycle.ViewModelStore
import com.novelnest.app.data.model.*
import com.novelnest.app.data.remote.*
import com.novelnest.app.data.repository.NovelRepositoryImpl
import com.novelnest.app.feature.home.HomeViewModel
import com.novelnest.app.data.bookmark.BookmarkManager
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelTest {
    private val np = NovelSourceType.NOVEL_PHOENIX
    private val fwn = NovelSourceType.FREE_WEB_NOVEL
    private fun novel(source: NovelSourceType, id: String) = Novel(id, id, "", source, "${source.baseUrl}/novel/${id.removePrefix("np-").removePrefix("fwn-")}")
    private abstract class Stub(override val sourceType: NovelSourceType) : NovelSource {
        override suspend fun getHomeSections() = Result.success(emptyList<NovelSection>())
        override suspend fun search(query: String) = Result.success(emptyList<Novel>())
        override suspend fun getNovelDetails(novelUrl: String) = Result.failure<Novel>(IOException())
    }

    @Test fun categoryFetchesRemotePageRatherThanFilteringFeedAndPaginationDeduplicates() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val category = BrowseCategory("Fantasy", "https://novelphoenix.com/genre-fantasy/sort-new/status-all/all-novel")
            var calls = 0
            val source = object : Stub(np) {
                override suspend fun getBrowsePage(url: String?): Result<BrowsePage> {
                    calls++
                    delay(10)
                    val novels = when {
                        url?.contains("page=2") == true -> listOf(novel(np, "fantasy"), novel(np, "second"))
                        url == category.url -> listOf(novel(np, "fantasy"))
                        else -> listOf(novel(np, "all"))
                    }
                    return Result.success(BrowsePage(novels, listOf(category),
                        if (url == category.url) category.url + "?page=2" else null))
                }
            }
            val vm = BrowseViewModel(NovelRepositoryImpl(sources = mapOf(np to source)), np)
            store.put("browse", vm)
            advanceUntilIdle()
            assertEquals("all", vm.uiState.value.novels.single().id)
            vm.selectGenre("Fantasy")
            assertTrue(vm.uiState.value.isLoading)
            assertTrue(vm.uiState.value.novels.isEmpty())
            advanceUntilIdle()
            assertEquals("fantasy", vm.uiState.value.novels.single().id)
            vm.loadMore(); vm.loadMore(); vm.loadMore()
            advanceUntilIdle()
            assertEquals(listOf("fantasy", "second"), vm.uiState.value.novels.map { it.id })
            assertEquals(3, calls)
            assertNull(vm.uiState.value.nextPageUrl)
            vm.selectGenre("All")
            advanceUntilIdle()
            assertEquals("all", vm.uiState.value.novels.single().id)
            assertEquals("Returning to a warm catalog must not fetch", 3, calls)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun delayedOldSourceCannotReplaceNewSourceResults() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val oldRequest = CompletableDeferred<Unit>()
            val old = object : Stub(np) {
                override suspend fun getBrowsePage(url: String?): Result<BrowsePage> {
                    withContext(NonCancellable) { oldRequest.await() }
                    return Result.success(BrowsePage(listOf(novel(np, "old")), emptyList()))
                }
            }
            val current = object : Stub(fwn) {
                override suspend fun getBrowsePage(url: String?) =
                    Result.success(BrowsePage(listOf(novel(fwn, "current")), emptyList()))
            }
            val vm = BrowseViewModel(NovelRepositoryImpl(sources = mapOf(np to old, fwn to current)))
            store.put("browse", vm)
            vm.selectSource(fwn)
            runCurrent()
            assertEquals(fwn, vm.uiState.value.selectedSource)
            oldRequest.complete(Unit)
            advanceUntilIdle()
            assertEquals("current", vm.uiState.value.novels.single().id)
            assertEquals("All", vm.uiState.value.selectedGenre)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun bookmarkLibraryUpdatesWithoutRemoteFetchAndRetainsTotalsAcrossRestart() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val context = bookmarkContext()
            val manager = BookmarkManager(context)
            val vm = HomeViewModel(manager)
            store.put("home", vm)
            val phoenix = novel(np, "np-book").copy(chapterCount = 100, latestChapter = "Chapter 100: Final")
            val free = novel(fwn, "fwn-book").copy(chapterCount = 200, latestChapter = "Chapter 200: Final")
            manager.addBookmark(phoenix); manager.addBookmark(free)
            assertEquals(phoenix, vm.uiState.value.visibleBookmarks.single())
            vm.selectSource(fwn)
            assertEquals("200 Chapters", vm.uiState.value.visibleBookmarks.single().chapterCountLabel())
            assertEquals(2, BookmarkManager(context).bookmarks.value.size)
            vm.removeBookmark(free.id)
            assertTrue(vm.uiState.value.visibleBookmarks.isEmpty())
            assertTrue(manager.isBookmarked(phoenix.id))
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
