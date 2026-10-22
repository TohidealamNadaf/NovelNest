package com.novelnest.app.feature.search

import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.remote.network.HostRateLimiter
import com.novelnest.app.data.remote.novelphoenix.NovelPhoenixSource
import com.novelnest.app.data.remote.freewebnovel.FreeWebNovelSource
import com.novelnest.app.data.repository.NovelRepositoryImpl
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class PopularSourceTest {
    @Test fun rankingsComeFromEachSourceAndAreCachedIndependently() = runBlocking {
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            val folder = if (chain.request().url.host.contains("phoenix")) "novelphoenix/fixtures/ranking.html" else "freewebnovel/fixtures/popular.html"
            val file = listOf(File("docs/sources/$folder"), File("../docs/sources/$folder")).first { it.exists() }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(file.readText().toResponseBody("text/html".toMediaType())).build()
        }.build()
        val repository = NovelRepositoryImpl(sources = mapOf(
            NovelSourceType.NOVEL_PHOENIX to NovelPhoenixSource(client, HostRateLimiter(0)),
            NovelSourceType.FREE_WEB_NOVEL to FreeWebNovelSource(client, HostRateLimiter(0))
        ))
        val phoenix = repository.getPopularNovels(NovelSourceType.NOVEL_PHOENIX).getOrThrow()
        val fwn = repository.getPopularNovels(NovelSourceType.FREE_WEB_NOVEL).getOrThrow()
        assertEquals("np-shadow-slave", phoenix.first().id)
        assertEquals("fwn-shadow-slave", fwn.first().id)
        assertTrue(fwn.all { it.source == NovelSourceType.FREE_WEB_NOVEL })
        repository.getPopularNovels(NovelSourceType.FREE_WEB_NOVEL).getOrThrow()
        assertEquals(2, calls.get())
    }

    @Test fun blockedRankingsDoNotBecomeHardcodedPopularSuggestions() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(403).message("Blocked")
                .body("Blocked".toResponseBody("text/html".toMediaType())).build()
        }.build()
        assertTrue(NovelPhoenixSource(client, HostRateLimiter(0)).getPopularNovels().isFailure)
        assertTrue(FreeWebNovelSource(client, HostRateLimiter(0)).getPopularNovels().isFailure)
    }
}
