package com.novelnest.app.data.narration

import android.os.SystemClock
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Process-wide, bounded admission for new synthesis; cached audio bypasses this gate. */
internal object NarrationRequestPolicy {
    private const val WINDOW_MS = 60_000L
    private const val MAX_STARTS = 12
    private val lock = Any()
    private val starts = ArrayDeque<Long>(MAX_STARTS)
    private var failedEndpoint = ""
    private var unavailableUntil = 0L
    private var failedStatus = 0

    suspend fun admit(endpoint: String, onWaiting: () -> Unit) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val waitMs = synchronized(lock) {
                val now = SystemClock.elapsedRealtime()
                if (endpoint == failedEndpoint && now < unavailableUntil) {
                    val seconds = (unavailableUntil - now + 999L) / 1000L
                    throw IOException("Cloud narration unavailable (HTTP $failedStatus). Wait $seconds seconds, then tap Play.")
                }
                while (starts.isNotEmpty() && now - starts.first() >= WINDOW_MS) starts.removeFirst()
                if (starts.size < MAX_STARTS) {
                    starts.addLast(now)
                    0L
                } else (WINDOW_MS - (now - starts.first()) + 50L).coerceAtLeast(1L)
            }
            if (waitMs == 0L) return
            onWaiting()
            delay(waitMs) // Cancellable admission delay, before any provider request.
        }
    }

    fun failed(endpoint: String, status: Int, retryAfter: String?): Long {
        if (status != 429 && status != 503) return 0L
        val seconds = retryAfter?.trim()?.toLongOrNull()?.coerceIn(5L, 300L)
            ?: if (status == 429) 60L else 30L
        synchronized(lock) {
            val previousDeadline = unavailableUntil.takeIf { failedEndpoint == endpoint } ?: 0L
            failedEndpoint = endpoint
            failedStatus = status
            unavailableUntil = maxOf(previousDeadline,
                SystemClock.elapsedRealtime() + seconds * 1000L)
        }
        return seconds
    }
}
