package com.novelnest.app.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.bookmark.BookmarkManager
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.preferences.ReaderPreferencesManager
import com.novelnest.app.data.preferences.ReaderSettings
import com.novelnest.app.data.preferences.ReaderTheme
import com.novelnest.app.data.remote.SeedData
import com.novelnest.app.data.repository.NovelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Chapter(
    val id: String,
    val title: String,
    val paragraphs: List<String> = emptyList(),
    val url: String = "",
    val prevUrl: String? = null,
    val nextUrl: String? = null
)

data class ReaderUiState(
    val novel: Novel? = null,
    val chapters: List<Chapter> = emptyList(),
    val currentChapterIndex: Int = 0,
    val isLoading: Boolean = false,
    val isChapterLoading: Boolean = false,
    val errorMessage: String? = null,
    val isChromeVisible: Boolean = true,
    val showQuickSettingsSheet: Boolean = false,
    val isBookmarked: Boolean = false
)

class ReaderViewModel(
    private val novelId: String,
    private val repository: NovelRepository,
    private val preferencesManager: ReaderPreferencesManager,
    private val bookmarkManager: BookmarkManager? = null
) : ViewModel() {

    val readerSettings: StateFlow<ReaderSettings> = preferencesManager.readerSettings

    private val _uiState = MutableStateFlow(
        ReaderUiState(
            isLoading = true,
            isBookmarked = bookmarkManager?.isBookmarked(novelId) ?: false
        )
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        loadNovelAndChapters()
    }

    fun toggleChrome() {
        _uiState.update { it.copy(isChromeVisible = !it.isChromeVisible) }
    }

    fun showQuickSettings(show: Boolean) {
        _uiState.update { it.copy(showQuickSettingsSheet = show) }
    }

    fun toggleBookmark() {
        val currentNovel = _uiState.value.novel ?: return
        val newStatus = bookmarkManager?.toggleBookmark(currentNovel) ?: !_uiState.value.isBookmarked
        _uiState.update { it.copy(isBookmarked = newStatus) }
    }

    fun resyncChapter() {
        val state = _uiState.value
        val currentChapter = state.chapters.getOrNull(state.currentChapterIndex) ?: return
        loadChapterContent(state.currentChapterIndex, currentChapter, forceRefresh = true)
    }

    fun nextChapter() {
        val state = _uiState.value
        if (state.currentChapterIndex < state.chapters.size - 1) {
            val nextIndex = state.currentChapterIndex + 1
            _uiState.update { it.copy(currentChapterIndex = nextIndex) }
            val nextChapter = state.chapters[nextIndex]
            if (nextChapter.paragraphs.isEmpty()) {
                loadChapterContent(nextIndex, nextChapter, forceRefresh = false)
            }
        }
    }

    fun prevChapter() {
        val state = _uiState.value
        if (state.currentChapterIndex > 0) {
            val prevIndex = state.currentChapterIndex - 1
            _uiState.update { it.copy(currentChapterIndex = prevIndex) }
            val prevChapter = state.chapters[prevIndex]
            if (prevChapter.paragraphs.isEmpty()) {
                loadChapterContent(prevIndex, prevChapter, forceRefresh = false)
            }
        }
    }

    fun selectChapter(index: Int) {
        val state = _uiState.value
        if (index in state.chapters.indices) {
            _uiState.update { it.copy(currentChapterIndex = index) }
            val selected = state.chapters[index]
            if (selected.paragraphs.isEmpty()) {
                loadChapterContent(index, selected, forceRefresh = false)
            }
        }
    }

    fun setFontSize(size: Float) = preferencesManager.updateFontSize(size)
    fun setTheme(theme: ReaderTheme) = preferencesManager.updateTheme(theme)
    fun setLineHeight(multiplier: Float) = preferencesManager.updateLineHeight(multiplier)

    private fun loadNovelAndChapters() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // 1. Resolve novel model
            val resolvedNovel = SeedData.findNovelById(novelId) ?: run {
                val formattedTitle = if (novelId.isNotBlank() && novelId != "sample") {
                    novelId.removePrefix("np-").removePrefix("fwn-").removePrefix("asura-")
                        .replace("-", " ")
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
                } else {
                    "The Whispering Sanctuary"
                }
                val source = when {
                    novelId.startsWith("fwn-") -> NovelSourceType.FREE_WEB_NOVEL
                    novelId.startsWith("asura-") -> NovelSourceType.ASURA_SCANS
                    else -> NovelSourceType.NOVEL_PHOENIX
                }
                val slug = novelId.removePrefix("np-").removePrefix("fwn-").removePrefix("asura-")
                Novel(
                    id = novelId,
                    title = formattedTitle,
                    coverUrl = "",
                    author = "Author",
                    source = source,
                    sourceUrl = "${source.baseUrl}/novel/$slug",
                    latestChapter = "Chapter 1",
                    synopsis = "A journey of cultivation, mysteries, and perseverance."
                )
            }

            val isBookmarked = bookmarkManager?.isBookmarked(resolvedNovel.id) ?: false

            _uiState.update {
                it.copy(
                    novel = resolvedNovel,
                    isBookmarked = isBookmarked
                )
            }

            // 2. Fetch chapter list via repository
            val source = resolvedNovel.source
            val slugOrUrl = resolvedNovel.sourceUrl.ifBlank { novelId }
            val chaptersResult = repository.getChapters(source, slugOrUrl, forceRefresh = false)

            val loadedChapters = chaptersResult.getOrNull()?.ifEmpty { null }
                ?: generateSampleChapters(resolvedNovel.title)

            _uiState.update {
                it.copy(
                    chapters = loadedChapters,
                    isLoading = false,
                    currentChapterIndex = 0
                )
            }

            // 3. Load initial chapter content
            if (loadedChapters.isNotEmpty()) {
                val firstChapter = loadedChapters[0]
                if (firstChapter.paragraphs.isEmpty()) {
                    loadChapterContent(0, firstChapter, forceRefresh = false)
                }
            }
        }
    }

    private fun loadChapterContent(chapterIndex: Int, chapter: Chapter, forceRefresh: Boolean) {
        viewModelScope.launch {
            val novel = _uiState.value.novel ?: return@launch
            _uiState.update { it.copy(isChapterLoading = true, errorMessage = null) }

            val contentResult = repository.getChapterContent(
                source = novel.source,
                chapterUrlOrId = chapter.url.ifBlank { chapter.id },
                forceRefresh = forceRefresh
            )

            contentResult.fold(
                onSuccess = { liveChapter ->
                    _uiState.update { state ->
                        val updatedList = state.chapters.toMutableList()
                        if (chapterIndex in updatedList.indices) {
                            updatedList[chapterIndex] = liveChapter.copy(
                                id = chapter.id,
                                title = liveChapter.title.ifBlank { chapter.title }
                            )
                        }
                        state.copy(
                            chapters = updatedList,
                            isChapterLoading = false,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { state ->
                        val updatedList = state.chapters.toMutableList()
                        if (chapterIndex in updatedList.indices && updatedList[chapterIndex].paragraphs.isEmpty()) {
                            // Fallback to sample paragraphs if live fetch failed
                            val sample = generateSampleChapters(novel.title).getOrNull(chapterIndex)
                                ?: generateSampleChapters(novel.title).first()
                            updatedList[chapterIndex] = updatedList[chapterIndex].copy(
                                paragraphs = sample.paragraphs
                            )
                        }
                        state.copy(
                            chapters = updatedList,
                            isChapterLoading = false,
                            errorMessage = "Failed to fetch live chapter (${err.message ?: "Network error"}). Showing offline fallback."
                        )
                    }
                }
            )
        }
    }

    private fun generateSampleChapters(novelTitle: String): List<Chapter> {
        return listOf(
            Chapter(
                id = "ch-1",
                title = "Chapter 1: The Whispering Sanctuary",
                paragraphs = listOf(
                    "The night was still, save for the faint hum of ancient pages turning under the silver lantern. Every word carried the weight of forgotten epochs, recorded by scribes whose names had long surrendered to dust.",
                    "Sunny sat cross-legged near the arched stone window, feeling the cool breath of twilight brushing his skin. Beyond the parapet, the sea of obsidian mist rolled across the valley like a sleeping leviathan.",
                    "\"Knowledge is not a shield,\" his mentor had once cautioned him. \"It is a blade that cuts both ways, sharper than any divine forging. If you unravel the first truth, you must be prepared to face the abyss behind it.\"",
                    "He unrolled the scroll further. The runes glimmered with a soft golden luminescence, pulsating in sync with his own core. The seal was weakening. Whatever had been bound beneath the sunken citadel three thousand years ago was beginning to stir.",
                    "With a measured breath, he committed the initial verse to memory. In this quiet refuge, the relentless storms of the mortal world faded into distant echoes, leaving only the rhythm of his determination."
                )
            ),
            Chapter(
                id = "ch-2",
                title = "Chapter 2: Embers in the Mist",
                paragraphs = listOf(
                    "Dawn did not break so much as bleed into the gray horizon. A lone raven perched on the weathered gargoyle, its obsidian eyes reflecting the faint glimmer of morning light.",
                    "Sunny descended the winding stone staircase into the lower vaults. The temperature dropped with each flight, the air tasting of ozone and old limestone.",
                    "Here lay the archives of the Phoenix Clan. Shelf after shelf of leather-bound grimoires stretched into the darkness, undisturbed for centuries until now.",
                    "A soft click resonated beneath his boot. Before he could retreat, ancient arrays carved into the flagstones flared to life with vibrant azure fire."
                )
            ),
            Chapter(
                id = "ch-3",
                title = "Chapter 3: The Awakening Rune",
                paragraphs = listOf(
                    "The azure flames did not burn; they hummed with pure spiritual resonance. Sunny raised his palm, allowing the tendrils of energy to weave through his fingertips.",
                    "A phantom bell tolled in the depths of his consciousness. The runes converged into a singular glyph—the Mark of Ascension.",
                    "\"So it begins,\" he whispered, a calm smile gracing his lips as the power settled into his soul sea."
                )
            )
        )
    }
}
