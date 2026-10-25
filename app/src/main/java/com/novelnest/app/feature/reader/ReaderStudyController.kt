package com.novelnest.app.feature.reader

import com.novelnest.app.data.study.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ReaderStudyState(
    val key: StudyChapterKey? = null,
    val notes: List<ChapterNotePreview> = emptyList(),
    val selectedNoteId: String? = null,
    val editorNote: ChapterNoteEntity? = null,
    val collections: List<TermCollectionRow> = emptyList(),
    val selectedCollectionId: String? = null,
    val discoveries: List<TermDiscoveryEntity> = emptyList(),
    val saving: Boolean = false,
    val noteSavedRevision: Long = 0,
    val collectionSavedRevision: Long = 0,
    val message: String? = null
)

/** Only observes chapter data while the sheet is open; never retains other novels' notebooks. */
class ReaderStudyController(private val scope: CoroutineScope, private val repository: StudyRepository) {
    private val mutable = MutableStateFlow(ReaderStudyState())
    val state = mutable.asStateFlow()
    private var observer: Job? = null
    private var detail: Job? = null
    private var noteLoader: Job? = null
    private var visible = false

    fun open(key: StudyChapterKey?, showing: Boolean) {
        if (mutable.value.key == key && visible == showing) return
        val changed = mutable.value.key != key
        visible = showing
        observer?.cancel(); detail?.cancel(); noteLoader?.cancel()
        if (changed) mutable.value = ReaderStudyState(key = key)
        if (key == null || !showing) {
            mutable.update { it.copy(notes = emptyList(), collections = emptyList(), discoveries = emptyList(), selectedCollectionId = null,
                selectedNoteId = null, editorNote = null) }
            return
        }
        observer = scope.launch {
            try {
                combine(repository.notes(key), repository.collections(key)) { notes, collections -> notes to collections }
                    .collect { (notes, collections) ->
                        mutable.update { if (it.key == key) it.copy(notes = notes, collections = collections) else it }
                        val selected = mutable.value.selectedCollectionId
                        if (selected != null && collections.none { it.collection.id == selected }) selectCollection(null)
                    }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message(key, "Could not load notes and terms. Close and reopen this menu to retry.") }
        }
        selectCollection(mutable.value.selectedCollectionId)
    }

    fun selectCollection(id: String?) {
        if (id != null && !visible) return
        detail?.cancel()
        mutable.update { it.copy(selectedCollectionId = id, discoveries = emptyList()) }
        val key = mutable.value.key ?: return
        if (id == null || !visible || mutable.value.collections.none { it.collection.id == id }) return
        detail = scope.launch {
            try {
                repository.discoveries(id).flowOn(Dispatchers.Default).collect { rows ->
                    mutable.update { if (it.key == key && it.selectedCollectionId == id) it.copy(discoveries = rows) else it }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message(key, "Could not load saved terms. Reopen the collection to retry.") }
        }
    }

    fun selectNote(id: String?) {
        if (id != null && !visible) return
        noteLoader?.cancel()
        mutable.update { it.copy(selectedNoteId = id, editorNote = null) }
        val key = mutable.value.key ?: return
        if (id == null) return
        noteLoader = scope.launch {
            try {
                val note = repository.note(key, id)
                mutable.update { if (it.key == key && it.selectedNoteId == id) it.copy(editorNote = note) else it }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message(key, "Could not open this note. Reopen the notes list and retry.") }
        }
    }

    fun saveNote(id: String?, title: String, markdown: String) = mutate { key ->
        val note = repository.saveNote(key, id, title.ifBlank { "Untitled Note" }, markdown)
        mutable.update { if (it.key == key) it.copy(noteSavedRevision = it.noteSavedRevision + 1,
            editorNote = if (visible) note else null, selectedNoteId = if (visible) note.id else null, message = "Note saved") else it }
    }
    fun deleteNote(id: String) = mutate { repository.deleteNote(it, id) }
    fun addCollection(name: String) = mutate { key ->
        repository.addCollection(key, name)
        mutable.update { if (it.key == key) it.copy(collectionSavedRevision = it.collectionSavedRevision + 1) else it }
    }
    fun deleteCollection(id: String) = mutate { repository.deleteCollection(it, id) }
    fun clearMessage() { mutable.update { it.copy(message = null) } }
    fun message(key: StudyChapterKey, message: String) { mutable.update { if (it.key == key) it.copy(message = message) else it } }

    private fun mutate(block: suspend (StudyChapterKey) -> Unit) {
        if (!visible) return
        val key = mutable.value.key ?: return
        if (mutable.value.saving) return
        mutable.update { it.copy(saving = true, message = null) }
        scope.launch {
            try { block(key) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message(key, if (error is IllegalArgumentException) error.message.orEmpty() else "Could not save changes. Check device storage and try again.") }
            finally { mutable.update { if (it.key == key) it.copy(saving = false) else it } }
        }
    }
}
