package com.novelnest.app.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.repository.NovelRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DiscoverViewModel(
    private val repository: NovelRepository,
    initialSource: NovelSourceType = NovelSourceType.NOVEL_PHOENIX
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiscoverUiState(selectedSource = initialSource))
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { loadDiscover() }

    fun selectSource(source: NovelSourceType) {
        if (_uiState.value.selectedSource == source) return
        _uiState.update { it.copy(selectedSource = source, sections = emptyList(), errorMessage = null) }
        loadDiscover()
    }

    fun refresh() = loadDiscover(forceRefresh = true)

    fun loadDiscover(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        val source = _uiState.value.selectedSource
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.sections.isEmpty(), isRefreshing = forceRefresh, errorMessage = null) }
            repository.getHomeSections(source, forceRefresh).collect { result ->
                if (_uiState.value.selectedSource != source) return@collect
                result.fold(
                    onSuccess = { sections -> _uiState.update { it.copy(sections = sections, isLoading = false, isRefreshing = false, errorMessage = null) } },
                    onFailure = { error -> _uiState.update { it.copy(isLoading = false, isRefreshing = false,
                        errorMessage = error.localizedMessage ?: "Unable to load novels. Please retry.") } }
                )
            }
        }
    }
}
