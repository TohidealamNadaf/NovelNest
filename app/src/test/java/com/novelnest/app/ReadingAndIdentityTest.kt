package com.novelnest.app

import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.novelnest.app.data.database.dao.ReadingProgressDao
import com.novelnest.app.data.database.entity.ReadingProgressEntity
import com.novelnest.app.data.model.*
import com.novelnest.app.data.preferences.*
import com.novelnest.app.data.progress.*
import com.novelnest.app.data.remote.NovelSource
import com.novelnest.app.data.remote.network.HostRateLimiter
import com.novelnest.app.data.remote.novelphoenix.NovelPhoenixParser
import com.novelnest.app.data.remote.novelphoenix.NovelPhoenixSource
import com.novelnest.app.data.repository.NovelRepositoryImpl
import com.novelnest.app.feature.reader.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingAndIdentityTest {
    private val np = NovelSourceType.NOVEL_PHOENIX
    private val fwn = NovelSourceType.FREE_WEB_NOVEL
    private fun book(id: String = "np-genetic-ascension", url: String = "https://novelphoenix.com/novel/genetic-ascension") =
        Novel(id, "Genetic Ascension", "", np, url, chapterCount = 100)
    private open class ProgressDao : ReadingProgressDao {
        val records = mutableMapOf<String, ReadingProgressEntity>()
        override fun getProgress(novelId: String) = flowOf(records[novelId])
        override suspend fun getProgressOnce(novelId: String) = records[novelId]
        override fun getAllProgress() = flowOf(records.values.toList())
        override suspend fun getAllProgressOnce() = records.values.toList()
        override suspend fun insertOrUpdate(progress: ReadingProgressEntity) { records[progress.novelId] = progress }
        override suspend fun deleteProgress(novelId: String) { records.remove(novelId) }
    }
    private open class Source : NovelSource {
        override val sourceType = NovelSourceType.NOVEL_PHOENIX
        override suspend fun getHomeSections() = Result.success(emptyList<NovelSection>())
        override suspend fun search(query: String) = Result.success(emptyList<Novel>())
        override suspend fun getNovelDetails(novelUrl: String) = Result.failure<Novel>(UnsupportedOperationException())
    }

    @Test fun progressCountsDistinctChaptersRatherThanLastOpenedIndex() {
        val progress = NovelProgress("np-book", "chapter-50", 49, "Fifty",
            readChapterIds = setOf("chapter-1", "/novel/book/chapter-1", "https://novelphoenix.com/novel/book/chapter-2?ref=x"))
        val result = book().readingCompletion(progress)
        assertEquals("2 / 100 Chapters", result.label)
        assertEquals(.02f, result.fraction, .0001f)
        assertEquals(0f, book().readingCompletion(null).fraction, 0f)
        assertEquals(1f, book().copy(chapterCount = 1).readingCompletion(progress).fraction, 0f)
        assertEquals("2 chapters read", book().copy(chapterCount = 0, latestChapter = "Chapter 50: End").readingCompletion(progress).label)
    }

    @Test fun loadingStoredProgressCannotOverwriteNewReadAndHistoryIsMerged() = runTest {
        val gate = CompletableDeferred<Unit>()
        val dao = object : ProgressDao() {
            override suspend fun getAllProgressOnce(): List<ReadingProgressEntity> {
                gate.await()
                return listOf(ReadingProgressEntity("np-book", "chapter-1", 0, "One", 1, listOf("chapter-1")))
            }
        }
        val manager = ReadingProgressManager(dao, this)
        runCurrent()
        manager.markChapterRead("np-book", "chapter-2", 1, "Two")
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(setOf("chapter-1", "chapter-2"), manager.getProgress("np-book")!!.readChapterIds)
        assertEquals("chapter-2", manager.getProgress("np-book")!!.lastReadChapterId)
        assertEquals(setOf("chapter-1", "chapter-2"), dao.records["np-book"]!!.readChapterIds.toSet())
    }

    @Test fun delayedPersistenceCannotLeaveAnOlderChapterInDatabase() = runTest {
        val gate = CompletableDeferred<Unit>()
        val dao = object : ProgressDao() {
            var first = true
            override suspend fun insertOrUpdate(progress: ReadingProgressEntity) {
                if (first) { first = false; gate.await() }
                super.insertOrUpdate(progress)
            }
        }
        val manager = ReadingProgressManager(dao, this)
        runCurrent()
        manager.markChapterRead("np-book", "chapter-1", 0, "One")
        runCurrent()
        manager.markChapterRead("np-book", "chapter-2", 1, "Two")
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("chapter-2", dao.records["np-book"]!!.lastReadChapterId)
        assertEquals(2, dao.records["np-book"]!!.readChapterIds.size)
    }

    @Test fun searchMergesLegacyAndCanonicalUrlRecordsWithoutMixingDifferentBooksOrSources() = runTest {
        val legacy = book("np-search-0", "https://novelphoenix.com/novel/genetic-ascension/?ref=search")
        val ranked = book().copy(title = "Genetic Ascension Rank 112", rank = 112)
        val source = object : Source() {
            override suspend fun search(query: String) = Result.success(listOf(legacy, ranked,
                book("np-other", "https://novelphoenix.com/novel/another-book")))
        }
        val repository = NovelRepositoryImpl(sources = mapOf(np to source), rateLimiter = HostRateLimiter(0))
        val results = repository.searchNovels(np, "Genetic").getOrThrow()
        assertEquals(2, results.size)
        assertEquals("np-genetic-ascension", results.first().id)
        assertEquals("Genetic Ascension", results.first().title)
        assertEquals(112, results.first().rank)
        val sameTitleOtherSource = legacy.copy(source = fwn, id = "fwn-genetic-ascension",
            sourceUrl = "https://freewebnovel.com/novel/genetic-ascension")
        assertEquals(2, NovelIdentity.distinct(listOf(legacy, sameTitleOtherSource)).size)
        assertEquals(1, NovelIdentity.distinct(listOf(sameTitleOtherSource,
            sameTitleOtherSource.copy(id = "fwn-search-2", sourceUrl = "https://freewebnovel.com/genetic-ascension.html"))).size)
    }

    @Test fun nestedListingCardsDoNotDuplicateNovelOrIncludeRankInTitle() {
        val card = """<div class='novel-item'><div class='novel-item'>
            <a href='/novel/genetic-ascension?ref=ranking' title='Genetic Ascension'>
            <h4 class='novel-title'>Genetic Ascension</h4><span class='badge _bl'>R 112</span></a>
            <div class='novel-stats'>100 Chapters</div></div></div>"""
        val result = NovelPhoenixParser.parseListingHtml(card)
        assertEquals(1, result.size)
        assertEquals("genetic-ascension", result.single().slug)
        assertEquals("Genetic Ascension", result.single().title)
    }

    @Test fun successfulEmptyPhoenixSearchDoesNotInventSeedResults() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"data":[]}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        assertTrue(NovelPhoenixSource(client, HostRateLimiter(0)).search("Shadow Slave").getOrThrow().isEmpty())
    }

    @Test fun readerRestoresCurrentChapterAndScrollInsteadOfOriginalRouteChapter() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val novel = book()
            val chapters = (0..3).map { Chapter("chapter-$it", "Chapter $it", listOf("Paragraph"),
                "https://novelphoenix.com/novel/genetic-ascension/chapter-$it") }
            val source = object : Source() {
                override suspend fun getChapters(novelSlugOrUrl: String, forceRefresh: Boolean) = Result.success(chapters)
                override suspend fun getChapterContent(chapterUrlOrId: String, forceRefresh: Boolean) = Result.success(chapters.first())
            }
            val repository = NovelRepositoryImpl(sources = mapOf(np to source))
            repository.rememberNovel(novel)
            val handle = SavedStateHandle(mapOf("readerChapterIndex" to 2, "scrollChapter" to 2, "scrollItem" to 5, "scrollOffset" to 24))
            val reader = ReaderViewModel(novel.id, repository, ReaderPreferencesManager(preferenceContext()),
                initialChapterIndex = 0, savedStateHandle = handle)
            store.put("reader", reader)
            advanceUntilIdle()
            assertEquals(2, reader.uiState.value.currentChapterIndex)
            assertEquals(5 to 24, reader.scrollPosition(2))
            reader.nextChapter()
            assertEquals(3, handle.get<Int>("readerChapterIndex"))
            reader.saveScrollPosition(3, 7, 30)
            assertEquals(7 to 30, reader.scrollPosition(3))
            reader.saveScrollPosition(2, 0, 0)
            assertEquals(7 to 30, reader.scrollPosition(3))
            val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
            val recreated = ReaderViewModel(novel.id, repository, ReaderPreferencesManager(preferenceContext()),
                initialChapterIndex = 0, savedStateHandle = restoredHandle)
            store.put("recreated", recreated)
            advanceUntilIdle()
            assertEquals(3, recreated.uiState.value.currentChapterIndex)
            assertEquals(7 to 30, recreated.scrollPosition(3))
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun startupRetainsOnlySourceAndRetiresTheOldReaderRoute() {
        val prefs = preferences()
        prefs.edit().putString("reader_session", "fwn-shadow-slave|42").apply()
        val migrated = NavigationPreferences(prefs)
        assertEquals(NovelSourceType.FREE_WEB_NOVEL, migrated.lastSource)
        assertFalse(prefs.contains("reader_session"))
        migrated.selectSource(NovelSourceType.ASURA_SCANS)
        assertEquals(NovelSourceType.ASURA_SCANS, NavigationPreferences(prefs).lastSource)
        prefs.edit().putString("last_source", "unknown-source").apply()
        assertEquals(NovelSourceType.NOVEL_PHOENIX, NavigationPreferences(prefs).lastSource)
    }

    private fun preferenceContext(): ContextWrapper {
        val prefs = preferences()
        return object : ContextWrapper(null) {
            override fun getSharedPreferences(name: String, mode: Int) = prefs
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

    @Test fun longReaderSessionBoundsTextAndCanReopenEvictedChapters() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val novel = book()
            val chapters = (0 until 150).map { Chapter("chapter-$it", "Chapter $it",
                url = "https://novelphoenix.com/novel/genetic-ascension/chapter-$it") }
            val source = object : Source() {
                override suspend fun getChapters(novelSlugOrUrl: String, forceRefresh: Boolean) = Result.success(chapters)
                override suspend fun getChapterContent(chapterUrlOrId: String, forceRefresh: Boolean): Result<Chapter> {
                    val index = chapterUrlOrId.substringAfterLast('-').toInt()
                    return Result.success(chapters[index].copy(paragraphs = listOf("Repeated", "Repeated", "Aa", "BB")))
                }
            }
            val repository = NovelRepositoryImpl(sources = mapOf(np to source))
            repository.rememberNovel(novel)
            val reader = ReaderViewModel(novel.id, repository, ReaderPreferencesManager(preferenceContext()))
            store.put("reader", reader)
            advanceUntilIdle()
            for (index in 0 until 150) {
                reader.selectChapter(index)
                runCurrent()
                val state = reader.uiState.value
                assertEquals(150, state.chapters.size)
                assertEquals(index, state.currentChapterIndex)
                assertFalse(state.chapters[index].paragraphs.isEmpty())
                assertTrue("Text should not accumulate as more chapters are read",
                    state.chapters.count { it.paragraphs.isNotEmpty() } <= 5)
            }
            assertTrue(reader.uiState.value.chapters.first().paragraphs.isEmpty())
            reader.selectChapter(0)
            runCurrent()
            assertFalse(reader.uiState.value.chapters.first().paragraphs.isEmpty())
            assertEquals(4, reader.uiState.value.chapters.first().paragraphs.size)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun thrownContentExceptionShowsRetryInsteadOfEscapingReaderCoroutine() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var failing = true
            val novel = book()
            val chapters = (0..2).map { Chapter("chapter-$it", "Chapter $it",
                url = "https://novelphoenix.com/novel/genetic-ascension/chapter-$it") }
            val source = object : Source() {
                override suspend fun getChapters(novelSlugOrUrl: String, forceRefresh: Boolean) = Result.success(chapters)
                override suspend fun getChapterContent(chapterUrlOrId: String, forceRefresh: Boolean): Result<Chapter> {
                    val index = chapterUrlOrId.substringAfterLast('-').toInt()
                    if (index == 1 && failing) throw java.io.IOException("Connection interrupted")
                    return Result.success(chapters[index].copy(paragraphs = listOf("Content")))
                }
            }
            val repository = NovelRepositoryImpl(sources = mapOf(np to source))
            repository.rememberNovel(novel)
            val reader = ReaderViewModel(novel.id, repository, ReaderPreferencesManager(preferenceContext()))
            store.put("reader", reader)
            advanceUntilIdle()
            assertNull("A failed prefetch must not affect the current chapter", reader.uiState.value.errorMessage)
            reader.selectChapter(1)
            runCurrent()
            assertEquals(1, reader.uiState.value.currentChapterIndex)
            assertFalse(reader.uiState.value.isChapterLoading)
            assertTrue(reader.uiState.value.errorMessage!!.contains("Connection interrupted"))
            failing = false
            reader.retryCurrentChapter()
            runCurrent()
            assertNull(reader.uiState.value.errorMessage)
            assertEquals(listOf("Content"), reader.uiState.value.chapters[1].paragraphs)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun repeatedSwipeCallbacksFromChapter83AdvanceOnlyOnce() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val novel = book()
            val chapters = (0 until 120).map { Chapter("chapter-${it + 1}", "Chapter ${it + 1}",
                url = "https://novelphoenix.com/novel/genetic-ascension/chapter-${it + 1}") }
            val source = object : Source() {
                override suspend fun getChapters(novelSlugOrUrl: String, forceRefresh: Boolean) = Result.success(chapters)
                override suspend fun getChapterContent(chapterUrlOrId: String, forceRefresh: Boolean): Result<Chapter> {
                    delay(20)
                    val index = chapterUrlOrId.substringAfterLast('-').toInt() - 1
                    return Result.success(chapters[index].copy(paragraphs = listOf("Repeated", "Repeated", "Aa", "BB")))
                }
            }
            val repository = NovelRepositoryImpl(sources = mapOf(np to source))
            repository.rememberNovel(novel)
            val reader = ReaderViewModel(novel.id, repository, ReaderPreferencesManager(preferenceContext()), initialChapterIndex = 82)
            store.put("reader", reader)
            advanceUntilIdle()
            assertEquals("chapter-83", reader.uiState.value.chapters[reader.uiState.value.currentChapterIndex].id)
            for (from in 82..88) {
                reader.nextChapter(expectedChapterIndex = from)
                reader.nextChapter(expectedChapterIndex = from)
                advanceUntilIdle()
                assertEquals("Late callbacks from the outgoing chapter must not skip another chapter",
                    from + 1, reader.uiState.value.currentChapterIndex)
                assertTrue(reader.uiState.value.chapters[from + 1].paragraphs.isNotEmpty())
            }
            reader.nextChapter(expectedChapterIndex = 82)
            assertEquals(89, reader.uiState.value.currentChapterIndex)
            reader.selectChapter(100)
            runCurrent()
            assertTrue(reader.uiState.value.isChapterLoading)
            reader.nextChapter(expectedChapterIndex = 100)
            reader.prevChapter(expectedChapterIndex = 100)
            assertEquals(100, reader.uiState.value.currentChapterIndex)
            advanceUntilIdle()
            reader.prevChapter(expectedChapterIndex = 100)
            reader.prevChapter(expectedChapterIndex = 100)
            advanceUntilIdle()
            assertEquals(99, reader.uiState.value.currentChapterIndex)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun latePrefetchAfterJumpCannotChangeCurrentChapterOrRetainDistantText() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val novel = book()
            val chapters = (0 until 120).map { Chapter("chapter-$it", "Chapter $it",
                url = "https://novelphoenix.com/novel/genetic-ascension/chapter-$it") }
            val source = object : Source() {
                override suspend fun getChapters(novelSlugOrUrl: String, forceRefresh: Boolean) = Result.success(chapters)
                override suspend fun getChapterContent(chapterUrlOrId: String, forceRefresh: Boolean): Result<Chapter> {
                    val index = chapterUrlOrId.substringAfterLast('-').toInt()
                    delay(if (index == 83) 1_000 else 20)
                    return Result.success(chapters[index].copy(paragraphs = listOf("Content $index")))
                }
            }
            val repository = NovelRepositoryImpl(sources = mapOf(np to source))
            repository.rememberNovel(novel)
            val reader = ReaderViewModel(novel.id, repository, ReaderPreferencesManager(preferenceContext()), initialChapterIndex = 82)
            store.put("reader", reader)
            advanceTimeBy(21)
            runCurrent()
            reader.selectChapter(100)
            advanceUntilIdle()
            val state = reader.uiState.value
            assertEquals(100, state.currentChapterIndex)
            assertEquals(listOf("Content 100"), state.chapters[100].paragraphs)
            assertTrue(state.chapters[83].paragraphs.isEmpty())
            assertNull(state.errorMessage)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
