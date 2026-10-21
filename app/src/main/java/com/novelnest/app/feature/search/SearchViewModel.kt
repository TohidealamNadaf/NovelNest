package com.novelnest.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.model.NovelSourceType
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
    initialSource: NovelSourceType = NovelSourceType.NOVEL_PHOENIX
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState(selectedSource = initialSource))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun setInitialSource(source: NovelSourceType) {
        if (_uiState.value.selectedSource != source) {
            _uiState.update { it.copy(selectedSource = source) }
            if (_uiState.value.query.isNotBlank()) {
                performSearch(_uiState.value.query)
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }

        searchJob?.cancel()
        if (newQuery.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), hasSearched = false, isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(350) // Debounce search
            performSearch(newQuery)
        }
    }

    fun selectSource(source: NovelSourceType) {
        if (_uiState.value.selectedSource == source) return
        _uiState.update { it.copy(selectedSource = source) }
        if (_uiState.value.query.isNotBlank()) {
            performSearch(_uiState.value.query)
        }
    }

    fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearching = true,
                    hasSearched = true,
                    errorMessage = null
                )
            }

            val result = repository.searchNovels(_uiState.value.selectedSource, query)
            result.fold(
                onSuccess = { novels ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            results = novels,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            errorMessage = throwable.localizedMessage ?: "Search failed"
                        )
                    }
                }
            )
        }
    }

    fun clearQuery() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(query = "", results = emptyList(), hasSearched = false, isSearching = false)
        }
    }

    fun clearRecentSearches() {
        _uiState.update { it.copy(recentSearches = emptyList()) }
    }

    fun removeRecentSearch(keyword: String) {
        _uiState.update { it.copy(recentSearches = it.recentSearches.filterNot { item -> item == keyword }) }
    }
}
