package com.novelnest.app.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.repository.NovelRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BrowseViewModel(
    private val repository: NovelRepository,
    initialSource: NovelSourceType = NovelSourceType.NOVEL_PHOENIX
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrowseUiState(selectedSource = initialSource))
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var generation = 0
    init { load() }

    fun selectSource(source: NovelSourceType) {
        if (_uiState.value.selectedSource == source) return
        _uiState.value = BrowseUiState(selectedSource = source)
        load()
    }

    fun selectGenre(genre: String) {
        val state = _uiState.value
        if (genre == state.selectedGenre) return
        val category = state.categories.firstOrNull { it.name == genre }
        if (genre != "All" && category == null) return
        _uiState.update { it.copy(selectedGenre = genre, selectedCategoryUrl = category?.url,
            novels = emptyList(), nextPageUrl = null, errorMessage = null) }
        load()
    }

    fun load(forceRefresh: Boolean = false) = fetch(append = false, forceRefresh = forceRefresh)

    fun loadMore() {
        if (loadJob?.isActive == true || _uiState.value.nextPageUrl == null) return
        fetch(append = true, forceRefresh = false)
    }

    private fun fetch(append: Boolean, forceRefresh: Boolean) {
        loadJob?.cancel()
        val ticket = ++generation
        val state = _uiState.value
        val url = if (append) state.nextPageUrl else state.selectedCategoryUrl
        _uiState.update { it.copy(isLoading = !append, isLoadingMore = append, errorMessage = null, paginationFailed = false) }
        loadJob = viewModelScope.launch {
            val result = repository.getBrowsePage(state.selectedSource, url, forceRefresh)
            if (ticket != generation) return@launch
            result.fold(
                onSuccess = { page -> _uiState.update { current ->
                    current.copy(
                        novels = (if (append) current.novels + page.novels else page.novels).distinctBy { it.id },
                        categories = page.categories.ifEmpty { current.categories },
                        // A repeated next link must not make an infinite request loop.
                        nextPageUrl = page.nextPageUrl?.takeUnless { it == url },
                        isLoading = false, isLoadingMore = false, paginationFailed = false,
                        errorMessage = if (page.isStale) "Offline / Showing cached novels. Retry to refresh." else null
                    )
                } },
                onFailure = { error -> _uiState.update { it.copy(isLoading = false, isLoadingMore = false, paginationFailed = append,
                    errorMessage = error.localizedMessage ?: "Unable to load novels. Please retry.") } }
            )
        }
    }
}
