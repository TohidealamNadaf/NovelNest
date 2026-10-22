package com.novelnest.app

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.cache.ReaderCacheStore
import com.novelnest.app.feature.reader.startReaderCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import org.junit.Assert.assertNull
import java.nio.file.Files
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.remote.NovelSource
import com.novelnest.app.data.remote.network.HostRateLimiter
import com.novelnest.app.data.remote.novelphoenix.NovelPhoenixSource
import com.novelnest.app.data.repository.NovelRepositoryImpl
import com.novelnest.app.feature.reader.Chapter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ChapterStartupTest {
    @Test
    fun progressiveSnapshotIsAvailableBeforePaginationFinishesAndIsNotACompleteCache() = runBlocking {
        val finishPaging = CompletableDeferred<Unit>()
        val firstPage = listOf(Chapter("chapter-1", "Chapter 1"))
        val completeList = firstPage + Chapter("chapter-2", "Chapter 2")
        var fullListRequests = 0
        val source = object : StubPhoenixSource() {
            override fun getChaptersFlow(novelSlugOrUrl: String, forceRefresh: Boolean) = flow {
                emit(Result.success(firstPage))
                finishPaging.await()
                emit(Result.success(completeList))
            }

            override suspend fun getChapters(novelSlugOrUrl: String, forceRefresh: Boolean): Result<List<Chapter>> {
                fullListRequests++
                return Result.success(completeList)
            }
        }
        val repository = NovelRepositoryImpl(sources = mapOf(NovelSourceType.NOVEL_PHOENIX to source))
        val snapshots = repository.observeChapterSnapshots(NovelSourceType.NOVEL_PHOENIX, "np-shadow-slave")
        val listing = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.getChaptersFlow(NovelSourceType.NOVEL_PHOENIX, "https://novelphoenix.com/novel/shadow-slave").collect {}
        }
        try {
            assertEquals(firstPage, snapshots.value)
            assertEquals(firstPage, repository.observeChapterSnapshots(NovelSourceType.NOVEL_PHOENIX,
                "https://novelphoenix.com/novel/np-1/?ref=bookmark#chapters").value)
            assertTrue(listing.isActive)
            assertEquals(0, fullListRequests)
            assertEquals(completeList, repository.getChapters(NovelSourceType.NOVEL_PHOENIX, "np-shadow-slave").getOrThrow())
            assertEquals(1, fullListRequests)
            finishPaging.complete(Unit)
            listing.join()
            assertEquals(completeList, snapshots.value)
        } finally {
            listing.cancel()
        }
    }

    @Test
    fun detailChapterListWarmsOnlyTheFirstChapter() = runBlocking {
        val requestedUrl = CompletableDeferred<String>()
        val calls = AtomicInteger()
        val source = object : StubPhoenixSource() {
            override fun getChaptersFlow(novelSlugOrUrl: String, forceRefresh: Boolean) = flow {
                val first = Chapter("chapter-1", "Chapter 1", url = "/novel/shadow-slave/chapter-1")
                emit(Result.success(listOf(first)))
                emit(Result.success(listOf(first, Chapter("chapter-2", "Chapter 2"))))
            }

            override suspend fun getChapterContent(chapterUrlOrId: String, forceRefresh: Boolean): Result<Chapter> {
                calls.incrementAndGet()
                requestedUrl.complete(chapterUrlOrId)
                return Result.success(Chapter("chapter-1", "Chapter 1", paragraphs = listOf("Content")))
            }
        }
        val repository = NovelRepositoryImpl(sources = mapOf(NovelSourceType.NOVEL_PHOENIX to source))
        repository.getChaptersFlow(NovelSourceType.NOVEL_PHOENIX, "np-shadow-slave").collect {}
        assertEquals("/novel/shadow-slave/chapter-1", withTimeout(5_000) { requestedUrl.await() })
        assertEquals(1, calls.get())
    }

    @Test
    fun concurrentPrefetchAndReadShareARequestAcrossRelativeAndAbsoluteUrls() = runBlocking {
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            Thread.sleep(75) // Keep the response in flight while other readers request the same URL.
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("<div id='content'><h4>Chapter 1</h4><p>Real chapter content.</p></div>".toResponseBody("text/html".toMediaType()))
                .build()
        }.build()
        val source = NovelPhoenixSource(client = client, rateLimiter = HostRateLimiter(0))
        val relative = "/novel/shadow-slave/chapter-1"
        val absolute = "https://novelphoenix.com$relative"
        val chapters = (0 until 16).map { index ->
            async(Dispatchers.Default) {
                source.getChapterContent(if (index % 2 == 0) relative else absolute, forceRefresh = false).getOrThrow()
            }
        }.awaitAll()
        assertTrue(chapters.all { it.paragraphs == listOf("Real chapter content.") })
        assertEquals(1, calls.get())
        source.getChapterContent(absolute, forceRefresh = true).getOrThrow()
        assertEquals(2, calls.get())
    }

    @Test fun coldChapter83StartupDoesNotWaitForTheRemainingCatalog() = runBlocking {
        val finish = CompletableDeferred<Unit>()
        var fullRequests = 0
        val first = (1..100).map { Chapter("chapter-" + it, "Chapter " + it) }
        val source = object : StubPhoenixSource() {
            override suspend fun getChapters(novelSlugOrUrl: String, forceRefresh: Boolean): Result<List<Chapter>> {
                fullRequests++
                return Result.failure(IllegalStateException("Full catalog must not gate startup"))
            }
            override fun getChaptersFlow(novelSlugOrUrl: String, forceRefresh: Boolean) = flow {
                emit(Result.success(first))
                finish.await()
                emit(Result.success(first + Chapter("chapter-101", "Chapter 101")))
            }
        }
        val repo = NovelRepositoryImpl(sources = mapOf(NovelSourceType.NOVEL_PHOENIX to source))
        try {
            val ready = startReaderCatalog(repo, NovelSourceType.NOVEL_PHOENIX, "np-book", 82)
            val chapters = withTimeout(2000) { ready.await().getOrThrow() }
            assertEquals("chapter-83", chapters[82].id)
            assertEquals(0, fullRequests)
            assertTrue(!finish.isCompleted)
        } finally { finish.complete(Unit) }
    }

    @Test fun laterChapterStartsWhenItsPageArrivesWithoutWaitingForTheLastPage() = runBlocking {
        val secondPage = CompletableDeferred<Unit>()
        val finalPage = CompletableDeferred<Unit>()
        val chapters = (1..300).map { Chapter("chapter-" + it, "Chapter " + it) }
        val source = object : StubPhoenixSource() {
            override suspend fun getChapters(novelSlugOrUrl: String, forceRefresh: Boolean) = Result.success(chapters)
            override fun getChaptersFlow(novelSlugOrUrl: String, forceRefresh: Boolean) = flow {
                emit(Result.success(chapters.take(100)))
                secondPage.await()
                emit(Result.success(chapters.take(200)))
                finalPage.await()
                emit(Result.success(chapters))
            }
        }
        val repo = NovelRepositoryImpl(sources = mapOf(NovelSourceType.NOVEL_PHOENIX to source))
        try {
            val ready = startReaderCatalog(repo, NovelSourceType.NOVEL_PHOENIX, "np-book", 182)
            yield()
            assertTrue(!ready.isCompleted)
            secondPage.complete(Unit)
            val available = withTimeout(2000) { ready.await().getOrThrow() }
            assertEquals("chapter-183", available[182].id)
            assertTrue(!finalPage.isCompleted)
        } finally { secondPage.complete(Unit); finalPage.complete(Unit) }
    }

    @Test fun diskResumeSurvivesANewRepositoryAndManualRefreshBypassesIt() = runBlocking {
        val directory = Files.createTempDirectory("novelnest-reader-resume-").toFile()
        val contentCalls = AtomicInteger()
        val catalogCalls = AtomicInteger()
        val base = "https://novelphoenix.com/novel/book"
        val chapters = (1..100).map { Chapter("chapter-" + it, "Chapter " + it, url = base + "/chapter-" + it) }
        val source = object : StubPhoenixSource() {
            override suspend fun getChapters(novelSlugOrUrl: String, forceRefresh: Boolean): Result<List<Chapter>> {
                catalogCalls.incrementAndGet()
                return Result.success(chapters)
            }
            override suspend fun getChapterContent(chapterUrlOrId: String, forceRefresh: Boolean): Result<Chapter> {
                contentCalls.incrementAndGet()
                return Result.success(chapters[82].copy(paragraphs = listOf("Stored chapter 83")))
            }
        }
        try {
            fun repository() = NovelRepositoryImpl(sources = mapOf(NovelSourceType.NOVEL_PHOENIX to source),
                readerCache = ReaderCacheStore(directory))
            val original = repository()
            original.getChapters(NovelSourceType.NOVEL_PHOENIX, base).getOrThrow()
            original.getChapterContent(NovelSourceType.NOVEL_PHOENIX, base + "/chapter-83").getOrThrow()
            val resumed = repository()
            val navigation = resumed.getChaptersFlow(NovelSourceType.NOVEL_PHOENIX, "np-book").first().getOrThrow()
            assertEquals(100, navigation.size)
            val chapter = resumed.getChapterContent(NovelSourceType.NOVEL_PHOENIX, "/novel/book/chapter-83").getOrThrow()
            assertEquals(listOf("Stored chapter 83"), chapter.paragraphs)
            assertEquals(1, catalogCalls.get())
            assertEquals(1, contentCalls.get())
            resumed.getChapterContent(NovelSourceType.NOVEL_PHOENIX, base + "/chapter-83", forceRefresh = true).getOrThrow()
            assertEquals(2, contentCalls.get())
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete() }
    }

    @Test fun readerDiskCacheEvictsOldEntriesAndTreatsCorruptionAsAMiss() = runBlocking {
        val directory = Files.createTempDirectory("novelnest-reader-lru-").toFile()
        try {
            val store = ReaderCacheStore(directory, maxEntries = 2)
            val chapter = Chapter("one", "One", paragraphs = listOf("Text"))
            store.write("one", listOf(chapter), includeText = true)
            delay(5)
            store.write("two", listOf(chapter), includeText = true)
            delay(5)
            store.write("three", listOf(chapter), includeText = true)
            assertNull(store.read("one"))
            assertEquals(1, store.read("three")!!.size)
            directory.listFiles()!!.forEach { it.writeText("broken json") }
            assertNull(store.read("three"))
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete() }
    }

    @Test fun shortCatalogRefreshDoesNotEraseTheLargerResumeSnapshot() = runBlocking {
        val directory = Files.createTempDirectory("novelnest-reader-catalog-").toFile()
        val sourceType = NovelSourceType.NOVEL_PHOENIX
        val full = (1..100).map { Chapter("chapter-" + it, "Chapter " + it) }
        val source = object : StubPhoenixSource() {
            override suspend fun getChapters(novelSlugOrUrl: String, forceRefresh: Boolean) = Result.success(full)
            override fun getChaptersFlow(novelSlugOrUrl: String, forceRefresh: Boolean) = flow {
                emit(Result.success(full.take(10)))
                emit(Result.failure<List<Chapter>>(IllegalStateException("Offline during pagination")))
            }
        }
        try {
            fun repo() = NovelRepositoryImpl(sources = mapOf(sourceType to source), readerCache = ReaderCacheStore(directory))
            repo().getChapters(sourceType, "np-book").getOrThrow()
            repo().getChaptersFlow(sourceType, "np-book").collect {}
            assertEquals(100, repo().getChaptersFlow(sourceType, "np-book").first().getOrThrow().size)
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete() }
    }

    private abstract class StubPhoenixSource : NovelSource {
        override val sourceType = NovelSourceType.NOVEL_PHOENIX
        override suspend fun getHomeSections(): Result<List<NovelSection>> = Result.success(emptyList())
        override suspend fun search(query: String): Result<List<Novel>> = Result.success(emptyList())
        override suspend fun getNovelDetails(novelUrl: String): Result<Novel> = Result.failure(UnsupportedOperationException())
    }
}
