package com.novelnest.app.data.remote.network

import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight Circuit Breaker to prevent hammering failing or Cloudflare-blocked endpoints.
 * When consecutive failures exceed [failureThreshold], trips OPEN for [resetTimeoutMillis].
 *
 * Only genuine block signals (403, 429, Cloudflare challenges) should record failures.
 * Transient network errors (timeouts, DNS, connection refused) should NOT trip the breaker.
 */
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val resetTimeoutMillis: Long = 60_000L // 1 minute (was 3 min — too aggressive)
) {
    private data class State(
        var failureCount: Int = 0,
        var lastFailureTimestamp: Long = 0L,
        var isOpen: Boolean = false
    )

    private val states = ConcurrentHashMap<String, State>()

    fun canExecute(key: String): Boolean {
        val state = states[key] ?: return true
        synchronized(state) {
            if (!state.isOpen) return true

            val now = System.currentTimeMillis()
            if (now - state.lastFailureTimestamp > resetTimeoutMillis) {
                // Half-open: allow a trial attempt
                state.isOpen = false
                state.failureCount = 0
                return true
            }
        }
        return false
    }

    fun recordSuccess(key: String) {
        val state = states[key] ?: return
        synchronized(state) {
            state.failureCount = 0
            state.isOpen = false
        }
    }

    fun recordFailure(key: String) {
        val state = states.computeIfAbsent(key) { State() }
        synchronized(state) {
            state.failureCount++
            state.lastFailureTimestamp = System.currentTimeMillis()
            if (state.failureCount >= failureThreshold) {
                state.isOpen = true
            }
        }
    }

    /**
     * Manually reset the circuit breaker for a specific key.
     * Useful when the user explicitly triggers a retry.
     */
    fun reset(key: String) {
        states.remove(key)
    }

    /**
     * Reset all circuit breaker states.
     */
    fun resetAll() {
        states.clear()
    }
}
