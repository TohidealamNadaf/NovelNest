package com.novelnest.app.data.remote.novelphoenix

import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.remote.network.HostRateLimiter
import com.novelnest.app.data.remote.sourceFixture
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class NovelPhoenixSearchPosterTest {
    private val kind = NovelSourceType.NOVEL_PHOENIX
    private fun fixture(name: String) = sourceFixture(kind, name)

    private fun source(requests: MutableList<Request>, body: String, code: Int = 200): NovelPhoenixSource {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            synchronized(requests) { requests.add(request) }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("Fixture").header("Retry-After", "60")
                .body(body.toResponseBody("text/html".toMediaType())).build()
        }.build()
        return NovelPhoenixSource(client, HostRateLimiter(0))
    }

    @Test fun searchUsesWebsitePostersWithoutPerBookDetailRequests() = runBlocking {
        val requests = mutableListOf<Request>()
        val novels = source(requests, fixture("search_page_shadow.html")).search("Shadow").getOrThrow()
        val detail = NovelPhoenixParser.parseNovelDetail(fixture("detail_shadow_slave.html"))
        val shadow = novels.first { it.id == "np-shadow-slave" }
        assertEquals(detail.coverUrl, shadow.coverUrl)
        assertTrue(novels.all { it.coverUrl.startsWith("https://novelphoenix.com/") })
        assertTrue(novels.none { it.coverUrl.contains("data:") || it.coverUrl.contains("\\") })
        assertEquals("https://novelphoenix.com/novel/shadow-slave", shadow.sourceUrl)
        assertEquals(kind, shadow.source)
        assertTrue(shadow.rank > 0)
        assertEquals("This captured search card has no rating; do not invent one", 0f, shadow.rating, 0f)
        assertEquals(novels.size, novels.map { it.id }.distinct().size)
        assertEquals(1, requests.size)
        assertEquals("/search", requests.single().url.encodedPath)
    }

    @Test fun queryCharactersCannotChangeTheEndpointOrAddParameters() = runBlocking {
        val requests = mutableListOf<Request>()
        source(requests, fixture("search_page_shadow.html")).search("  Shadow & Slave + \u7687  ").getOrThrow()
        val url = requests.single().url
        assertEquals("Shadow & Slave + \u7687", url.queryParameter("keyword"))
        assertEquals("title", url.queryParameter("type"))
        assertEquals(setOf("keyword", "type"), url.queryParameterNames)
        assertEquals("novelphoenix.com", url.host)
    }

    @Test fun emptySearchDoesNotInventResultsOrFetchDetailPages() = runBlocking {
        val requests = mutableListOf<Request>()
        val doc = Jsoup.parse(fixture("search_page_shadow.html"))
        doc.select(NovelPhoenixSelectors.LISTING_ITEMS).remove()
        val novels = source(requests, doc.outerHtml()).search("missing title").getOrThrow()
        assertTrue(novels.isEmpty())
        assertEquals(1, requests.size)
    }

    @Test fun blankQueryNeverRequestsTheSource() = runBlocking {
        val requests = mutableListOf<Request>()
        assertTrue(source(requests, "").search("   ").getOrThrow().isEmpty())
        assertTrue(requests.isEmpty())
    }

    @Test fun sourceBlockKeepsCooldownAndDoesNotTryAnotherEndpoint() = runBlocking {
        val requests = mutableListOf<Request>()
        val source = source(requests, "<title>Just a moment...</title>", 403)
        assertTrue(source.search("Shadow").isFailure)
        assertTrue(source.search("Another title").isFailure)
        assertEquals(1, requests.size)
        assertTrue(source.isCoolingDown())
    }
}
