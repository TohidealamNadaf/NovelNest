package com.novelnest.app.data.local.epub

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelnest.app.feature.epub.EpubReaderState
import com.novelnest.app.feature.epub.EpubReaderViewModel
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.*
import org.readium.r2.shared.util.*
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.resource.Resource

/** Locator parsing needs the real Android Uri implementation. Engine faults are deliberately injected. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class EpubLifecycleTest {
    @get:Rule val temp = TemporaryFolder()
    private val id = "epub-" + "a".repeat(64)
    private fun book() = LocalBookEntity(id, "a".repeat(64), "Book", "Author", "", 1, 20)
    private fun locator() = checkNotNull(Locator.fromJSON(JSONObject("""{"href":"one.xhtml","type":"application/xhtml+xml","locations":{"progression":0.4}}""")))
    private class Dao(initial: LocalBookEntity) : LocalBookDao {
        val books = mutableMapOf(initial.id to initial)
        val positions = mutableMapOf<String, EpubPositionEntity>()
        val saved = mutableListOf<EpubPositionEntity>()
        private val groups = mutableMapOf<String, EpubCollectionEntity>()
        override fun observeShelfChanges() = flowOf(0L)
        override suspend fun libraryEntries() = books.values.map { LocalBookEntry(it, positions[it.id]?.preview()) }
        override suspend fun collections() = groups.values.toList()
        override suspend fun collection(id: String) = groups[id]
        override suspend fun insertCollection(collection: EpubCollectionEntity) { groups[collection.id] = collection }
        override suspend fun renameCollection(id: String, name: String, nameKey: String): Int {
            val group = groups[id] ?: return 0
            groups[id] = group.copy(name = name, nameKey = nameKey); return 1
        }
        override suspend fun deleteCollection(id: String) { groups.remove(id) }
        override suspend fun collectionBookIds(id: String) = books.values.filter { it.collectionId == id }.sortedBy { it.volumeOrder }.map { it.id }
        override suspend fun lastVolumeOrder(id: String) = books.values.filter { it.collectionId == id }.maxOfOrNull { it.volumeOrder } ?: -1L
        override suspend fun moveBook(id: String, collectionId: String?, order: Long): Int {
            val book = books[id] ?: return 0
            books[id] = book.copy(collectionId = collectionId, volumeOrder = order); return 1
        }
        override suspend fun labelVolume(id: String, label: String?): Int {
            val book = books[id] ?: return 0
            books[id] = book.copy(volumeLabel = label); return 1
        }
        override fun observeLibrary() = flowOf(books.values.map { LocalBookEntry(it, positions[it.id]?.preview()) })
        override suspend fun book(id: String) = books[id]
        override suspend fun position(id: String) = positions[id]
        override suspend fun insert(book: LocalBookEntity): Long { books[book.id] = book; return 1 }
        override suspend fun savePosition(position: EpubPositionEntity) { positions[position.bookId] = position; saved.add(position) }
        override suspend fun delete(id: String) { books.remove(id); positions.remove(id) }
        override suspend fun ids() = books.keys.toList()
    }
    private fun repository(dao: Dao, scope: CoroutineScope, dispatcher: CoroutineDispatcher,
        open: suspend () -> Publication): EpubRepository {
        val files = temp.newFolder()
        val context = object : ContextWrapper(null) {
            override fun getFilesDir() = files
            override fun getApplicationContext(): Context = this
            override fun getContentResolver() = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>().contentResolver
        }
        return EpubRepository(context, dao, object : EpubEngine {
            override suspend fun open(file: File) = open()
        }, scope = scope, ioDispatcher = dispatcher)
    }
    private fun position(progress: Double = 0.4) = EpubPositionEntity(id, locator().toJSON().toString(), progress, "Chapter", 100)
    private fun publication(onClose: () -> Unit = {}): Publication {
        val manifest = checkNotNull(Manifest.fromJSON(JSONObject("""{"metadata":{"title":"Book"},"readingOrder":[{"href":"one.xhtml","type":"application/xhtml+xml"}]}""")))
        return Publication(manifest, object : Container<Resource> {
            override val entries: Set<Url> = emptySet()
            override fun get(url: Url): Resource? = null
            override fun close() = onClose()
        })
    }
    private fun importFixture(): Uri {
        val file = temp.newFile("cancellation.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, text) in listOf("mimetype" to "application/epub+zip", "META-INF/container.xml" to "<container/>")) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
            }
        }
        return Uri.fromFile(file)
    }
    @Test fun cancellationBeforeImportStartsStillReturnsToIdle() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val repo = repository(Dao(book()), scope, dispatcher, { publication() })
            repo.import(Uri.fromFile(File(temp.root, "missing.epub")))
            repo.cancelImport()
            runCurrent()
            assertTrue(repo.importState.value is EpubImportState.Idle)
            repo.import(Uri.fromFile(File(temp.root, "missing.epub")))
            advanceUntilIdle()
            assertTrue(repo.importState.value is EpubImportState.Failed)
        } finally { scope.cancel() }
    }
    @Test fun cancelledImportFinishesCleanupBeforeAnotherImportMayStart() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val gate = CompletableDeferred<Unit>()
            var opens = 0
            val repo = repository(Dao(book()), scope, dispatcher, {
                opens++
                withContext(NonCancellable) { gate.await() }
                publication()
            })
            val uri = importFixture()
            repo.import(uri)
            runCurrent()
            assertEquals(1, opens)
            repo.cancelImport()
            repo.import(uri)
            assertTrue(repo.importState.value is EpubImportState.Working)
            gate.complete(Unit)
            advanceUntilIdle()
            assertTrue(repo.importState.value is EpubImportState.Idle)
            assertEquals(1, opens)
            repo.import(uri)
            advanceUntilIdle()
            // Thumbnail extraction uses real Dispatchers.IO. Advancing virtual
            // time cannot complete that work; await the same terminal contract
            // on a real-time bounded dispatcher instead of sampling Working.
            val completed = withContext(Dispatchers.IO) {
                withTimeout(15_000) {
                    repo.importState.first { it is EpubImportState.Complete || it is EpubImportState.Failed }
                }
            }
            assertTrue(completed.toString(), completed is EpubImportState.Complete)
            assertEquals(2, opens)
        } finally { scope.cancel() }
    }
    @Test fun contentsRoutingAcceptsSpineFragmentsAndRejectsOtherResources() {
        val pub = publication()
        try {
            assertTrue(pub.containsReadingResource(Url("one.xhtml#first")!!))
            assertFalse(pub.containsReadingResource(Url("missing.xhtml#section")!!))
            assertFalse(pub.containsReadingResource(Url("https://example.test/one.xhtml")!!))
            assertFalse(pub.containsReadingResource(Url("../private")!!))
        } finally { pub.close() }
    }
    @Test fun readerRestoresAggregatePercentageWhenInitialLocatorDoesNotHaveOne() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = ViewModelStore()
        try {
            val dao = Dao(book()).apply { positions[id] = position(0.65) }
            val repo = repository(dao, scope, dispatcher, { publication() })
            repo.bookFile(id).apply { parentFile!!.mkdirs(); writeText("engine fixture") }
            val vm = EpubReaderViewModel(id, repo, dispatcher)
            store.put("reader", vm)
            advanceUntilIdle()
            assertTrue(vm.state.value is EpubReaderState.Ready)
            vm.checkpoint(locator(), true)
            advanceUntilIdle()
            assertEquals(0.65, dao.positions[id]!!.progression, 0.0)
        } finally { store.clear(); advanceUntilIdle(); scope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun invalidLocatorSerializationCannotCrashPauseOrClose() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = ViewModelStore()
        try {
            val dao = Dao(book())
            val repo = repository(dao, scope, dispatcher, { publication() })
            repo.bookFile(id).apply { parentFile!!.mkdirs(); writeText("engine fixture") }
            val vm = EpubReaderViewModel(id, repo, dispatcher)
            store.put("reader", vm)
            advanceUntilIdle()
            vm.checkpoint(locator().copyWithLocations(progression = Double.NaN), true)
            vm.flush()
            assertNotNull(repo.positionError.value)
            assertTrue(dao.saved.isEmpty())
        } finally { store.clear(); advanceUntilIdle(); scope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun cancelledOpeningReleasesPublicationExactlyOnceWithoutPublishingReady() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = ViewModelStore()
        try {
            val gate = CompletableDeferred<Unit>()
            var closed = 0
            val repo = repository(Dao(book()), scope, dispatcher, {
                withContext(NonCancellable) { gate.await() }
                publication { closed++ }
            })
            repo.bookFile(id).apply { parentFile!!.mkdirs(); writeText("engine fixture") }
            val vm = EpubReaderViewModel(id, repo, dispatcher)
            store.put("reader", vm)
            runCurrent()
            store.clear()
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, closed)
            assertFalse(vm.state.value is EpubReaderState.Ready)
        } finally { scope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun openingFailureCanBeRetriedWithoutLeakingTheSuccessfulPublication() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = ViewModelStore()
        try {
            var opens = 0
            var closed = 0
            val repo = repository(Dao(book()), scope, dispatcher, {
                if (++opens == 1) throw IOException("first opening failed")
                publication { closed++ }
            })
            repo.bookFile(id).apply { parentFile!!.mkdirs(); writeText("engine fixture") }
            val vm = EpubReaderViewModel(id, repo, dispatcher)
            store.put("reader", vm)
            advanceUntilIdle()
            assertTrue(vm.state.value is EpubReaderState.Failed)
            vm.load()
            advanceUntilIdle()
            assertTrue(vm.state.value is EpubReaderState.Ready)
            assertEquals(0, closed)
            store.clear()
            advanceUntilIdle()
            assertEquals(1, closed)
        } finally { store.clear(); scope.cancel(); Dispatchers.resetMain() }
    }
}
