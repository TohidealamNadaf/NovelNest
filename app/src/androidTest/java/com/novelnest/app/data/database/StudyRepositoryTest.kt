package com.novelnest.app.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelnest.app.data.ai.*
import com.novelnest.app.data.study.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyRepositoryTest {
    private lateinit var database: NovelNestDatabase
    private lateinit var repository: StudyRepository
    private val key = StudyChapterKey("NOVEL_PHOENIX", "book", "chapter-1")
    private val input = AiChapterInput(key.source, key.novelId, key.chapterId, "Chapter 1", listOf("Qi gathers in the Core."), "Cultivation terms")
    private fun result(input: AiChapterInput) = AiResult(AiAction.EXTRACT, input.fingerprint(), AiProviderCatalog.models.first().route,
        extractedTerms = listOf(AiTerm("Qi", "CONCEPT", "Gathered energy", 0, "Qi gathers")))

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NovelNestDatabase::class.java).build()
        repository = StudyRepository(database)
    }
    @After fun tearDown() { database.close() }

    @Test fun notesPersistFullMarkdownButListsReadOnlySmallPreviews() = runBlocking {
        val markdown = "**Actual note**\n".repeat(200)
        repository.saveNote(key, null, "Title", markdown)
        val preview = repository.notes(key).first().single()
        assertEquals(markdown.take(240), preview.preview)
        assertEquals(markdown, repository.note(key, preview.id).markdown)
        repository.saveNote(key, preview.id, "Edited", "# Edited text")
        assertEquals("# Edited text", repository.note(key, preview.id).markdown)
        repository.deleteNote(key.copy(chapterId = "another"), preview.id)
        assertEquals(1, repository.notes(key).first().size)
        repository.deleteNote(key, preview.id)
        assertTrue(repository.notes(key).first().isEmpty())
    }

    @Test fun noteIdentityCannotCrossSourceNovelOrChapter() = runBlocking {
        repository.saveNote(key, null, "Mine", "private note")
        val id = repository.notes(key).first().single().id
        for (other in listOf(key.copy(source = "FREE_WEB_NOVEL"), key.copy(novelId = "other"), key.copy(chapterId = "other"))) {
            assertTrue(repository.notes(other).first().isEmpty())
            try { repository.saveNote(other, id, "Wrong", "Overwrite"); fail("Scope should be rejected") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals("private note", repository.note(key, id).markdown)
    }

    @Test fun validationDoesNotOverwriteExistingNotes() = runBlocking {
        repository.saveNote(key, null, "Title", "Original")
        val id = repository.notes(key).first().single().id
        try { repository.saveNote(key, id, "Title", "x".repeat(64_001)); fail("Should reject oversized notes") }
        catch (_: IllegalArgumentException) { }
        assertEquals("Original", repository.note(key, id).markdown)
    }

    @Test fun repeatedExtractionIsIdempotentAndLaterChaptersAppend() = runBlocking {
        repository.addCollection(key, "Cultivation terms")
        val collection = repository.collections(key).first().single().collection
        repository.append(collection, input, result(input))
        repository.append(collection, input, result(input))
        assertEquals(1, repository.discoveries(collection.id).first().size)
        val second = input.copy(chapterId = "chapter-2", title = "Chapter 2")
        repository.append(collection, second, result(second))
        val rows = repository.discoveries(collection.id).first()
        assertEquals(setOf("chapter-1", "chapter-2"), rows.map { it.chapterId }.toSet())
        assertEquals(1, repository.collections(key).first().single().termCount)
        assertTrue(repository.collections(key.copy(novelId = "other")).first().isEmpty())
    }

    @Test fun deletingCollectionDuringGenerationCannotRecreateItOrEraseOtherCollections() = runBlocking {
        repository.addCollection(key, "Cultivation terms")
        val collection = repository.collections(key).first().single().collection
        repository.append(collection, input, result(input))
        repository.addCollection(key, "People")
        repository.deleteCollection(key, collection.id)
        assertTrue(repository.discoveries(collection.id).first().isEmpty())
        try { repository.append(collection, input, result(input)); fail("Deleted collection should not be recreated") }
        catch (_: IllegalArgumentException) { }
        assertEquals(listOf("People"), repository.collections(key).first().map { it.collection.name })
    }

    @Test fun duplicateCategoryNamesAreRejectedWithoutDeletingExistingDiscoveries() = runBlocking {
        repository.addCollection(key, "Cultivation terms")
        val collection = repository.collections(key).first().single().collection
        repository.append(collection, input, result(input))
        try { repository.addCollection(key, "  CULTIVATION   terms  "); fail("Duplicate should be rejected") }
        catch (_: IllegalArgumentException) { }
        assertEquals(1, repository.collections(key).first().size)
        assertEquals(1, repository.discoveries(collection.id).first().size)
    }
}
