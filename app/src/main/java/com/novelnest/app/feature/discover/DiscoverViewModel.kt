package com.novelnest.app.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.repository.NovelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DiscoverViewModel(
    private val repository: NovelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    init {
        loadDiscover()
    }

    fun selectSource(source: NovelSourceType) {
        if (_uiState.value.selectedSource == source) return
        _uiState.update { it.copy(selectedSource = source) }
        loadDiscover()
    }

    fun selectGenre(genre: String) {
        _uiState.update { it.copy(selectedGenre = genre) }
    }

    fun loadDiscover() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val currentSource = _uiState.value.selectedSource

            repository.getHomeSections(currentSource).collect { result ->
                result.fold(
                    onSuccess = { sections ->
                        val allNovels = sections.flatMap { it.novels }.distinctBy { it.id }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                novels = allNovels,
                                errorMessage = null
                            )
                        }
                    },
                    onFailure = { throwable ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = throwable.localizedMessage ?: "Failed to load"
                            )
                        }
                    }
                )
            }
        }
    }
}
