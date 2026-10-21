package com.novelnest.app.data.remote.network

import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight Circuit Breaker to prevent hammering failing or Cloudflare-blocked endpoints.
 * When consecutive failures exceed [failureThreshold], trips OPEN for [resetTimeoutMillis].
 */
class CircuitBreaker(
    private val failureThreshold: Int = 3,
    private val resetTimeoutMillis: Long = 180_000L // 3 minutes
) {
    private data class State(
        var failureCount: Int = 0,
        var lastFailureTimestamp: Long = 0L,
        var isOpen: Boolean = false
    )

    private val states = ConcurrentHashMap<String, State>()

    fun canExecute(key: String): Boolean {
        val state = states[key] ?: return true
        if (!state.isOpen) return true

        val now = System.currentTimeMillis()
        if (now - state.lastFailureTimestamp > resetTimeoutMillis) {
            // Half-open: allow a trial attempt
            state.isOpen = false
            state.failureCount = 0
            return true
        }
        return false
    }

    fun recordSuccess(key: String) {
        states[key]?.let {
            it.failureCount = 0
            it.isOpen = false
        }
    }

    fun recordFailure(key: String) {
        val state = states.computeIfAbsent(key) { State() }
        state.failureCount++
        state.lastFailureTimestamp = System.currentTimeMillis()
        if (state.failureCount >= failureThreshold) {
            state.isOpen = true
        }
    }
}
