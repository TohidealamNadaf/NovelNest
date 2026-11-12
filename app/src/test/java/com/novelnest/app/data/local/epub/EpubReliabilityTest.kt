package com.novelnest.app.data.local.epub

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.readium.r2.shared.publication.*
import org.readium.r2.shared.util.*
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.resource.Resource

@OptIn(ExperimentalCoroutinesApi::class)
class EpubReliabilityTest {
    @get:Rule val temp = TemporaryFolder()
    private val id = "epub-" + "a".repeat(64)
    private fun book() = LocalBookEntity(id, "a".repeat(64), "Book", "Author", "", 1, 20)

    private class Source(private val bytes: ByteArray = "<p>Chapter</p>".toByteArray(), private val fail: Throwable? = null) : Resource {
        var reads = 0
        var closes = 0
        override val sourceUrl: AbsoluteUrl? = null
        override suspend fun properties(): Try<Resource.Properties, ReadError> = Try.success(Resource.Properties())
        override suspend fun length(): Try<Long, ReadError> = Try.success(bytes.size.toLong())
        override suspend fun read(range: LongRange?): Try<ByteArray, ReadError> {
            reads++
            fail?.let { throw it }
            return Try.success(bytes)
        }
        override fun close() { closes++ }
    }

    @Test fun markupReadsAndSanitizesOnlyOncePerRequestWithoutRetainingBytes() = runTest {
        val source = Source()
        val resource = EpubMarkupResource(source)
        assertTrue(resource.read().getOrNull()!!.toString(Charsets.UTF_8).contains("Chapter"))
        assertEquals(1, source.reads)
        resource.read()
        assertEquals(2, source.reads)
        resource.close()
        assertEquals(1, source.closes)
    }
    @Test fun markupRangeAndLengthUseTheTransformedContent() = runTest {
        val source = Source()
        val resource = EpubMarkupResource(source)
        val full = resource.read().getOrNull()!!
        assertArrayEquals(full.copyOfRange(3, 11), resource.read(3L..10L).getOrNull())
        assertEquals(full.size.toLong(), resource.length().getOrNull())
        assertEquals(3, source.reads)
        assertTrue(resource.read(Long.MAX_VALUE..Long.MAX_VALUE).getOrNull()!!.isEmpty())
        assertTrue(resource.read(10L..2L).getOrNull()!!.isEmpty())
    }
    @Test fun oversizedMarkupReturnsReadiumErrorInsteadOfThrowingIntoItsCoroutine() = runTest {
        val result = EpubMarkupResource(Source(ByteArray(EpubArchiveValidator.MAX_MARKUP_BYTES.toInt() + 1))).read()
        assertNull(result.getOrNull())
        result.getOrElse { assertTrue(it is ReadError.Decoding); byteArrayOf() }
    }
    @Test fun resourceIOExceptionReturnsReadiumError() = runTest {
        assertNull(EpubMarkupResource(Source(fail = IOException("unreadable resource"))).read().getOrNull())
    }
    @Test fun resourceMemoryFailureReturnsReadiumError() = runTest {
        val result = EpubMarkupResource(Source(fail = OutOfMemoryError("simulated resource allocation failure"))).read()
        assertNull(result.getOrNull())
        result.getOrElse { assertTrue(it is ReadError.OutOfMemory); byteArrayOf() }
    }
    @Test fun resourceCancellationStillPropagates() {
        assertThrows(CancellationException::class.java) {
            runBlocking { EpubMarkupResource(Source(fail = CancellationException())).read() }
        }
    }

