package com.novelnest.app.data.dictionary

import com.novelnest.app.data.cache.MemoryLruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicLong

internal data class DictionarySnapshot(val result: DictionaryResult?, val generation: Long)

/** Owns only dictionary files. Locks cover bounded cache I/O, never network calls or reader state. */
internal class DictionaryCache(private val directory: File) {
    private data class MemoryEntry(val result: DictionaryResult, var touchedAt: Long)
    private val memory = MemoryLruCache<String, MemoryEntry>(32)
    private val mutex = Mutex()
    private val epoch = AtomicLong()
    private var maintained = false
    private var diskWritable = true
    val generation: Long get() = epoch.get()

    suspend fun read(word: String): DictionarySnapshot = withContext(Dispatchers.IO) { mutex.withLock {
        val key = DictionaryWord.key(word)
        val file = File(directory, "$key.json")
        try {
            maintain()
            val now = System.currentTimeMillis()
            memory[key]?.let {
                if (now < it.touchedAt || now - it.touchedAt >= 60_000) {
                    file.setLastModified(now)
                    it.touchedAt = now
                }
                return@withLock DictionarySnapshot(it.result, epoch.get())
            }
            if (!file.isFile) return@withLock DictionarySnapshot(null, epoch.get())
            if (file.length() !in 1..MAX_ENTRY_BYTES.toLong()) {
                file.delete()
                return@withLock DictionarySnapshot(null, epoch.get())
            }
            val result = DictionaryParser.decode(file.readText(Charsets.UTF_8), word, key)
            currentCoroutineContext().ensureActive()
            memory[key] = MemoryEntry(result, now)
            file.setLastModified(now)
            DictionarySnapshot(result, epoch.get())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            runCatching { file.delete() }
            DictionarySnapshot(null, epoch.get())
        }
    } }

    suspend fun write(result: DictionaryResult, expectedGeneration: Long) = withContext(Dispatchers.IO) { mutex.withLock {
        if (epoch.get() != expectedGeneration) return@withLock
        val key = DictionaryWord.key(result.query)
        val temporary = File(directory, "$key.tmp")
        try {
            val bytes = DictionaryParser.encode(result, key).toByteArray(Charsets.UTF_8)
            // Valid larger responses are still displayed; caching must not become a memory sink.
            if (bytes.size > MAX_ENTRY_BYTES) return@withLock
            currentCoroutineContext().ensureActive()
            maintain()
            memory[key] = MemoryEntry(result, System.currentTimeMillis())
            if (!diskWritable || !trim(bytes.size, key)) return@withLock
            if (!directory.isDirectory && !directory.mkdirs()) { diskWritable = false; return@withLock }
            temporary.writeBytes(bytes)
            currentCoroutineContext().ensureActive()
            val target = File(directory, "$key.json")
            try { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            trim()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { diskWritable = false /* Keep using memory until clear or the next process. */ }
        finally { if (temporary.exists() && !temporary.delete()) diskWritable = false }
    } }

    suspend fun clear(): Boolean = withContext(Dispatchers.IO) { mutex.withLock {
        epoch.incrementAndGet()
        memory.clear()
        var success = true
        val files = directory.listFiles()
        if (files == null && directory.exists()) success = false
        files?.filter { owned.matches(it.name) }?.forEach {
            if (!it.isFile || (!it.delete() && it.exists())) success = false
        }
        diskWritable = success
        success
    } }

    private fun maintain() {
        if (maintained) return
        maintained = true
        directory.listFiles()?.filter { it.isFile && owned.matches(it.name) }?.forEach {
            if ((it.extension == "tmp" || it.length() > MAX_ENTRY_BYTES) && !it.delete()) diskWritable = false
        }
        trim()
    }

    private fun trim(reserveBytes: Int = 0, replacing: String? = null): Boolean {
        val files = directory.listFiles { file -> file.isFile && owned.matches(file.name) && file.extension == "json" }
            .orEmpty().filter { it.nameWithoutExtension != replacing }.sortedBy { it.lastModified() }
        var size = files.sumOf { it.length() }
        var count = files.size
        for (file in files) {
            if (size + reserveBytes <= MAX_DISK_BYTES && count + (if (replacing == null) 0 else 1) <= MAX_DISK_ENTRIES) break
            val bytes = file.length()
            if (file.delete()) {
                size -= bytes
                count--
                memory.remove(file.nameWithoutExtension)
            }
        }
        return size + reserveBytes <= MAX_DISK_BYTES && count + (if (replacing == null) 0 else 1) <= MAX_DISK_ENTRIES
    }

    companion object {
        private const val MAX_ENTRY_BYTES = 64 * 1024
        private const val MAX_DISK_BYTES = 8L * 1024 * 1024
        private const val MAX_DISK_ENTRIES = 256
        private val owned = Regex("[0-9a-f]{64}\\.(json|tmp)")
    }
}
