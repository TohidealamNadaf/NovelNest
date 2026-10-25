package com.novelnest.app.data.ai

import kotlinx.coroutines.delay

/** Process-local admission only; the provider is authoritative for account/project quotas. */
class AiRequestPolicy(private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private data class Start(val time: Long, val tokens: Int)
    private val starts = AiProviderId.entries.associateWith { ArrayDeque<Start>() }
    private val cooldowns = mutableMapOf<AiProviderId, Long>()

    suspend fun admit(provider: AiProviderId, prompt: AiPrompt, outputTokens: Int, waiting: () -> Unit) {
        // UTF-8 byte count is a conservative token estimate for these text tokenizers, plus envelope reserve.
        val tokens = prompt.system.toByteArray().size + prompt.data.toByteArray().size +
            prompt.schema.toString().toByteArray().size + outputTokens + 512
        if (provider == AiProviderId.GROQ && tokens > 8_000)
            throw AiException(AiErrorCode.TOO_LARGE, "This request exceeds the configured free-plan token budget. Select a larger-capacity route.")
        while (true) {
            val wait = synchronized(this) {
                val now = nowMs()
                val until = cooldowns[provider] ?: 0
                if (until > now) throw AiException(AiErrorCode.RATE_LIMIT, "This provider is cooling down. Use another route or retry later.", (until - now + 999) / 1000)
                val recent = starts.getValue(provider)
                while (recent.firstOrNull()?.let { now - it.time >= 60_000 } == true) recent.removeFirst()
                if (recent.size >= 6 || (provider == AiProviderId.GROQ && recent.sumOf { it.tokens } + tokens > 8_000)) {
                    (60_000 - (now - recent.first().time)).coerceAtLeast(1)
                } else { recent.addLast(Start(now, tokens)); 0L }
            }
            if (wait == 0L) return
            waiting()
            delay(wait)
        }
    }

    @Synchronized fun failed(provider: AiProviderId, error: AiException) {
        val duration = when (error.code) {
            AiErrorCode.RATE_LIMIT -> error.retryAfterSeconds ?: 60L
            AiErrorCode.UNAVAILABLE -> error.retryAfterSeconds ?: 30L
            else -> return
        }
        cooldowns[provider] = nowMs() + duration.coerceIn(1, 86_400) * 1000
    }
}
