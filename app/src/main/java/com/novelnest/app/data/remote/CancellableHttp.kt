package com.novelnest.app.data.remote

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class TextResponse(val code: Int, val body: String, val retryAfter: String?, val cloudflareChallenge: Boolean) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** Cancellation stays attached until the body has been read, and every response is closed. */
internal suspend fun Call.awaitTextResponse(): TextResponse = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val result = response.use {
                    TextResponse(it.code, if (continuation.isActive && it.isSuccessful) it.body?.string().orEmpty() else "",
                        it.header("Retry-After"), it.header("cf-mitigated").equals("challenge", ignoreCase = true))
                }
                if (continuation.isActive) continuation.resume(result)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    })
}
