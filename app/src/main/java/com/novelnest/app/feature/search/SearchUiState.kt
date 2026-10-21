package com.novelnest.app.feature.search

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType

data class SearchUiState(
    val query: String = "",
    val selectedSource: NovelSourceType = NovelSourceType.NOVEL_PHOENIX,
    val isSearching: Boolean = false,
    val results: List<Novel> = emptyList(),
    val recentSearches: List<String> = listOf(
        "Shadow Slave",
        "Solo Leveling",
        "Lord of the Mysteries",
        "Mount Hua Sect",
        "Reverend Insanity"
    ),
    val errorMessage: String? = null,
    val hasSearched: Boolean = false
)
