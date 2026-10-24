package com.novelnest.app.data.narration

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.BreakIterator
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.net.InetSocketAddress
import java.net.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal fun audioHash(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

internal fun narrationPassages(paragraphs: List<String>): List<String> {
    val result = mutableListOf<String>()
    val sentence = BreakIterator.getSentenceInstance(Locale.ENGLISH)
    for (paragraph in paragraphs) {
        val text = paragraph.trim()
        if (text.isEmpty()) continue
        sentence.setText(text)
        var start = sentence.first()
        var end = sentence.next()
        while (end != BreakIterator.DONE) {
            var remaining = text.substring(start, end).trim()
            while (remaining.length > 700) {
                var cut = remaining.lastIndexOf(' ', 700).takeIf { it > 300 } ?: 700
                if (cut < remaining.length && Character.isLowSurrogate(remaining[cut])) cut--
                result.add(remaining.substring(0, cut).trim())
                remaining = remaining.substring(cut).trim()
            }
            if (remaining.isNotEmpty()) {
                if (result.isNotEmpty() && result.last().length + remaining.length < 450) {
                    result[result.lastIndex] += " " + remaining
                } else result.add(remaining)
            }
            start = end; end = sentence.next()
        }
    }
    return result
}

/** Only one service uses this cache; no raw PCM or chapter-sized audio in memory. */
internal class NarrationAudio(private val directory: File, private val store: NarrationStore) {
    private var cleaned = false
    private val closed = AtomicBoolean(false)
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
        .eventListenerFactory { call -> object : EventListener() {
            override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
                call.request().tag(AudioProgress::class.java)?.update?.invoke(NarrationPhase.CONNECTING)
            }
            override fun requestBodyEnd(call: Call, byteCount: Long) {
                call.request().tag(AudioProgress::class.java)?.update?.invoke(NarrationPhase.WAITING_FOR_AUDIO)
            }
        } }.build()

    suspend fun get(text: String, onPhase: (NarrationPhase) -> Unit = {}): File = withContext(Dispatchers.IO) {
        check(!closed.get()) { "Narration session is closed." }
        onPhase(NarrationPhase.CHECKING_CACHE)
        val (endpoint, token) = store.credentials()
        val key = audioHash("melotts-en-v1|$endpoint|$text")
        val target = File(directory, "$key.mp3")
        val cached = NarrationCache.access {
            check(directory.isDirectory || directory.mkdirs()) { "Audio storage unavailable." }
            if (!cleaned) {
                val staleBefore = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
                directory.listFiles { f -> f.extension == "tmp" && f.lastModified() < staleBefore }
                    ?.forEach { it.delete() }
                cleaned = true
            }
            if (target.isFile && isAudio(target)) {
                target.setLastModified(System.currentTimeMillis()); true
            } else { if (target.exists()) target.delete(); false }
        }
        if (cached) { onPhase(NarrationPhase.USING_CACHE); return@withContext target }
        if (endpoint.isBlank() || token.isBlank()) throw IOException("Configure cloud narration first.")
        val request = Request.Builder().url(endpoint)
            .tag(AudioProgress::class.java, AudioProgress(onPhase))
            .header("Authorization", "Bearer $token")
            .post(JSONObject().put("text", text).toString().toRequestBody("application/json".toMediaType())).build()
        val call = client.newCall(request)
        onPhase(NarrationPhase.CONNECTING)
        // Cancellation remains registered until the response body has been copied and closed.
        suspendCancellableCoroutine<File> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!call.isCanceled()) android.util.Log.w("NovelNestNarration",
                        "Audio transport failed: ${e.javaClass.simpleName}")
                    val message = when (e) {
                        is java.net.SocketTimeoutException, is java.io.InterruptedIOException -> "Cloud audio request timed out. Tap Play to retry."
                        is java.net.UnknownHostException -> "Cannot reach the Worker. Check your internet connection and endpoint."
                        else -> "Cloud audio connection failed. Check your network and tap Play to retry."
                    }
                    if (continuation.isActive) continuation.resumeWith(Result.failure(IOException(message, e)))
                }
                override fun onResponse(call: Call, response: Response) {
                    val temporary = File(directory, "$key.${java.util.UUID.randomUUID()}.tmp")
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) {
                                android.util.Log.w("NovelNestNarration", "Audio response failed: HTTP ${it.code}")
                                throw IOException(responseError(it))
                            }
                            if (it.header("Content-Type")?.startsWith("audio/") != true) throw IOException("Invalid audio response.")
                            val body = it.body ?: throw IOException("Empty audio response.")
                            onPhase(NarrationPhase.DOWNLOADING)
                            body.byteStream().use { input -> temporary.outputStream().use { output ->
                                val buffer = ByteArray(8192); var total = 0L
                                while (true) {
                                    val size = input.read(buffer); if (size < 0) break
                                    total += size
                                    if (total > 8L * 1024 * 1024 || call.isCanceled()) throw IOException("Audio request interrupted or too large.")
                                    output.write(buffer, 0, size)
                                }
                                if (total == 0L) throw IOException("Empty audio response.")
                            } }
                            if (!isAudio(temporary)) throw IOException("Worker returned invalid audio. Redeploy the updated Worker and retry.")
                            if (!temporary.renameTo(target)) throw IOException("Could not save audio.")
                            target
                        }
                    }
                    temporary.delete()
                    result.onFailure { error ->
                        if (!call.isCanceled()) android.util.Log.w("NovelNestNarration",
                            "Audio download failed: ${error.javaClass.simpleName}")
                    }
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            })
        }
    }

    private class AudioProgress(val update: (NarrationPhase) -> Unit)

    fun close() {
        if (closed.compareAndSet(false, true)) NarrationNetworkCleanup.release(client)
    }

    private fun isAudio(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val bytes = ByteArray(12)
            val size = input.read(bytes)
            size >= 3 && (String(bytes, 0, 3, Charsets.US_ASCII) == "ID3" ||
                ((bytes[0].toInt() and 255) == 255 && (bytes[1].toInt() and 224) == 224 && (bytes[1].toInt() and 6) != 0) ||
                (size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE"))
        }
    }.getOrDefault(false)

    private fun responseError(response: Response): String {
        val code = runCatching { JSONObject(response.peekBody(4096).string()).optString("code") }.getOrDefault("")
        return when (code) {
            "INVALID_AUDIO", "UPSTREAM_FORMAT" -> "Cloudflare returned unreadable audio. Redeploy the updated Worker and retry."
            "QUOTA_EXCEEDED" -> "Cloudflare free audio allowance is exhausted. Try after the daily reset."
            "RATE_LIMITED", "BUSY" -> "Cloud narration is busy or rate limited. Wait a minute before retrying."
            "CONFIGURATION" -> "Worker AI binding is missing. Check the Worker deployment."
            else -> when (response.code) {
                401, 403 -> "Cloud access denied. Check the Worker access token in Settings."
                404 -> "Narration endpoint not found. Check the Worker URL in Settings."
                429 -> "Cloudflare quota or request limit reached. Wait before retrying."
                else -> "Cloud audio generation failed (HTTP ${response.code}). Check the Worker and retry."
            }
        }
    }

    suspend fun trim(protected: Set<String>) = withContext(Dispatchers.IO) {
        val files = directory.listFiles { f -> f.extension == "mp3" }.orEmpty().sortedBy { it.lastModified() }
        var size = files.sumOf { it.length() }
        for (file in files) {
            if (size <= 64L * 1024 * 1024) break
            if (file.absolutePath !in protected) { val bytes = file.length(); if (file.delete()) size -= bytes }
        }
    }
}
