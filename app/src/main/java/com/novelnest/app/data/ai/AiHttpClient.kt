package com.novelnest.app.data.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.UnknownHostException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Application lifetime client, isolated from scraper/audio pools and their teardown. Never logs content or secrets. */
class AiHttpClient internal constructor(clientOverride: OkHttpClient?) {
    constructor() : this(null)
    private class Progress(val sent: () -> Unit)
    private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = (clientOverride?.newBuilder() ?: OkHttpClient.Builder()).connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
        .eventListener(object : EventListener() {
            override fun requestBodyEnd(call: Call, byteCount: Long) { call.request().tag(Progress::class.java)?.sent?.invoke() }
        }).build()

    suspend fun json(url: String, credential: String, gemini: Boolean = false, body: JSONObject? = null,
        sent: () -> Unit = {}): JSONObject {
        // Endpoints are supplied only by fixed adapters; reject accidental insecure expansion.
        val request = Request.Builder().url(url)
        require(request.build().url.isHttps)
        if (gemini) request.header("x-goog-api-key", credential)
        else request.header("Authorization", "Bearer $credential")
        request.header("Accept", "application/json").header("Cache-Control", "no-store")
        if (body != null) {
            val encoded = body.toString()
            if (encoded.toByteArray(Charsets.UTF_8).size > 256 * 1024)
                throw AiException(AiErrorCode.TOO_LARGE, "The chapter request exceeds the supported size.")
            request.post(encoded.toRequestBody("application/json; charset=utf-8".toMediaType())).tag(Progress::class.java, Progress(sent))
        }
        val call = client.newCall(request.build())
        val raw = suspendCancellableCoroutine<String> { continuation ->
            // Socket/TLS cancellation can perform I/O. Do not run it in a main-thread cancellation handler.
            continuation.invokeOnCancellation { cleanup.launch { call.cancel() } }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val code = when (e) {
                        is UnknownHostException -> AiErrorCode.NETWORK
                        is java.io.InterruptedIOException -> AiErrorCode.TIMEOUT
                        else -> AiErrorCode.NETWORK
                    }
                    val message = if (code == AiErrorCode.TIMEOUT) "The AI request timed out. Retry when ready."
                        else "Cannot reach the AI service. Check your connection."
                    if (continuation.isActive) continuation.resumeWith(Result.failure(AiException(code, message)))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) {
                                val rawError = it.peekBody(8192).string()
                                val error = runCatching { JSONObject(rawError).optJSONObject("error") }.getOrNull()
                                val flags = listOf(error?.optString("code"), error?.optString("type"))
                                if (flags.any { flag -> flag?.lowercase(java.util.Locale.ROOT) in setOf(
                                        "content_policy_violation", "content_filter", "safety", "prohibited_content", "moderation_blocked", "recitation") })
                                    throw AiException(AiErrorCode.REFUSED, "The provider declined this content. The original is unchanged.")
                                if (error?.optString("code") == "json_validate_failed")
                                    throw AiException(AiErrorCode.INVALID_OUTPUT,
                                        "The model generated a response that failed the required JSON schema. Try another configured model. Source text and saved results are unchanged.")
                                throw httpFailure(it.code, it.header("Retry-After"))
                            }
                            val source = it.body ?: throw AiException(AiErrorCode.INVALID_OUTPUT, "The AI service returned an empty response.")
                            val limit = if (body == null) 4 * 1024 * 1024 else 512 * 1024
                            if (source.contentLength() > limit) throw AiException(AiErrorCode.INVALID_OUTPUT, "The AI response is too large.")
                            source.byteStream().use { input ->
                                val output = ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (true) {
                                    if (!continuation.isActive) throw IOException("Cancelled")
                                    val size = input.read(buffer)
                                    if (size < 0) break
                                    if (output.size() + size > limit) throw AiException(AiErrorCode.INVALID_OUTPUT, "The AI response is too large.")
                                    output.write(buffer, 0, size)
                                }
                                output.toString(Charsets.UTF_8.name())
                            }
                        }
                    }.recoverCatching { error ->
                        if (error is AiException) throw error
                        if (error is java.io.InterruptedIOException) throw AiException(AiErrorCode.TIMEOUT, "The AI response timed out. Retry when ready.")
                        throw AiException(AiErrorCode.NETWORK, "The AI response was interrupted. Check your connection.")
                    }
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            })
        }
        return withContext(Dispatchers.Default) {
            try { JSONObject(raw) }
            catch (_: Exception) { throw AiException(AiErrorCode.INVALID_OUTPUT, "The AI service returned an invalid response.") }
        }
    }

    private fun httpFailure(status: Int, retry: String?): AiException {
        val seconds = retry?.toLongOrNull() ?: runCatching {
            val reset = ZonedDateTime.parse(retry, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
            ((reset - System.currentTimeMillis()) / 1000).coerceAtLeast(1)
        }.getOrNull()
        val code = when (status) {
            401, 403 -> AiErrorCode.CREDENTIALS
            402 -> AiErrorCode.BILLING_POLICY
            404, 410 -> AiErrorCode.MODEL_UNAVAILABLE
            429 -> AiErrorCode.RATE_LIMIT
            408, 504 -> AiErrorCode.TIMEOUT
            in 500..599 -> AiErrorCode.UNAVAILABLE
            else -> AiErrorCode.INVALID_OUTPUT
        }
        val message = when (code) {
            AiErrorCode.CREDENTIALS -> "The provider rejected this API key or access. Check AI Reading Tools."
            AiErrorCode.BILLING_POLICY -> "The provider requires billing or additional credit. No paid fallback was selected."
            AiErrorCode.MODEL_UNAVAILABLE -> "This model is unavailable. Select another supported model."
            AiErrorCode.RATE_LIMIT -> "The provider's usage limit was reached. Wait for its allowance to reset."
            AiErrorCode.TIMEOUT -> "The provider did not respond in time."
            AiErrorCode.UNAVAILABLE -> "The AI provider is temporarily unavailable."
            else -> "The provider rejected the requested output format or chapter size."
        }
        return AiException(code, message, seconds?.coerceIn(1, 86_400))
    }
}
