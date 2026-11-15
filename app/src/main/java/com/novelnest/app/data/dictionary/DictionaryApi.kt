package com.novelnest.app.data.dictionary

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Isolated dispatcher and no HTTP disk cache, scraper headers, credentials, retries, or redirects. */
internal class DictionaryApi {
    private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply { maxRequests = 2; maxRequestsPerHost = 2 })
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()

    suspend fun fetch(word: String): DictionaryResult {
        val url = "https://api.dictionaryapi.dev/api/v2/entries/en/".toHttpUrl().newBuilder().addPathSegment(word).build()
        val call = client.newCall(Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", "NovelNest-Dictionary/1.0").build())
        val raw = suspendCancellableCoroutine<String> { continuation ->
            // Cancellation can close a TLS socket. Keep that work away from Main.
            continuation.invokeOnCancellation { cleanup.launch { call.cancel() } }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(networkFailure(e)))
                }
                override fun onResponse(call: Call, response: Response) {
                    if (!continuation.isActive) { response.close(); return }
                    val result = try {
                        Result.success(response.use {
                            when (it.code) {
                                404 -> throw DictionaryException(DictionaryError.NOT_FOUND,
                                    "No dictionary entry was found. Try the base form of the word. Fictional names and terms may not be included.")
                                429 -> throw DictionaryException(DictionaryError.RATE_LIMIT,
                                    "The dictionary is temporarily rate limited. Cached definitions are still available.", retryAfter(it.header("Retry-After")))
                                in 500..599 -> throw DictionaryException(DictionaryError.UNAVAILABLE,
                                    "The dictionary service is temporarily unavailable. Please try again later.")
                            }
                            if (!it.isSuccessful) throw DictionaryException(DictionaryError.UNAVAILABLE,
                                "The dictionary could not complete this lookup. Please try again later.")
                            val body = it.body ?: invalid()
                            if (body.contentLength() > MAX_BYTES) invalid()
                            body.byteStream().use { input ->
                                val output = ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (true) {
                                    if (!continuation.isActive) throw IOException("Cancelled")
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    if (output.size() + read > MAX_BYTES) invalid()
                                    output.write(buffer, 0, read)
                                }
                                output.toString(Charsets.UTF_8.name())
                            }
                        })
                    } catch (error: DictionaryException) { Result.failure(error) }
                    catch (error: IOException) { Result.failure(networkFailure(error)) }
                    catch (_: Exception) { Result.failure(DictionaryException(DictionaryError.INVALID_RESPONSE, "The dictionary response could not be read.")) }
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            })
        }
        return withContext(Dispatchers.Default) { DictionaryParser.parse(raw, word, System.currentTimeMillis()) }
    }

    private fun retryAfter(header: String?): Long {
        val seconds = header?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
        if (seconds != null) return seconds.coerceIn(1L, 7L * 24 * 60 * 60) * 1000
        val date = runCatching { ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
        return date?.let { (it - System.currentTimeMillis()).coerceIn(1000, 7L * 24 * 60 * 60 * 1000) } ?: 60_000
    }
    private fun networkFailure(error: IOException) = if (error is InterruptedIOException)
        DictionaryException(DictionaryError.TIMEOUT, "The dictionary lookup timed out. Please retry when ready.")
        else DictionaryException(DictionaryError.NETWORK, "Cannot reach the dictionary. Check your connection or use a cached definition.")
    private fun invalid(): Nothing = throw DictionaryException(DictionaryError.INVALID_RESPONSE, "The dictionary response is empty or too large.")
    companion object { private const val MAX_BYTES = 512 * 1024 }
}
