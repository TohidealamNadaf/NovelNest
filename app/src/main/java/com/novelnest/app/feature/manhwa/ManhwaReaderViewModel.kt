package com.novelnest.app.feature.manhwa

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.bookmark.BookmarkManager
import com.novelnest.app.data.model.*
import com.novelnest.app.data.progress.ReadingProgressManager
import com.novelnest.app.data.remote.asura.AsuraException
import com.novelnest.app.data.repository.NovelRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ManhwaFailureKind { NETWORK, BLOCKED, LOCKED, ACCESS_REQUIRED, NOT_FOUND, RATE_LIMITED, FORMAT }
data class ManhwaReaderFailure(val kind: ManhwaFailureKind, val message: String, val unlockTime: String? = null)
data class ManhwaReaderUiState(
    val details: ManhwaDetails? = null,
    val currentChapterIndex: Int = -1,
    val content: ManhwaChapterContent? = null,
    val isLoading: Boolean = true,
    val isChapterLoading: Boolean = false,
    val error: ManhwaReaderFailure? = null,
    val currentPageIndex: Int = 0,
    val pageOffsetFraction: Float = 0f,
    val isBookmarked: Boolean = false,
    val readChapterIds: Set<String> = emptySet()
) {
    val isEmpty get() = details != null && details.chapters.isEmpty() && !isLoading
    val canPrevious get() = !isLoading && !isChapterLoading && currentChapterIndex > 0
    val canNext get() = !isLoading && !isChapterLoading && currentChapterIndex in 0 until (details?.chapters?.lastIndex ?: -1)
}

