package com.novelnest.app.feature.epub

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.data.preferences.ReaderTheme
import com.novelnest.app.feature.reader.ReaderStudyState
import com.novelnest.app.feature.reader.components.*
import java.text.DateFormat
import java.util.Date

internal enum class EpubNotesExit { LIST, CONTROLS, DISMISS, READING_MODE, CHAPTERS, FINISH }

/** One editor per reader session. No Activity, View, or callback is retained across rotation. */
@Stable
internal class EpubNotesSession {
    val draft = ReaderNoteDraft()
    var editing by mutableStateOf(false)
        private set
    var note by mutableStateOf<ChapterNoteUi?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    var pendingExit by mutableStateOf<EpubNotesExit?>(null)
    var savingRevision by mutableStateOf<Long?>(null)
        private set
    private var requestedId: String? = null
    val loadingNoteId: String? get() = requestedId.takeIf { editing && !loaded }

    fun open(id: String?) {
        requestedId = id
        note = null
        draft.reset(null)
        loaded = id == null
        editing = true
        pendingExit = null
    }

    fun synchronize(state: ReaderStudyState) {
        if (editing && !loaded && state.editorNote?.id == requestedId) {
            val saved = state.editorNote ?: return
            note = ChapterNoteUi(saved.id, saved.title, saved.markdown, formatNoteDate(saved.updatedAt))
            draft.reset(note)
            loaded = true
        }
        savingRevision?.let { revision ->
            if (state.noteSavedRevision > revision) closeEditor()
            else if (!state.saving && state.message != null) savingRevision = null
        }
    }

    fun beginSave(state: ReaderStudyState): Boolean {
        if (!editing || !loaded || state.key == null || state.saving || savingRevision != null) return false
        savingRevision = state.noteSavedRevision
        return true
    }

    fun canExit(exit: EpubNotesExit, saving: Boolean): Boolean {
        if (saving || savingRevision != null) return false
        if (editing && loaded && draft.changedFrom(note)) {
            pendingExit = exit
            return false
        }
        return true
    }

    fun closeEditor() {
        editing = false
        loaded = false
        requestedId = null
        note = null
        draft.reset(null)
        savingRevision = null
        pendingExit = null
    }
}

/** Fits in the existing EPUB control surface; both list and editor own their internal scrolling. */
@Composable
internal fun EpubReaderNotesPanel(
    state: ReaderStudyState,
    session: EpubNotesSession,
    onSelect: (String?) -> Unit,
    onSave: (String?, String, String) -> Unit,
    onDelete: (String) -> Unit,
    onClearMessage: () -> Unit,
    onExit: (EpubNotesExit) -> Unit
) {
    LaunchedEffect(state.editorNote, state.noteSavedRevision, state.saving, state.message) {
        session.synchronize(state)
    }
    val notes = remember(state.notes) {
        val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        state.notes.map { ChapterNoteUi(it.id, it.title, it.preview, format.format(Date(it.updatedAt))) }
    }
    Column(Modifier.fillMaxWidth()) {
        // Keep errors visible in the editor as well as the list. Failed writes never discard a draft.
        if (session.editing) state.message?.takeIf { it != "Note saved" }?.let { message ->
            Surface(color = NovelError.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, color = NovelTextPrimary, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onClearMessage) { Text("Dismiss", color = NovelPrimary) }
                }
            }
        }
        if (!session.editing) {
            ReaderNotesListSubPage(
                notes = notes,
                textColor = NovelTextPrimary,
                secondaryColor = NovelTextSecondary,
                theme = ReaderTheme.DARK,
                studyMessage = state.message,
                onDismissStudyMessage = onClearMessage,
                onBack = { onExit(EpubNotesExit.CONTROLS) },
                onNewNote = {
                    if (state.key != null && !state.saving) {
                        onClearMessage()
                        onSelect(null)
                        session.open(null)
                    }
                },
                onOpenNote = {
                    if (!state.saving) {
                        onClearMessage()
                        onSelect(it.id)
                        session.open(it.id)
                    }
                },
                onDeleteNote = onDelete,
                title = "Book Notes"
            )
        } else if (session.loaded) {
            ReaderNoteEditSubPage(
                note = session.note, draft = session.draft,
                textColor = NovelTextPrimary, secondaryColor = NovelTextSecondary,
                theme = ReaderTheme.DARK,
                saving = state.saving || session.savingRevision != null || state.key == null,
                onBack = { onExit(EpubNotesExit.LIST) },
                onSave = { title, markdown ->
                    if (session.beginSave(state)) onSave(session.note?.id, title, markdown)
                }
            )
        } else {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = NovelPrimary, strokeWidth = 2.dp)
                Text("Loading note...", color = NovelTextPrimary, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                TextButton(onClick = { onExit(EpubNotesExit.LIST) }) { Text("Back", color = NovelPrimary) }
            }
        }
    }
}

@Composable
internal fun EpubNotesDiscardDialog(session: EpubNotesSession, onDiscard: (EpubNotesExit) -> Unit) {
    session.pendingExit?.let { exit ->
        AlertDialog(
            onDismissRequest = { session.pendingExit = null },
            title = { Text("Discard note changes?") },
            text = { Text("Your unsaved changes will be lost.") },
            confirmButton = {
                TextButton(onClick = { session.pendingExit = null; onDiscard(exit) }) {
                    Text("Discard changes", color = NovelError)
                }
            },
            dismissButton = {
                TextButton(onClick = { session.pendingExit = null }) { Text("Keep editing") }
            }
        )
    }
}

private fun formatNoteDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
