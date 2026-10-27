package com.novelnest.app.feature.reader.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

/** One bounded editor draft, owned by the sheet so every exit can check it before navigation. */
@Stable
internal class ReaderNoteDraft(title: String = "", content: String = "") {
    var title by mutableStateOf(title)
    var content by mutableStateOf(content)

    fun changedFrom(note: ChapterNoteUi?): Boolean =
        title != note?.title.orEmpty() || content != note?.content.orEmpty()

    fun reset(note: ChapterNoteUi?) {
        title = note?.title.orEmpty()
        content = note?.content.orEmpty()
    }

    companion object {
        val Saver = listSaver<ReaderNoteDraft, String>(
            save = { listOf(it.title, it.content) },
            restore = { ReaderNoteDraft(it[0], it[1]) }
        )
    }
}
