package com.novelnest.app.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.bookmark.BookmarkManager
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.preferences.ReaderFont
import com.novelnest.app.data.preferences.ReaderPreferencesManager
import com.novelnest.app.data.preferences.ReaderSettings
import com.novelnest.app.data.preferences.ReaderTheme
import com.novelnest.app.data.remote.SeedData
import com.novelnest.app.data.remote.freewebnovel.FreeWebNovelUrls
import com.novelnest.app.data.repository.NovelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import com.novelnest.app.data.ai.AiAction
import com.novelnest.app.data.ai.AiChapterInput
import com.novelnest.app.data.ai.ChapterAiRepository

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
    val isBookmarked: Boolean = false,
    val readChapterIds: Set<String> = emptySet()
)

class ReaderViewModel(
    private val novelId: String,
    private val repository: NovelRepository,
    private val preferencesManager: ReaderPreferencesManager,
    private val bookmarkManager: BookmarkManager? = null,
    private val readingProgressManager: com.novelnest.app.data.progress.ReadingProgressManager? = null,
    private val initialChapterIndex: Int = 0,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    aiRepository: ChapterAiRepository? = null,
    aiConfigurationRevision: StateFlow<Long>? = null,
    private val aiForeground: StateFlow<Boolean>? = null,
    private val studyRepository: com.novelnest.app.data.study.StudyRepository? = null
) : ViewModel() {

    val readerSettings: StateFlow<ReaderSettings> = preferencesManager.readerSettings

    private val _uiState = MutableStateFlow(
        ReaderUiState(
            isLoading = true,
            currentChapterIndex = (savedStateHandle.get<Int>("readerChapterIndex") ?: initialChapterIndex).coerceAtLeast(0),
            isBookmarked = bookmarkManager?.isBookmarked(novelId) ?: false,
            readChapterIds = readingProgressManager?.getProgress(novelId)?.readChapterIds ?: emptySet()
        )
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    private val prefetchedChapterIds = mutableSetOf<String>()
    private val aiController = aiRepository?.let { ReaderAiController(viewModelScope, it) }
    val aiState: StateFlow<ReaderAiState> = aiController?.state ?: MutableStateFlow(ReaderAiState()).asStateFlow()
    private val studyController = studyRepository?.let { ReaderStudyController(viewModelScope, it) }
    val studyState: StateFlow<ReaderStudyState> = studyController?.state ?: MutableStateFlow(ReaderStudyState()).asStateFlow()

    init {
        loadNovelAndChapters()
        observeReadingProgress()
        studyController?.let { controller -> viewModelScope.launch {
            uiState.map { state ->
                val novel = state.novel
                val chapter = state.chapters.getOrNull(state.currentChapterIndex)
                val key = if (novel != null && chapter != null)
                    com.novelnest.app.data.study.StudyChapterKey(novel.source.name, novel.id, chapter.id) else null
                key to state.showQuickSettingsSheet
            }.distinctUntilChanged().collect { (key, visible) -> controller.open(key, visible) }
        } }
        if (aiController != null) {
            viewModelScope.launch {
                uiState.map(::aiInput).distinctUntilChanged().collect { aiController.sourceChanged(it) }
            }
            aiConfigurationRevision?.let { revision -> viewModelScope.launch {
                revision.drop(1).collect { aiController.cancel() }
            } }
            aiForeground?.let { foreground -> viewModelScope.launch {
                foreground.collect { if (!it) aiController.cancel() }
            } }
        }
    }

    private fun aiInput(state: ReaderUiState): AiChapterInput? {
        if (state.isLoading || state.isChapterLoading) return null
        val novel = state.novel ?: return null
        val chapter = state.chapters.getOrNull(state.currentChapterIndex) ?: return null
        if (chapter.paragraphs.none { it.isNotBlank() }) return null
        return AiChapterInput(novel.source.name, novel.id, chapter.id, chapter.title, chapter.paragraphs)
    }

    fun requestAiSummary() = requestAi(AiAction.SUMMARY)
    fun requestAiRewrite() = requestAi(AiAction.REWRITE)
    private fun requestAi(action: AiAction) {
        if (aiForeground?.value == false) return
        aiController?.sourceChanged(aiInput(uiState.value))
        aiController?.request(action)
    }
    fun cancelAiRequest() { aiController?.cancel() }
    fun showAiContentVersion(version: ReaderContentVersion) { aiController?.showVersion(version) }
    fun displayedChapterParagraphs(chapter: Chapter): List<String> = aiState.value.paragraphs(chapter)
    fun saveChapterNote(id: String?, title: String, markdown: String) { studyController?.saveNote(id, title, markdown) }
    fun deleteChapterNote(id: String) { studyController?.deleteNote(id) }
    fun selectChapterNote(id: String?) { studyController?.selectNote(id) }
    fun addTermCollection(name: String) { studyController?.addCollection(name) }
    fun selectTermCollection(id: String?) { studyController?.selectCollection(id) }
    fun deleteTermCollection(id: String) {
        if (aiState.value.action == AiAction.EXTRACT && aiState.value.extractionCollectionId == id) aiController?.cancel()
        studyController?.deleteCollection(id)
    }
    fun dismissStudyMessage() { studyController?.clearMessage() }
    fun extractTerms(id: String) {
        if (aiForeground?.value == false || aiState.value.busy) return
        val input = aiInput(uiState.value) ?: return
        val key = com.novelnest.app.data.study.StudyChapterKey(input.source, input.novelId, input.chapterId)
        val collection = studyState.value.collections.firstOrNull { it.collection.id == id }?.collection ?: return
        if (collection.source != key.source || collection.novelId != key.novelId) return
        aiController?.sourceChanged(input)
        aiController?.extract(id, collection.target) { request, result ->
            try { studyRepository?.append(collection, request, result) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { throw com.novelnest.app.data.ai.AiException(com.novelnest.app.data.ai.AiErrorCode.STORAGE,
                if (error is IllegalArgumentException) error.message.orEmpty() else "Could not save extracted terms. Check device storage and try again. Earlier discoveries are kept.") }
            studyController?.message(key, if (result.extractedTerms.isEmpty()) "No matching terms found in this chapter. Saved terms are unchanged."
                else "Extraction saved. Earlier discoveries are kept.")
        }
    }

    private fun observeReadingProgress() {
        readingProgressManager?.let { pm ->
            viewModelScope.launch {
                pm.progressMap.collect { map ->
                    val progress = map[novelId]
                    _uiState.update { current ->
                        current.copy(readChapterIds = progress?.readChapterIds ?: emptySet())
                    }
                }
            }
        }
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
        aiController?.sourceChanged(null)
        // Reset circuit breaker when user manually resyncs
        val source = state.novel?.source ?: NovelSourceType.NOVEL_PHOENIX
        repository.resetSourceCircuitBreaker(source)
        loadChapterContent(state.currentChapterIndex, currentChapter, forceRefresh = true)
    }

    fun scrollPosition(chapterIndex: Int, contentKey: String = "original"): Pair<Int, Int> =
        if (contentKey != "original") {
            if (savedStateHandle.get<Int>("aiScrollChapter") == chapterIndex && savedStateHandle.get<String>("aiScrollContent") == contentKey)
                (savedStateHandle.get<Int>("aiScrollItem") ?: 0) to (savedStateHandle.get<Int>("aiScrollOffset") ?: 0)
            else scrollPosition(chapterIndex).first to 0
        } else if (savedStateHandle.get<Int>("scrollChapter") == chapterIndex)
            (savedStateHandle.get<Int>("scrollItem") ?: 0) to (savedStateHandle.get<Int>("scrollOffset") ?: 0)
        else 0 to 0

    fun saveScrollPosition(chapterIndex: Int, item: Int, offset: Int, contentKey: String = "original") {
        if (_uiState.value.currentChapterIndex != chapterIndex) return
        if (aiState.value.contentKey != contentKey) return
        if (contentKey != "original") {
            savedStateHandle["aiScrollChapter"] = chapterIndex
            savedStateHandle["aiScrollContent"] = contentKey
            savedStateHandle["aiScrollItem"] = item
            savedStateHandle["aiScrollOffset"] = offset
            return
        }
        savedStateHandle["scrollChapter"] = chapterIndex
        savedStateHandle["scrollItem"] = item
        savedStateHandle["scrollOffset"] = offset
    }

    fun nextChapter(expectedChapterIndex: Int? = null) {
        val state = _uiState.value
        if (expectedChapterIndex != null && !state.canNavigateFrom(expectedChapterIndex)) return
        if (state.currentChapterIndex < state.chapters.size - 1) {
            aiController?.sourceChanged(null)
            val nextIndex = state.currentChapterIndex + 1
            savedStateHandle["readerChapterIndex"] = nextIndex
            _uiState.update { it.copy(currentChapterIndex = nextIndex, chapters = retainReaderContent(it.chapters, nextIndex)) }
            val nextChapter = state.chapters[nextIndex]
            loadChapterContent(nextIndex, nextChapter, forceRefresh = false)
        }
    }

    fun prevChapter(expectedChapterIndex: Int? = null) {
        val state = _uiState.value
        if (expectedChapterIndex != null && !state.canNavigateFrom(expectedChapterIndex)) return
        if (state.currentChapterIndex > 0) {
            aiController?.sourceChanged(null)
            val prevIndex = state.currentChapterIndex - 1
            savedStateHandle["readerChapterIndex"] = prevIndex
            _uiState.update { it.copy(currentChapterIndex = prevIndex, chapters = retainReaderContent(it.chapters, prevIndex)) }
            val prevChapter = state.chapters[prevIndex]
            loadChapterContent(prevIndex, prevChapter, forceRefresh = false)
        }
    }

    fun selectChapter(index: Int) {
        val state = _uiState.value
        if (index in state.chapters.indices) {
            if (index != state.currentChapterIndex) aiController?.sourceChanged(null)
            savedStateHandle["readerChapterIndex"] = index
            _uiState.update { it.copy(currentChapterIndex = index, chapters = retainReaderContent(it.chapters, index)) }
            val selected = state.chapters[index]
            loadChapterContent(index, selected, forceRefresh = false)
        }
    }

    fun setFontSize(size: Float) = preferencesManager.updateFontSize(size)
    fun setTheme(theme: ReaderTheme) = preferencesManager.updateTheme(theme)
    fun setLineHeight(multiplier: Float) = preferencesManager.updateLineHeight(multiplier)
    fun setFontFamily(font: ReaderFont) = preferencesManager.updateFontFamily(font)
    fun setParagraphSpacing(spacingDp: Float) = preferencesManager.updateParagraphSpacing(spacingDp)

    fun retryCurrentChapter() {
        val state = _uiState.value
        // Reset circuit breaker on explicit retry
        val source = state.novel?.source ?: NovelSourceType.NOVEL_PHOENIX
        repository.resetSourceCircuitBreaker(source)
        if (state.currentChapterIndex in state.chapters.indices) {
            loadChapterContent(state.currentChapterIndex, state.chapters[state.currentChapterIndex], forceRefresh = true)
        } else if (!state.isLoading) {
            loadNovelAndChapters()
        }
    }

    /**
     * Constructs a reliable sourceUrl for the novel.
     */
    private fun resolveSourceUrl(novel: Novel): String {
        if (novel.source == NovelSourceType.FREE_WEB_NOVEL) {
            return FreeWebNovelUrls.novelUrl(novel.sourceUrl.ifBlank { novel.id })
        }
        if (novel.sourceUrl.isNotBlank() && novel.sourceUrl.contains("/novel/")) {
            val url = novel.sourceUrl.trim()
            return if (url.startsWith("http")) url else "${novel.source.baseUrl}${if (url.startsWith("/")) "" else "/"}$url"
        }
        val mappedSlug = mapLegacyIdToSlug(novelId) ?: mapLegacyIdToSlug(novel.id)
        val slug = mappedSlug ?: novelId
            .removePrefix("np-")
            .removePrefix("fwn-")
            .removePrefix("asura-")
            .trimEnd('/')
            .substringAfterLast('/')
        return "${novel.source.baseUrl}/novel/$slug"
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

    private fun loadNovelAndChapters() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // 1. Resolve novel model from database first, then BookmarkManager, then SeedData, then synthetic shell
            val resolvedNovel = repository.getNovelById(novelId)
                ?: bookmarkManager?.getBookmark(novelId)
                ?: SeedData.findNovelById(novelId)
                ?: run {
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

            // 2. Reuse the detail page's progressive snapshot before requesting a complete list.
            val source = resolvedNovel.source
            val slugOrUrl = resolveSourceUrl(resolvedNovel)
            val snapshots = repository.observeChapterSnapshots(source, slugOrUrl)
            readingProgressManager?.awaitLoaded()
            val resumeIndex = savedStateHandle.get<Int>("readerChapterIndex") ?: initialChapterIndex
            val requestedIndex = (if (resumeIndex >= 0) resumeIndex
                else readingProgressManager?.getProgress(novelId)?.lastReadChapterIndex ?: 0).coerceAtLeast(0)
            // Start the exact saved chapter before catalog requests occupy the host queue.
            val progress = readingProgressManager?.getProgress(novelId)
            val savedContentUrl = progress?.takeIf { it.lastReadChapterIndex == requestedIndex }
                ?.lastReadChapterId?.takeIf { it.isNotBlank() }?.let { id ->
                    if (source !in setOf(NovelSourceType.NOVEL_PHOENIX, NovelSourceType.FREE_WEB_NOVEL)) null
                    else runCatching {
                        val root = slugOrUrl.trimEnd('/') + "/"
                        val url = java.net.URI(root).resolve(id).toString()
                        url.takeIf { it.startsWith(root) }
                    }.getOrNull()
                }
            val pendingContent = savedContentUrl?.let { url -> async {
                try { repository.getChapterContent(source, url, forceRefresh = false) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Result.failure(error) }
            } }
            val availableChapters = snapshots.value
            val chaptersResult = if (requestedIndex in availableChapters.indices) {
                Result.success(availableChapters)
            } else {
                viewModelScope.startReaderCatalog(repository, source, slugOrUrl, requestedIndex).await()
            }

            val loadedChapters = chaptersResult.getOrNull()?.ifEmpty { null }
                ?: if (novelId == "sample" || novelId.isBlank()) {
                    generateSampleChapters(resolvedNovel.title)
                } else {
                    emptyList()
                }

            val targetIndex = if (resumeIndex >= 0) {
                resumeIndex.coerceIn(0, (loadedChapters.size - 1).coerceAtLeast(0))
            } else {
                readingProgressManager?.getProgress(novelId)?.lastReadChapterIndex?.coerceIn(0, (loadedChapters.size - 1).coerceAtLeast(0)) ?: 0
            }

            if (loadedChapters.isEmpty()) pendingContent?.cancel()
            if (loadedChapters.isNotEmpty()) savedStateHandle["readerChapterIndex"] = targetIndex
            _uiState.update {
                it.copy(
                    chapters = retainReaderContent(loadedChapters, targetIndex),
                    isLoading = false,
                    currentChapterIndex = targetIndex,
                    errorMessage = if (loadedChapters.isEmpty()) {
                        chaptersResult.exceptionOrNull()?.message ?: "No chapters available from source."
                    } else null
                )
            }

            // 3. Load initial chapter content
            if (loadedChapters.isNotEmpty()) {
                val targetChapter = loadedChapters[targetIndex]
                val targetUrl = runCatching { java.net.URI(slugOrUrl.trimEnd('/') + "/")
                    .resolve(targetChapter.url.ifBlank { targetChapter.id }).toString() }.getOrNull()
                val sameSavedChapter = savedContentUrl?.substringBefore('?')?.substringBefore('#') ==
                    targetUrl?.substringBefore('?')?.substringBefore('#')
                if (!sameSavedChapter) pendingContent?.cancel()
                loadChapterContent(targetIndex, targetChapter, forceRefresh = false,
                    pendingContent = pendingContent.takeIf { sameSavedChapter })

                // The detail page can continue loading later pages while the first chapter is read.
                snapshots.collect { incoming ->
                    _uiState.update { state ->
                        // Repository snapshots retain partial refreshes; a smaller snapshot
                        // now represents a completed refresh, not a temporary first page.
                        if (incoming.isEmpty()) return@update state
                        val currentId = state.chapters.getOrNull(state.currentChapterIndex)?.id
                        val known = state.chapters.associateBy { it.id }
                        val merged = incoming.map { item ->
                            known[item.id]?.takeIf { it.paragraphs.isNotEmpty() } ?: item
                        }
                        val currentIndex = merged.indexOfFirst { it.id == currentId }
                        if (currentIndex < 0) state else state.copy(chapters = retainReaderContent(merged, currentIndex), currentChapterIndex = currentIndex)
                    }
                    val state = _uiState.value
                    if (state.chapters.getOrNull(state.currentChapterIndex)?.paragraphs?.isNotEmpty() == true) {
                        prefetchSurroundingChapters(state.currentChapterIndex)
                    }
                }
            }
        }
    }

    private fun loadChapterContent(
        chapterIndex: Int,
        chapter: Chapter,
        forceRefresh: Boolean,
        isPrefetch: Boolean = false,
        pendingContent: Deferred<Result<Chapter>>? = null
    ) {
        viewModelScope.launch {
            val novel = _uiState.value.novel ?: return@launch
            if (!forceRefresh && chapter.paragraphs.isNotEmpty()) {
                if (!isPrefetch) {
                    _uiState.update { it.copy(isChapterLoading = false, errorMessage = null) }
                    readingProgressManager?.markChapterRead(novelId, chapter.id, chapterIndex, chapter.title)
                    prefetchSurroundingChapters(chapterIndex)
                }
                return@launch
            }
            if (!isPrefetch) {
                _uiState.update { it.copy(isChapterLoading = true, errorMessage = null) }
            }

            val targetUrl = when {
                chapter.url.isNotBlank() -> chapter.url
                novel.sourceUrl.isNotBlank() -> "${novel.sourceUrl.trimEnd('/')}/${chapter.id}"
                else -> chapter.id
            }

            val contentResult = try {
                pendingContent?.await() ?: repository.getChapterContent(
                    source = novel.source,
                    chapterUrlOrId = targetUrl,
                    forceRefresh = forceRefresh
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            if (isPrefetch) prefetchedChapterIds.remove(chapter.id)

            contentResult.fold(
                onSuccess = { liveChapter ->
                    val resolvedTitle = liveChapter.title.ifBlank { chapter.title }
                    _uiState.update { state ->
                        val updatedList = state.chapters.toMutableList()
                        val index = updatedList.indexOfFirst { it.id == chapter.id }
                        if (index >= 0) {
                            updatedList[index] = liveChapter.copy(
                                id = chapter.id,
                                title = resolvedTitle
                            )
                        }
                        val isCurrent = !isPrefetch && state.chapters.getOrNull(state.currentChapterIndex)?.id == chapter.id
                        state.copy(
                            chapters = retainReaderContent(updatedList, state.currentChapterIndex),
                            isChapterLoading = if (isCurrent) false else state.isChapterLoading,
                            errorMessage = if (isCurrent) null else state.errorMessage
                        )
                    }

                    if (!isPrefetch && _uiState.value.chapters.getOrNull(_uiState.value.currentChapterIndex)?.id == chapter.id) {
                        // Record reading progress and mark chapter as read
                        readingProgressManager?.markChapterRead(
                            novelId = novelId,
                            chapterId = chapter.id,
                            chapterIndex = chapterIndex,
                            chapterTitle = resolvedTitle
                        )

                        // Trigger prefetch for next chapter in background
                        prefetchSurroundingChapters(chapterIndex)
                    }
                },
                onFailure = { err ->
                    if (!isPrefetch) {
                        _uiState.update { state ->
                            if (state.chapters.getOrNull(state.currentChapterIndex)?.id != chapter.id) return@update state
                            val updatedList = state.chapters.toMutableList()
                            if (chapterIndex in updatedList.indices && updatedList[chapterIndex].paragraphs.isEmpty()) {
                                if (novelId == "sample" || novelId.isBlank()) {
                                    val sample = generateSampleChapters(novel.title).getOrNull(chapterIndex)
                                        ?: generateSampleChapters(novel.title).first()
                                    updatedList[chapterIndex] = updatedList[chapterIndex].copy(
                                        paragraphs = sample.paragraphs
                                    )
                                }
                            }
                            state.copy(
                                chapters = updatedList,
                                isChapterLoading = false,
                                errorMessage = "Failed to fetch live chapter (${err.message ?: "Network error"}). Tap to retry."
                            )
                        }
                    }
                }
            )
        }
    }

    private fun prefetchSurroundingChapters(currentIndex: Int) {
        val nextIndex = currentIndex + 1
        val chapters = _uiState.value.chapters
        if (nextIndex in chapters.indices) {
            val nextChapter = chapters[nextIndex]
            if (nextChapter.paragraphs.isEmpty() && prefetchedChapterIds.size < 2 && prefetchedChapterIds.add(nextChapter.id)) {
                loadChapterContent(nextIndex, nextChapter, forceRefresh = false, isPrefetch = true)
            }
        }
    }

    private fun generateSampleChapters(novelTitle: String): List<Chapter> {
        return listOf(
            Chapter(
                id = "sample-1",
                title = "Chapter 1: The Sanctuary Chronicles",
                paragraphs = listOf(
                    "The journey of $novelTitle begins here.",
                    "Ancient pages whisper in the quiet study as the lantern flickers with soft silver light. The road ahead stretches far into uncharted horizons.",
                    "With quiet determination, the first chapter of this saga unfolds."
                )
            ),
            Chapter(
                id = "sample-2",
                title = "Chapter 2: Pathways in the Mist",
                paragraphs = listOf(
                    "Dawn rises steadily across the peaks, casting a warm golden glow upon the stones.",
                    "Every great odyssey begins with patience and resolve. The chronicles continue as new truths reveal themselves in time."
                )
            )
        )
    }
}
