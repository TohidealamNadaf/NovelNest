package com.novelnest.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.preferences.SearchHistoryManager
import com.novelnest.app.data.repository.NovelRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: NovelRepository,
    initialSource: NovelSourceType = NovelSourceType.NOVEL_PHOENIX,
    private val historyManager: SearchHistoryManager? = null
) : ViewModel() {
    private val sessionHistory = mutableMapOf<NovelSourceType, List<String>>()
    private val _uiState = MutableStateFlow(SearchUiState(
        selectedSource = initialSource,
        recentSearches = historyManager?.history?.value?.get(initialSource).orEmpty()
    ))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var popularJob: Job? = null

    init {
        historyManager?.let { manager ->
            viewModelScope.launch {
                manager.history.collect { history ->
                    _uiState.update { it.copy(recentSearches = history[it.selectedSource].orEmpty()) }
                }
            }
        }
        loadPopular()
    }

    private fun recent(source: NovelSourceType) =
        historyManager?.history?.value?.get(source) ?: sessionHistory[source].orEmpty()

    private fun record(query: String) {
        val source = _uiState.value.selectedSource
        if (historyManager != null) historyManager.record(source, query) else {
            val trimmed = query.trim()
            if (trimmed.isNotEmpty()) {
                sessionHistory[source] = (listOf(trimmed) + recent(source).filterNot { it.equals(trimmed, true) })
                    .take(SearchHistoryManager.MAX_ENTRIES)
            }
        }
        _uiState.update { it.copy(recentSearches = recent(source)) }
    }

    fun setInitialSource(source: NovelSourceType) = selectSource(source)

    fun onQueryChange(newQuery: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(query = newQuery, errorMessage = null) }
        if (newQuery.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), hasSearched = false, isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            // Typing fragments are not personal search history.
            performSearch(newQuery, recordHistory = false)
        }
    }

    fun selectSource(source: NovelSourceType) {
        if (_uiState.value.selectedSource == source) return
        searchJob?.cancel()
        _uiState.update { it.copy(selectedSource = source, results = emptyList(), errorMessage = null,
            isSearching = false, recentSearches = recent(source), popularNovels = emptyList(),
            popularErrorMessage = null, hasSearched = false) }
        loadPopular()
        if (_uiState.value.query.isNotBlank()) performSearch(_uiState.value.query, recordHistory = false)
    }

    fun loadPopular(forceRefresh: Boolean = false) {
        popularJob?.cancel()
        val source = _uiState.value.selectedSource
        popularJob = viewModelScope.launch {
            _uiState.update { it.copy(isPopularLoading = true, popularErrorMessage = null) }
            val result = repository.getPopularNovels(source, forceRefresh)
            if (_uiState.value.selectedSource != source) return@launch
            result.fold(
                onSuccess = { novels ->
                    _uiState.update { it.copy(popularNovels = novels, isPopularLoading = false, popularErrorMessage = null) }
                },
                onFailure = {
                    _uiState.update { it.copy(isPopularLoading = false,
                        popularErrorMessage = "Popular novels couldn't load. Check your connection and retry.") }
                }
            )
        }
    }

    fun performSearch(query: String, recordHistory: Boolean = true) {
        searchJob?.cancel()
        if (query.isBlank()) {
            clearQuery()
            return
        }
        _uiState.update { it.copy(query = query) }
        if (recordHistory) record(query)
        val source = _uiState.value.selectedSource
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, hasSearched = true, errorMessage = null) }
            val result = repository.searchNovels(source, query.trim())
            if (_uiState.value.selectedSource != source || _uiState.value.query.trim() != query.trim()) return@launch
            result.fold(
                onSuccess = { novels ->
                    _uiState.update { it.copy(isSearching = false, results = com.novelnest.app.data.model.NovelIdentity.distinct(novels), errorMessage = null) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isSearching = false, results = emptyList(),
                        errorMessage = error.localizedMessage ?: "Search failed. Please retry.") }
                }
            )
        }
    }

    fun onNovelOpened(novel: Novel) {
        if (_uiState.value.query.isNotBlank()) record(_uiState.value.query)
        repository.rememberNovel(novel)
    }

    fun clearQuery() {
        searchJob?.cancel()
        _uiState.update { it.copy(query = "", results = emptyList(), hasSearched = false, isSearching = false, errorMessage = null) }
    }

    fun clearRecentSearches() {
        val source = _uiState.value.selectedSource
        if (historyManager != null) historyManager.clear(source) else sessionHistory[source] = emptyList()
        _uiState.update { it.copy(recentSearches = recent(source)) }
    }

    fun removeRecentSearch(keyword: String) {
        val source = _uiState.value.selectedSource
        if (historyManager != null) historyManager.remove(source, keyword)
        else sessionHistory[source] = recent(source).filterNot { it.equals(keyword, true) }
        _uiState.update { it.copy(recentSearches = recent(source)) }
    }
}
