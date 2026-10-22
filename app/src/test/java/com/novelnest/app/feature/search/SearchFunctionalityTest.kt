package com.novelnest.app.feature.search

import android.content.SharedPreferences
import androidx.lifecycle.ViewModelStore
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.preferences.SearchHistoryManager
import com.novelnest.app.data.repository.NovelRepository
import com.novelnest.app.feature.reader.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class SearchFunctionalityTest {
    private val phoenix = NovelSourceType.NOVEL_PHOENIX
    private val fwn = NovelSourceType.FREE_WEB_NOVEL
    private val store = ViewModelStore()

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test fun historyPersistsDeduplicatesAndStaysSeparateBySource() {
        val prefs = memoryPreferences()
        val manager = SearchHistoryManager(prefs)
        manager.record(phoenix, "  Shadow   Slave  ")
        manager.record(phoenix, "Lord of the Mysteries")
        manager.record(phoenix, "shadow slave")
        manager.record(fwn, "Martial World")
        val restored = SearchHistoryManager(prefs)
        assertEquals(listOf("shadow slave", "Lord of the Mysteries"), restored.history.value[phoenix])
        assertEquals(listOf("Martial World"), restored.history.value[fwn])
        restored.remove(phoenix, "SHADOW SLAVE")
        assertEquals(listOf("Lord of the Mysteries"), SearchHistoryManager(prefs).history.value[phoenix])
        restored.clear(phoenix)
        assertTrue(SearchHistoryManager(prefs).history.value[phoenix].orEmpty().isEmpty())
        assertEquals(listOf("Martial World"), restored.history.value[fwn])
    }

    @Test fun historyIsBoundedAndBlankQueriesAreIgnored() {
        val history = SearchHistoryManager(memoryPreferences())
        history.record(phoenix, "   ")
        assertTrue(history.history.value[phoenix].orEmpty().isEmpty())
        repeat(25) { history.record(phoenix, "Novel $it") }
        assertEquals(SearchHistoryManager.MAX_ENTRIES, history.history.value[phoenix]?.size)
        assertEquals("Novel 24", history.history.value[phoenix]?.first())
    }

    @Test fun typingFragmentsDoesNotPolluteHistoryButSubmitDoes() = runTest {
        val prefs = memoryPreferences()
        val repository = FakeRepository()
        val vm = model(repository, SearchHistoryManager(prefs))
        vm.onQueryChange("Sha")
        advanceTimeBy(350)
        runCurrent()
        assertTrue(vm.uiState.value.recentSearches.isEmpty())
        vm.performSearch("Shadow Slave")
        runCurrent()
        assertEquals("Shadow Slave", vm.uiState.value.query)
        assertEquals(listOf("Shadow Slave"), vm.uiState.value.recentSearches)
        assertEquals("Shadow Slave", repository.lastQuery)
        assertEquals(listOf("Shadow Slave"), SearchHistoryManager(prefs).history.value[phoenix])
    }

    @Test fun openingADebouncedResultRecordsQueryAndRemembersNovel() = runTest {
        val repository = FakeRepository()
        val vm = model(repository, SearchHistoryManager(memoryPreferences()))
        vm.onQueryChange("Shadow Slave")
        advanceTimeBy(350)
        runCurrent()
        val novel = vm.uiState.value.results.single()
        vm.onNovelOpened(novel)
        assertEquals(novel, repository.remembered)
        assertEquals(listOf("Shadow Slave"), vm.uiState.value.recentSearches)
    }

    @Test fun sourceSwitchUpdatesPopularSuggestionsAndHistoryTogether() = runTest {
        val history = SearchHistoryManager(memoryPreferences())
        history.record(phoenix, "Shadow Slave")
        history.record(fwn, "Martial World")
        val vm = model(FakeRepository(), history)
        assertEquals(phoenix, vm.uiState.value.popularNovels.single().source)
        vm.selectSource(fwn)
        runCurrent()
        assertEquals(listOf("Martial World"), vm.uiState.value.recentSearches)
        assertEquals(fwn, vm.uiState.value.popularNovels.single().source)
        vm.clearRecentSearches()
        assertTrue(vm.uiState.value.recentSearches.isEmpty())
        assertTrue(vm.uiState.value.popularNovels.isNotEmpty())
        vm.selectSource(phoenix)
        assertEquals(listOf("Shadow Slave"), vm.uiState.value.recentSearches)
    }

    @Test fun popularFailureShowsRetryStateAndNeverInventsSuggestions() = runTest {
        val repository = FakeRepository().apply { popularFails = true }
        val vm = model(repository, SearchHistoryManager(memoryPreferences()))
        assertTrue(vm.uiState.value.popularNovels.isEmpty())
        assertNotNull(vm.uiState.value.popularErrorMessage)
        repository.popularFails = false
        vm.loadPopular(forceRefresh = true)
        assertNull(vm.uiState.value.popularErrorMessage)
        assertTrue(vm.uiState.value.popularNovels.isNotEmpty())
    }

    private fun model(repository: FakeRepository, history: SearchHistoryManager) =
        SearchViewModel(repository, historyManager = history).also { store.put("search", it) }

    private class FakeRepository : NovelRepository {
        var popularFails = false
        var lastQuery = ""
        var remembered: Novel? = null
        private fun novel(source: NovelSourceType) = Novel("${source.name}-sample", "Actual ranked novel", "", source, source.baseUrl)
        override suspend fun getPopularNovels(source: NovelSourceType, forceRefresh: Boolean): Result<List<Novel>> =
            if (popularFails) Result.failure(IllegalStateException("offline")) else Result.success(listOf(novel(source)))
        override suspend fun searchNovels(source: NovelSourceType, query: String): Result<List<Novel>> {
            lastQuery = query
            return Result.success(listOf(novel(source)))
        }
        override fun rememberNovel(novel: Novel) { remembered = novel }
        override fun getHomeSections(source: NovelSourceType, forceRefresh: Boolean) = flowOf(Result.success(emptyList<NovelSection>()))
        override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String) = Result.success(novel(source))
        override suspend fun getNovelById(novelId: String): Novel? = null
        override suspend fun getChapters(source: NovelSourceType, novelSlugOrUrl: String, forceRefresh: Boolean) = Result.success(emptyList<Chapter>())
        override suspend fun getChapterContent(source: NovelSourceType, chapterUrlOrId: String, forceRefresh: Boolean) = Result.failure<Chapter>(UnsupportedOperationException())
    }

    private fun memoryPreferences(): SharedPreferences {
        val values = mutableMapOf<String, String?>()
        val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putString" -> { values[args[0] as String] = args[1] as String?; proxy }
                "apply" -> null
                else -> throw UnsupportedOperationException(method.name)
            }
        }
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> values[args[0] as String] ?: args[1]
                "edit" -> editor
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SharedPreferences
    }
}
