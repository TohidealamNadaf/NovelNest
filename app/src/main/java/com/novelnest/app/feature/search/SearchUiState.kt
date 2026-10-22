package com.novelnest.app.feature.search

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType

data class SearchUiState(
    val query: String = "",
    val selectedSource: NovelSourceType = NovelSourceType.NOVEL_PHOENIX,
    val isSearching: Boolean = false,
    val results: List<Novel> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val popularNovels: List<Novel> = emptyList(),
    val isPopularLoading: Boolean = false,
    val popularErrorMessage: String? = null,
    val errorMessage: String? = null,
    val hasSearched: Boolean = false
)
