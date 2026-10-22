package com.novelnest.app

import com.novelnest.app.data.model.Novel
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

    private abstract class StubPhoenixSource : NovelSource {
        override val sourceType = NovelSourceType.NOVEL_PHOENIX
        override suspend fun getHomeSections(): Result<List<NovelSection>> = Result.success(emptyList())
        override suspend fun search(query: String): Result<List<Novel>> = Result.success(emptyList())
        override suspend fun getNovelDetails(novelUrl: String): Result<Novel> = Result.failure(UnsupportedOperationException())
    }
}
