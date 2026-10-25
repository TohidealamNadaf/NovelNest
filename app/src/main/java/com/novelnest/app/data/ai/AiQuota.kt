package com.novelnest.app.data.ai

import okhttp3.Headers
import kotlin.math.ceil

enum class AiLimitKind { UNKNOWN, LOCAL, MINUTE_TOKENS, MINUTE_REQUESTS, DAILY_TOKENS, DAILY_REQUESTS }
data class AiRateLimitInfo(val kind: AiLimitKind, val modelScoped: Boolean = false, val localRejection: Boolean = false)

/** Groq request counters describe DAILY requests, not RPM. No body, keys or organization IDs. */
data class AiQuotaSnapshot(
    val tokenLimit: Int? = null, val tokensRemaining: Int? = null, val tokenResetMs: Long? = null,
    val requestsRemaining: Int? = null, val requestResetMs: Long? = null
) {
    companion object {
        fun groq(headers: Headers): AiQuotaSnapshot? {
            fun count(name: String) = headers[name]?.toIntOrNull()?.takeIf { it in 0..100_000_000 }
            val result = AiQuotaSnapshot(count("x-ratelimit-limit-tokens"), count("x-ratelimit-remaining-tokens"),
                AiQuotaParsing.durationMs(headers["x-ratelimit-reset-tokens"]),
                count("x-ratelimit-remaining-requests"), AiQuotaParsing.durationMs(headers["x-ratelimit-reset-requests"]))
            return result.takeUnless { it == AiQuotaSnapshot() }
        }
    }
}

internal object AiQuotaParsing {
    private val duration = Regex("^(?:(\\d+(?:\\.\\d+)?)h)?(?:(\\d+(?:\\.\\d+)?)m)?(?:(\\d+(?:\\.\\d+)?)s)?$")
    fun durationMs(value: String?): Long? {
        if (value.isNullOrBlank() || value.length > 80) return null
        val match = duration.matchEntire(value.trim()) ?: return null
        val parts = match.groupValues.drop(1).map { it.toDoubleOrNull() ?: 0.0 }
        val ms = (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
        return ceil(ms).takeIf { it.isFinite() && it > 0 && it <= 7 * 86_400_000L }?.toLong()
    }

    fun groqKind(message: String): AiLimitKind = when {
        message.contains("tokens per day", true) || message.contains("(TPD)", true) -> AiLimitKind.DAILY_TOKENS
        message.contains("requests per day", true) || message.contains("(RPD)", true) -> AiLimitKind.DAILY_REQUESTS
        message.contains("tokens per minute", true) || message.contains("(TPM)", true) -> AiLimitKind.MINUTE_TOKENS
        message.contains("requests per minute", true) || message.contains("(RPM)", true) -> AiLimitKind.MINUTE_REQUESTS
        else -> AiLimitKind.UNKNOWN
    }

    fun groqRetryMs(message: String): Long? =
        Regex("try again in\\s+((?:\\d+(?:\\.\\d+)?[hms])+)", RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.get(1)?.let(::durationMs)

    fun exceedsMinuteRequestBudget(message: String): Boolean {
        if (groqKind(message) != AiLimitKind.MINUTE_TOKENS) return false
        fun counter(label: String) = Regex("\\b$label\\s*:?\\s*([0-9][0-9,]*)", RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
        val limit = counter("Limit") ?: return false
        val requested = counter("Requested") ?: return false
        return limit > 0 && requested > limit
    }
}
