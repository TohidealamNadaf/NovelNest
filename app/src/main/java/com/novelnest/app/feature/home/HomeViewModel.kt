package com.novelnest.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.bookmark.BookmarkManager
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.preferences.NavigationPreferences
import com.novelnest.app.data.progress.NovelProgress
import com.novelnest.app.data.progress.ReadingProgressManager
import com.novelnest.app.data.repository.NovelRepository
import com.novelnest.app.data.remote.freewebnovel.FreeWebNovelUrls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Home resolves history from bookmarks and local metadata; it never requests source feeds. */
class HomeViewModel(
    private val bookmarkManager: BookmarkManager,
    progressManager: ReadingProgressManager? = null,
    private val repository: NovelRepository? = null,
    private val navigationPreferences: NavigationPreferences? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState(
        selectedSource = navigationPreferences?.lastSource ?: NovelSourceType.NOVEL_PHOENIX,
        bookmarkedNovels = bookmarkManager.bookmarks.value,
        progress = progressManager?.progressMap?.value.orEmpty()
    ))
    val uiState = _uiState.asStateFlow()
    private var historyMetadata: Map<String, Novel> = emptyMap()

    init {
        viewModelScope.launch {
            combine(
                bookmarkManager.bookmarks,
                progressManager?.progressMap ?: MutableStateFlow<Map<String, NovelProgress>>(emptyMap())
            ) { bookmarks, progress -> bookmarks to progress }
                .collectLatest { (bookmarks, progress) ->
                    _uiState.update { it.copy(bookmarkedNovels = bookmarks, progress = progress) }
                    val byId = bookmarks.associateBy { it.id }
                    val recent = progress.values.sortedByDescending { it.lastReadTimestamp }
                        .groupBy { entry ->
                            byId[entry.novelId]?.source ?: historyMetadata[entry.novelId]?.source ?: sourceFor(entry.novelId)
                        }.values.flatMap { it.take(10) }
                    val resolved = recent.map { entry ->
                        val novel = byId[entry.novelId] ?: historyMetadata[entry.novelId]
                            ?: localNovel(entry.novelId) ?: fallbackNovel(entry.novelId)
                        HomeReadingEntry(novel, entry)
                    }
                    // At most ten resolved history records per source, not a growing novel cache.
                    historyMetadata = resolved.associate { it.progress.novelId to it.novel }
                    _uiState.update { it.copy(readingHistory = resolved) }
                }
        }
    }

    private suspend fun localNovel(id: String): Novel? = try {
        repository?.getNovelById(id)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun sourceFor(id: String) = when {
        id.startsWith("fwn-") -> NovelSourceType.FREE_WEB_NOVEL
        id.startsWith("asura-") -> NovelSourceType.ASURA_SCANS
        else -> NovelSourceType.NOVEL_PHOENIX
    }

    private fun fallbackNovel(id: String): Novel {
        val source = sourceFor(id)
        val slug = id.removePrefix("np-").removePrefix("fwn-").removePrefix("asura-")
        val url = if (source == NovelSourceType.FREE_WEB_NOVEL)
            runCatching { FreeWebNovelUrls.novelUrl(id) }.getOrDefault("")
        else if (source == NovelSourceType.ASURA_SCANS)
            runCatching { com.novelnest.app.data.remote.asura.AsuraUrls.seriesUrl(id) }.getOrDefault("")
        else "${source.baseUrl}/novel/$slug"
        return Novel(id, slug.split('-').joinToString(" ") { it.replaceFirstChar(Char::titlecase) },
            "", source, url, latestChapter = "")
    }

    fun selectSource(source: NovelSourceType) {
        navigationPreferences?.selectSource(source)
        if (_uiState.value.selectedSource == source) return
        _uiState.update { it.copy(selectedSource = source) }
    }
    fun removeBookmark(novelId: String) = bookmarkManager.removeBookmark(novelId)
}
