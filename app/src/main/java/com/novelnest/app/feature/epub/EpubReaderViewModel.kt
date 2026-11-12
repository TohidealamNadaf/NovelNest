package com.novelnest.app.feature.epub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.local.epub.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.readium.r2.shared.publication.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

sealed interface EpubReaderState {
    data object Loading : EpubReaderState
    data class Ready(val book: LocalBookEntity, val publication: Publication, val initialLocator: Locator?) : EpubReaderState
    data class Failed(val message: String) : EpubReaderState
}

class EpubReaderViewModel(private val id: String, private val repository: EpubRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private val currentPublication = AtomicReference<Publication?>(null)
    private val cleared = AtomicBoolean(false)
    private val mutableState = MutableStateFlow<EpubReaderState>(EpubReaderState.Loading)
    val state = mutableState.asStateFlow()
    @Volatile private var latest: Locator? = null
    private var latestProgression = 0.0
    private var lastSaved = 0L
    private var loadJob: Job? = null
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
                val resume = saved?.takeIf { locator -> publication.readingOrder.any { it.href.toString().substringBefore('#') == locator.href.toString().substringBefore('#') } }
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
    fun resumeLocator(): Locator? = latest
    fun flush() { latest?.let { checkpoint(it, true) } }
    override fun onCleared() {
        flush()
        cleared.set(true)
        currentPublication.getAndSet(null)?.let(repository::releasePublication)
    }
}
