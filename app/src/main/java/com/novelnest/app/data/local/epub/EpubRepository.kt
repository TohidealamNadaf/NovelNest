@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.novelnest.app.data.local.epub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.readium.r2.shared.publication.Publication
import java.io.File
import java.util.UUID

sealed interface EpubImportState {
    data object Idle : EpubImportState
    data class Working(val message: String) : EpubImportState
    data class Complete(val book: LocalBookEntity, val duplicate: Boolean,
        val importedCount: Int = if (duplicate) 0 else 1,
        val duplicateCount: Int = if (duplicate) 1 else 0,
        val failedCount: Int = 0, val message: String? = null) : EpubImportState
    data class Failed(val message: String) : EpubImportState
}

class EpubRepository(context: Context, private val dao: LocalBookDao,
    val engine: EpubEngine = ReadiumEpubEngine(context),
    private val onRead: (Long) -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "epub_library")
    private val writes = Mutex()
    private val imports = Mutex()
    @Volatile private var recoveryComplete = false
    private val mutableImport = MutableStateFlow<EpubImportState>(EpubImportState.Idle)
    val importState = mutableImport.asStateFlow()
    private val mutableLibraryError = MutableStateFlow<String?>(null)
    val libraryError = mutableLibraryError.asStateFlow()
    private val libraryRevision = MutableStateFlow(0)
    private val mutablePositionError = MutableStateFlow<String?>(null)
    val positionError = mutablePositionError.asStateFlow()
    private val pendingPositions = java.util.concurrent.ConcurrentHashMap<String, EpubPositionEntity>()
    private val checkpoints = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    @Volatile private var lastSnapshot = EpubLibrarySnapshot()
    val shelf = libraryRevision.flatMapLatest {
        dao.observeShelfChanges().mapLatest { dao.librarySnapshot() }.onEach {
            lastSnapshot = it
            if (recoveryComplete) mutableLibraryError.value = null
        }.catch { error ->
            if (error is CancellationException) throw error
            mutableLibraryError.value = "Could not load the local library. Please retry. Your imported files are kept."
            emit(lastSnapshot.copy(loaded = true))
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), EpubLibrarySnapshot())
    val library = shelf.map { snapshot -> snapshot.books.sortedByDescending { it.position?.updatedAt ?: it.book.importedAt } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var importJob: Job? = null

    init {
        scope.launch {
            try { ensureRecovered() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { reportRecoveryFailure() }
        }
        scope.launch {
            val failedBooks = mutableSetOf<String>()
            for (signal in checkpoints) {
                for (position in pendingPositions.values.toList()) {
                    for (attempt in 0..2) {
                        try {
                            savePosition(position)
                            pendingPositions.remove(position.bookId, position)
                            failedBooks.remove(position.bookId)
                            runCatching { onRead(position.updatedAt) }
                            break
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            if (attempt < 2) delay(250L * (attempt + 1))
                            else failedBooks.add(position.bookId)
                        }
                    }
                }
                failedBooks.retainAll(pendingPositions.keys)
                mutablePositionError.value = if (failedBooks.isEmpty()) null else "Reading position could not be saved. Check device storage and tap Retry."
            }
        }
    }
    fun reloadLibrary() {
        scope.launch {
            try { ensureRecovered(); libraryRevision.value += 1 }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { reportRecoveryFailure() }
        }
    }
    private suspend fun ensureRecovered() = writes.withLock {
        if (!recoveryComplete) {
            recover()
            recoveryComplete = true
            mutableLibraryError.value = null
        }
    }
    private fun reportRecoveryFailure() {
        mutableLibraryError.value = "Local library recovery failed. Your files are kept. Check storage and tap Retry library."
    }
    fun reportPositionFailure() {
        mutablePositionError.value = "Reading position could not be saved. Check device storage and tap Retry."
    }
    internal fun releasePublication(publication: Publication) {
        scope.launch(ioDispatcher) {
            try { publication.close() }
            catch (error: Exception) { android.util.Log.w("EpubRepository", "Could not release EPUB resources", error) }
        }
    }
    fun retryPositionWrites() { checkpoints.trySend(Unit) }
    fun enqueuePosition(position: EpubPositionEntity) {
        if (!validPosition(position)) { reportPositionFailure(); return }
        pendingPositions.compute(position.bookId) { _, _ ->
            // Arrival order is monotonic even when the device wall clock changes.
            position
        }
        checkpoints.trySend(Unit)
    }
    private fun directory(id: String): File {
        require(id.matches(Regex("epub-[0-9a-f]{64}"))) { "Invalid local book identifier" }
        return File(root, id)
    }
    fun bookFile(id: String) = File(directory(id), "book.epub")
    fun coverFile(id: String) = File(directory(id), "cover.jpg")
    suspend fun book(id: String) = dao.book(id)
    suspend fun position(id: String) = dao.position(id)
    private fun validPosition(position: EpubPositionEntity) =
        position.bookId.matches(Regex("epub-[0-9a-f]{64}")) && position.progression.isFinite() &&
            position.progression in 0.0..1.0 && position.locatorJson.length in 1..64000
    suspend fun savePosition(position: EpubPositionEntity) = withContext(ioDispatcher) {
        require(validPosition(position)) { "Invalid EPUB reading position" }
        ensureRecovered()
        writes.withLock { if (dao.book(position.bookId) != null) dao.savePosition(position) }
    }
    fun import(uri: Uri) { importBooks(listOf(uri)) }

    /** One bounded queue; the destination is captured once and never follows page navigation. */
    @Synchronized fun importBooks(uris: List<Uri>, destination: String? = null): Boolean {
        if (importJob?.isCompleted == false) return false
        if (uris.isEmpty()) return true
        if (uris.size > MAX_IMPORT_BATCH) {
            mutableImport.value = EpubImportState.Failed("Select up to " + MAX_IMPORT_BATCH + " EPUBs at a time.")
            return false
        }
        val selected = uris.distinct().toList()
        mutableImport.value = EpubImportState.Working("Preparing import...")
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                ensureRecovered()
                if (destination != null && dao.collection(destination) == null)
                    throw EpubImportException("The destination series no longer exists. Select a series and import again.")
                var imported = 0
                var duplicates = 0
                var failed = 0
                var last: EpubImportState.Complete? = null
                var firstFailure: String? = null
                val notices = linkedSetOf<String>()
                imports.withLock {
                    selected.forEachIndexed { index, uri ->
                        currentCoroutineContext().ensureActive()
                        fun stage(message: String) {
                            mutableImport.value = EpubImportState.Working(
                                if (selected.size == 1) message else (index + 1).toString() + " / " + selected.size + " - " + message)
                        }
                        try {
                            stage("Copying EPUB...")
                            val result = importFile(uri, destination, ::stage)
                            if (result.duplicate) duplicates++ else imported++
                            result.message?.let(notices::add)
                            last = result
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { failed++; if (firstFailure == null) firstFailure = importFailure(error) }
                    }
                }
                val result = last
                mutableImport.value = if (result == null) EpubImportState.Failed(firstFailure ?: "No EPUBs were imported.") else {
                    if (failed > 0) notices.add(firstFailure.orEmpty())
                    result.copy(importedCount = imported, duplicateCount = duplicates, failedCount = failed,
                        message = notices.takeIf { it.isNotEmpty() }?.joinToString(" "))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableImport.value = EpubImportState.Failed(importFailure(error)) }
        }
        importJob = job
        job.invokeOnCompletion {
            synchronized(this) {
                if (importJob === job && job.isCancelled) mutableImport.value = EpubImportState.Idle
            }
        }
        job.start()
        return true
    }
    private fun importFailure(error: Exception): String = when (error) {
        is EpubImportException -> error.message.orEmpty()
        is java.util.zip.ZipException -> "The selected file is damaged or is not a valid EPUB archive."
        is java.io.FileNotFoundException -> "The selected file is no longer accessible. Choose it again from the document picker."
        else -> "Could not import this EPUB. Check file access and available device storage, then try again."
    }
    @Synchronized fun cancelImport() {
        if (importJob?.isCompleted == false) {
            mutableImport.value = EpubImportState.Working("Cancelling import...")
            importJob?.cancel()
        }
    }
    fun clearImportMessage() { if (mutableImport.value !is EpubImportState.Working) mutableImport.value = EpubImportState.Idle }

    private suspend fun importFile(uri: Uri, destinationCollection: String?, stageMessage: (String) -> Unit): EpubImportState.Complete {
        if (!root.isDirectory && !root.mkdirs()) throw EpubImportException("The local book folder is unavailable. Check device storage.")
        val stage = File(root, ".import-" + UUID.randomUUID())
        if (!stage.mkdir()) throw EpubImportException("Could not create an import folder. Check device storage.")
        var finalized: File? = null
        var committed = false
        try {
            val context = currentCoroutineContext()
            val file = File(stage, "book.epub")
            val stream = app.contentResolver.openInputStream(uri) ?: throw EpubImportException("The selected document cannot be opened.")
            val (hash, bytes) = stream.use { EpubArchiveValidator.copyAndHash(it, file, { context.ensureActive() }) }
            val id = "epub-" + hash
            dao.book(id)?.let { existing ->
                if (!bookFile(id).isFile) {
                    // A backup restored metadata without its large publication: reimport repairs the file.
                    EpubArchiveValidator.validate(file) { context.ensureActive() }
                    val publication = engine.open(file)
                    try { saveCover(publication, File(stage, "cover.jpg")) } finally { publication.close() }
                    writes.withLock {
                        if (dao.book(id) == null) throw EpubImportException("The book was removed during import. Please import again.")
                        directory(id).mkdirs()
                        if (!file.renameTo(bookFile(id))) throw EpubImportException("Could not restore the imported book file.")
                        File(stage, "cover.jpg").takeIf { it.exists() }?.renameTo(coverFile(id))
                    }
                }
                return EpubImportState.Complete(existing, true, message =
                    if (destinationCollection != null && existing.collectionId != destinationCollection)
                        "Already imported EPUBs kept their existing location. Use Move to organize them." else null)
            }
            stageMessage("Checking EPUB and reading book information...")
            EpubArchiveValidator.validate(file) { context.ensureActive() }
            val publication = engine.open(file)
            val book = try {
                val metadata = publication.metadata
                saveCover(publication, File(stage, "cover.jpg"))
                LocalBookEntity(id, hash, metadata.title?.take(1000)?.takeIf { it.isNotBlank() } ?: "Untitled book",
                    metadata.authors.joinToString(", ") { it.name }.take(2000), metadata.description.orEmpty().take(16000), System.currentTimeMillis(), bytes)
            } finally { publication.close() }
            context.ensureActive()
            var savedBook = book
            writes.withLock {
                val destination = directory(id)
                if (destination.exists() && dao.book(id) == null) destination.deleteRecursively()
                File(stage, ".pending-import").writeText("1")
                if (!stage.renameTo(destination)) throw EpubImportException("Could not save the imported EPUB. Check device storage.")
                finalized = destination
                // File and Room cannot share a transaction. Recovery removes any orphan left before this commit.
                withContext(NonCancellable) {
                    savedBook = dao.insertOrganized(book, destinationCollection)
                    committed = true
                    File(destination, ".pending-import").delete()
                }
            }
            return EpubImportState.Complete(savedBook, false, message =
                if (destinationCollection != null && savedBook.collectionId == null)
                    "The destination series was removed. Imported books were saved in My Library." else null)
        } finally {
            stage.deleteRecursively()
            if (!committed) finalized?.deleteRecursively()
        }
    }

    suspend fun createCollection(name: String): String = withContext(ioDispatcher) {
        ensureRecovered()
        val title = checkedLabel(name, 160)
        val id = "series-" + UUID.randomUUID()
        writes.withLock { dao.insertCollection(EpubCollectionEntity(id, title, title.lowercase(java.util.Locale.ROOT), System.currentTimeMillis())) }
        id
    }
    suspend fun renameCollection(id: String, name: String) = withContext(ioDispatcher) {
        ensureRecovered()
        val title = checkedLabel(name, 160)
        writes.withLock { check(dao.renameCollection(id, title, title.lowercase(java.util.Locale.ROOT)) == 1) { "This series no longer exists." } }
    }
    suspend fun removeCollection(id: String) = withContext(ioDispatcher) {
        ensureRecovered()
        writes.withLock { dao.deleteCollection(id) } // FK SET NULL keeps every EPUB and its position.
    }
    suspend fun moveBook(id: String, destination: String?) = withContext(ioDispatcher) {
        ensureRecovered()
        writes.withLock { dao.organizeBook(id, destination) }
    }
    suspend fun moveBooks(ids: List<String>, destination: String?) = withContext(ioDispatcher) {
        val selected = ids.distinct().toList()
        ensureRecovered()
        writes.withLock { dao.organizeBooks(selected, destination) }
    }

    suspend fun removeBooksFromSeries(ids: List<String>, sourceCollection: String) = withContext(ioDispatcher) {
        val selected = ids.distinct().toList()
        ensureRecovered()
        writes.withLock { dao.detachBooks(selected, sourceCollection) }
    }

    /** Only explicitly selected books are deleted; removing folders keeps their other volumes. */
    suspend fun removeItems(bookIds: List<String>, collectionIds: List<String>): Int = withContext(ioDispatcher + NonCancellable) {
        val books = bookIds.distinct().toList()
        val collections = collectionIds.distinct().toList()
        val directories = books.map(::directory) // Validate every ID before filesystem mutation.
        ensureRecovered()
        writes.withLock {
            books.forEach { require(dao.book(it) != null) { "The selection changed. Select the EPUBs again." } }
            collections.forEach { require(dao.collection(it) != null) { "The selection changed. Select the series again." } }
            val journaled = mutableListOf<File>()
            try {
                directories.filter { it.isDirectory }.forEach { directory ->
                    val marker = File(directory, ".pending-delete")
                    journaled.add(marker)
                    marker.writeText("1")
                }
                dao.removeShelfItems(books, collections)
            } catch (error: Exception) {
                journaled.forEach { it.delete() }
                throw error
            }
            books.forEach(pendingPositions::remove)
            // Keep a journal if OS file handles/storage prevent cleanup; recovery retries it.
            directories.count { directory ->
                try { directory.exists() && !directory.deleteRecursively() }
                catch (_: Exception) { true }
            }
        }
    }

    suspend fun labelVolume(id: String, label: String) = withContext(ioDispatcher) {
        ensureRecovered()
        val title = label.trim().takeIf { it.isNotEmpty() }?.let { checkedLabel(it, 128) }
        writes.withLock { check(dao.labelVolume(id, title) == 1) { "This EPUB is no longer in the library." } }
    }
    suspend fun reorderVolumes(id: String, orderedIds: List<String>) = withContext(ioDispatcher) {
        ensureRecovered()
        val snapshot = orderedIds.toList()
        writes.withLock { dao.reorderVolumes(id, snapshot) }
    }
    private fun checkedLabel(value: String, limit: Int): String = value.trim().also {
        require(it.isNotEmpty() && it.length <= limit && it.none { char -> Character.isISOControl(char) }) {
            "Enter a name of 1 to " + limit + " characters."
        }
    }

    private suspend fun saveCover(publication: Publication, target: File) {
        val link = publication.linkWithRel("cover") ?: return
        val resource = publication.get(link) ?: return
        try {
            val data = resource.read().getOrNull() ?: return
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return
            var sample = 1
            while (bounds.outWidth / sample > 720 || bounds.outHeight / sample > 1000) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
            try { target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
            finally { bitmap.recycle() }
        } finally { resource.close() }
    }
    suspend fun delete(id: String) = withContext(Dispatchers.IO + NonCancellable) {
        ensureRecovered()
        writes.withLock {
            val directory = directory(id)
            if (directory.isDirectory) File(directory, ".pending-delete").writeText("1")
            dao.delete(id)
            pendingPositions.remove(id)
            directory.deleteRecursively() // recovery retries app-owned orphan cleanup after interrupted deletion
        }
    }
    companion object { const val MAX_IMPORT_BATCH = 100 }

    private suspend fun recover() {
        val ids = dao.ids().toSet() // if storage fails, preserve all files
        val directories = if (!root.exists()) emptyArray() else root.listFiles()
            ?: throw EpubImportException("The local book folder cannot be read.")
        directories.forEach { directory ->
            when {
                directory.name.startsWith(".import-") -> directory.deleteRecursively()
                directory.name.matches(Regex("epub-[0-9a-f]{64}")) -> {
                    val pendingImport = File(directory, ".pending-import")
                    val pendingDelete = File(directory, ".pending-delete")
                    when {
                        pendingDelete.exists() -> { dao.delete(directory.name); directory.deleteRecursively() }
                        directory.name in ids -> pendingImport.delete()
                        pendingImport.exists() -> directory.deleteRecursively()
                        // An unexplained missing database row must never erase a durable book file.
                    }
                }
            }
        }
    }
}
