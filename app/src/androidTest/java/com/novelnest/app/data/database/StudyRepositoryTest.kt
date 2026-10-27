package com.novelnest.app.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelnest.app.data.ai.*
import com.novelnest.app.data.study.*
import com.novelnest.app.feature.reader.ReaderStudyController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
        val anotherChapter = key.copy(chapterId = "chapter-2")
        assertEquals(preview, repository.notes(anotherChapter).first().single())
        assertEquals(markdown, repository.note(anotherChapter, preview.id).markdown)
        repository.saveNote(anotherChapter, preview.id, "Edited", "# Edited text")
        assertEquals("# Edited text", repository.note(key, preview.id).markdown)
        assertEquals(key.chapterId, repository.note(anotherChapter, preview.id).chapterId)
        assertEquals("Edited", repository.notes(anotherChapter).first().single().title)
        repository.deleteNote(anotherChapter, preview.id)
        assertTrue(repository.notes(key).first().isEmpty())
        assertTrue(repository.notes(anotherChapter).first().isEmpty())
    }

    @Test fun noteIdentityCannotCrossSourceOrNovel() = runBlocking {
        repository.saveNote(key, null, "Mine", "private note")
        val id = repository.notes(key).first().single().id
        for (other in listOf(key.copy(source = "FREE_WEB_NOVEL"), key.copy(novelId = "other"))) {
            assertTrue(repository.notes(other).first().isEmpty())
            try { repository.note(other, id); fail("Reading another novel's note should be rejected") }
            catch (_: IllegalArgumentException) { }
            try { repository.saveNote(other, id, "Wrong", "Overwrite"); fail("Scope should be rejected") }
            catch (_: IllegalArgumentException) { }
            repository.deleteNote(other, id)
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
        assertEquals(collection.id, repository.collections(key.copy(chapterId = "chapter-83")).first().single().collection.id)
        assertTrue(repository.collections(key.copy(novelId = "other")).first().isEmpty())
        assertTrue(repository.collections(key.copy(source = "FREE_WEB_NOVEL")).first().isEmpty())
    }

    @Test fun readerKeepsNovelCollectionsAndObservesNewDiscoveriesAcrossChapters() = runBlocking {
        repository.addCollection(key, "Cultivation terms")
        val collection = repository.collections(key).first().single().collection
        repository.append(collection, input, result(input))
        val scope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]))
        val controller = ReaderStudyController(scope, repository)
        try {
            controller.open(key, true)
            val loaded = withTimeout(5_000) { controller.state.first { it.collections.size == 1 } }
            controller.selectCollection(collection.id)
            val selected = withTimeout(5_000) { controller.state.first { it.discoveries.size == 1 } }

            val nextKey = key.copy(chapterId = "chapter-83")
            controller.open(nextKey, true)
            // The chapter switch must not clear an already loaded novel list or its selected terms.
            assertEquals(nextKey, controller.state.value.key)
            assertSame(loaded.collections, controller.state.value.collections)
            assertSame(selected.discoveries, controller.state.value.discoveries)
            assertEquals(collection.id, controller.state.value.selectedCollectionId)

            val nextInput = input.copy(chapterId = nextKey.chapterId, title = "Chapter 83")
            repository.append(collection, nextInput, result(nextInput))
            val appended = withTimeout(5_000) { controller.state.first { it.discoveries.size == 2 } }
            assertEquals(setOf(key.chapterId, nextKey.chapterId), appended.discoveries.map { it.chapterId }.toSet())

            controller.open(nextKey, false)
            assertTrue(controller.state.value.collections.isEmpty())
            assertTrue(controller.state.value.discoveries.isEmpty())
            controller.open(key.copy(chapterId = "chapter-200"), true)
            withTimeout(5_000) { controller.state.first { it.collections.size == 1 } }
            controller.selectCollection(collection.id)
            assertEquals(2, withTimeout(5_000) { controller.state.first { it.discoveries.size == 2 } }.discoveries.size)

            // Removing the collection from a later chapter updates the list and selected detail together.
            repository.deleteCollection(nextKey, collection.id)
            withTimeout(5_000) { controller.state.first { it.collections.isEmpty() && it.selectedCollectionId == null } }
            assertTrue(controller.state.value.discoveries.isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun readerCollectionsDoNotLeakAcrossNovelsOrSourcesAndCanReload() = runBlocking {
        repository.addCollection(key, "Cultivation terms")
        val collection = repository.collections(key).first().single().collection
        repository.append(collection, input, result(input))
        val scope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]))
        val controller = ReaderStudyController(scope, repository)
        try {
            controller.open(key, true)
            withTimeout(5_000) { controller.state.first { it.collections.isNotEmpty() } }
            controller.selectCollection(collection.id)
            withTimeout(5_000) { controller.state.first { it.discoveries.isNotEmpty() } }
            for (other in listOf(key.copy(novelId = "another-book"), key.copy(source = "FREE_WEB_NOVEL"))) {
                controller.open(other, true)
                assertTrue(controller.state.value.collections.isEmpty())
                assertTrue(controller.state.value.discoveries.isEmpty())
                controller.selectCollection(collection.id)
                assertNull(controller.state.value.selectedCollectionId)
                controller.open(key.copy(chapterId = "chapter-2"), true)
                withTimeout(5_000) { controller.state.first { it.collections.singleOrNull()?.collection?.id == collection.id } }
            }
        } finally { scope.cancel() }
    }

    @Test fun creatingCollectionDuringChapterSwitchKeepsWriteGateUntilCommit() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]))
        val controller = ReaderStudyController(scope, repository)
        try {
            controller.open(key, true)
            controller.addCollection("Monster levels")
            assertTrue(controller.state.value.saving)
            val nextKey = key.copy(chapterId = "chapter-2")
            controller.open(nextKey, true)
            assertTrue(controller.state.value.saving)
            controller.addCollection("Duplicate rapid action")
            val saved = withTimeout(5_000) { controller.state.first { !it.saving && it.collections.isNotEmpty() } }
            assertEquals(nextKey, saved.key)
            assertEquals(listOf("Monster levels"), saved.collections.map { it.collection.name })
            assertEquals("Monster levels", repository.collections(key.copy(chapterId = "chapter-3")).first().single().collection.name)
        } finally { scope.cancel() }
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

    @Test fun clearKeepsTopicAndRejectsOlderInFlightResults() = runBlocking {
        repository.addCollection(key, "Cultivation terms")
        val previous = repository.collections(key).first().single().collection
        repository.append(previous, input, result(input))
        repository.addCollection(key, "People")
        try { repository.clearCollection(key.copy(novelId = "other"), previous.id); fail("Scope must match") }
        catch (_: IllegalArgumentException) { }
        assertEquals(1, repository.discoveries(previous.id).first().size)
        repository.clearCollection(key, previous.id)
        val cleared = repository.collections(key).first().first { it.collection.name == previous.name }
        assertEquals(previous.target, cleared.collection.target)
        assertNotEquals(previous.id, cleared.collection.id)
        assertEquals(0, cleared.termCount)
        assertTrue(repository.discoveries(previous.id).first().isEmpty())
        try { repository.append(previous, input, result(input)); fail("Stale result must not repopulate cleared data") }
        catch (_: IllegalArgumentException) { }
        repository.append(cleared.collection, input, result(input))
        assertEquals(1, repository.discoveries(cleared.collection.id).first().size)
        assertEquals(2, repository.collections(key).first().size)
    }
}
