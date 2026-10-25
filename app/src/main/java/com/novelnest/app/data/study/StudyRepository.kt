package com.novelnest.app.data.study

import androidx.room.withTransaction
import com.novelnest.app.data.ai.*
import com.novelnest.app.data.database.NovelNestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

class StudyRepository(private val database: NovelNestDatabase) {
    private val dao get() = database.studyDao()
    fun notes(key: StudyChapterKey) = dao.notes(key.source, key.novelId, key.chapterId)
    fun collections(key: StudyChapterKey) = dao.collections(key.source, key.novelId)
    fun discoveries(id: String) = dao.discoveries(id)
    suspend fun note(key: StudyChapterKey, id: String): ChapterNoteEntity {
        val note = dao.note(id)
        require(note != null && note.key() == key) { "This note is no longer available." }
        return note
    }

    suspend fun saveNote(key: StudyChapterKey, id: String?, title: String, markdown: String) = database.withTransaction {
        require(title.trim().length in 1..160) { "Use a note title between 1 and 160 characters." }
        require(markdown.length <= 64_000) { "Notes support up to 64,000 characters." }
        val existing = id?.let { dao.note(it) }
        if (id != null) require(existing != null && existing.key() == key) { "This note is no longer available. Reopen the notes list." }
        if (id == null) require(dao.noteCount(key.source, key.novelId, key.chapterId) < 200) { "This chapter already has 200 notes." }
        val note = ChapterNoteEntity(id ?: UUID.randomUUID().toString(), key.source, key.novelId,
            key.chapterId, title.trim(), markdown, System.currentTimeMillis())
        dao.saveNote(note)
        note
    }

    suspend fun deleteNote(key: StudyChapterKey, id: String) = dao.deleteNote(id, key.source, key.novelId, key.chapterId)
    suspend fun addCollection(key: StudyChapterKey, name: String) = database.withTransaction {
        require(name.trim().length in 1..160) { "Describe what to extract in 1 to 160 characters." }
        require(dao.collectionCount(key.source, key.novelId) < 100) { "This novel already has 100 extraction collections." }
        val saved = dao.insertCollection(TermCollectionEntity(UUID.randomUUID().toString(), key.source,
            key.novelId, name.trim(), termKey(name), name.trim(), System.currentTimeMillis(), ""))
        require(saved != -1L) { "A collection with that name already exists." }
    }
    suspend fun deleteCollection(key: StudyChapterKey, id: String) = dao.deleteCollection(id, key.source, key.novelId)

    /** Atomic append: never overwrites earlier evidence or recreates a deleted collection. */
    suspend fun append(collection: TermCollectionEntity, input: AiChapterInput, result: AiResult) = withContext(Dispatchers.Default) {
        require(result.action == AiAction.EXTRACT && result.sourceFingerprint == input.fingerprint())
        require(collection.source == input.source && collection.novelId == input.novelId && collection.target == input.extractionTarget)
        val rows = discoveries(collection.id, input, result)
        database.withTransaction {
            currentCoroutineContext().ensureActive()
            val current = dao.collection(collection.id)
            require(current != null && current.source == collection.source && current.novelId == collection.novelId && current.target == collection.target) {
                "The collection changed during extraction. Reopen it and try again."
            }
            val existing = dao.discoveryIds(collection.id).toHashSet()
            val additions = rows.filterNot { it.id in existing }
            require(existing.size + additions.size <= 10_000) { "This collection has reached 10,000 discoveries. Create a more specific collection; existing terms are kept." }
            dao.insertDiscoveries(additions)
            dao.updateCollection(current.copy(updatedAt = System.currentTimeMillis(), lastChapter = input.title))
        }
    }

    companion object {
        private val spaces = Regex("\\s+")
        fun termKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim().lowercase(Locale.ROOT).replace(spaces, " ")
        internal fun discoveries(collection: String, input: AiChapterInput, result: AiResult): List<TermDiscoveryEntity> {
            val fingerprint = input.fingerprint()
            return result.extractedTerms.map { term ->
                // Meaning/evidence changes become new contributions; identical rediscoveries are idempotent.
                val key = termKey(term.name)
                val id = aiHash(org.json.JSONArray(listOf(collection, key, input.chapterId, fingerprint,
                    term.paragraphId, term.kind, term.meaning, term.evidence)).toString())
                TermDiscoveryEntity(id, collection, key, term.name, term.kind, term.meaning, input.chapterId,
                    input.title, fingerprint, term.paragraphId, term.evidence, result.route.provider.name,
                    result.route.model, result.createdAtMs)
            }.distinctBy { it.id }
        }
        private fun ChapterNoteEntity.key() = StudyChapterKey(source, novelId, chapterId)
    }
}
