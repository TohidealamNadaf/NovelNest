package com.novelnest.app.feature.epub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.study.StudyRepository
import com.novelnest.app.data.study.StudyChapterKey
import com.novelnest.app.feature.reader.ReaderStudyController
import com.novelnest.app.feature.reader.ReaderStudyState
import com.novelnest.app.data.local.epub.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.readium.r2.shared.util.Url
import org.json.JSONObject
import org.readium.r2.shared.publication.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

sealed interface EpubReaderState {
    data object Loading : EpubReaderState
    data class Ready(val book: LocalBookEntity, val publication: Publication, val initialLocator: Locator?) : EpubReaderState
    data class Failed(val message: String) : EpubReaderState
}

internal data class EpubImageViewerState(
    val section: Url,
    val images: List<EpubImageRef> = emptyList(),
    val index: Int = 0,
    val loading: Boolean = true,
    val asset: EpubImageAsset? = null,
    val error: String? = null
)

internal data class EpubReaderChromeState(
    val fullscreen: Boolean = false,
    val autoHideControls: Boolean = false,
    val autoHideControlsRevealed: Boolean = true
)

class EpubReaderViewModel(private val id: String, private val repository: EpubRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    studyRepository: StudyRepository? = null) : ViewModel() {
    // The default keeps publication-only lifecycle tests independent of the notes database.
    private val notes = studyRepository?.let { ReaderStudyController(viewModelScope, it, observeCollections = false) }
    internal val notesState = notes?.state ?: MutableStateFlow(ReaderStudyState()).asStateFlow()
    // UI draft only: retained across rotation, never placed in the EPUB or its locator.
    internal val notesSession = EpubNotesSession()
    internal fun showNotes(showing: Boolean) {
        val ready = mutableState.value as? EpubReaderState.Ready
        val section = latest?.href ?: ready?.publication?.readingOrder?.firstOrNull()?.url()
        val key = if (ready != null && section != null)
            StudyChapterKey("LOCAL_EPUB", id, section.toString().substringBefore('#')) else null
        notes?.open(key, showing && !cleared.get())
        if (showing && !cleared.get()) notesSession.loadingNoteId?.let { notes?.selectNote(it) }
    }
    internal fun selectNote(id: String?) { notes?.selectNote(id) }
    internal fun saveNote(id: String?, title: String, markdown: String) { notes?.saveNote(id, title, markdown) }
    internal fun deleteNote(id: String) { notes?.deleteNote(id) }
    internal fun clearNoteMessage() { notes?.clearMessage() }

    private val currentPublication = AtomicReference<Publication?>(null)
    private val cleared = AtomicBoolean(false)
    private val mutableState = MutableStateFlow<EpubReaderState>(EpubReaderState.Loading)
    val state = mutableState.asStateFlow()
    internal val chapterPositions = EpubChapterPositions()
    @Volatile private var latest: Locator? = null
    @Volatile private var latestProgression = 0.0
    private var lastSaved = 0L
    private var loadJob: Job? = null
    private val imageMutex = Mutex()
    private var imageJob: Job? = null
    private var imageGeneration = 0L
    private val mutableImageViewer = MutableStateFlow<EpubImageViewerState?>(null)
    internal val imageViewer = mutableImageViewer.asStateFlow()
    // One atomic session state keeps the two independent modes and reveal state consistent.
    private val mutableChromeState = MutableStateFlow(EpubReaderChromeState())
    internal val chromeState = mutableChromeState.asStateFlow()
    internal fun setFullscreen(enabled: Boolean) {
        if (!cleared.get() && (!enabled || mutableState.value is EpubReaderState.Ready))
            mutableChromeState.update { it.copy(fullscreen = enabled, autoHideControlsRevealed = !enabled) }
    }
    internal fun setAutoHideControls(enabled: Boolean) {
        if (!cleared.get() && (!enabled || mutableState.value is EpubReaderState.Ready))
            mutableChromeState.update { it.copy(autoHideControls = enabled, autoHideControlsRevealed = true) }
    }
    internal fun revealAutoHideControls() {
        if (!cleared.get()) mutableChromeState.update {
            if (it.autoHideControls && !it.autoHideControlsRevealed) it.copy(autoHideControlsRevealed = true) else it
        }
    }
    internal fun hideAutoHideControls() {
        if (!cleared.get()) mutableChromeState.update {
            if (it.autoHideControls && it.autoHideControlsRevealed) it.copy(autoHideControlsRevealed = false) else it
        }
    }

