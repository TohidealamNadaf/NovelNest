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
    val isChaptersLoading: Boolean = false,
    val isSynopsisExpanded: Boolean = false,
    val errorMessage: String? = null,
    val chaptersErrorMessage: String? = null
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

    fun resyncChapters() {
        loadChapters(forceRefresh = true)
    }

    private fun filterChapters(chapters: List<Chapter>, query: String): List<Chapter> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return chapters
        return chapters.filter { it.title.lowercase().contains(q) || it.id.lowercase().contains(q) }
    }

    private fun loadNovelDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // 1. Initial fast resolution from SeedData
            val initialNovel = SeedData.findNovelById(novelId) ?: run {
                val formattedTitle = if (novelId.isNotBlank() && novelId != "sample") {
                    novelId.removePrefix("np-")
                        .replace("-", " ")
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
                } else {
                    "The Whispering Sanctuary"
                }
                val slug = novelId.removePrefix("np-")
                Novel(
                    id = novelId,
                    title = formattedTitle,
                    coverUrl = "https://novelphoenix.com/server-1/$slug.jpg",
                    author = "Author",
                    source = if (novelId.startsWith("asura-")) NovelSourceType.ASURA_SCANS else NovelSourceType.NOVEL_PHOENIX,
                    sourceUrl = "https://novelphoenix.com/novel/$slug",
                    rating = 4.8f,
                    latestChapter = "Loading...",
                    views = "",
                    status = "Ongoing",
                    genres = listOf("Fantasy", "Action"),
                    synopsis = ""
                )
            }

            _uiState.update {
                it.copy(
                    novel = initialNovel,
                    isLoading = false
                )
            }

            // 2. Fetch live novel details in background
            val source = initialNovel.source
            val novelUrl = initialNovel.sourceUrl.ifBlank {
                val slug = novelId.removePrefix("np-")
                "${source.baseUrl}/novel/$slug"
            }

            val detailResult = runCatching { repository.getNovelDetails(source, novelUrl) }.getOrNull()
            if (detailResult != null && detailResult.isSuccess) {
                val liveNovel = detailResult.getOrThrow()
                _uiState.update { it.copy(novel = liveNovel) }
            }

            // 3. Load chapter list
            loadChapters(forceRefresh = false)
        }
    }

    private fun loadChapters(forceRefresh: Boolean) {
        viewModelScope.launch {
            val currentNovel = _uiState.value.novel ?: return@launch
            val source = currentNovel.source
            val slugOrUrl = currentNovel.sourceUrl.ifBlank { novelId }

            _uiState.update { it.copy(isChaptersLoading = true, chaptersErrorMessage = null) }

            val result = repository.getChapters(source, slugOrUrl, forceRefresh = forceRefresh)
            result.onSuccess { liveChapters ->
                if (liveChapters.isNotEmpty()) {
                    _uiState.update { current ->
                        val sorted = if (current.isAscending) liveChapters else liveChapters.reversed()
                        current.copy(
                            chapters = sorted,
                            filteredChapters = filterChapters(sorted, current.chapterQuery),
                            isChaptersLoading = false,
                            chaptersErrorMessage = null
                        )
                    }
                } else {
                    // Empty live chapters: fallback to default chapters
                    fallbackToDefaultChapters("No chapters available from source.")
                }
            }.onFailure { err ->
                // Handled fallback: Keep or provide fallback chapters, but show retry error banner
                fallbackToDefaultChapters("Failed to fetch live chapters (${err.message ?: "Network error"}).")
            }
        }
    }

    private fun fallbackToDefaultChapters(reason: String) {
        val currentChapters = _uiState.value.chapters
        val chaptersToUse = if (currentChapters.isNotEmpty()) {
            currentChapters
        } else {
            val title = _uiState.value.novel?.title ?: "Novel"
            (1..40).map { chNum ->
                Chapter(
                    id = "ch-$chNum",
                    title = "Chapter $chNum: ${getChapterName(chNum, title)}",
                    paragraphs = emptyList()
                )
            }
        }

        _uiState.update { current ->
            current.copy(
                chapters = chaptersToUse,
                filteredChapters = filterChapters(chaptersToUse, current.chapterQuery),
                isChaptersLoading = false,
                chaptersErrorMessage = reason
            )
        }
    }

    private fun getChapterName(chNum: Int, novelTitle: String): String {
        return when (chNum) {
            1 -> "The Awakening"
            2 -> "Beyond the Mortal Veil"
            3 -> "Trial of Obsidian Shadows"
            4 -> "Celestial Archive"
            5 -> "Echoes of the Forgotten"
            else -> "Chapter $chNum"
        }
    }
}
