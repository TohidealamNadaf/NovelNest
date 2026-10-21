package com.novelnest.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.repository.NovelRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: NovelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadSectionsForSource(_uiState.value.selectedSource)
    }

    fun selectSource(source: NovelSourceType) {
        if (_uiState.value.selectedSource == source && _uiState.value.sections.isNotEmpty()) {
            return
        }
        _uiState.update { it.copy(selectedSource = source) }
        loadSectionsForSource(source)
    }

    fun refresh() {
        loadSectionsForSource(_uiState.value.selectedSource, forceRefresh = true)
    }

    private fun loadSectionsForSource(source: NovelSourceType, forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = it.sections.isEmpty() || forceRefresh,
                    isRefreshing = forceRefresh,
                    errorMessage = null
                )
            }

            repository.getHomeSections(source, forceRefresh).collect { result ->
                result.fold(
                    onSuccess = { sections ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                sections = sections,
                                errorMessage = null
                            )
                        }
                    },
                    onFailure = { throwable ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = throwable.localizedMessage ?: "Failed to load novels"
                            )
                        }
                    }
                )
            }
        }
    }
}
