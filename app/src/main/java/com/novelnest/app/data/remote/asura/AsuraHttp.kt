package com.novelnest.app.data.remote.asura

import com.novelnest.app.data.remote.network.HostRateLimiter
import com.novelnest.app.data.remote.network.HttpFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface AsuraHttp {
    suspend fun get(url: String, forceRefresh: Boolean): String
}

/** Bounded, cancellable GETs; no retries, JS execution, cookies, challenge or account bypass. */
class AsuraHttpClient(
    client: OkHttpClient = HttpFactory.getClient(),
    private val rateLimiter: HostRateLimiter = HostRateLimiter(600),
    private val maxBytes: Long = AsuraCachePolicy.MAX_RESPONSE_BYTES
) : AsuraHttp {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(30, TimeUnit.SECONDS).build()
    private val slots = Semaphore(2)

    override suspend fun get(url: String, forceRefresh: Boolean): String = slots.withPermit {
        var current = AsuraUrls.request(url)
        repeat(4) {
            rateLimiter.acquire("asura-public")
            val builder = Request.Builder().url(current).header("Referer", AsuraUrls.BASE + "/")
                .header("User-Agent", AsuraCachePolicy.USER_AGENT)
                .header("Accept", if (URI(current).host == "api.asurascans.com") "application/json" else "text/html,application/xhtml+xml")
            if (forceRefresh) builder.cacheControl(CacheControl.FORCE_NETWORK)
            val result = client.newCall(builder.build()).read()
            if (result.location != null) {
                current = AsuraUrls.request(URI(current).resolve(result.location).toString())
            } else return@withPermit result.body
        }
        throw AsuraException.Parse("Too many redirects")
    }

    private data class Text(val body: String, val location: String? = null)
    private suspend fun Call.read(): Text = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(AsuraException.Network(e))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        when (it.code) {
                            301, 302, 303, 307, 308 -> return@use Text("", it.header("Location") ?: throw AsuraException.Parse("Redirect missing Location"))
                            401 -> throw AsuraException.AccessRequired()
                            403 -> throw AsuraException.Blocked()
                            404 -> throw AsuraException.NotFound()
                        }
                        if (!it.isSuccessful) throw AsuraException.Http(it.code, it.header("Retry-After"))
                        val body = it.body ?: throw AsuraException.Parse("Empty response")
                        if (body.contentLength() > maxBytes) throw AsuraException.TooLarge()
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        body.byteStream().use { input ->
                            while (true) {
                                if (!continuation.isActive) return@onResponse
                                val size = input.read(buffer)
                                if (size < 0) break
                                if (out.size().toLong() + size > maxBytes) throw AsuraException.TooLarge()
                                out.write(buffer, 0, size)
                            }
                        }
                        Text(out.toString(Charsets.UTF_8.name()).takeIf { value -> value.isNotBlank() }
                            ?: throw AsuraException.Parse("Empty response"))
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }
}
