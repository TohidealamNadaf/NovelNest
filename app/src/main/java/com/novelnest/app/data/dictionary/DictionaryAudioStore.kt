package com.novelnest.app.data.dictionary

import android.os.SystemClock
import com.novelnest.app.data.cache.MemoryLruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class DictionaryAudioException(message: String, val discardRecording: Boolean = false) : IOException(message)

/** One transfer/playback lease and at most one bounded waiter. Cache clear never waits for either. */
internal class DictionaryAudioStore(private val directory: File) {
    private val transfer = Mutex()
    private val waiting = AtomicBoolean()
    // Only the four fixed metadata/media hosts can enter this bounded, process-local backoff.
    private data class SourceCooldown(val untilMs: Long, val httpStatus: Int)
    private val cooldowns = MemoryLruCache<String, SourceCooldown>(4)
    private val files = Mutex()
    private val epoch = AtomicLong()
    private var active: File? = null
    private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply { maxRequests = 1; maxRequestsPerHost = 1 })
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()

    suspend fun <T> withRecording(metadata: DictionaryRecording, use: suspend (File) -> T): T = withContext(Dispatchers.IO) {
        val url = DictionaryAudioUrls.normalize(metadata.url) ?: throw DictionaryAudioException("This recording is unavailable.")
        val sourceId = DictionaryAudioUrls.commonsPageId(metadata.source)
        var temporary: File? = null
        var leased: File? = null
        var discard = false
        // Include a waiting request in cache-clear invalidation, not only the active lease.
        val generation = epoch.get()
        acquireTransfer()
        try {
            currentCoroutineContext().ensureActive()
            val key = MessageDigest.getInstance("SHA-256").digest(("dictionary-audio2|" + url + "|" + sourceId).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val target = File(directory, "$key.audio")
            var recording = files.withLock {
                if (generation == epoch.get() && target.isFile && target.length() in 1..MAX_CLIP_BYTES.toLong()) {
                    active = target
                    leased = target
                    target.setLastModified(System.currentTimeMillis())
                    target
                } else { maintain(); null }
            }
            if (recording == null) {
                val part = files.withLock {
                    if (!directory.isDirectory && !directory.mkdirs())
                        throw DictionaryAudioException("Cannot store pronunciation audio. Check available storage.")
                    File(directory, "download-" + UUID.randomUUID() + ".tmp").also {
                        temporary = it
                        active = it
                        leased = it
                    }
                }
                try {
                    // Cache hits need no resolution. For a dictionary mirror with attribution, use
                    // that exact original recording rather than the mirror's broken MP3 address.
                    withTimeout(18_000) {
                        val address = if (sourceId != null && DictionaryAudioUrls.isDictionaryMirror(url)) {
                            val raw = request(DictionaryAudioUrls.metadataUrl(sourceId), "application/json") { response, checkActive ->
                                val body = response.body ?: throw DictionaryAudioException("The pronunciation source returned empty metadata.")
                                if (body.contentLength() > MAX_METADATA_BYTES || body.contentType()?.subtype != "json")
                                    throw DictionaryAudioException("The pronunciation source returned unsupported metadata.")
                                body.byteStream().use { input ->
                                    val output = ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        checkActive()
                                        val read = input.read(buffer)
                                        if (read < 0) break
                                        if (output.size() + read > MAX_METADATA_BYTES)
                                            throw DictionaryAudioException("The pronunciation source returned oversized metadata.")
                                        output.write(buffer, 0, read)
                                    }
                                    output.toString(Charsets.UTF_8.name())
                                }
                            }
                            DictionaryAudioUrls.originalRecording(raw, sourceId)
                        } else url
                        download(address, part)
                    }
                } catch (timeout: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    throw DictionaryAudioException("Pronunciation download timed out. Please check your connection and try again.")
                }
                currentCoroutineContext().ensureActive()
                recording = files.withLock {
                    // Clearing during a download allows this tap to finish but forbids cache repopulation.
                    if (generation == epoch.get() && trim(part.length(), target) && part.renameTo(target)) {
                        active = target
                        leased = target
                        target.setLastModified(System.currentTimeMillis())
                        target
                    } else part
                }
            }
            use(recording)
        } catch (error: Exception) {
            discard = error is DictionaryAudioException && error.discardRecording
            throw error
        } finally {
            withContext(NonCancellable) {
                try {
                    files.withLock {
                        try {
                            if (discard || generation != epoch.get()) leased?.let { it.delete() }
                            temporary?.let { if (it.exists()) it.delete() }
                        } finally { active = null }
                    }
                } finally { transfer.unlock() }
            }
        }
    }

    suspend fun clear(): Boolean = withContext(Dispatchers.IO) { files.withLock {
        epoch.incrementAndGet()
        val entries = directory.listFiles()
        var success = entries != null || !directory.exists()
        entries?.filter { owned(it) && it != active }?.forEach {
            if (!it.delete() && it.exists()) success = false
        }
        // A leased clip is deleted when its player releases it; it cannot be cached again.
        success
    } }

    private fun maintain() {
        directory.listFiles()?.filter { it != active && owned(it) }?.forEach {
            if ((it.extension == "tmp" || it.length() !in 1..MAX_CLIP_BYTES.toLong()) && !it.delete() && it.exists())
                throw DictionaryAudioException("Cannot maintain pronunciation storage. Please clear the app cache.")
        }
        trim()
    }

    private fun trim(reserve: Long = 0, replacing: File? = null): Boolean {
        val entries = directory.listFiles()?.filter { owned(it) && it.extension == "audio" && it != replacing }
            .orEmpty().sortedBy { it.lastModified() }
        var size = entries.sumOf { it.length() }
        var count = entries.size
        for (entry in entries) {
            if (size + reserve <= MAX_CACHE_BYTES && count + (if (replacing == null) 0 else 1) <= MAX_ENTRIES) break
            val length = entry.length()
            if (entry != active && entry.delete()) { size -= length; count-- }
        }
        return size + reserve <= MAX_CACHE_BYTES && count + (if (replacing == null) 0 else 1) <= MAX_ENTRIES
    }

    private suspend fun download(url: String, target: File) {
        request(url, "audio/*, application/ogg") { response, checkActive ->
            val body = response.body ?: throw DictionaryAudioException("The pronunciation recording is empty.")
            if (body.contentLength() > MAX_CLIP_BYTES || !DictionaryAudioUrls.isAudioMime(body.contentType()?.toString()))
                throw DictionaryAudioException("Unsupported pronunciation recording.")
            body.byteStream().use { input -> target.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    checkActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_CLIP_BYTES) throw DictionaryAudioException("The pronunciation recording is too large.")
                    output.write(buffer, 0, read)
                }
                if (total == 0) throw DictionaryAudioException("The pronunciation recording is empty.")
            } }
        }
    }

    private suspend fun acquireTransfer() {
        if (transfer.tryLock()) return
        if (!waiting.compareAndSet(false, true)) throw busy()
        var acquired = false
        try {
            try {
                withTimeout(1_500) { transfer.lock(); acquired = true }
            } catch (error: Exception) {
                // A timeout may race the successful lock acquisition before withTimeout returns.
                if (acquired) transfer.unlock()
                if (error is TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    throw busy()
                }
                throw error
            }
        } finally { waiting.set(false) }
    }

    private fun busy() = DictionaryAudioException("Another pronunciation is finishing. Please try again.")

    /** Join an active response reader before its file/lease can be cleaned up or reused. */
    private suspend fun <T> request(url: String, accept: String, read: (Response, () -> Unit) -> T): T {
        currentCoroutineContext().ensureActive()
        val host = url.toHttpUrl().host
        cooldowns[host]?.let { if (it.untilMs > SystemClock.elapsedRealtime()) throw cooldownError(host, it) }
        cooldowns.remove(host)
        val guard = Any()
        val readerFinished = CompletableDeferred<Unit>()
        var acceptResponse = true
        var readingResponse = false
        try {
            return suspendCancellableCoroutine { continuation ->
                val call = client.newCall(Request.Builder().url(url).header("Accept", accept)
                    .header("User-Agent", DictionaryAudioUrls.USER_AGENT).build())
                continuation.invokeOnCancellation {
                    synchronized(guard) { acceptResponse = false }
                    cleanup.launch { call.cancel() }
                }
                if (!continuation.isActive) return@suspendCancellableCoroutine
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(
                            DictionaryAudioException("Cannot load pronunciation. Check your connection and try again.")))
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val canRead = synchronized(guard) {
                            if (acceptResponse && continuation.isActive) { readingResponse = true; true } else false
                        }
                        if (!canRead) { response.close(); return }
                        val result = try {
                            Result.success(response.use {
                                val checkActive = { if (!continuation.isActive) throw CancellationException() }
                                checkActive()
                                if (it.code == 429 || it.code == 503 || (it.code in 500..599 && it.header("Retry-After") != null)) {
                                    throw cooldownError(host, rememberCooldown(host, it))
                                }
                                when (it.code) {
                                    404, 410 -> throw DictionaryAudioException("This pronunciation recording is missing (HTTP " + it.code + ").")
                                    in 500..599 -> throw DictionaryAudioException(host + " is temporarily unavailable (HTTP " + it.code + "). Please try later.")
                                }
                                if (!it.isSuccessful) throw DictionaryAudioException("The pronunciation source refused the request (HTTP " + it.code + ").")
                                read(it, checkActive)
                            })
                        } catch (error: Exception) {
                            Result.failure(if (error is DictionaryAudioException || error is CancellationException) error else
                                DictionaryAudioException("Cannot load pronunciation. Check your connection and try again."))
                        } finally { readerFinished.complete(Unit) }
                        if (continuation.isActive) continuation.resumeWith(result)
                    }
                })
            }
        } finally {
            val mustJoin = synchronized(guard) { acceptResponse = false; readingResponse }
            // If cancellation won before onResponse began, late callbacks only close the response.
            // Otherwise the bounded reader must close its stream before finally deletes the file.
            if (mustJoin) withContext(NonCancellable) { readerFinished.await() }
        }
    }

    private fun rememberCooldown(host: String, response: Response): SourceCooldown {
        val header = response.header("Retry-After")?.trim()
        val seconds = header?.toLongOrNull()?.takeIf { it >= 0 }
        val delay = if (seconds != null) seconds.coerceIn(1, 7L * 24 * 60 * 60) * 1000 else {
            val date = runCatching { ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            date?.let { (it - System.currentTimeMillis()).coerceIn(1000, 7L * 24 * 60 * 60 * 1000) }
                ?: if (response.code == 429) 60_000L else 15_000L
        }
        val next = SourceCooldown(SystemClock.elapsedRealtime() + delay, response.code)
        val cooldown = cooldowns[host]?.takeIf { it.untilMs > next.untilMs } ?: next
        cooldowns[host] = cooldown
        return cooldown
    }

    private fun cooldownError(host: String, cooldown: SourceCooldown): DictionaryAudioException {
        val seconds = ((cooldown.untilMs - SystemClock.elapsedRealtime()).coerceAtLeast(1) + 999) / 1000
        val reason = if (cooldown.httpStatus == 429) " is rate limiting pronunciation" else " is temporarily unavailable"
        return DictionaryAudioException(host + reason + " (HTTP " + cooldown.httpStatus + "). Try again in " +
            seconds + " seconds. Cached recordings are still available.")
    }

    private fun owned(file: File) = file.isFile && (file.name.matches(Regex("[0-9a-f]{64}\\.audio")) ||
        file.name.matches(Regex("download-[0-9a-f-]{36}\\.tmp")))
    private companion object {
        const val MAX_METADATA_BYTES = 32 * 1024
        const val MAX_CLIP_BYTES = 512 * 1024
        const val MAX_CACHE_BYTES = 4L * 1024 * 1024
        const val MAX_ENTRIES = 64
    }
}
