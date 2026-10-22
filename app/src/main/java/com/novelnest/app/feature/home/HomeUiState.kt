package com.novelnest.app.feature.home

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.progress.NovelProgress

data class HomeReadingEntry(val novel: Novel, val progress: NovelProgress)

data class HomeUiState(
    val selectedSource: NovelSourceType = NovelSourceType.NOVEL_PHOENIX,
    val progress: Map<String, NovelProgress> = emptyMap(),
    val bookmarkedNovels: List<Novel> = emptyList(),
    val readingHistory: List<HomeReadingEntry> = emptyList()
) {
    fun progressFor(novel: Novel): NovelProgress? = progress[novel.id]
        ?: readingHistory.firstOrNull {
            it.novel.source == novel.source && it.novel.sourceUrl == novel.sourceUrl
        }?.progress

    val recentlyRead: List<HomeReadingEntry> = readingHistory
        .filter { it.novel.source == selectedSource }
        .sortedByDescending { it.progress.lastReadTimestamp }
        .distinctBy { it.novel.sourceUrl.ifBlank { it.novel.id } }
        .take(10)
    val currentReading: HomeReadingEntry? = recentlyRead.firstOrNull()
    val visibleBookmarks: List<Novel> = bookmarkedNovels.filter { it.source == selectedSource }
        .sortedByDescending { progressFor(it)?.lastReadTimestamp ?: Long.MIN_VALUE }
}