    private class Dao(initial: LocalBookEntity) : LocalBookDao {
        val books = mutableMapOf(initial.id to initial)
        val positions = mutableMapOf<String, EpubPositionEntity>()
        val saved = mutableListOf<EpubPositionEntity>()
        var recoveryFailures = 0
        var saveFailures = 0
        override fun observeLibrary() = flowOf(books.values.map { LocalBookEntry(it, positions[it.id]) })
        override suspend fun book(id: String) = books[id]
        override suspend fun position(id: String) = positions[id]
        override suspend fun insert(book: LocalBookEntity): Long { books[book.id] = book; return 1 }
        override suspend fun savePosition(position: EpubPositionEntity) {
            if (saveFailures > 0) { saveFailures--; throw IOException("storage unavailable") }
            positions[position.bookId] = position; saved.add(position)
        }
        override suspend fun delete(id: String) { books.remove(id); positions.remove(id) }
        override suspend fun ids(): List<String> {
            if (recoveryFailures > 0) { recoveryFailures--; throw IOException("storage unavailable") }
            return books.keys.toList()
        }
    }
    private fun repository(dao: Dao, scope: CoroutineScope, dispatcher: CoroutineDispatcher,
        open: suspend () -> Publication = { error("Engine should not be opened") }): EpubRepository {
        val files = temp.newFolder()
        val context = object : ContextWrapper(null) {
            override fun getFilesDir() = files
            override fun getApplicationContext(): Context = this
        }
        return EpubRepository(context, dao, object : EpubEngine {
            override suspend fun open(file: File) = open()
        }, scope = scope, ioDispatcher = dispatcher)
    }
    private fun position(progress: Double = 0.4, time: Long = 100) = EpubPositionEntity(id, """{"href":"one.xhtml","type":"application/xhtml+xml","locations":{"progression":0.4}}""", progress, "Chapter", time)
    private fun publication(onClose: () -> Unit = {}): Publication {
        val manifest = checkNotNull(Manifest.fromJSON(JSONObject("""{"metadata":{"title":"Book"},"readingOrder":[]}""")))
        return Publication(manifest, object : Container<Resource> {
            override val entries: Set<Url> = emptySet()
            override fun get(url: Url): Resource? = null
            override fun close() = onClose()
        })
    }

    @Test fun failedRecoveryCanBeRetriedAndPreservesUnexplainedFiles() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val dao = Dao(book()).apply { recoveryFailures = 1 }
            val repo = repository(dao, scope, dispatcher)
            val durable = repo.bookFile(id).apply { parentFile!!.mkdirs(); writeText("durable book") }
            runCurrent()
            assertNotNull(repo.libraryError.value)
            assertEquals("durable book", durable.readText())
            repo.reloadLibrary()
            advanceUntilIdle()
            assertNull(repo.libraryError.value)
            assertEquals("durable book", durable.readText())
        } finally { scope.cancel() }
    }
    @Test fun invalidCheckpointIsReportedAndCannotReplaceSavedProgress() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val dao = Dao(book())
            val repo = repository(dao, scope, dispatcher)
            repo.enqueuePosition(position())
            advanceUntilIdle()
            repo.enqueuePosition(position(Double.NaN))
            runCurrent()
            assertEquals(0.4, dao.positions[id]!!.progression, 0.0)
            assertNotNull(repo.positionError.value)
            assertEquals(1, dao.saved.size)
        } finally { scope.cancel() }
    }
    @Test fun checkpointOrderingSurvivesADeviceClockMovingBackwards() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val dao = Dao(book())
            val repo = repository(dao, scope, dispatcher)
            repo.enqueuePosition(position(0.4, 1000))
            repo.enqueuePosition(position(0.5, 100))
            advanceUntilIdle()
            assertEquals(0.5, dao.positions[id]!!.progression, 0.0)
        } finally { scope.cancel() }
    }
    @Test fun boundedCheckpointFailuresRemainRetryable() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val dao = Dao(book()).apply { saveFailures = 3 }
            val repo = repository(dao, scope, dispatcher)
            repo.enqueuePosition(position())
            advanceUntilIdle()
            assertNotNull(repo.positionError.value)
            assertTrue(dao.positions.isEmpty())
            repo.retryPositionWrites()
            advanceUntilIdle()
            assertNull(repo.positionError.value)
            assertEquals(0.4, dao.positions[id]!!.progression, 0.0)
        } finally { scope.cancel() }
    }
    @Test fun delayedCheckpointDoesNotRecreateADeletedBook() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val dao = Dao(book())
            val repo = repository(dao, scope, dispatcher)
            dao.delete(id)
            repo.enqueuePosition(position())
            advanceUntilIdle()
            assertTrue(dao.saved.isEmpty())
            assertNull(repo.positionError.value)
        } finally { scope.cancel() }
    }
    @Test fun publicationCloseFailureIsContainedAndScheduledAwayFromCaller() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val repo = repository(Dao(book()), scope, dispatcher)
            var closed = 0
            repo.releasePublication(publication { closed++; throw IOException("close failed") })
            assertEquals(0, closed)
            advanceUntilIdle()
            assertEquals(1, closed)
        } finally { scope.cancel() }
    }
}
