package com.novelnest.app.data.remote.novelphoenix

import com.novelnest.app.data.remote.network.HostRateLimiter
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class NovelPhoenixPaginationTest {
    @Test fun unreasonablePageCountFailsBeforeAllocatingPaginationWork() {
        for (count in listOf("1001", "999999999999999999999")) {
            try {
                NovelPhoenixParser.parseChapterListPage("<select aria-label='Chapter range'><option value='?page=$count'>Pages</option></select>")
                fail("Expected an invalid pagination count to be rejected")
            } catch (expected: PhoenixException.ParseError) {
                assertEquals("totalPages", expected.fieldName)
            }
        }
    }

    @Test fun fixedWorkersProduceOrderedCompleteCatalogInBothFetchPaths() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val concurrent = active.incrementAndGet()
            peak.updateAndGet { maxOf(it, concurrent) }
            try {
                calls.incrementAndGet()
                val page = chain.request().url.queryParameter("page")?.toInt() ?: 1
                Thread.sleep(if (page == 2) 40 else 5)
                val html = """
                    <select aria-label='Chapter range'><option value='?page=9'>Pages</option></select>
                    <ul class='chapter-list'><li><a href='/novel/book/chapter-$page'>
                    <span class='chapter-no'>$page</span><span class='chapter-title'>Chapter $page</span></a></li></ul>
                """.trimIndent()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(html.toResponseBody("text/html".toMediaType())).build()
            } finally { active.decrementAndGet() }
        }.build()
        val source = NovelPhoenixSource(client = client, rateLimiter = HostRateLimiter(0))
        val expected = (1..9).map { "chapter-$it" }
        val emissions = source.getChaptersFlow("book", true).toList()
        assertEquals(listOf("chapter-1"), emissions.first().getOrThrow().map { it.id })
        assertEquals(expected, emissions.last().getOrThrow().map { it.id })
        assertEquals(9, calls.get())
        assertTrue(peak.get() <= 3)
        assertEquals(expected, source.getChapters("book", fetchAll = true, forceRefresh = true).map { it.id })
        assertEquals(18, calls.get())
        assertTrue(peak.get() <= 3)
    }
}
