package com.novelnest.app.data.remote.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Host-aware rate limiter that enforces a polite minimum interval
 * between successive requests to the same origin to reduce scraper stress
 * and avoid triggering Cloudflare / DDoS blocks.
 */
class HostRateLimiter(
    private val minIntervalMillis: Long = 800L
) {
    private val hostTimestamps = ConcurrentHashMap<String, Long>()
    private val mutex = Mutex()

    suspend fun acquire(host: String) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val lastTime = hostTimestamps[host] ?: 0L
            val elapsed = now - lastTime
            if (elapsed < minIntervalMillis) {
                delay(minIntervalMillis - elapsed)
            }
            hostTimestamps[host] = System.currentTimeMillis()
        }
    }
}
