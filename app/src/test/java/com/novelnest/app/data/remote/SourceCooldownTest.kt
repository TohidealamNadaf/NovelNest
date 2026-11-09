package com.novelnest.app.data.remote

import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.remote.freewebnovel.FreeWebNovelSource
import com.novelnest.app.data.remote.network.HostRateLimiter
import com.novelnest.app.data.remote.network.SourceCooldownException
import com.novelnest.app.data.remote.novelphoenix.NovelPhoenixSource
import com.novelnest.app.data.repository.NovelRepositoryImpl
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class SourceCooldownTest {
    @Test fun retryAfterSecondsDateAndHostIsolation() = runBlocking {
        var monotonic = 10_000L
        val wall = 1_440_000_000_000L
        val limiter = HostRateLimiter(0, { monotonic }, { wall })
        val blocked = limiter.block("a", 429, false, "30")
        assertEquals(30L, blocked.remainingSeconds)
        limiter.acquire("b")
        assertEquals(30L, limiter.cooldown("a")?.remainingSeconds)
        monotonic += 29_001L
        assertEquals(1L, limiter.cooldown("a")?.remainingSeconds)
        monotonic += 999L
        limiter.acquire("a")
        assertNull(limiter.cooldown("a"))
        // 2015-08-26 00:00:00 GMT plus 45 seconds.
        val dateWall = java.time.Instant.parse("2015-08-26T00:00:00Z").toEpochMilli()
        val dateLimiter = HostRateLimiter(0, { 0L }, { dateWall })
        assertEquals(45L, dateLimiter.block("date", 429, false, "Wed, 26 Aug 2015 00:00:45 GMT").remainingSeconds)
        assertEquals(60L, dateLimiter.block("invalid", 429, false, "invalid").remainingSeconds)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun queuedRequestRechecksCooldownAndCancellationDoesNotBlockOthers() = runTest {
        val limiter = HostRateLimiter(1_000L, { testScheduler.currentTime }, { 0L })
        limiter.acquire("host")
        val waiting = async { runCatching { limiter.acquire("host") } }
        runCurrent()
        limiter.block("host", 429, false, "3")
        advanceTimeBy(1_000L); runCurrent()
        assertTrue(waiting.await().exceptionOrNull() is SourceCooldownException)
        val another = async { limiter.acquire("different") }
        runCurrent(); assertTrue(another.isCompleted)
        val cancelled = async { limiter.acquire("different") }
        runCurrent(); cancelled.cancel(); runCurrent()
        advanceTimeBy(1_000L); runCurrent()
        limiter.acquire("different")
    }

    @Test fun rateLimitsPauseBothSourcesWithoutRetriesAndPreserveCachedContent() = runBlocking {
        for (kind in listOf(NovelSourceType.NOVEL_PHOENIX, NovelSourceType.FREE_WEB_NOVEL)) {
            var now = 0L
            val calls = AtomicInteger()
            val fixture = sourceFixture(kind, "chapter_1_shadow_slave.html")
            var blocked = true
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                calls.incrementAndGet()
                val isSecond = chain.request().url.toString().contains("chapter-2")
                val rateLimited = blocked && isSecond
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(if (rateLimited) 429 else 200).message("fixture")
                    .header("Retry-After", "30")
                    .body((if (rateLimited) "rate limit" else fixture).toResponseBody("text/html".toMediaType())).build()
            }.build()
            val limiter = HostRateLimiter(0, { now }, { 0L })
            val source = if (kind == NovelSourceType.FREE_WEB_NOVEL) FreeWebNovelSource(client, limiter) else NovelPhoenixSource(client, limiter)
            val repository = NovelRepositoryImpl(sources = mapOf(kind to source))
            val first = "${kind.baseUrl}/novel/shadow-slave/chapter-1"
            val second = "${kind.baseUrl}/novel/shadow-slave/chapter-2"
            assertTrue(repository.getChapterContent(kind, first).getOrThrow().paragraphs.isNotEmpty())
            assertTrue(repository.getChapterContent(kind, second).exceptionOrNull() is SourceCooldownException)
            assertEquals(2, calls.get())
            assertTrue(repository.isSourceCoolingDown(kind))
            assertTrue(repository.getChapterContent(kind, first).isSuccess)
            repository.resetSourceCircuitBreaker(kind)
            assertTrue(repository.getChapterContent(kind, second).exceptionOrNull() is SourceCooldownException)
            assertEquals(2, calls.get())
            now += 30_000L; blocked = false
            assertFalse(repository.isSourceCoolingDown(kind))
            assertTrue(repository.getChapterContent(kind, second).isSuccess)
            assertEquals(3, calls.get())
        }
    }

    @Test fun challengeHeaderAnd403CreateAccurateCooldown() = runBlocking {
        for (kind in listOf(NovelSourceType.NOVEL_PHOENIX, NovelSourceType.FREE_WEB_NOVEL)) {
            val calls = AtomicInteger()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                calls.incrementAndGet()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(403).message("forbidden").header("cf-mitigated", "challenge")
                    .body("blocked".toResponseBody("text/html".toMediaType())).build()
            }.build()
            val limiter = HostRateLimiter(0, { 0L }, { 0L })
            val source = if (kind == NovelSourceType.FREE_WEB_NOVEL) FreeWebNovelSource(client, limiter) else NovelPhoenixSource(client, limiter)
            val error = source.getChapterContent("${kind.baseUrl}/novel/shadow-slave/chapter-1").exceptionOrNull()
            assertTrue(error is SourceCooldownException && error.challenge && error.statusCode == 403)
            assertTrue(source.isCoolingDown())
            assertTrue(source.search("shadow").isFailure)
            assertEquals(1, calls.get())
        }
    }
}
