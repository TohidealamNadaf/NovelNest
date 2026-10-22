package com.novelnest.app.feature.browse

import com.novelnest.app.data.model.*

data class BrowseUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val selectedSource: NovelSourceType = NovelSourceType.NOVEL_PHOENIX,
    val selectedGenre: String = "All",
    val selectedCategoryUrl: String? = null,
    val categories: List<BrowseCategory> = emptyList(),
    val novels: List<Novel> = emptyList(),
    val nextPageUrl: String? = null,
    val errorMessage: String? = null,
    val paginationFailed: Boolean = false
) {
    val genres: List<String> get() = listOf("All") + categories.map { it.name }
    val filteredNovels: List<Novel> get() = novels
}
