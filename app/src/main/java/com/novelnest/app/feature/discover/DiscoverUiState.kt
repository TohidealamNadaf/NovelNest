package com.novelnest.app.feature.discover

import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType

data class DiscoverUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val selectedSource: NovelSourceType = NovelSourceType.NOVEL_PHOENIX,
    val sections: List<NovelSection> = emptyList(),
    val errorMessage: String? = null
)
