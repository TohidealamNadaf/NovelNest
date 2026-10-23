package com.novelnest.app.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.model.*
import com.novelnest.app.data.preferences.NavigationPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.novelnest.app.data.repository.NovelRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DiscoverViewModel(
    private val repository: NovelRepository,
    initialSource: NovelSourceType = NovelSourceType.NOVEL_PHOENIX,
    private val navigationPreferences: NavigationPreferences? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiscoverUiState(selectedSource = initialSource))
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    private val ratingBlockedUntil = mutableMapOf<NovelSourceType, Long>()
    private var ratingsActive = true
    private val ratingSlots = Semaphore(2)
    private val visibleRatings = mutableMapOf<Pair<NovelSourceType, String>, Int>()
    private val ratingJobs = mutableMapOf<Pair<NovelSourceType, String>, Job>()
    private val ratingAttempts = linkedMapOf<Pair<NovelSourceType, String>, Long>()

    /** Called by cards in the viewport; duplicate sections share one request and one score. */
    fun ratingVisibility(novel: Novel, visible: Boolean) {
        val normalized = NovelIdentity.normalize(novel)
        val key = normalized.source to normalized.id
        if (!visible) {
            val remaining = (visibleRatings[key] ?: 0) - 1
            if (remaining <= 0) {
                visibleRatings.remove(key)
                ratingJobs[key]?.cancel()
            } else visibleRatings[key] = remaining
            return
        }
        if (normalized.source != _uiState.value.selectedSource) return
        visibleRatings[key] = (visibleRatings[key] ?: 0) + 1
        requestMissingRating(normalized)
    }

    private fun requestMissingRating(normalized: Novel) {
        if (!ratingsActive) return
        val key = normalized.source to normalized.id
        val known = _uiState.value.sections.flatMap { it.novels }.any {
            it.source == normalized.source && it.id == normalized.id && it.hasRating()
        }
        if (known || normalized.hasRating() || ratingJobs.containsKey(key) ||
            normalized.source == NovelSourceType.ASURA_SCANS) return
        val now = System.currentTimeMillis()
        if (now < (ratingBlockedUntil[normalized.source] ?: 0L) ||
            now - (ratingAttempts[key] ?: 0L) < 60_000L) return
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                // Ignore cards passed during a quick scroll before downloading detail HTML.
                delay(650)
                ratingSlots.withPermit {
                    if (!ratingsActive || !visibleRatings.containsKey(key) || _uiState.value.selectedSource != normalized.source) return@withPermit
                    if (System.currentTimeMillis() < (ratingBlockedUntil[normalized.source] ?: 0L)) return@withPermit
                    ratingAttempts[key] = System.currentTimeMillis()
                    while (ratingAttempts.size > 128) ratingAttempts.remove(ratingAttempts.keys.first())
                    val result = repository.getNovelDetails(normalized.source, normalized.sourceUrl)
                    val error = result.exceptionOrNull()
                    if (error is com.novelnest.app.data.remote.novelphoenix.PhoenixException.SourceBlockedError ||
                        error is com.novelnest.app.data.remote.freewebnovel.FreeWebNovelException.Blocked ||
                        (error is com.novelnest.app.data.remote.freewebnovel.FreeWebNovelException.HttpError && error.status == 429)) {
                        ratingBlockedUntil[normalized.source] = System.currentTimeMillis() + 60_000L
                    }
                    val fresh = result.getOrNull()?.let(NovelIdentity::normalize)
                    if (fresh != null && fresh.source == normalized.source && fresh.id == normalized.id && fresh.hasRating()) {
                        _uiState.update { state ->
                            if (state.selectedSource != normalized.source) state else state.copy(
                                sections = state.sections.map { section -> section.copy(novels = section.novels.map {
                                    if (it.source == fresh.source && it.id == fresh.id) it.copy(rating = fresh.rating) else it
                                }) })
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                // Keep the cooldown if HTTP started; scrolling must not restart downloads.
                throw cancelled
            } catch (_: Exception) {
                // A failed rating lookup must not fail the feed or retry on every recomposition.
            } finally {
                if (ratingJobs[key] === coroutineContext[Job]) ratingJobs.remove(key)
            }
        }
        ratingJobs[key] = job
        job.start()
    }

    fun setRatingActive(active: Boolean) {
        ratingsActive = active
        if (!active) cancelRatingRequests(clearVisibility = false)
        else _uiState.value.sections.flatMap { it.novels }.distinctBy { it.source to it.id }
            .filter { (it.source to it.id) in visibleRatings }.forEach(::requestMissingRating)
    }

    fun cancelRatingRequests(clearVisibility: Boolean = true) {
        ratingJobs.values.toList().forEach { it.cancel() }
        ratingJobs.clear()
        if (clearVisibility) visibleRatings.clear()
    }

    init { loadDiscover() }

    fun selectSource(source: NovelSourceType) {
        navigationPreferences?.selectSource(source)
        if (_uiState.value.selectedSource == source) return
        _uiState.update { it.copy(selectedSource = source, sections = emptyList(), errorMessage = null) }
        loadDiscover()
    }

    fun refresh() {
        repository.resetSourceCircuitBreaker(_uiState.value.selectedSource)
        loadDiscover(forceRefresh = true)
    }

    fun loadDiscover(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        cancelRatingRequests(clearVisibility = _uiState.value.sections.isEmpty())
        if (forceRefresh) {
            ratingAttempts.clear()
            ratingBlockedUntil.remove(_uiState.value.selectedSource)
        }
        val source = _uiState.value.selectedSource
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.sections.isEmpty(), isRefreshing = forceRefresh, errorMessage = null) }
            repository.getHomeSections(source, forceRefresh).collect { result ->
                if (_uiState.value.selectedSource != source) return@collect
                result.fold(
                    onSuccess = { sections ->
                        _uiState.update { previous ->
                            val known = previous.sections.flatMap { it.novels }.filter { it.hasRating() }
                                .associateBy { it.source to it.id }
                            val enriched = sections.map { section -> section.copy(novels = section.novels.map { novel ->
                                if (novel.hasRating()) novel else known[novel.source to novel.id]
                                    ?.let { novel.copy(rating = it.rating) } ?: novel
                            }) }.reuseRatings()
                            previous.copy(sections = enriched, isLoading = false, isRefreshing = false, errorMessage = null)
                        }
                        val visible = visibleRatings.keys.toSet()
                        sections.flatMap { it.novels }.distinctBy { it.source to it.id }.forEach {
                            if ((it.source to it.id) in visible) requestMissingRating(it)
                        }
                    },
                    onFailure = { error -> _uiState.update { it.copy(isLoading = false, isRefreshing = false,
                        errorMessage = error.localizedMessage ?: "Unable to load novels. Please retry.") } }
                )
            }
        }
    }
}
