package com.novelnest.app.data.narration

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class NarrationCacheStats(val files: Int = 0, val bytes: Long = 0)
data class NarrationCacheClearResult(
    val remaining: NarrationCacheStats, val deleted: Int, val failed: Int, val playbackProtected: Boolean
)

/** One directory and admission gate for both Settings and the playback service. */
object NarrationCache {
    private val fileOperations = Mutex()
    private val playbackUsers = AtomicInteger(0)
    fun directory(context: Context) = File(context.applicationContext.cacheDir, "narration_audio")
    internal fun playbackStarted() { playbackUsers.incrementAndGet() }
    internal fun playbackStopped() { playbackUsers.decrementAndGet() }

    internal suspend fun <T> access(block: () -> T): T = withContext(Dispatchers.IO) {
        fileOperations.withLock { block() }
    }

    private fun files(directory: File): List<File> {
        if (!directory.exists()) return emptyList()
        return directory.listFiles { f -> f.isFile && f.extension == "mp3" }?.toList()
            ?: throw IOException("Could not inspect the audio cache.")
    }
    private fun stats(directory: File): NarrationCacheStats {
        val entries = files(directory)
        return NarrationCacheStats(entries.size, entries.sumOf { it.length() })
    }
    suspend fun inspect(context: Context): NarrationCacheStats = access { stats(directory(context)) }

    suspend fun clear(context: Context): NarrationCacheClearResult = access {
        val directory = directory(context)
        var deleted = 0
        var failed = 0
        var protected = playbackUsers.get() > 0
        // Keep the session's cache intact, including future passages and downloads.
        // A session starting during clearing waits at access() before its first cache lookup.
        if (!protected) for (file in files(directory)) {
            if (playbackUsers.get() > 0) { protected = true; break }
            if (file.delete()) deleted++ else if (file.exists()) failed++
        }
        NarrationCacheClearResult(stats(directory), deleted, failed, protected)
    }
}
