package com.novelnest.app.data.local.epub

import androidx.test.ext.junit.runners.AndroidJUnit4
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class EpubOrganizationIntegrationTest {
    @Test fun moveRenameReorderAndRemoveSeriesKeepPositionsAndFileIdentity() = runBlocking {
        EpubLibraryTestStore().use { store ->
            val repo = store.repository
            val a = repo.createCollection("Series A")
            val b = repo.createCollection("Series B")
            val first = store.dao.insertOrganized(store.book(1), a)
            val second = store.dao.insertOrganized(store.book(2), a)
            val file = repo.bookFile(first.id).apply { parentFile!!.mkdirs(); writeText("app-owned fixture bytes") }
            val position = EpubPositionEntity(first.id, "{}", 0.4, "Saved chapter", 55)
            repo.savePosition(position)
            store.snapshot { it.books.size == 2 && it.books.firstOrNull { book -> book.book.id == first.id }?.position == position.preview() }
            repo.reorderVolumes(a, listOf(second.id, first.id))
            assertEquals(listOf(second.id, first.id), store.dao.collectionBookIds(a))
            repo.labelVolume(first.id, "Volume 1.5")
            repo.renameCollection(a, "Renamed series")
            repo.moveBook(first.id, b)
            assertEquals(b, store.dao.book(first.id)!!.collectionId)
            assertEquals("Volume 1.5", store.dao.book(first.id)!!.volumeLabel)
            assertEquals(position, repo.position(first.id))
            repo.removeCollection(b)
            assertNull(store.dao.book(first.id)!!.collectionId)
            assertEquals(position, repo.position(first.id))
            assertEquals("app-owned fixture bytes", file.readText())
            assertEquals(2, store.dao.ids().size)
        }
    }
    @Test fun positionEditsRefreshTheSnapshotEvenWhenRowCountsStayEqual() = runBlocking {
        EpubLibraryTestStore().use { store ->
            val book = store.book(1)
            store.dao.insert(book)
            store.repository.savePosition(EpubPositionEntity(book.id, "{}", 0.1, "One", 1))
            store.snapshot { it.books.singleOrNull()?.position?.progression == 0.1 }
            store.repository.savePosition(EpubPositionEntity(book.id, "{new}", 0.8, "Two", 2))
            store.snapshot { it.books.singleOrNull()?.position?.progression == 0.8 }
            Unit
        }
    }
    @Test fun staleReorderAndMissingDestinationsDoNotEraseOrDetachVolumes() = runBlocking {
        EpubLibraryTestStore().use { store ->
            val group = store.repository.createCollection("Series")
            val one = store.dao.insertOrganized(store.book(1), group)
            val two = store.dao.insertOrganized(store.book(2), group)
            assertTrue(runCatching { store.repository.reorderVolumes(group, listOf(one.id)) }.isFailure)
            assertEquals(listOf(one.id, two.id), store.dao.collectionBookIds(group))
            assertTrue(runCatching { store.repository.moveBook(one.id, "missing") }.isFailure)
            assertEquals(group, store.dao.book(one.id)!!.collectionId)
            assertTrue(runCatching { store.repository.createCollection(" SERIES ") }.isFailure)
        }
    }
    @Test fun batchImportsContinuePastBadFilesAndDuplicatesKeepTheirLocation() = runBlocking {
        EpubLibraryTestStore().use { store ->
            val repo = store.repository
            val target = repo.createCollection("Volumes")
            val first = store.fixture(1)
            val bad = Uri.fromFile(File(store.root, "bad.epub").apply { writeText("not an EPUB") })
            val second = store.fixture(2)
            repo.importBooks(listOf(first, bad, second), target)
            val result = withTimeout(20_000) { repo.importState.first { it is EpubImportState.Complete || it is EpubImportState.Failed } }
            assertTrue(result.toString(), result is EpubImportState.Complete)
            result as EpubImportState.Complete
            assertEquals(2, result.importedCount); assertEquals(1, result.failedCount)
            val snapshot = store.snapshot { it.books.size == 2 }
            assertTrue(snapshot.books.all { it.book.collectionId == target })
            val existing = snapshot.books.first().book
            repo.savePosition(EpubPositionEntity(existing.id, "{}", 0.3, "Saved", 4))
            val other = repo.createCollection("Other")
            repo.importBooks(listOf(first, second), other)
            val duplicate = withTimeout(20_000) { repo.importState.first { it is EpubImportState.Complete || it is EpubImportState.Failed } }
            assertTrue(duplicate.toString(), duplicate is EpubImportState.Complete)
            duplicate as EpubImportState.Complete
            assertEquals(0, duplicate.importedCount); assertEquals(2, duplicate.duplicateCount)
            assertEquals(target, store.dao.book(existing.id)!!.collectionId)
            assertEquals(0.3, repo.position(existing.id)!!.progression, 0.0)
        }
    }
    @Test fun cancellingBatchKeepsAlreadyCommittedVolumes() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val hold = CompletableDeferred<Unit>()
        val opens = AtomicInteger()
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine = ReadiumEpubEngine(app)
        EpubLibraryTestStore(object : EpubEngine {
            override suspend fun open(file: File): org.readium.r2.shared.publication.Publication {
                if (opens.incrementAndGet() == 2) { entered.complete(Unit); hold.await() }
                return engine.open(file)
            }
        }).use { store ->
            val target = store.repository.createCollection("Series")
            store.repository.importBooks(listOf(store.fixture(1), store.fixture(2)), target)
            withTimeout(20_000) { entered.await() }
            store.repository.cancelImport()
            withTimeout(5000) { store.repository.importState.first { it is EpubImportState.Idle } }
            val kept = store.snapshot { it.books.size == 1 }.books.single().book
            assertEquals(target, kept.collectionId)
            assertTrue(store.repository.bookFile(kept.id).isFile)
        }
    }
    @Test fun seriesRemovedDuringImportSavesTheBookStandaloneWithNotice() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val hold = CompletableDeferred<Unit>()
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine = ReadiumEpubEngine(app)
        EpubLibraryTestStore(object : EpubEngine {
            override suspend fun open(file: File): org.readium.r2.shared.publication.Publication {
                entered.complete(Unit); hold.await(); return engine.open(file)
            }
        }).use { store ->
            val target = store.repository.createCollection("Series")
            store.repository.importBooks(listOf(store.fixture(1)), target)
            withTimeout(20_000) { entered.await() }
            store.repository.removeCollection(target)
            hold.complete(Unit)
            val result = withTimeout(20_000) { store.repository.importState.first { it is EpubImportState.Complete || it is EpubImportState.Failed } }
            assertTrue(result.toString(), result is EpubImportState.Complete)
            result as EpubImportState.Complete
            assertNull(result.book.collectionId)
            assertNotNull(result.message)
            assertTrue(store.repository.bookFile(result.book.id).isFile)
        }
    }
}
