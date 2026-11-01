package com.novelnest.app.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelIdentity
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.remote.SeedData
import com.novelnest.app.data.remote.freewebnovel.FreeWebNovelUrls
import com.novelnest.app.data.repository.NovelRepository
import com.novelnest.app.feature.reader.Chapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NovelDetailUiState(
    val novel: Novel? = null,
    val chapters: List<Chapter> = emptyList(),
    val filteredChapters: List<Chapter> = emptyList(),
    val isAscending: Boolean = true,
    val chapterQuery: String = "",
    val isBookmarked: Boolean = false,
    val readingProgress: com.novelnest.app.data.progress.NovelProgress? = null,
    val readChapterIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isChaptersLoading: Boolean = false,
    val isSynopsisExpanded: Boolean = false,
    val errorMessage: String? = null,
    val chaptersErrorMessage: String? = null,
    val manhwaDetails: com.novelnest.app.data.model.ManhwaDetails? = null
)

class NovelDetailViewModel(
    private val novelId: String,
    private val repository: NovelRepository,
    private val bookmarkManager: com.novelnest.app.data.bookmark.BookmarkManager? = null,
    private val readingProgressManager: com.novelnest.app.data.progress.ReadingProgressManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        NovelDetailUiState(
            isBookmarked = bookmarkManager?.isBookmarked(novelId) ?: false,
            readingProgress = readingProgressManager?.getProgress(novelId),
            readChapterIds = readingProgressManager?.getProgress(novelId)?.readChapterIds ?: emptySet()
        )
    )
    val uiState: StateFlow<NovelDetailUiState> = _uiState.asStateFlow()
    private var chapterJob: Job? = null
    private var detailJob: Job? = null
    private val resolvedNovelId = _uiState.map { it.novel }.distinctUntilChanged().map { novel ->
        novel?.let(NovelIdentity::normalize)?.id ?: novelId
    }.distinctUntilChanged()

    init {
        loadNovelDetails()
        observeReadingProgress()
        observeBookmarks()
    }

    private fun observeReadingProgress() {
        readingProgressManager?.let { pm ->
            viewModelScope.launch {
                combine(resolvedNovelId, pm.progressMap) { id, map ->
                    // Old notifications/bookmarks may still contain a pre-canonical ID.
                    val canonical = map[id]
                    val legacy = map[novelId]
                    when {
                        canonical == null -> legacy
                        legacy == null || id == novelId -> canonical
                        else -> (if (canonical.lastReadTimestamp >= legacy.lastReadTimestamp) canonical else legacy)
                            .copy(novelId = id, readChapterIds = canonical.readChapterIds + legacy.readChapterIds)
                    }
                }.distinctUntilChanged().collect { progress ->
                    _uiState.update { current ->
                        current.copy(
                            readingProgress = progress,
                            readChapterIds = progress?.readChapterIds ?: emptySet()
                        )
                    }
                }
            }
        }
    }

    private fun observeBookmarks() {
        bookmarkManager?.let { manager ->
            viewModelScope.launch {
                combine(resolvedNovelId, manager.bookmarks) { id, _ ->
                    manager.isBookmarked(id) || manager.isBookmarked(novelId)
                }.distinctUntilChanged().collect { bookmarked ->
                    _uiState.update { it.copy(isBookmarked = bookmarked) }
                }
            }
        }
    }

    fun toggleBookmark() {
        val currentNovel = _uiState.value.novel ?: return
        val newStatus = bookmarkManager?.toggleBookmark(currentNovel) ?: !_uiState.value.isBookmarked
        _uiState.update { it.copy(isBookmarked = newStatus) }
    }

    fun toggleSynopsis() {
        _uiState.update { it.copy(isSynopsisExpanded = !it.isSynopsisExpanded) }
    }

    fun toggleSortOrder() {
        _uiState.update { current ->
            val newAscending = !current.isAscending
            val sorted = current.chapters.reversed()
            current.copy(
                isAscending = newAscending,
                chapters = sorted,
                filteredChapters = filterChapters(sorted, current.chapterQuery)
            )
        }
    }

    fun readerChapterIndex(chapterId: String): Int {
        val state = _uiState.value
        val chapters = if (state.isAscending) state.chapters else state.chapters.asReversed()
        return chapters.indexOfFirst { it.id == chapterId }.coerceAtLeast(0)
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
        // Reset circuit breaker so manual retry always makes a fresh attempt
        repository.resetSourceCircuitBreaker(_uiState.value.novel?.source ?: NovelSourceType.NOVEL_PHOENIX)
        loadChapters(forceRefresh = true)
    }

    private fun filterChapters(chapters: List<Chapter>, query: String): List<Chapter> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return chapters
        return chapters.filter { it.title.lowercase().contains(q) || it.id.lowercase().contains(q) }
    }

    fun retryLoadDetails() {
        // Reset circuit breaker on explicit user retry
        repository.resetSourceCircuitBreaker(_uiState.value.novel?.source ?: NovelSourceType.NOVEL_PHOENIX)
        loadNovelDetails()
    }

    /**
     * Constructs a reliable sourceUrl for the novel, ensuring it's always a valid full URL.
     * This handles cases where the novel was loaded from bookmarks/cache with incomplete data or legacy IDs.
     */
    private fun resolveSourceUrl(novel: Novel): String {
        if (novel.source == NovelSourceType.ASURA_SCANS) {
            return com.novelnest.app.data.remote.asura.AsuraUrls.seriesUrl(novel.sourceUrl.ifBlank { novel.id })
        }
        if (novel.source == NovelSourceType.FREE_WEB_NOVEL) {
            return FreeWebNovelUrls.novelUrl(novel.sourceUrl.ifBlank { novel.id })
        }
        // 1. If sourceUrl is already a valid URL (contains "/novel/"), use it
        if (novel.sourceUrl.isNotBlank() && novel.sourceUrl.contains("/novel/")) {
            val url = novel.sourceUrl.trim()
            return if (url.startsWith("http")) url else "${novel.source.baseUrl}${if (url.startsWith("/")) "" else "/"}$url"
        }

        // 2. Map legacy numeric IDs (np-1..np-10) or extract clean slug
        val mappedSlug = mapLegacyIdToSlug(novelId) ?: mapLegacyIdToSlug(novel.id)
        val slug = mappedSlug ?: novelId
            .removePrefix("np-")
            .removePrefix("fwn-")
            .removePrefix("asura-")
            .trimEnd('/')
            .substringAfterLast('/')

        val source = novel.source
        return "${source.baseUrl}/novel/$slug"
    }

    private fun mapLegacyIdToSlug(id: String): String? = when (id) {
        "np-1", "1" -> "shadow-slave"
        "np-2", "2" -> "full-star-player-returns-as-a-godslayer"
        "np-3", "3" -> "genius-martial-arts-trainer"
        "np-4", "4" -> "the-mine-lord-a-dwarven-survival-base-builder"
        "np-5", "5" -> "lord-of-the-mysteries"
        "np-6", "6" -> "reverend-insanity"
        "np-7", "7" -> "collecting-powerful-girlfriends-my-invincible-harem"
        "np-8", "8" -> "tyrant-no-i-am-the-villain"
        "np-9", "9" -> "the-beginning-after-the-end"
        "np-10", "10" -> "mother-of-learning"
        else -> null
    }

    private fun loadNovelDetails() {
        detailJob?.cancel()
        detailJob = loadWithErrorHandling(onFailure = { error ->
            _uiState.update {
                it.copy(isLoading = false,
                    errorMessage = "Unable to load details (${formatFriendlyError(error)}). Tap to retry.")
            }
        }) {
            _uiState.update { it.copy(isLoading = it.novel == null, errorMessage = null) }

            // 1. Initial resolution: Check repository/DB first, then BookmarkManager, then SeedData, then synthetic shell
            val initialNovel = repository.getNovelById(novelId)
                ?: bookmarkManager?.getBookmark(novelId)
                ?: SeedData.findNovelById(novelId)?.takeUnless { it.source == NovelSourceType.ASURA_SCANS }
                ?: run {
                if (novelId.startsWith("asura-")) {
                    val slug = com.novelnest.app.data.remote.asura.AsuraUrls.slug(novelId)
                    return@run Novel(novelId, slug.replace('-', ' '), "", NovelSourceType.ASURA_SCANS,
                        com.novelnest.app.data.remote.asura.AsuraUrls.seriesUrl(slug), latestChapter = "", status = "Unknown")
                }
                val formattedTitle = if (novelId.isNotBlank() && novelId != "sample") {
                    novelId.removePrefix("np-")
                        .replace("-", " ")
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
                } else {
                    "The Whispering Sanctuary"
                }
                val mappedSlug = mapLegacyIdToSlug(novelId)
                val slug = mappedSlug ?: novelId.removePrefix("np-").removePrefix("fwn-").removePrefix("asura-")
                val src = when {
                    novelId.startsWith("asura-") -> NovelSourceType.ASURA_SCANS
                    novelId.startsWith("fwn-") -> NovelSourceType.FREE_WEB_NOVEL
                    else -> NovelSourceType.NOVEL_PHOENIX
                }
                Novel(
                    id = novelId,
                    title = formattedTitle,
                    coverUrl = "https://novelphoenix.com/server-1/$slug.jpg",
                    author = "Author",
                    source = src,
                    sourceUrl = "${src.baseUrl}/novel/$slug",
                    rating = 4.8f,
                    latestChapter = "Loading...",
                    views = "",
                    status = "Ongoing",
                    genres = listOf("Fantasy", "Action"),
                    synopsis = ""
                )
            }

            val hasFullDetails = initialNovel.synopsis.isNotBlank()
            val bookmarked = bookmarkManager?.isBookmarked(novelId) == true || bookmarkManager?.isBookmarked(initialNovel.id) == true
            _uiState.update {
                it.copy(
                    novel = initialNovel,
                    isLoading = !hasFullDetails,
                    isBookmarked = bookmarked
                )
            }

            // Immediately kick off chapter fetching concurrently with details
            loadChapters(forceRefresh = false)

            // 2. Fetch live novel details from remote source
            val source = initialNovel.source
            val novelUrl = resolveSourceUrl(initialNovel)

            val detailResult = if (source == NovelSourceType.ASURA_SCANS) {
                repository.getManhwaDetails(source, novelUrl).map { manhwa ->
                    _uiState.update { it.copy(manhwaDetails = manhwa) }
                    manhwa.novel
                }
            } else {
                repository.getNovelDetails(source, novelUrl)
            }
            detailResult.fold(
                onSuccess = { liveNovel ->
                    // Ensure liveNovel has a proper sourceUrl
                    val novelWithUrl = if (liveNovel.sourceUrl.isBlank()) {
                        liveNovel.copy(sourceUrl = novelUrl)
                    } else {
                        liveNovel
                    }
                    if (bookmarkManager?.isBookmarked(initialNovel.id) == true) {
                        bookmarkManager.addBookmark(novelWithUrl)
                    }
                    _uiState.update {
                        it.copy(
                            novel = novelWithUrl,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { current ->
                        if (current.novel == null || current.novel.synopsis.isBlank()) {
                            val msg = formatFriendlyError(err)
                            current.copy(
                                isLoading = false,
                                errorMessage = "Unable to load details ($msg). Tap to retry."
                            )
                        } else {
                            current.copy(isLoading = false)
                        }
                    }
                }
            )
        }
    }

    private fun formatFriendlyError(err: Throwable): String {
        val msg = err.message.orEmpty()
        return when {
            msg.contains("Circuit breaker", ignoreCase = true) -> "Service temporarily paused due to rate limits"
            msg.contains("403") || msg.contains("429") || msg.contains("blocked", ignoreCase = true) -> "Source temporarily rate limited"
            msg.contains("404") || msg.contains("not found", ignoreCase = true) -> "Novel not found at source"
            msg.contains("timeout", ignoreCase = true) || msg.contains("timed out", ignoreCase = true) -> "Connection timed out"
            msg.isNotBlank() -> msg
            else -> "Network connection error"
        }
    }

    private fun loadChapters(forceRefresh: Boolean) {
        chapterJob?.cancel()
        chapterJob = loadWithErrorHandling(onFailure = { error ->
            showChapterError("Failed to fetch live chapters (${formatFriendlyError(error)}).")
        }) {
            val currentNovel = _uiState.value.novel
                ?: repository.getNovelById(novelId)
                ?: bookmarkManager?.getBookmark(novelId)
                ?: SeedData.findNovelById(novelId)
                ?: return@loadWithErrorHandling

            val source = currentNovel.source

            // Build a robust slug/URL for chapter fetching
            val slugOrUrl = resolveSourceUrl(currentNovel)

            _uiState.update { it.copy(isChaptersLoading = true, chaptersErrorMessage = null) }

            repository.getChaptersFlow(source, slugOrUrl, forceRefresh = forceRefresh).collect { result ->
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
                    } else if (_uiState.value.chapters.isEmpty()) {
                        showChapterError("No chapters available from source.")
                    } else {
                        _uiState.update { it.copy(isChaptersLoading = false) }
                    }
                }.onFailure { err ->
                    val friendlyMsg = formatFriendlyError(err)
                    if (_uiState.value.chapters.isEmpty()) {
                        showChapterError("Failed to fetch live chapters ($friendlyMsg).")
                    } else {
                        // Handled offline state: Keep existing chapters! Never clear them out!
                        _uiState.update { current ->
                            current.copy(
                                isChaptersLoading = false,
                                chaptersErrorMessage = "Offline / Showing cached chapters ($friendlyMsg)"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun showChapterError(reason: String) {
        _uiState.update { current ->
            current.copy(
                isChaptersLoading = false,
                chaptersErrorMessage = reason
            )
        }
    }

    private fun loadWithErrorHandling(onFailure: (Exception) -> Unit, load: suspend () -> Unit): Job =
        viewModelScope.launch {
            try {
                load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Cold notification opens lack the warm metadata cache. Storage or
                // unexpected source exceptions must reach Retry rather than the
                // process-wide uncaught coroutine handler. Retain displayed data.
                android.util.Log.w("NovelNestLaunch", "Novel loading failed: ${error.javaClass.simpleName}")
                onFailure(error)
            }
        }
}
