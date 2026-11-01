package com.novelnest.app

import com.novelnest.app.data.remote.awaitTextResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.*
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CancellableHttpTest {
    private class FakeCall : Call {
        lateinit var callback: Callback
        var cancelled = false
        var executed = false
        override fun request() = Request.Builder().url("https://example.com/novel/book").build()
        override fun enqueue(responseCallback: Callback) { executed = true; callback = responseCallback }
        override fun execute(): Response = error("Synchronous execution is not expected")
        override fun cancel() { cancelled = true }
        override fun isCanceled() = cancelled
        override fun isExecuted() = executed
        override fun timeout() = Timeout.NONE
        override fun clone(): Call = FakeCall()
        fun respond(body: ResponseBody, code: Int = 200) {
            callback.onResponse(this, Response.Builder().request(request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("Response").body(body).build())
        }
    }

    private class Body(onRead: () -> Unit = {}) : ResponseBody() {
        var closed = false
        var reads = 0
        private val stream = object : ForwardingSource(Buffer().writeUtf8("chapter text")) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                reads++; onRead(); return super.read(sink, byteCount)
            }
            override fun close() { closed = true; super.close() }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength() = -1L
        override fun source(): BufferedSource = stream
    }

    @Test fun readsSuccessfulBodyAndClosesIt() = runTest {
        val call = FakeCall(); val body = Body()
        val result = async { call.awaitTextResponse() }; runCurrent()
        call.respond(body)
        assertEquals("chapter text", result.await().body)
        assertTrue(body.closed)
    }

    @Test fun rejectedResponsesCloseWithoutDownloadingTheBody() = runTest {
        for (code in listOf(403, 404, 429, 503)) {
            val call = FakeCall(); val body = Body()
            val result = async { call.awaitTextResponse() }; runCurrent()
            call.respond(body, code)
            assertEquals(code, result.await().code)
            assertTrue(body.closed); assertEquals(0, body.reads)
        }
    }

    @Test fun cancellationBeforeHeadersCancelsHttpAndClosesLateResponses() = runTest {
        val call = FakeCall(); val body = Body()
        val result = async { call.awaitTextResponse() }; runCurrent()
        result.cancel(); runCurrent(); call.respond(body)
        assertTrue(call.cancelled); assertTrue(body.closed); assertEquals(0, body.reads)
    }

    @Test fun cancellationRemainsAttachedDuringBodyDownload() = runTest {
        val call = FakeCall()
        val result = async { call.awaitTextResponse() }; runCurrent()
        val body = Body { result.cancel() }
        call.respond(body); runCurrent()
        assertTrue(call.cancelled); assertTrue(body.closed); assertTrue(result.isCancelled)
    }
}
