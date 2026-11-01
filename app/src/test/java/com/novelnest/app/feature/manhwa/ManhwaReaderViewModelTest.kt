package com.novelnest.app.feature.manhwa

import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.novelnest.app.data.bookmark.BookmarkManager
import com.novelnest.app.data.database.dao.ReadingProgressDao
import com.novelnest.app.data.database.entity.ReadingProgressEntity
import com.novelnest.app.data.model.*
import com.novelnest.app.data.progress.ReadingProgressManager
import com.novelnest.app.data.progress.readingCompletion
import com.novelnest.app.feature.detail.NovelDetailViewModel
import com.novelnest.app.feature.reader.Chapter
import com.novelnest.app.data.remote.asura.*
import com.novelnest.app.data.repository.NovelRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class ManhwaReaderViewModelTest {
    private val details get() = AsuraParser.details(asuraFixture("detail.html"), ASURA_URL)
    private open class Repository(private val details: ManhwaDetails) : NovelRepository {
        var calls = 0
        override fun getHomeSections(source: NovelSourceType, forceRefresh: Boolean) = flowOf(Result.success(emptyList<NovelSection>()))
        override suspend fun searchNovels(source: NovelSourceType, query: String) = Result.success(emptyList<Novel>())
        override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String) = Result.success(details.novel)
        override suspend fun getNovelById(novelId: String) = details.novel
        override suspend fun getChapters(source: NovelSourceType, novelSlugOrUrl: String, forceRefresh: Boolean) = Result.success(emptyList<com.novelnest.app.feature.reader.Chapter>())
        override suspend fun getChapterContent(source: NovelSourceType, chapterUrlOrId: String, forceRefresh: Boolean) = Result.failure<com.novelnest.app.feature.reader.Chapter>(UnsupportedOperationException())
        override suspend fun getManhwaDetails(source: NovelSourceType, seriesUrlOrId: String, forceRefresh: Boolean) = Result.success(details)
        override suspend fun getManhwaChapter(source: NovelSourceType, chapterUrl: String, forceRefresh: Boolean): Result<ManhwaChapterContent> {
            calls++
            val chapter = details.chapters.first { it.url == chapterUrl }
            val content = AsuraParser.chapterContent(asuraFixture("chapter.json"), ASURA_CHAPTER)
            return Result.success(content.copy(chapter = chapter))
        }
    }
    private class Dao : ReadingProgressDao {
        val records = mutableMapOf<String, ReadingProgressEntity>()
        override fun getProgress(novelId: String) = flowOf(records[novelId])
        override suspend fun getProgressOnce(novelId: String) = records[novelId]
        override fun getAllProgress() = flowOf(records.values.toList())
        override suspend fun getAllProgressOnce() = records.values.toList()
        override suspend fun insertOrUpdate(progress: ReadingProgressEntity) { records[progress.novelId] = progress }
        override suspend fun deleteProgress(novelId: String) { records.remove(novelId) }
    }
    private suspend fun TestScope.withReader(block: suspend (ViewModelStore) -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try { block(store) } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun explicitChapterAndPagePositionRestoreMarksOnlyTheOpenedChapterRead() = runTest {
        withReader { store ->
            val details = details; val dao = Dao(); val progress = ReadingProgressManager(dao, this)
            val saved = SavedStateHandle(mapOf("manhwa.chapter" to details.chapters[82].id, "manhwa.page" to 3, "manhwa.offset" to .4f))
            val reader = ManhwaReaderViewModel(details.novel.id, Repository(details), progressManager = progress, savedStateHandle = saved)
            store.put("reader", reader); advanceUntilIdle()
            assertEquals(82, reader.uiState.value.currentChapterIndex); assertEquals(3, reader.uiState.value.currentPageIndex)
            assertEquals(.4f, reader.uiState.value.pageOffsetFraction, 0f)
            assertEquals(setOf(details.chapters[82].id), progress.getProgress(details.novel.id)!!.readChapterIds)
            reader.markChapterFinished(details.chapters[81].id)
            assertEquals(setOf(details.chapters[82].id), progress.getProgress(details.novel.id)!!.readChapterIds)
            reader.markChapterFinished(details.chapters[82].id); advanceUntilIdle()
            assertEquals(setOf(details.chapters[82].id), progress.getProgress(details.novel.id)!!.readChapterIds)
            reader.savePagePosition(details.chapters[82].id, 4, .5f)
            assertEquals(4, saved.get<Int>("manhwa.page"))
            reader.selectChapter(83); advanceUntilIdle(); assertEquals(0, reader.uiState.value.currentPageIndex)
            reader.savePagePosition(details.chapters[82].id, 7)
            assertEquals(0, reader.uiState.value.currentPageIndex)
        }
    }
    @Test fun lateCancelledChapterCannotReplaceANewerSelectionAndRapidNextCoalesces() = runTest {
        withReader { store ->
            val details = details
            val repo = object : Repository(details) {
                override suspend fun getManhwaChapter(source: NovelSourceType, chapterUrl: String, forceRefresh: Boolean): Result<ManhwaChapterContent> {
                    val result = super.getManhwaChapter(source, chapterUrl, forceRefresh)
                    // Deliberately noncooperative test double proves the generation guard too.
                    withContext(NonCancellable) { delay(if (chapterUrl.endsWith("/2")) 1000 else 20) }
                    return result
                }
            }
            val reader = ManhwaReaderViewModel(details.novel.id, repo); store.put("reader", reader); advanceUntilIdle()
            reader.nextChapter(); reader.nextChapter(); reader.nextChapter()
            assertEquals(1, reader.uiState.value.currentChapterIndex)
            runCurrent(); reader.selectChapter(82); advanceUntilIdle()
            assertEquals(82, reader.uiState.value.currentChapterIndex)
            assertEquals(details.chapters[82].id, reader.uiState.value.content!!.chapter.id)
            assertFalse(reader.uiState.value.isChapterLoading)
        }
    }
    @Test fun lockedFailureRetryAndEmptyCatalogHaveExplicitStates() = runTest {
        withReader { store ->
            val details = details; var locked = true
            val repo = object : Repository(details) {
                override suspend fun getManhwaChapter(source: NovelSourceType, chapterUrl: String, forceRefresh: Boolean): Result<ManhwaChapterContent> =
                    if (locked) Result.failure(AsuraException.Locked("2026-10-27T07:00:00Z")) else super.getManhwaChapter(source, chapterUrl, forceRefresh)
            }
            val reader = ManhwaReaderViewModel(details.novel.id, repo); store.put("reader", reader); advanceUntilIdle()
            assertEquals(ManhwaFailureKind.LOCKED, reader.uiState.value.error!!.kind); assertNull(reader.uiState.value.content)
            locked = false; reader.retryChapter(); advanceUntilIdle()
            assertNull(reader.uiState.value.error); assertEquals(14, reader.uiState.value.content!!.pages.size)
            val empty = ManhwaReaderViewModel(details.novel.id, Repository(details.copy(chapters = emptyList())))
            store.put("empty", empty); advanceUntilIdle()
            assertTrue(empty.uiState.value.isEmpty); assertFalse(empty.uiState.value.canNext)
        }
    }
    @Test fun bookmarkIdentityIsTheSameAcrossEveryRouteAndAfterReload() {
        val prefs = preferences()
        val context = object : ContextWrapper(null) { override fun getSharedPreferences(name: String, mode: Int) = prefs }
        val manager = BookmarkManager(context)
        val record = details.novel
        manager.addBookmark(record.copy(id = "asura-s-0", sourceUrl = "https://asuracomic.net/series/$ASURA_SERIES"))
        assertTrue(manager.isBookmarked(record.id)); assertEquals(record.id, manager.bookmarks.value.single().id)
        val fromSearch = AsuraParser.search(asuraFixture("search.json")).single()
        manager.addBookmark(fromSearch); assertEquals(1, manager.bookmarks.value.size)
        assertEquals(record.id, BookmarkManager(context).getBookmark(record.id)!!.id)
        assertFalse(manager.toggleBookmark(record)); assertTrue(manager.bookmarks.value.isEmpty())
    }
    @Test fun readChapterIdsUpdatesImmediatelyAsManhwaChaptersOpen() = runTest {
        withReader { store ->
            val details = details; val dao = Dao(); val progress = ReadingProgressManager(dao, this)
            val reader = ManhwaReaderViewModel(details.novel.id, Repository(details), progressManager = progress)
            store.put("reader", reader); advanceUntilIdle()
            assertEquals(setOf(details.chapters[0].id), reader.uiState.value.readChapterIds)
            reader.nextChapter()
            advanceUntilIdle()
            assertEquals(setOf(details.chapters[0].id, details.chapters[1].id), reader.uiState.value.readChapterIds)
        }
    }
    @Test fun resumeUsesPersistentPagePositionWithoutASavedStateHandle() = runTest {
        withReader { store ->
            val details = details; val dao = Dao(); val progress = ReadingProgressManager(dao, this)
            progress.awaitLoaded()
            progress.recordManhwaChapterOpened(details.novel, details.chapters[82], 82, 14)
            progress.saveManhwaPagePosition(details.novel.id, details.chapters[82].id, 4, .6f, 14)
            advanceUntilIdle()
            val recreatedProgress = ReadingProgressManager(dao, this)
            val reader = ManhwaReaderViewModel(details.novel.id, Repository(details), progressManager = recreatedProgress)
            store.put("reader", reader); advanceUntilIdle()
            assertEquals(82, reader.uiState.value.currentChapterIndex)
            assertEquals(4, reader.uiState.value.currentPageIndex)
            assertEquals(.6f, reader.uiState.value.pageOffsetFraction, 0f)
            reader.selectChapter(83); advanceUntilIdle()
            assertEquals(0, reader.uiState.value.currentPageIndex)
            assertEquals(0f, reader.uiState.value.pageOffsetFraction, 0f)
            reader.savePagePosition(details.chapters[82].id, 7, .8f)
            assertEquals(0, reader.uiState.value.currentPageIndex)
        }
    }
    @Test fun openedChapterUpdatesDetailProgressAndSidebarReadIdsBeforeReachingTheEnd() = runTest {
        withReader { store ->
            val details = details
            val dao = Dao()
            val progress = ReadingProgressManager(dao, this)
            var failChapter: String? = null
            val repo = object : Repository(details) {
                override suspend fun getChapters(source: NovelSourceType, novelSlugOrUrl: String, forceRefresh: Boolean) =
                    Result.success(details.chapters.map { Chapter(it.id, it.title, url = it.url) })
                override suspend fun getManhwaChapter(source: NovelSourceType, chapterUrl: String, forceRefresh: Boolean) =
                    if (chapterUrl == failChapter) Result.failure<ManhwaChapterContent>(AsuraException.Locked(null))
                    else super.getManhwaChapter(source, chapterUrl, forceRefresh)
            }
            val detail = NovelDetailViewModel(details.novel.id, repo, readingProgressManager = progress)
            val reader = ManhwaReaderViewModel(details.novel.id, repo, progressManager = progress)
            store.put("detail", detail); store.put("reader", reader)
            advanceUntilIdle()

            val first = details.chapters.first().id
            // These are the exact sets/IDs consumed by Detail's dim/tick rows and the shared sidebar.
            assertEquals(setOf(first), reader.uiState.value.readChapterIds)
            assertTrue(detail.uiState.value.readChapterIds.contains(detail.uiState.value.chapters.first().id))
            assertEquals(1, details.novel.readingCompletion(detail.uiState.value.readingProgress).read)
            assertTrue(details.novel.readingCompletion(detail.uiState.value.readingProgress).fraction > 0f)
            assertEquals(0, reader.uiState.value.currentPageIndex)

            reader.selectChapter(82); advanceUntilIdle()
            val read = setOf(first, details.chapters[82].id)
            assertEquals(read, reader.uiState.value.readChapterIds)
            assertEquals(read, detail.uiState.value.readChapterIds)
            assertEquals(2, details.novel.readingCompletion(detail.uiState.value.readingProgress).read)
            assertFalse(detail.uiState.value.readChapterIds.contains(details.chapters[81].id))
            assertEquals(read, dao.records[details.novel.id]!!.readChapterIds.toSet())

            // Revisiting cannot increase the badge; failed/locked requests do not mark anything read.
            reader.selectChapter(0); advanceUntilIdle()
            assertEquals(read, reader.uiState.value.readChapterIds)
            failChapter = details.chapters[83].url
            reader.selectChapter(83); advanceUntilIdle()
            assertNotNull(reader.uiState.value.error)
            assertEquals(read, reader.uiState.value.readChapterIds)
            assertEquals(read, detail.uiState.value.readChapterIds)

            val reloaded = ReadingProgressManager(dao, this)
            reloaded.awaitLoaded()
            assertEquals(read, reloaded.getProgress(details.novel.id)!!.readChapterIds)
        }
    }
    private fun preferences(): SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when {
                method.name.startsWith("put") -> { values[args[0] as String] = args[1]; proxy }
                method.name == "remove" -> { values.remove(args[0] as String); proxy }
                method.name == "apply" -> null
                method.name == "commit" -> true
                else -> error(method.name)
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when {
                method.name.startsWith("get") -> values[args[0] as String] ?: args[1]
                method.name == "contains" -> values.containsKey(args[0] as String)
                method.name == "edit" -> editor
                else -> error(method.name)
            }
        } as SharedPreferences
    }
}
