package com.novelnest.app

import com.novelnest.app.data.cache.ChapterSnapshotStore
import com.novelnest.app.data.cache.MemoryLruCache
import com.novelnest.app.data.cache.ReaderCacheStore
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.remote.NovelSource
import com.novelnest.app.data.repository.NovelRepositoryImpl
import com.novelnest.app.feature.reader.Chapter
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SystemDesignRegressionTest {
    private val sourceType = NovelSourceType.NOVEL_PHOENIX
    private val full = (1..200).map { Chapter("chapter-$it", "Chapter $it") }

    private fun source(updates: Flow<Result<List<Chapter>>>) = object : NovelSource {
        override val sourceType = NovelSourceType.NOVEL_PHOENIX
        override suspend fun getHomeSections(): Result<List<NovelSection>> = Result.success(emptyList())
        override suspend fun search(query: String): Result<List<Novel>> = Result.success(emptyList())
        override suspend fun getNovelDetails(novelUrl: String): Result<Novel> = Result.failure(UnsupportedOperationException())
        override suspend fun getChapters(novelSlugOrUrl: String, forceRefresh: Boolean) = Result.success(full)
        override fun getChaptersFlow(novelSlugOrUrl: String, forceRefresh: Boolean) = updates
    }

    @Test fun failedRefreshRetainsLargeCatalogInEveryEmissionAndSharedMemory() = runBlocking {
        val remote = source(flow {
            emit(Result.success(full.take(10)))
            emit(Result.failure(IOException("Later page unavailable")))
        })
        val repo = NovelRepositoryImpl(sources = mapOf(sourceType to remote))
        repo.getChapters(sourceType, "np-book").getOrThrow()
        val observer = repo.observeChapterSnapshots(sourceType, "np-book")
        val results = repo.getReaderChaptersFlow(sourceType, "np-book").toList()
        assertTrue(results.last().isFailure)
        assertTrue(results.filter { it.isSuccess }.all { it.getOrThrow().size == 200 })
        assertEquals(full, observer.value)
    }

    @Test fun thrownSourceFailureAlsoRetainsUsableCatalog() = runBlocking {
        val repo = NovelRepositoryImpl(sources = mapOf(sourceType to source(flow {
            emit(Result.success(full.take(10)))
            throw IOException("Connection lost")
        })))
        repo.getChapters(sourceType, "np-book").getOrThrow()
        assertTrue(repo.getReaderChaptersFlow(sourceType, "np-book").toList().last().isFailure)
        assertEquals(200, repo.observeChapterSnapshots(sourceType, "np-book").value.size)
    }

    @Test fun cancelledPartialCollectionCannotBecomeACompleteSmallerCatalog() = runBlocking {
        val repo = NovelRepositoryImpl(sources = mapOf(sourceType to source(flow {
            emit(Result.success(full.take(10)))
            emit(Result.success(full.take(20)))
        })))
        repo.getChapters(sourceType, "np-book").getOrThrow()
        repo.getChaptersFlow(sourceType, "np-book", forceRefresh = true).first()
        assertEquals(200, repo.observeChapterSnapshots(sourceType, "np-book").value.size)
    }

    @Test fun completedRefreshMayReconcileRemovalsAndPersistTheValidatedList() = runBlocking {
        val directory = Files.createTempDirectory("catalog-reconciliation-").toFile()
        try {
            val remote = source(flow { emit(Result.success(full.take(20))) })
            fun repository() = NovelRepositoryImpl(sources = mapOf(sourceType to remote), readerCache = ReaderCacheStore(directory))
            val repo = repository()
            repo.getChapters(sourceType, "np-book").getOrThrow()
            val results = repo.getReaderChaptersFlow(sourceType, "np-book").toList()
            assertEquals(200, results.first().getOrThrow().size)
            assertEquals(20, results.last().getOrThrow().size)
            assertEquals(20, repo.observeChapterSnapshots(sourceType, "np-book").value.size)
            assertEquals(20, repository().getReaderChaptersFlow(sourceType, "np-book").first().getOrThrow().size)
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete() }
    }

    @Test fun emptyRefreshDoesNotEraseKnownCatalog() = runBlocking {
        val repo = NovelRepositoryImpl(sources = mapOf(sourceType to source(flow {
            emit(Result.success(emptyList()))
        })))
        repo.getChapters(sourceType, "np-book").getOrThrow()
        assertTrue(repo.getReaderChaptersFlow(sourceType, "np-book").toList().last().isFailure)
        assertEquals(200, repo.observeChapterSnapshots(sourceType, "np-book").value.size)
    }

    @Test fun snapshotEvictionKeepsExternalObserversConnected() {
        val store = ChapterSnapshotStore(recentLimit = 2)
        val observer = store.get("active").asStateFlow()
        repeat(100) { store.get("book-$it") }
        assertEquals(2, store.retainedCount)
        store.get("active").value = full
        assertEquals(full, observer.value)
        assertEquals(2, store.retainedCount)
    }

    @Test fun memoryLruBoundsEntriesAndAtomicPrefetchMarkers() = runBlocking {
        val cache = MemoryLruCache<String, Boolean>(8)
        val inserted = (1..32).map { async(Dispatchers.Default) {
            cache.putIfAbsent("shared", true) == null
        } }.awaitAll()
        assertEquals(1, inserted.count { it })
        repeat(100) { cache["book-$it"] = true }
        assertEquals(8, cache.size)
        assertEquals(8, cache.valuesSnapshot().size)
        assertNull(cache["shared"])
    }

    @Test fun readerCacheClearSerializesWithWritesAndTheOwnerRemainsUsable() = runBlocking {
        val directory = Files.createTempDirectory("reader-owned-clear-").toFile()
        try {
            val store = ReaderCacheStore(directory)
            val text = full.first().copy(paragraphs = listOf("Readable text"))
            listOf(
                async(Dispatchers.IO) { repeat(20) { store.write("chapter-$it", listOf(text), includeText = true) } },
                async(Dispatchers.IO) { repeat(5) { assertTrue(store.clear()) } }
            ).awaitAll()
            assertTrue(store.clear())
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            store.write("new", listOf(text), includeText = true)
            assertEquals(listOf(text), store.read("new"))
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete() }
    }
}
