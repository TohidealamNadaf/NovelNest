package com.novelnest.app.data.remote.asura

import com.novelnest.app.data.model.*
import com.novelnest.app.data.remote.network.HostRateLimiter
import com.novelnest.app.data.repository.NovelRepositoryImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okio.Buffer
import okio.ForwardingSource
import okio.buffer

class AsuraSourceTest {
    private val asura = NovelSourceType.ASURA_SCANS
    private fun fixtureHttp(counter: AtomicInteger = AtomicInteger()) = AsuraHttp { url, _ ->
        counter.incrementAndGet()
        when {
            url.contains("/api/series?") -> asuraFixture("search.json")
            url.contains("/api/series/") -> asuraFixture("chapter.json")
            url.contains("/browse") -> asuraFixture("browse.html")
            url.endsWith(".com/") -> asuraFixture("home.html")
            else -> asuraFixture("detail.html")
        }
    }
    @Test fun concurrentDetailsAndChapterListShareOneFetchAndExpiryRefetches() = runBlocking {
        val count = AtomicInteger(); var now = 0L
        val source = AsuraSource(http = AsuraHttp { _, _ -> count.incrementAndGet(); delay(20); asuraFixture("detail.html") }, clock = { now })
        coroutineScope { (1..12).map { async {
            if (it % 2 == 0) source.getChapters(ASURA_URL).getOrThrow() else source.getManhwaDetails("asura-$ASURA_SERIES").getOrThrow()
        } }.awaitAll() }
        assertEquals(1, count.get())
        now = AsuraCachePolicy.METADATA_TTL_MS + 1
        source.getManhwaDetails(ASURA_URL).getOrThrow(); assertEquals(2, count.get())
        source.getManhwaDetails(ASURA_URL, true).getOrThrow(); assertEquals(3, count.get())
    }
    @Test fun catalogCacheIsBoundedByTotalChaptersAsWellAsNovelCount() = runBlocking {
        val count = AtomicInteger()
        val source = AsuraSource(http = AsuraHttp { url, _ ->
            count.incrementAndGet()
            asuraFixture("detail.html").replace(ASURA_SERIES, AsuraUrls.slug(url))
        }, cachedChapterBudget = 150)
        source.getManhwaDetails("series-one").getOrThrow()
        source.getManhwaDetails("series-two").getOrThrow()
        source.getManhwaDetails("series-two").getOrThrow(); assertEquals(2, count.get())
        source.getManhwaDetails("series-one").getOrThrow(); assertEquals(3, count.get())
    }
    @Test fun homeBrowseSearchAndBookmarkStyleReentrySeedTheSameDetailsAndChapters() = runBlocking {
        val source = AsuraSource(http = fixtureHttp())
        val repository = NovelRepositoryImpl(sources = mapOf(asura to source), rateLimiter = HostRateLimiter(0))
        val records = listOf(
            repository.getHomeSections(asura).first().getOrThrow().flatMap { it.novels }.first { it.id == "asura-$ASURA_SERIES" },
            repository.getBrowsePage(asura).getOrThrow().novels.first { it.id == "asura-$ASURA_SERIES" },
            repository.searchNovels(asura, "zenith").getOrThrow().single()
        )
        for (record in records) {
            repository.rememberNovel(record)
            assertEquals(record.id, repository.getNovelById(record.id)!!.id)
            val details = repository.getManhwaDetails(asura, record.sourceUrl).getOrThrow()
            assertEquals(record.id, details.novel.id)
            assertEquals(112, repository.getChapters(asura, record.id).getOrThrow().size)
            assertEquals(14, repository.getManhwaChapter(asura, details.chapters.first().url).getOrThrow().pages.size)
            assertTrue(repository.getChapterContent(asura, details.chapters.first().url).isFailure)
        }
        assertTrue(repository.getManhwaChapter(NovelSourceType.NOVEL_PHOENIX, ASURA_CHAPTER).isFailure)
    }
    @Test fun errorsNeverBecomeSeedDataAndBlankSearchDoesNotFetch() = runBlocking {
        val count = AtomicInteger()
        val source = AsuraSource(http = AsuraHttp { _, _ -> count.incrementAndGet(); throw AsuraException.Blocked() })
        val repository = NovelRepositoryImpl(sources = mapOf(asura to source), rateLimiter = HostRateLimiter(0))
        assertTrue(repository.searchNovels(asura, " ").getOrThrow().isEmpty()); assertEquals(0, count.get())
        assertTrue(repository.getHomeSections(asura).first().exceptionOrNull() is AsuraException.Blocked)
        assertTrue(repository.searchNovels(asura, "book").exceptionOrNull() is AsuraException.Blocked)
        assertNull(repository.getNovelById("asura-1"))
        repository.rememberNovel(com.novelnest.app.data.remote.SeedData.asuraHeroSlides.first())
        assertTrue(repository.searchNovels(asura, "Solo").isFailure)
    }
    @Test fun freshImageMetadataIsSharedButExpiredOrForcedMetadataIsReloaded() = runBlocking {
        val count = AtomicInteger(); var now = 0L
        val source = AsuraSource(http = fixtureHttp(count), clock = { now })
        source.getManhwaChapter(ASURA_CHAPTER).getOrThrow()
        source.getManhwaChapter(ASURA_CHAPTER).getOrThrow(); assertEquals(1, count.get())
        now += AsuraCachePolicy.PAGE_URL_TTL_MS + 1
        source.getManhwaChapter(ASURA_CHAPTER).getOrThrow(); assertEquals(2, count.get())
        source.getManhwaChapter(ASURA_CHAPTER, true).getOrThrow(); assertEquals(3, count.get())
    }
    @Test fun onlyAnApi404TriggersThePublicHtmlFallback() = runBlocking {
        val count = AtomicInteger()
        val source = AsuraSource(http = AsuraHttp { url, _ ->
            count.incrementAndGet(); if (url.contains("api.asurascans.com")) throw AsuraException.NotFound() else asuraFixture("chapter.html")
        })
        assertEquals(14, source.getManhwaChapter(ASURA_CHAPTER).getOrThrow().pages.size); assertEquals(2, count.get())
        val blocked = AsuraSource(http = AsuraHttp { _, _ -> count.incrementAndGet(); throw AsuraException.Http(429) })
        assertTrue(blocked.getManhwaChapter(ASURA_CHAPTER).exceptionOrNull() is AsuraException.Http); assertEquals(3, count.get())
    }
    @Test fun cancellationReleasesCacheLockAndNeverBecomesSuccessOrErrorState() = runBlocking {
        val started = CompletableDeferred<Unit>(); val count = AtomicInteger()
        val source = AsuraSource(http = AsuraHttp { _, _ ->
            if (count.incrementAndGet() == 1) { started.complete(Unit); awaitCancellation() }
            asuraFixture("detail.html")
        })
        val cancelled = async { source.getManhwaDetails(ASURA_URL) }; started.await(); cancelled.cancelAndJoin()
        assertEquals(112, withTimeout(2000) { source.getManhwaDetails(ASURA_URL).getOrThrow() }.chapters.size)
        assertEquals(2, count.get())
    }
    private fun client(code: Int, text: String, closed: AtomicInteger, location: String? = null): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            val bytes = Buffer().writeUtf8(text)
            val tracked = object : ForwardingSource(bytes) { override fun close() { closed.incrementAndGet(); super.close() } }.buffer()
            val body = object : ResponseBody() {
                override fun contentType() = "text/html".toMediaType()
                override fun contentLength() = -1L
                override fun source() = tracked
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(body)
                .apply { if (location != null) header("Location", location) }.build()
        }.build()
    @Test fun wireClosesBodiesOnSuccessHttpFailureAndOversizedUnknownLengthResponse() = runBlocking {
        val closed = AtomicInteger()
        assertEquals("hello", AsuraHttpClient(client(200, "hello", closed), HostRateLimiter(0)).get(ASURA_URL, false))
        assertEquals(1, closed.get())
        assertTrue(runCatching { AsuraHttpClient(client(403, "blocked", closed), HostRateLimiter(0)).get(ASURA_URL, false) }.exceptionOrNull() is AsuraException.Blocked)
        assertTrue(runCatching { AsuraHttpClient(client(200, "too long", closed), HostRateLimiter(0), 3).get(ASURA_URL, false) }.exceptionOrNull() is AsuraException.TooLarge)
        assertEquals(3, closed.get())
    }
    @Test fun wireRejectsExternalRedirectWithoutContactingIt() = runBlocking {
        val closed = AtomicInteger()
        assertTrue(runCatching { AsuraHttpClient(client(302, "", closed, "https://evil.example/"), HostRateLimiter(0)).get(ASURA_URL, false) }.isFailure)
        assertEquals(1, closed.get())
    }
    @Test fun cancellingWireRequestCancelsTheUnderlyingOkHttpCall() = runBlocking {
        val entered = CountDownLatch(1); val cancelled = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            entered.countDown()
            while (!chain.call().isCanceled()) Thread.sleep(5)
            cancelled.countDown(); throw java.io.IOException("cancelled")
        }.build()
        val request = async(Dispatchers.Default) { AsuraHttpClient(client, HostRateLimiter(0)).get(ASURA_URL, false) }
        assertTrue(withContext(Dispatchers.IO) { entered.await(2, TimeUnit.SECONDS) }); request.cancelAndJoin()
        assertTrue(withContext(Dispatchers.IO) { cancelled.await(2, TimeUnit.SECONDS) })
    }
}
