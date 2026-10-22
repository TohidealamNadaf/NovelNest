package com.novelnest.app.data.cache

import com.novelnest.app.feature.reader.Chapter
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Disposable, bounded resume cache. Every operation runs off the UI thread. */
class ReaderCacheStore(
    private val directory: File,
    private val maxBytes: Long = 12L * 1024 * 1024,
    private val maxEntries: Int = 96
) {
    private val lock = Mutex()
    private fun file(key: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(directory, hash + ".json")
    }

    suspend fun read(key: String): List<Chapter>? = withContext(Dispatchers.IO) {
        lock.withLock {
            val target = file(key)
            if (!target.isFile) return@withLock null
            try {
                if (target.length() > maxBytes) { target.delete(); return@withLock null }
                val json = JSONObject(target.readText())
                if (json.optInt("version") != 1 || json.optString("key") != key) {
                    target.delete(); return@withLock null
                }
                val items = json.getJSONArray("chapters")
                val chapters = (0 until items.length()).map { index ->
                    val item = items.getJSONObject(index)
                    val text = item.optJSONArray("text") ?: JSONArray()
                    Chapter(item.getString("id"), item.getString("title"),
                        (0 until text.length()).map { text.getString(it) }, item.optString("url"),
                        item.optString("prev").takeIf { it.isNotBlank() }, item.optString("next").takeIf { it.isNotBlank() })
                }
                target.setLastModified(System.currentTimeMillis())
                chapters
            } catch (_: Exception) {
                target.delete()
                null
            }
        }
    }

    suspend fun write(key: String, chapters: List<Chapter>, includeText: Boolean) = withContext(Dispatchers.IO) {
        lock.withLock {
            if (chapters.isEmpty()) return@withLock
            val target = file(key)
            val temporary = File(directory, target.name + ".tmp")
            try {
                if (!directory.isDirectory && !directory.mkdirs()) return@withLock
                val items = JSONArray()
                chapters.forEach { chapter ->
                    items.put(JSONObject().apply {
                        put("id", chapter.id); put("title", chapter.title); put("url", chapter.url)
                        put("prev", chapter.prevUrl.orEmpty()); put("next", chapter.nextUrl.orEmpty())
                        if (includeText) put("text", JSONArray(chapter.paragraphs))
                    })
                }
                val bytes = JSONObject().put("version", 1).put("key", key).put("chapters", items)
                    .toString().toByteArray()
                val entryLimit = minOf(maxBytes / 2, if (includeText) 512L * 1024 else 2L * 1024 * 1024)
                if (bytes.size > entryLimit) return@withLock
                temporary.writeBytes(bytes)
                try {
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                val files = directory.listFiles { entry -> entry.extension == "json" }.orEmpty().sortedBy { it.lastModified() }
                var bytesRemaining = files.sumOf { it.length() }
                var entriesRemaining = files.size
                for (entry in files) {
                    if (bytesRemaining <= maxBytes && entriesRemaining <= maxEntries) break
                    val length = entry.length()
                    if (entry.delete()) { bytesRemaining -= length; entriesRemaining-- }
                }
            } catch (_: Exception) {
                // Cache failures must not fail reading or turn a successful fetch into an error.
            } finally {
                temporary.delete()
            }
        }
    }
}
