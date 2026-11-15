package com.novelnest.app.feature.epub

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.local.epub.EpubRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EpubLibraryViewModel(
    val repository: EpubRepository,
    val collectionId: String? = null,
    private val savedState: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    val query = savedState.getStateFlow("epubQuery", "")
    val selection = savedState.getStateFlow("epubSelection", emptyList<String>())
    fun clearSelection() { if (!mutableOperation.value.busy) savedState["epubSelection"] = emptyList<String>() }
    /** Idempotent long-press entry; never opens or edits an item. */
    fun selectItem(key: String) {
        if (mutableOperation.value.busy || state.value.items.none { it.key == key } || key in selection.value) return
        if (selection.value.size >= MAX_SELECTION) { selectionLimitMessage(); return }
        savedState["epubSelection"] = selection.value + key
    }
    fun toggleSelection(key: String) {
        if (mutableOperation.value.busy || state.value.items.none { it.key == key }) return
        val current = selection.value
        if (key !in current && current.size >= MAX_SELECTION) { selectionLimitMessage(); return }
        savedState["epubSelection"] = if (key in current) current - key else current + key
    }
    fun selectVisible() {
        if (mutableOperation.value.busy) return
        val keys = (selection.value + state.value.visibleItems.map { it.key }).distinct()
        savedState["epubSelection"] = keys.take(MAX_SELECTION)
        if (keys.size > MAX_SELECTION) selectionLimitMessage()
    }
    val state = combine(repository.shelf, query.debounce(150)) { snapshot, text -> snapshot to text }
        .mapLatest { (snapshot, text) -> withContext(Dispatchers.Default) { buildEpubLibraryState(snapshot, collectionId, text) } }
        .onEach { state ->
            if (state.loaded && repository.libraryError.value == null) {
                val available = state.items.map { it.key }.toSet()
                savedState["epubSelection"] = selection.value.filter { it in available }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EpubLibraryUiState())
    private fun selectionLimitMessage() {
        mutableOperation.value = mutableOperation.value.copy(message = "Select up to " + MAX_SELECTION + " items at a time.", failed = false)
    }
    private val mutableOperation = MutableStateFlow(EpubLibraryOperation())
    val operation = mutableOperation.asStateFlow()
    fun setQuery(value: String) { savedState["epubQuery"] = value.take(500) }
    fun clearMessage() { mutableOperation.value = mutableOperation.value.copy(message = null) }
    fun createSeries(name: String) = perform("Series created") { repository.createCollection(name) }
    fun renameSeries(id: String, name: String) = perform("Series renamed") { repository.renameCollection(id, name) }
    fun removeSeries(id: String) = perform("Series removed. Its EPUBs are kept in My Library.") { repository.removeCollection(id) }
    fun moveBook(id: String, target: String?) = perform("EPUB moved") { repository.moveBook(id, target) }
    fun labelVolume(id: String, label: String) = perform("Volume label saved") { repository.labelVolume(id, label) }
    fun reorder(id: String, bookIds: List<String>) = perform("Volume order saved") { repository.reorderVolumes(id, bookIds) }
    fun removeBook(id: String) = perform("EPUB removed from this device") { repository.delete(id) }
    fun moveSelection(bookIds: List<String>, target: String?) = performResult(clearSelection = true) {
        repository.moveBooks(bookIds, target)
        bookIds.size.toString() + " EPUBs moved"
    }
    fun removeSelectionFromSeries(bookIds: List<String>) = performResult(clearSelection = true) {
        val source = collectionId ?: error("Open a series to remove its EPUBs.")
        repository.removeBooksFromSeries(bookIds, source)
        bookIds.size.toString() + " EPUBs removed from the series. Books and reading progress are kept in My Library."
    }
    fun removeSelection(bookIds: List<String>, collectionIds: List<String>) = performResult(clearSelection = true) {
        val pendingCleanup = repository.removeItems(bookIds, collectionIds)
        if (pendingCleanup == 0) "Selected items removed. Unselected volumes and original files are kept."
        else "Items removed. Some private files will be cleaned up when the app next starts."
    }
    companion object { private const val MAX_SELECTION = 500 }
    private fun perform(message: String, action: suspend () -> Unit) = performResult { action(); message }
    private fun performResult(clearSelection: Boolean = false, action: suspend () -> String) {
        if (mutableOperation.value.busy) return
        mutableOperation.value = mutableOperation.value.copy(busy = true, message = null, failed = false)
        viewModelScope.launch {
            try {
                val message = action()
                if (clearSelection) savedState["epubSelection"] = emptyList<String>()
                val previous = mutableOperation.value
                mutableOperation.value = EpubLibraryOperation(message = message, revision = previous.revision + 1)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val reason = when (error) {
                    is android.database.sqlite.SQLiteConstraintException -> "A series with that name already exists. Choose another name."
                    is IllegalArgumentException, is IllegalStateException -> error.message ?: "The library changed. Please try again."
                    else -> "Could not update the EPUB library. Check storage and try again."
                }
                mutableOperation.value = mutableOperation.value.copy(busy = false, message = reason, failed = true)
            }
        }
    }
}

data class EpubLibraryOperation(val busy: Boolean = false, val message: String? = null,
    val failed: Boolean = false, val revision: Long = 0)
