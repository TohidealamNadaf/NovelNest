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
        message.contains("tokens per day", true) || message.contains("token per day", true) ||
            message.contains("tokens/day", true) || message.contains("(TPD)", true) -> AiLimitKind.DAILY_TOKENS
        message.contains("requests per day", true) || message.contains("request per day", true) ||
            message.contains("requests/day", true) || message.contains("(RPD)", true) -> AiLimitKind.DAILY_REQUESTS
        message.contains("tokens per minute", true) || message.contains("token per minute", true) ||
            message.contains("tokens/min", true) || message.contains("(TPM)", true) -> AiLimitKind.MINUTE_TOKENS
        message.contains("requests per minute", true) || message.contains("request per minute", true) ||
            message.contains("requests/min", true) || message.contains("(RPM)", true) -> AiLimitKind.MINUTE_REQUESTS
        else -> AiLimitKind.UNKNOWN
    }

    fun openRouterKind(message: String): AiLimitKind = when {
        message.contains("free-models-per-day", true) || message.contains("per-day", true) ||
            message.contains("per day", true) || message.contains("(RPD)", true) -> AiLimitKind.DAILY_REQUESTS
        message.contains("tokens per day", true) || message.contains("(TPD)", true) -> AiLimitKind.DAILY_TOKENS
        message.contains("free-models-per-min", true) || message.contains("per-min", true) ||
            message.contains("per minute", true) || message.contains("(RPM)", true) -> AiLimitKind.MINUTE_REQUESTS
        message.contains("tokens per minute", true) || message.contains("(TPM)", true) -> AiLimitKind.MINUTE_TOKENS
        else -> AiLimitKind.UNKNOWN
    }

    fun openRouterResetMs(headers: Headers?, error: org.json.JSONObject?, nowEpochMs: Long = System.currentTimeMillis()): Long? {
        val metaHeaders = error?.optJSONObject("metadata")?.optJSONObject("headers")
        val rawReset = headers?.get("X-RateLimit-Reset")
            ?: metaHeaders?.optString("X-RateLimit-Reset")?.ifBlank { null }
            ?: metaHeaders?.optString("x-ratelimit-reset")?.ifBlank { null }
        val resetValue = rawReset?.trim()?.toLongOrNull()
        val fromHeader = when {
            resetValue == null || resetValue <= 0L -> null
            resetValue >= 1_000_000_000_000L -> (resetValue - nowEpochMs).takeIf { it in 1..(7 * 86_400_000L) }
            resetValue >= 1_000_000_000L -> (resetValue * 1000L - nowEpochMs).takeIf { it in 1..(7 * 86_400_000L) }
            resetValue <= 7 * 86_400L -> resetValue * 1000L
            else -> null
        }
        return fromHeader ?: error?.optString("message")?.let(::groqRetryMs)
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
