package com.novelnest.app.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.remote.SeedData
import com.novelnest.app.data.repository.NovelRepository
import com.novelnest.app.feature.reader.Chapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NovelDetailUiState(
    val novel: Novel? = null,
    val chapters: List<Chapter> = emptyList(),
    val filteredChapters: List<Chapter> = emptyList(),
    val isAscending: Boolean = true,
    val chapterQuery: String = "",
    val isBookmarked: Boolean = false,
    val isLoading: Boolean = true,
    val isSynopsisExpanded: Boolean = false,
    val errorMessage: String? = null
)

class NovelDetailViewModel(
    private val novelId: String,
    private val repository: NovelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NovelDetailUiState())
    val uiState: StateFlow<NovelDetailUiState> = _uiState.asStateFlow()

    init {
        loadNovelDetails()
    }

    fun toggleBookmark() {
        _uiState.update { it.copy(isBookmarked = !it.isBookmarked) }
    }

    fun toggleSynopsis() {
        _uiState.update { it.copy(isSynopsisExpanded = !it.isSynopsisExpanded) }
    }

    fun toggleSortOrder() {
        _uiState.update { current ->
            val newAscending = !current.isAscending
            val sorted = if (newAscending) {
                current.chapters.sortedBy { it.id }
            } else {
                current.chapters.sortedByDescending { it.id }
            }
            current.copy(
                isAscending = newAscending,
                chapters = sorted,
                filteredChapters = filterChapters(sorted, current.chapterQuery)
            )
        }
    }

    fun onChapterSearch(query: String) {
        _uiState.update { current ->
            current.copy(
                chapterQuery = query,
                filteredChapters = filterChapters(current.chapters, query)
            )
        }
    }

    private fun filterChapters(chapters: List<Chapter>, query: String): List<Chapter> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return chapters
        return chapters.filter { it.title.lowercase().contains(q) || it.id.lowercase().contains(q) }
    }

    private fun loadNovelDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 1. Find from seed data / repository
            val foundNovel = SeedData.findNovelById(novelId) ?: run {
                val formattedTitle = if (novelId.isNotBlank() && novelId != "sample") {
                    novelId.replace("-", " ")
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
                } else {
                    "The Whispering Sanctuary"
                }
                Novel(
                    id = novelId,
                    title = formattedTitle,
                    coverUrl = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600&q=80",
                    author = "Gu Zhen Ren",
                    source = NovelSourceType.NOVEL_PHOENIX,
                    sourceUrl = "",
                    rating = 4.8f,
                    latestChapter = "Chapter 250",
                    views = "1.8M",
                    status = "Ongoing",
                    genres = listOf("Action", "Cultivation", "Fantasy", "Mystery"),
                    synopsis = "A journey of cultivation, mysteries, and perseverance. In a realm where destiny is carved in blood and ancient runes, a solitary wanderer discovers a dormant artifact that can invert the laws of the mortal cosmos."
                )
            }

            // Generate representative chapter list (e.g. 100 chapters)
            val generatedChapters = (1..60).map { chNum ->
                Chapter(
                    id = "ch-$chNum",
                    title = "Chapter $chNum: ${getChapterName(chNum, foundNovel.title)}",
                    paragraphs = listOf(
                        "The celestial runes hummed with silent authority as Chapter $chNum commenced.",
                        "Paths diverged across the mortal wilderness, each step leading deeper into the sanctuary of the heavens."
                    )
                )
            }

            _uiState.update {
                it.copy(
                    novel = foundNovel,
                    chapters = generatedChapters,
                    filteredChapters = generatedChapters,
                    isLoading = false
                )
            }
        }
    }

    private fun getChapterName(chNum: Int, novelTitle: String): String {
        return when (chNum) {
            1 -> "The Awakening of Ancient Starlight"
            2 -> "A Glimpse Beyond the Mortal Veil"
            3 -> "The Trial of Obsidian Shadows"
            4 -> "Riddle of the Celestial Archive"
            5 -> "Echoes of the Forgotten Emperor"
            6 -> "Runes of the Golden Phoenix"
            7 -> "Whispers in the Astral Wind"
            8 -> "The Sovereign’s Bloodline"
            9 -> "Shattered Horizons"
            10 -> "Dawn of the Ascendant Monarch"
            else -> "Path of Destiny Part $chNum"
        }
    }
}
