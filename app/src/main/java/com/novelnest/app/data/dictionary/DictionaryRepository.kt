package com.novelnest.app.data.dictionary

import android.os.SystemClock
import com.novelnest.app.data.cache.MemoryLruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** One application owner; in-flight operations contain data only and have reference-counted callers. */
internal class DictionaryRepository(directory: File) {
    private val cache = DictionaryCache(directory)
    private val api = DictionaryApi()
    val pronunciation = DictionaryAudioStore(File(directory, "audio"))
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private data class FlightKey(val word: String, val generation: Long)
    private class Flight(val task: Deferred<DictionaryResult>, var users: Int = 0, var abandoned: Boolean = false)
    private val flights = mutableMapOf<FlightKey, Flight>()
    private val missing = MemoryLruCache<String, Long>(32)
    private val cooldownUntil = AtomicLong()

    suspend fun cached(word: String): DictionarySnapshot = cache.read(word)
    fun cooldownRemainingMs(): Long = (cooldownUntil.get() - SystemClock.elapsedRealtime()).coerceAtLeast(0)

    suspend fun fetch(word: String, generation: Long, force: Boolean): DictionaryResult = withContext(Dispatchers.IO) {
        val key = FlightKey(word, generation)
        val flight = synchronized(lock) {
            val existing = flights[key]
            if (existing != null && !existing.abandoned && !existing.task.isCancelled) existing.apply { users++ }
            else {
                // Bound the registry as well as the HTTP dispatcher; no unbounded word/Deferred map.
                if (flights.size >= 2 || existing != null) throw DictionaryException(DictionaryError.BUSY,
                    "Another dictionary lookup is finishing. Please retry in a moment.")
                val task = work.async(start = CoroutineStart.LAZY) { request(word, generation, force) }
                Flight(task, 1).also { created ->
                    flights[key] = created
                    task.invokeOnCompletion {
                        synchronized(lock) { if (flights[key] === created) flights.remove(key) }
                    }
                }
            }
        }
        try {
            flight.task.start()
            flight.task.await()
        } finally {
            val cancel = synchronized(lock) {
                (--flight.users == 0 && !flight.task.isCompleted).also { if (it) flight.abandoned = true }
            }
            if (cancel) flight.task.cancel()
        }
    }

    private suspend fun request(word: String, generation: Long, force: Boolean): DictionaryResult {
        if (!force) {
            val latest = cache.read(word)
            latest.result?.takeIf { it.isFresh(System.currentTimeMillis()) }?.let { return it }
            missing[word]?.let { until ->
                if (until > SystemClock.elapsedRealtime()) throw notFound()
                missing.remove(word)
            }
        }
        val remaining = cooldownRemainingMs()
        if (remaining > 0) throw DictionaryException(DictionaryError.RATE_LIMIT,
            "The dictionary is temporarily rate limited. Cached definitions are still available.", remaining)
        try {
            val result = api.fetch(word)
            cache.write(result, generation)
            return result
        } catch (error: DictionaryException) {
            when (error.kind) {
                DictionaryError.NOT_FOUND -> if (cache.generation == generation) synchronized(lock) {
                    // Clear and error completion use the same short registry lock.
                    if (cache.generation == generation) missing[word] = SystemClock.elapsedRealtime() + 10 * 60 * 1000
                }
                DictionaryError.RATE_LIMIT -> cooldownUntil.updateAndGet {
                    maxOf(it, SystemClock.elapsedRealtime() + error.retryAfterMs.coerceAtLeast(1000))
                }
                else -> Unit
            }
            throw error
        }
    }

    suspend fun clearCache(): Boolean {
        val cleared = cache.clear()
        val audioCleared = pronunciation.clear()
        synchronized(lock) { missing.clear() }
        // A cache clear does not bypass an active provider cooldown.
        return cleared && audioCleared
    }
    private fun notFound() = DictionaryException(DictionaryError.NOT_FOUND,
        "No dictionary entry was found. Try the base form of the word. Fictional names and terms may not be included.")
}
