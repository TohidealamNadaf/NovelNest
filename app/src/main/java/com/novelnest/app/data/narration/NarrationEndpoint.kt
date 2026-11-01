package com.novelnest.app.data.narration

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject

/** Explicit, authenticated reachability check. Does not call Workers AI or generate audio. */
object NarrationEndpoint {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()

    suspend fun check(endpoint: String, token: String) = withContext(Dispatchers.IO) {
        val url = NarrationStore.validateEndpoint(endpoint)
        require(token.length in 24..512 && token.none { it.isWhitespace() }) { "Enter a valid Worker access token." }
        val request = Request.Builder().url(url.newBuilder().addPathSegment("health").build())
            .header("Authorization", "Bearer $token").get().build()
        val call = client.newCall(request)
        suspendCancellableCoroutine<Unit> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(IOException("Could not reach the Worker. Check the address and network.", e)))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw IOException(when (it.code) {
                                401, 403 -> "Worker rejected the access token."
                                404 -> "Health endpoint missing. Deploy the updated Worker."
                                429 -> "Worker is rate limited. Try later."
                                else -> "Worker check failed (${it.code})."
                            })
                            if (it.body?.contentLength()?.let { size -> size > 4096 } == true) throw IOException("Unexpected Worker response.")
                            val bytes = it.body?.byteStream()?.use { input ->
                                val buffer = ByteArray(4097)
                                var size = 0
                                while (size < buffer.size) {
                                    val count = input.read(buffer, size, buffer.size - size)
                                    if (count < 0) break
                                    size += count
                                }
                                buffer.copyOf(size)
                            }
                                ?: throw IOException("Empty Worker response.")
                            if (bytes.size > 4096) throw IOException("Unexpected Worker response.")
                            val body = JSONObject(String(bytes, Charsets.UTF_8))
                            if (body.optInt("protocol") != 1 || body.optString("service") != "novelnest-narration" ||
                                !body.optBoolean("ready")) throw IOException("Worker configuration is incomplete.")
                        }
                    }
                    if (continuation.isActive) continuation.resumeWith(result.map { Unit })
                }
            })
        }
    }
}
