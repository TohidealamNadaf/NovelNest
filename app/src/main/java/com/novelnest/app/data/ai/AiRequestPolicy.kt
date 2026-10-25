package com.novelnest.app.data.ai

import kotlinx.coroutines.delay

/** Bounded process-local admission. Provider feedback is authoritative for its quota dimensions. */
class AiRequestPolicy(private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 }) {
    internal data class Scope(val provider: AiProviderId, val model: String?)
    class Reservation internal constructor(internal val scope: Scope, internal val id: Long, internal val credentialEpoch: Long,
        internal val time: Long, internal var tokens: Int)
    private data class Cooldown(val until: Long, val error: AiException)
    private data class Observed(val quota: AiQuotaSnapshot, val through: Long, val tokensUntil: Long, val requestsUntil: Long,
        var pendingTokens: Int = 0, var pendingRequests: Int = 0)
    private val starts = mutableMapOf<Scope, ArrayDeque<Reservation>>()
    private val cooldowns = mutableMapOf<Scope, Cooldown>()
    private val observed = mutableMapOf<Scope, Observed>()
    private var sequence = 0L
    private val credentialHashes = mutableMapOf<AiProviderId, String>()
    private val credentialEpochs = mutableMapOf<AiProviderId, Long>()

    /** One fingerprint/provider, never the key. Replacing a credential must not inherit another
     * account's cached quota. The provider still enforces organization quotas across its keys. */
    @Synchronized fun bindCredential(provider: AiProviderId, fingerprint: String) {
        require(fingerprint.matches(Regex("[0-9a-f]{64}"))) { "Expected a credential fingerprint." }
        val previous = credentialHashes.put(provider, fingerprint)
        if (previous == null || previous == fingerprint) return
        credentialEpochs[provider] = (credentialEpochs[provider] ?: 0L) + 1L
        starts.keys.removeAll { it.provider == provider }
        cooldowns.keys.removeAll { it.provider == provider }
        observed.keys.removeAll { it.provider == provider }
    }

    private fun scope(provider: AiProviderId, model: String?) = Scope(provider,
        model?.takeIf { provider == AiProviderId.GROQ && AiProviderCatalog.models.any { it.route == AiRoute(provider, model) } })

    @Synchronized fun checkCooldown(provider: AiProviderId, model: String? = null) {
        val now = nowMs()
        cooldowns.entries.removeAll { it.value.until <= now }
        for (candidate in listOf(Scope(provider, null), scope(provider, model)).distinct()) {
            cooldowns[candidate]?.let {
                throw AiException(it.error.code, it.error.message.orEmpty(), (it.until - now + 999) / 1000,
                    (it.error.rateLimit ?: AiRateLimitInfo(AiLimitKind.UNKNOWN))
                        .copy(modelScoped = candidate.model != null, localRejection = true))
            }
        }
    }

    suspend fun admit(provider: AiProviderId, prompt: AiPrompt, outputTokens: Int,
        maxWaitMs: Long = 60_000, model: String? = null, waiting: (Long) -> Unit): Reservation {
        val key = scope(provider, model)
        val tokens = estimatedTokens(prompt, outputTokens)
        val deadline = nowMs() + maxWaitMs.coerceAtLeast(0)
        if (provider == AiProviderId.GROQ && tokens > GROQ_TOKEN_BUDGET)
            throw AiException(AiErrorCode.TOO_LARGE, "This request exceeds the configured free-plan token budget. Select a larger-capacity route.")
        while (true) {
            var admitted: Reservation? = null
            var reason = "Local request budget is temporarily reserved."
            var kind = AiLimitKind.LOCAL
            val wait = synchronized(this) {
                val now = nowMs()
                checkCooldown(provider, model)
                val recent = starts.getOrPut(key) { ArrayDeque() }
                while (recent.firstOrNull()?.let { now - it.time >= 60_000 } == true) recent.removeFirst()
                var waitMs = if (recent.size >= 6) (60_000 - (now - recent.first().time)).coerceAtLeast(1) else 0L
                val feedback = observed[key]
                if (provider == AiProviderId.GROQ) {
                    val quota = feedback?.quota
                    val fresh = feedback != null && feedback.tokensUntil > now && quota?.tokensRemaining != null
                    val limit = if (fresh) minOf(GROQ_TOKEN_BUDGET, quota?.tokenLimit?.takeIf { it > 0 } ?: GROQ_TOKEN_BUDGET) else GROQ_TOKEN_BUDGET
                    if (tokens > limit) throw AiException(AiErrorCode.TOO_LARGE, "The estimated request exceeds this model's reported per-minute allowance. Select another route.")
                    if (fresh) {
                        if (tokens + requireNotNull(feedback).pendingTokens > requireNotNull(quota?.tokensRemaining)) {
                            waitMs = maxOf(waitMs, requireNotNull(feedback).tokensUntil - now)
                            reason = "Groq's reported per-minute token allowance is not yet sufficient."
                            kind = AiLimitKind.MINUTE_TOKENS
                        }
                    } else if (tokens + recent.sumOf { it.tokens } > limit) {
                        var remaining = recent.sumOf { it.tokens }
                        for (start in recent) {
                            remaining -= start.tokens
                            if (remaining + tokens <= limit) {
                                waitMs = maxOf(waitMs, 60_000 - (now - start.time)); break
                            }
                        }
                    }
                    if (feedback != null && feedback.requestsUntil > now && quota?.requestsRemaining != null &&
                        feedback.pendingRequests >= quota.requestsRemaining) {
                        waitMs = maxOf(waitMs, feedback.requestsUntil - now)
                        reason = "Groq's reported daily request allowance is exhausted. A one-minute wait does not reset a daily quota."
                        kind = AiLimitKind.DAILY_REQUESTS
                    }
                }
                if (waitMs == 0L) admitted = Reservation(key, ++sequence, credentialEpochs[provider] ?: 0L, now, tokens).also {
                    recent.addLast(it)
                    feedback?.let { snapshot -> snapshot.pendingTokens += tokens; snapshot.pendingRequests++ }
                }
                waitMs
            }
            admitted?.let { return it }
            if (wait > deadline - nowMs()) throw AiException(AiErrorCode.RATE_LIMIT,
                "$reason Retry delay: about ${(wait + 999) / 1000} seconds. Try another enabled route or retry later.",
                (wait + 999) / 1000, AiRateLimitInfo(kind, key.model != null, localRejection = true))
            waiting(wait)
            delay(wait)
        }
    }