    internal fun openImages(section: Url, index: Int = 0) {
        if (cleared.get()) return
        val publication = currentPublication.get() ?: return
        imageJob?.cancel()
        val generation = ++imageGeneration
        mutableImageViewer.value = EpubImageViewerState(section)
        imageJob = viewModelScope.launch {
            try {
                val images = withContext(ioDispatcher) {
                    imageMutex.withLock { ensureActive(); EpubImages.catalog(publication, section) }
                }
                ensureActive()
                if (generation != imageGeneration) return@launch
                if (images.isEmpty()) {
                    mutableImageViewer.value = EpubImageViewerState(section, loading = false)
                } else {
                    mutableImageViewer.value = EpubImageViewerState(section, images, index.coerceIn(images.indices))
                    imageJob = null
                    selectImage(index.coerceIn(images.indices))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failImage(generation, error.message ?: "Could not list illustrations.") }
            catch (_: OutOfMemoryError) { failImage(generation, "Not enough memory to open these illustrations.") }
        }
    }

    internal fun selectImage(index: Int) {
        val previous = mutableImageViewer.value ?: return
        if (index !in previous.images.indices || cleared.get()) return
        val publication = currentPublication.get() ?: return
        imageJob?.cancel()
        val generation = ++imageGeneration
        mutableImageViewer.value = previous.copy(index = index, asset = null, loading = true, error = null)
        imageJob = viewModelScope.launch {
            try {
                val asset = withContext(ioDispatcher) {
                    imageMutex.withLock { ensureActive(); EpubImages.load(publication, previous.images[index]) }
                }
                ensureActive()
                if (generation == imageGeneration) mutableImageViewer.value =
                    mutableImageViewer.value?.copy(asset = asset, loading = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failImage(generation, error.message ?: "Could not open this illustration.") }
            catch (_: OutOfMemoryError) { failImage(generation, "Not enough memory to display this illustration.") }
        }
    }

    private fun failImage(generation: Long, message: String) {
        if (generation == imageGeneration && !cleared.get())
            mutableImageViewer.value = mutableImageViewer.value?.copy(loading = false, asset = null, error = message)
    }

    internal fun retryImage() {
        val current = mutableImageViewer.value ?: return
        if (current.images.isEmpty()) openImages(current.section) else selectImage(current.index)
    }

    internal fun closeImages() {
        ++imageGeneration
        imageJob?.cancel()
        imageJob = null
        mutableImageViewer.value = null
    }

    init { load() }
    fun load() {
        if (currentPublication.get() != null || loadJob?.isActive == true) return
        mutableState.value = EpubReaderState.Loading
        loadJob = viewModelScope.launch(ioDispatcher) {
            var opened: Publication? = null
            try {
                val book = repository.book(id) ?: throw EpubImportException("This book is no longer in your local library.")
                if (!repository.bookFile(id).isFile) throw EpubImportException("The EPUB file is missing. Import the original file again to restore access.")
                val position = repository.position(id)
                val saved = position?.locatorJson?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
                opened = repository.engine.open(repository.bookFile(id))
                ensureActive()
                val publication = opened
                val resume = saved?.takeIf { publication.containsReadingResource(it.href) }
                latest = resume
                latestProgression = if (resume != null) position?.progression?.takeIf { it.isFinite() && it in 0.0..1.0 } ?: 0.0 else 0.0
                currentPublication.set(publication)
                opened = null // Ownership transfers only after all initial-location work succeeds.
                if (cleared.get()) {
                    currentPublication.getAndSet(null)?.let(repository::releasePublication)
                    return@launch
                }
                mutableState.value = EpubReaderState.Ready(book, publication, resume)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableState.value = EpubReaderState.Failed(if (error is EpubImportException) error.message.orEmpty() else "Could not open this EPUB. Please try again.")
            } finally { opened?.let(repository::releasePublication) }
        }
    }
    fun checkpoint(locator: Locator, force: Boolean = false) {
        // A missing aggregate position on Readium's initial locator must not reset a restored percentage.
        val progression = locator.locations.totalProgression ?: latestProgression
        if (!progression.isFinite() || progression !in 0.0..1.0) {
            repository.reportPositionFailure()
            return
        }
        latest = locator
        latestProgression = progression
        chapterPositions.remember(locator)
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastSaved < 2000) return
        try {
            repository.enqueuePosition(EpubPositionEntity(id, locator.toJSON().toString(),
                progression, locator.title.orEmpty().take(1000), System.currentTimeMillis()))
            lastSaved = now
        } catch (_: Exception) {
            // Serialization is invoked from locator collection and onPause; never escape onto Main.
            repository.reportPositionFailure()
        }
    }
    fun progression(): Double = latestProgression
    fun resumeLocator(): Locator? = latest
    fun flush() { latest?.let { checkpoint(it, true) } }
    override fun onCleared() {
        flush()
        cleared.set(true)
        closeImages()
        val publication = currentPublication.getAndSet(null)
        // Let a cancelled resource read close its handle before releasing the shared publication.
        if (publication != null) repository.releasePublicationAfter(publication, imageMutex)
    }
}
