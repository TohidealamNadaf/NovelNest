package com.novelnest.app.data.remote.network

import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A temporary local pause after the source reports a block or rate limit. */
class SourceCooldownException(
    val remainingSeconds: Long,
    val statusCode: Int?,
    val challenge: Boolean
) : IOException(buildString {
    when {
        challenge -> append("The source requires browser verification")
        statusCode == 429 -> append("The source is rate limiting requests (HTTP 429)")
        statusCode == 403 -> append("The source denied the request (HTTP 403)")
        else -> append("The source is temporarily unavailable")
    }
    append(". Try again in about ").append(remainingSeconds).append(" seconds.")
})

/** Throttles request starts and shares a server-directed cooldown per source host. */
class HostRateLimiter(
    private val minIntervalMillis: Long = 800L,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val wallMillis: () -> Long = System::currentTimeMillis
) {
    private data class Cooldown(val until: Long, val status: Int?, val challenge: Boolean)
    private val hostTimestamps = ConcurrentHashMap<String, Long>()
    private val cooldowns = ConcurrentHashMap<String, Cooldown>()
    private val mutex = Mutex()

    fun cooldown(host: String): SourceCooldownException? {
        val entry = cooldowns[host] ?: return null
        val now = monotonicMillis()
        val remaining = if (now < 0L && entry.until > Long.MAX_VALUE + now) Long.MAX_VALUE else entry.until - now
        if (remaining <= 0L) {
            cooldowns.remove(host, entry)
            return null
        }
        return SourceCooldownException((remaining / 1000L) + if (remaining % 1000L > 0L) 1L else 0L, entry.status, entry.challenge)
    }

    /** Retry-After accepts delta seconds or an HTTP date. Fallback is 60 seconds. */
    fun block(host: String, statusCode: Int?, challenge: Boolean, retryAfter: String?): SourceCooldownException {
        val fallbackMs = 60_000L
        val duration = retryAfter?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
            value.toLongOrNull()?.takeIf { it >= 0L }?.let { seconds ->
                if (seconds > Long.MAX_VALUE / 1000L) Long.MAX_VALUE else seconds * 1000L
            } ?: runCatching {
                (ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - wallMillis()).coerceAtLeast(0L)
            }.getOrNull()
        } ?: fallbackMs
        val now = monotonicMillis()
        val bounded = duration.coerceAtLeast(1_000L)
        val until = if (now > Long.MAX_VALUE - bounded) Long.MAX_VALUE else now + bounded
        val proposed = Cooldown(until, statusCode, challenge)
        cooldowns.compute(host) { _, existing ->
            if (existing != null && existing.until > proposed.until) existing else proposed
        }
        return cooldown(host) ?: SourceCooldownException(0L, statusCode, challenge)
    }

    suspend fun acquire(host: String) {
        mutex.withLock {
            cooldown(host)?.let { throw it }
            val now = monotonicMillis()
            hostTimestamps[host]?.let { last ->
                val elapsed = (now - last).coerceAtLeast(0L)
                if (elapsed < minIntervalMillis) delay(minIntervalMillis - elapsed)
            }
            // A response from a concurrent request may have started a cooldown while waiting.
            cooldown(host)?.let { throw it }
            hostTimestamps[host] = monotonicMillis()
        }
    }
}
