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
    data class Complete(val book: LocalBookEntity, val duplicate: Boolean) : EpubImportState
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
    val library = libraryRevision.flatMapLatest { dao.observeLibrary().onEach { if (recoveryComplete) mutableLibraryError.value = null }.catch {
        mutableLibraryError.value = "Could not load the local library. Please retry. Your imported files are kept."
        emit(emptyList())
    } }.map { it.sortedByDescending { entry -> entry.position?.updatedAt ?: entry.book.importedAt } }
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
    @Synchronized fun import(uri: Uri) {
        // Cancellation does not mean cleanup has completed. Do not let an old job overwrite a new import.
        if (importJob?.isCompleted == false) return
        mutableImport.value = EpubImportState.Working("Copying EPUB...")
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                ensureRecovered()
                val result = imports.withLock { importFile(uri) }
                mutableImport.value = result
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableImport.value = EpubImportState.Failed(when (error) {
                    is EpubImportException -> error.message.orEmpty()
                    is java.util.zip.ZipException -> "The selected file is damaged or is not a valid EPUB archive."
                    is java.io.FileNotFoundException -> "The selected file is no longer accessible. Choose it again from the document picker."
                    else -> "Could not import this EPUB. Check file access and available device storage, then try again."
                })
            }
        }
        importJob = job
        job.invokeOnCompletion {
            synchronized(this) {
                if (importJob === job && job.isCancelled) mutableImport.value = EpubImportState.Idle
            }
        }
        job.start()
    }
    @Synchronized fun cancelImport() {
        if (importJob?.isCompleted == false) {
            mutableImport.value = EpubImportState.Working("Cancelling import...")
            importJob?.cancel()
        }
    }
    fun clearImportMessage() { if (mutableImport.value !is EpubImportState.Working) mutableImport.value = EpubImportState.Idle }

    private suspend fun importFile(uri: Uri): EpubImportState.Complete {
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
                return EpubImportState.Complete(existing, true)
            }
            mutableImport.value = EpubImportState.Working("Checking EPUB and reading book information...")
            EpubArchiveValidator.validate(file) { context.ensureActive() }
            val publication = engine.open(file)
            val book = try {
                val metadata = publication.metadata
                saveCover(publication, File(stage, "cover.jpg"))
                LocalBookEntity(id, hash, metadata.title?.take(1000)?.takeIf { it.isNotBlank() } ?: "Untitled book",
                    metadata.authors.joinToString(", ") { it.name }.take(2000), metadata.description.orEmpty().take(16000), System.currentTimeMillis(), bytes)
            } finally { publication.close() }
            context.ensureActive()
            writes.withLock {
                val destination = directory(id)
                if (destination.exists() && dao.book(id) == null) destination.deleteRecursively()
                File(stage, ".pending-import").writeText("1")
                if (!stage.renameTo(destination)) throw EpubImportException("Could not save the imported EPUB. Check device storage.")
                finalized = destination
                // File and Room cannot share a transaction. Recovery removes any orphan left before this commit.
                withContext(NonCancellable) {
                    check(dao.insert(book) != -1L) { "The imported book could not be recorded" }
                    committed = true
                    File(destination, ".pending-import").delete()
                }
            }
            return EpubImportState.Complete(book, false)
        } finally {
            stage.deleteRecursively()
            if (!committed) finalized?.deleteRecursively()
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
