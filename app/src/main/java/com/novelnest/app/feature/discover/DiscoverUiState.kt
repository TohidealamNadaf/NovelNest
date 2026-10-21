package com.novelnest.app.feature.discover

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType

data class DiscoverUiState(
    val isLoading: Boolean = false,
    val selectedSource: NovelSourceType = NovelSourceType.NOVEL_FIRE,
    val selectedGenre: String = "All",
    val genres: List<String> = listOf(
        "All", "Action", "Fantasy", "Xianxia", "Mystery", "Sci-Fi", "Comedy", "Martial Arts", "Reincarnation"
    ),
    val novels: List<Novel> = emptyList(),
    val errorMessage: String? = null
)