/** UI-free image-reader owner. The future screen renders pages lazily; this never decodes them. */
class ManhwaReaderViewModel(
    private val novelId: String,
    private val repository: NovelRepository,
    private val bookmarkManager: BookmarkManager? = null,
    private val progressManager: ReadingProgressManager? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val initialChapterIndex: Int = -1
) : ViewModel() {
    private val mutable = MutableStateFlow(ManhwaReaderUiState())
    val uiState = mutable.asStateFlow()
    private var detailsJob: Job? = null
    private var chapterJob: Job? = null
    private var generation = 0L
    private var chapterGeneration = 0L
    private var catalogFailure = false

    init {
        refreshCatalog(false)
        bookmarkManager?.let { manager -> viewModelScope.launch {
            manager.bookmarks.collect {
                mutable.update { it.copy(isBookmarked = manager.isBookmarked(mutable.value.details?.novel?.id ?: novelId)) }
            }
        } }
        progressManager?.let { pm -> viewModelScope.launch {
            combine(mutable.map { it.details?.novel?.id ?: novelId }.distinctUntilChanged(), pm.progressMap) { id, map ->
                map[id]?.readChapterIds ?: emptySet()
            }.distinctUntilChanged().collect { readIds ->
                mutable.update { it.copy(readChapterIds = readIds) }
            }
        } }
    }

    fun refreshCatalog(forceRefresh: Boolean = true) {
        detailsJob?.cancel()
        chapterJob?.cancel()
        val epoch = ++generation
        ++chapterGeneration
        catalogFailure = false
        mutable.update { it.copy(isLoading = true, isChapterLoading = false, error = null) }
        detailsJob = viewModelScope.launch {
            try {
                val local = repository.getNovelById(novelId) ?: bookmarkManager?.getBookmark(novelId)
                val result = repository.getManhwaDetails(NovelSourceType.ASURA_SCANS, local?.sourceUrl?.ifBlank { novelId } ?: novelId, forceRefresh)
                progressManager?.awaitLoaded()
                if (generation != epoch) return@launch
                result.fold(onSuccess = { details ->
                    val storedId = savedStateHandle.get<String>("manhwa.chapter")
                    val progress = progressManager?.getProgress(details.novel.id)
                    val restored = details.chapters.indexOfFirst { it.id == storedId }
                    val lastRead = details.chapters.indexOfFirst { it.id == progress?.lastReadChapterId }
                    val index = when {
                        restored >= 0 -> restored
                        initialChapterIndex >= 0 -> initialChapterIndex
                        lastRead >= 0 -> lastRead
                        else -> 0
                    }.coerceIn(0, details.chapters.lastIndex.coerceAtLeast(0))
                    mutable.update { it.copy(details = details, isLoading = false, currentChapterIndex = -1, content = null,
                        isBookmarked = bookmarkManager?.isBookmarked(details.novel.id) == true) }
                    if (details.chapters.isNotEmpty()) selectChapter(index, forceRefresh)
                }, onFailure = { error -> catalogFailure = true; mutable.update { it.copy(isLoading = false, error = failure(error)) } })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (generation == epoch) { catalogFailure = true; mutable.update { it.copy(isLoading = false, error = failure(error)) } } }
        }
    }

    fun selectChapter(index: Int, forceRefresh: Boolean = false) {
        val details = mutable.value.details ?: return
        val chapter = details.chapters.getOrNull(index) ?: return
        if (mutable.value.isLoading) return
        if (!forceRefresh && mutable.value.currentChapterIndex == index && (mutable.value.isChapterLoading || mutable.value.content != null)) return
        chapterJob?.cancel()
        catalogFailure = false
        val epoch = ++chapterGeneration
        val restored = savedStateHandle.get<String>("manhwa.chapter") == chapter.id
        if (!restored) {
            savedStateHandle["manhwa.page"] = 0
            savedStateHandle["manhwa.offset"] = 0f
        }
        savedStateHandle["manhwa.chapter"] = chapter.id
        mutable.update { it.copy(currentChapterIndex = index, content = null, isChapterLoading = true, error = null,
            currentPageIndex = 0, pageOffsetFraction = 0f) }
        chapterJob = viewModelScope.launch {
            try {
                val result = repository.getManhwaChapter(NovelSourceType.ASURA_SCANS, chapter.url, forceRefresh)
                if (chapterGeneration != epoch) return@launch
                result.fold(onSuccess = { content ->
                    if (content.chapter.id != chapter.id || content.pages.isEmpty()) {
                        mutable.update { it.copy(isChapterLoading = false, error = failure(AsuraException.Parse("Wrong or empty image chapter"))) }
                    } else {
                        val persistent = progressManager?.recordManhwaChapterOpened(details.novel, chapter, index, content.pages.size)
                        val page = (if (restored) savedStateHandle.get<Int>("manhwa.page") ?: persistent?.pageIndex ?: 0
                            else persistent?.pageIndex ?: 0).coerceIn(content.pages.indices)
                        val fraction = (if (restored) savedStateHandle.get<Float>("manhwa.offset") ?: persistent?.offsetFraction ?: 0f
                            else persistent?.offsetFraction ?: 0f).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
                        savedStateHandle["manhwa.page"] = page
                        savedStateHandle["manhwa.offset"] = fraction
                        mutable.update { it.copy(content = content, isChapterLoading = false, currentPageIndex = page, pageOffsetFraction = fraction) }
                        progressManager?.saveManhwaPagePosition(details.novel.id, chapter.id, page, fraction, content.pages.size)
                    }
                }, onFailure = { error -> mutable.update { it.copy(isChapterLoading = false, error = failure(error)) } })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (chapterGeneration == epoch) mutable.update { it.copy(isChapterLoading = false, error = failure(error)) } }
        }
    }

    fun nextChapter() { if (mutable.value.canNext) selectChapter(mutable.value.currentChapterIndex + 1) }
    fun previousChapter() { if (mutable.value.canPrevious) selectChapter(mutable.value.currentChapterIndex - 1) }
    fun retryChapter() { selectChapter(mutable.value.currentChapterIndex, true) }
    fun retry() { if (catalogFailure || mutable.value.details == null) refreshCatalog(true) else retryChapter() }
    fun toggleBookmark() {
        val novel = mutable.value.details?.novel ?: return
        val bookmarked = bookmarkManager?.toggleBookmark(novel) ?: return
        mutable.update { it.copy(isBookmarked = bookmarked) }
    }

    /** Call on idle/page change, with the chapter ID captured by the displayed content. */
    fun savePagePosition(chapterId: String, pageIndex: Int, offsetFraction: Float = 0f) {
        val content = mutable.value.content ?: return
        if (content.chapter.id != chapterId || pageIndex !in content.pages.indices || !offsetFraction.isFinite()) return
        val fraction = offsetFraction.coerceIn(0f, 1f)
        if (mutable.value.currentPageIndex == pageIndex && mutable.value.pageOffsetFraction == fraction) return
        savedStateHandle["manhwa.page"] = pageIndex
        savedStateHandle["manhwa.offset"] = fraction
        mutable.update { it.copy(currentPageIndex = pageIndex, pageOffsetFraction = fraction) }
        mutable.value.details?.novel?.id?.let { id ->
            progressManager?.saveManhwaPagePosition(id, chapterId, pageIndex, fraction, content.pages.size)
        }
    }
    /** Call only after the last image has loaded and the user has actually reached its end. */
    fun markChapterFinished(chapterId: String) {
        val state = mutable.value
        val content = state.content ?: return
        val novel = state.details?.novel ?: return
        if (content.chapter.id != chapterId) return
        progressManager?.markManhwaChapterCompleted(novel.id, chapterId)
    }

    private fun failure(error: Throwable) = ManhwaReaderFailure(when (error) {
        is AsuraException.Network -> ManhwaFailureKind.NETWORK
        is AsuraException.Blocked -> ManhwaFailureKind.BLOCKED
        is AsuraException.Locked -> ManhwaFailureKind.LOCKED
        is AsuraException.AccessRequired -> ManhwaFailureKind.ACCESS_REQUIRED
        is AsuraException.NotFound -> ManhwaFailureKind.NOT_FOUND
        is AsuraException.Http -> if (error.status == 429) ManhwaFailureKind.RATE_LIMITED else ManhwaFailureKind.NETWORK
        else -> ManhwaFailureKind.FORMAT
    }, error.message ?: "Unable to load this manhwa. Retry later.", (error as? AsuraException.Locked)?.unlockTime)
}
