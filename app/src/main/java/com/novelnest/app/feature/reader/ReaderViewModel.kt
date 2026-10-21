package com.novelnest.app.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.preferences.ReaderPreferencesManager
import com.novelnest.app.data.preferences.ReaderSettings
import com.novelnest.app.data.preferences.ReaderTheme
import com.novelnest.app.data.repository.NovelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Chapter(
    val id: String,
    val title: String,
    val paragraphs: List<String>
)

data class ReaderUiState(
    val novel: Novel? = null,
    val chapters: List<Chapter> = emptyList(),
    val currentChapterIndex: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isChromeVisible: Boolean = true,
    val showQuickSettingsSheet: Boolean = false
)

class ReaderViewModel(
    private val novelId: String,
    private val repository: NovelRepository,
    private val preferencesManager: ReaderPreferencesManager
) : ViewModel() {

    val readerSettings: StateFlow<ReaderSettings> = preferencesManager.readerSettings

    private val _uiState = MutableStateFlow(ReaderUiState(isLoading = true))
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

    fun nextChapter() {
        val state = _uiState.value
        if (state.currentChapterIndex < state.chapters.size - 1) {
            _uiState.update { it.copy(currentChapterIndex = it.currentChapterIndex + 1) }
        }
    }

    fun prevChapter() {
        val state = _uiState.value
        if (state.currentChapterIndex > 0) {
            _uiState.update { it.copy(currentChapterIndex = it.currentChapterIndex - 1) }
        }
    }

    fun selectChapter(index: Int) {
        if (index in _uiState.value.chapters.indices) {
            _uiState.update { it.copy(currentChapterIndex = index) }
        }
    }

    fun setFontSize(size: Float) = preferencesManager.updateFontSize(size)
    fun setTheme(theme: ReaderTheme) = preferencesManager.updateTheme(theme)
    fun setLineHeight(multiplier: Float) = preferencesManager.updateLineHeight(multiplier)

    private fun loadNovelAndChapters() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val formattedTitle = if (novelId.isNotBlank() && novelId != "sample") {
                novelId.replace("-", " ")
                    .split(" ")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
            } else {
                "The Whispering Sanctuary"
            }
            val fallbackNovel = Novel(
                id = novelId,
                title = formattedTitle,
                coverUrl = "",
                author = "Author",
                source = NovelSourceType.NOVEL_PHOENIX,
                sourceUrl = "",
                latestChapter = "Chapter 1",
                synopsis = "A journey of cultivation, mysteries, and perseverance."
            )

            val generatedChapters = generateSampleChapters(fallbackNovel.title)
            _uiState.update {
                it.copy(
                    novel = fallbackNovel,
                    chapters = generatedChapters,
                    isLoading = false,
                    currentChapterIndex = 0
                )
            }
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
