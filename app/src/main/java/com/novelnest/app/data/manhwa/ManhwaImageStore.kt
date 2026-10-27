package com.novelnest.app.data.manhwa

import com.novelnest.app.data.cache.MutexPool
import com.novelnest.app.data.model.ManhwaPage
import com.novelnest.app.data.remote.asura.AsuraUrls
import com.novelnest.app.data.remote.asura.AsuraCachePolicy
import com.novelnest.app.data.remote.network.HttpFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A decoder must close its lease when disposed. Eviction never deletes an in-use image. */
class ManhwaPageLease internal constructor(val file: File, private val release: () -> Unit) : Closeable {
    private val closed = AtomicBoolean()
    override fun close() { if (closed.compareAndSet(false, true)) release() }
}

/** Original image files for tiled/region decoding. Never decodes a Bitmap or warms a whole chapter. */
class ManhwaImageStore(
    private val directory: File,
    client: OkHttpClient = HttpFactory.getClient(),
    private val capacityBytes: Long = 128L * 1024 * 1024,
    private val maxImageBytes: Long = 24L * 1024 * 1024
) {
    private val client = client.newBuilder().cache(null).followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(45, TimeUnit.SECONDS).build()
    private val guard = Any()
    private val active = ConcurrentHashMap<String, Int>()
    private val locks = MutexPool()
    private val downloads = Semaphore(2)
    private var initialized = false
    init { require(capacityBytes > 0 && maxImageBytes in 1..capacityBytes) }

    private fun initialize() {
        if (initialized) return
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Manhwa image cache is unavailable")
        directory.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
        initialized = true
    }
    private fun lease(key: String, file: File): ManhwaPageLease {
        active.compute(key) { _, count -> (count ?: 0) + 1 }
        file.setLastModified(System.currentTimeMillis())
        // Decoder disposal can happen on main; it must not wait for disk eviction/clear.
        return ManhwaPageLease(file) { active.computeIfPresent(key) { _, count -> (count - 1).takeIf { it > 0 } } }
    }
    suspend fun acquire(page: ManhwaPage, chapterUrl: String, forceRefresh: Boolean = false): ManhwaPageLease {
        var acquired: ManhwaPageLease? = null
        return try {
            withContext(Dispatchers.IO) { acquireFile(page, chapterUrl, forceRefresh).also { acquired = it } }
        } catch (cancelled: CancellationException) {
            // withContext has prompt cancellation even after IO created the lease.
            acquired?.close()
            throw cancelled
        }
    }
    private suspend fun acquireFile(page: ManhwaPage, chapterUrl: String, forceRefresh: Boolean): ManhwaPageLease {
        val url = AsuraUrls.image(page.url)
        AsuraUrls.chapterParts(chapterUrl)
        val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return locks.forKey(key).withLock {
            val file = File(directory, "$key.image")
            synchronized(guard) {
                initialize()
                if (forceRefresh && (active[key] ?: 0) > 0) throw IOException("Release the current image before refreshing it")
                if (!forceRefresh && file.isFile && file.length() > 0) return@withLock lease(key, file)
            }
            downloads.withPermit {
                val temporary = File.createTempFile("page-", ".part", directory)
                try {
                    download(url, AsuraUrls.site(chapterUrl), temporary, forceRefresh)
                    synchronized(guard) {
                        val cached = directory.listFiles()?.filter { it.name.endsWith(".image") }.orEmpty()
                        var total = cached.sumOf { it.length() } - file.length() + temporary.length()
                        var count = cached.size + if (file.exists()) 0 else 1
                        for (old in cached.sortedBy { it.lastModified() }) {
                            if (total <= capacityBytes && count <= 512) break
                            if (old != file && (active[old.name.removeSuffix(".image")] ?: 0) == 0) {
                                val bytes = old.length()
                                if (old.delete()) { total -= bytes; count-- }
                            }
                        }
                        if (total > capacityBytes || count > 512) throw IOException("Manhwa image cache is full of active pages. Release unused pages and retry.")
                        if (file.exists() && !file.delete()) throw IOException("Could not replace the cached page")
                        if (!temporary.renameTo(file)) throw IOException("Could not save the manhwa page")
                        lease(key, file)
                    }
                } finally { temporary.delete() }
            }
        }
    }

    /** Called by Settings on IO; active region decoders retain their leased files. */
    suspend fun clear(): Boolean = withContext(Dispatchers.IO) { synchronized(guard) {
        initialize()
        var complete = true
        directory.listFiles()?.filter { it.name.endsWith(".image") }?.forEach { file ->
            if ((active[file.name.removeSuffix(".image")] ?: 0) > 0 || !file.delete()) complete = false
        }
        complete
    } }

    private suspend fun download(url: String, referer: String, destination: File, force: Boolean): Unit = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).header("Referer", referer).header("Accept", "image/webp,image/png,image/jpeg")
            .header("User-Agent", AsuraCachePolicy.USER_AGENT)
            .apply { if (force) cacheControl(CacheControl.FORCE_NETWORK) }.build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                destination.delete()
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                var delivered = false
                try {
                    response.use {
                        if (!it.isSuccessful) throw IOException("Asura image returned HTTP ${it.code}. Refresh chapter images or retry later.")
                        val body = it.body ?: throw IOException("Asura returned an empty image")
                        if (body.contentLength() > maxImageBytes || !body.contentType()?.type.orEmpty().equals("image", true)) throw IOException("Unsupported or oversized Asura image")
                        body.byteStream().use { input -> destination.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var total = 0L
                            while (true) {
                                if (!continuation.isActive) return@onResponse
                                val size = input.read(buffer)
                                if (size < 0) break
                                total += size
                                if (total > maxImageBytes) throw IOException("Asura image exceeds the supported size")
                                output.write(buffer, 0, size)
                            }
                        } }
                        val header = ByteArray(12)
                        val count = destination.inputStream().use { input -> input.read(header) }
                        val webp = count == 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WEBP"
                        val png = count >= 8 && header.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10).map { n -> n.toByte() }
                        val jpeg = count >= 3 && header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() && header[2] == 0xff.toByte()
                        if (!webp && !png && !jpeg) throw IOException("Asura returned an invalid or unsupported image")
                    }
                    if (continuation.isActive) { delivered = true; continuation.resume(Unit) }
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                } finally {
                    // Cancellation can resume the owner before this callback closes its stream.
                    if (!delivered) destination.delete()
                }
            }
        })
    }
}