    /** Unknown/cancelled consumption retains its reservation. Total usage includes reasoning. */
    @Synchronized fun completed(reservation: Reservation, totalTokens: Int) {
        if (reservation.credentialEpoch != (credentialEpochs[reservation.scope.provider] ?: 0L)) return
        if (totalTokens in 1..2_000_000) {
            observed[reservation.scope]?.takeIf { reservation.id > it.through }?.let {
                it.pendingTokens += totalTokens - reservation.tokens
            }
            reservation.tokens = totalTokens
        }
    }

    @Synchronized fun observed(reservation: Reservation, quota: AiQuotaSnapshot) {
        if (reservation.scope.provider != AiProviderId.GROQ) return
        if (reservation.credentialEpoch != (credentialEpochs[reservation.scope.provider] ?: 0L)) return
        if ((observed[reservation.scope]?.through ?: 0L) > reservation.id) return
        val now = nowMs()
        observed[reservation.scope] = Observed(quota, reservation.id,
            now + (quota.tokenResetMs ?: 60_000), now + (quota.requestResetMs ?: 60_000))
    }

    /** Only a real provider call may create/extend a remote cooldown. Local rejections never do. */
    @Synchronized fun failed(provider: AiProviderId, error: AiException, model: String? = null) {
        if (error.rateLimit?.localRejection == true) return
        val duration = when (error.code) {
            AiErrorCode.RATE_LIMIT -> error.retryAfterSeconds ?: 60L
            AiErrorCode.UNAVAILABLE -> error.retryAfterSeconds ?: 30L
            else -> return
        }
        val key = scope(provider, model.takeIf { error.rateLimit?.modelScoped == true })
        // A failed call's explicit retry hint supersedes its depleted quota headers. Otherwise an
        // old snapshot could continue blocking after the provider's own cooldown has expired.
        if (error.code == AiErrorCode.RATE_LIMIT) {
            if (key.model == null) observed.keys.removeAll { it.provider == provider } else observed.remove(key)
        }
        val until = nowMs() + duration.coerceIn(1, 7 * 86_400L) * 1000
        if (until > (cooldowns[key]?.until ?: 0)) cooldowns[key] = Cooldown(until, error)
    }

    companion object {
        internal const val GROQ_TOKEN_BUDGET = 8_000
        /** Padded prose estimate; not an exact tokenizer, account counter or billing guarantee. */
        internal fun estimatedTokens(prompt: AiPrompt, outputTokens: Int): Int {
            fun estimate(text: String): Int {
                val bytes = text.toByteArray(Charsets.UTF_8)
                val ascii = bytes.count { it >= 0 }
                return (ascii + 2) / 3 + (bytes.size - ascii)
            }
            return estimate(prompt.system) + estimate(prompt.data) + estimate(prompt.schema.toString()) + outputTokens + 512
        }
    }
}
