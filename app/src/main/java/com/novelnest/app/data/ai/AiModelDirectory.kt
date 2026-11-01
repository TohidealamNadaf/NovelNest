package com.novelnest.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** At most one metadata snapshot per provider, scoped to a key fingerprint and five-minute TTL. */
class AiModelDirectory(providers: List<AiProvider>, private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private data class Entry(val keyHash: String, val at: Long, val models: Set<String>)
    private val providers = providers.associateBy { it.id }
    private val locks = AiProviderId.entries.associateWith { Mutex() }
    private val entries = mutableMapOf<AiProviderId, Entry>()

    suspend fun available(provider: AiProviderId, credential: String, force: Boolean = false): Set<String> =
        withContext(Dispatchers.Default) { locks.getValue(provider).withLock {
            val hash = aiHash(credential)
            val entry = synchronized(entries) { entries[provider] }
            if (!force && entry != null && entry.keyHash == hash && nowMs() - entry.at < 300_000) return@withLock entry.models
            val adapter = providers[provider] ?: throw AiException(AiErrorCode.MODEL_UNAVAILABLE, "Unsupported AI provider.")
            val models = try { withTimeout(45_000) { adapter.available(credential, force) } }
            catch (timeout: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                throw AiException(AiErrorCode.TIMEOUT, "Model availability checking timed out. Retry when ready.")
            }
            synchronized(entries) { entries[provider] = Entry(hash, nowMs(), models) }
            models
        } }

    fun invalidate(provider: AiProviderId) { synchronized(entries) { entries.remove(provider) } }
}
