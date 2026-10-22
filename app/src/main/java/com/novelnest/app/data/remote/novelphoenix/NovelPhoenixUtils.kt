package com.novelnest.app.data.remote.novelphoenix

import java.util.regex.Pattern

/**
 * Utility functions for resolving URLs, parsing approximate dates, and parsing count strings.
 */
object NovelPhoenixUtils {

    private const val BASE_URL = NovelPhoenixSelectors.BASE_URL

    /**
     * Resolves an image or page URL against the base URL.
     * Handles:
     * - Absolute URLs: "https://novelphoenix.com/path" -> returned as is
     * - Protocol-relative: "//server-1/..." -> "https://server-1/..."
     * - Leading slash: "/server-1/pic.jpg" -> "https://novelphoenix.com/server-1/pic.jpg"
     * - No leading slash: "server-1/pic.jpg" -> "https://novelphoenix.com/server-1/pic.jpg"
     * - Data URIs / blank: returns fallbackUrl
     */
    fun resolveUrl(url: String?, fallbackUrl: String = ""): String {
        if (url.isNullOrBlank()) return fallbackUrl
        val trimmed = url.trim()
        if (trimmed.startsWith("data:", ignoreCase = true) || trimmed.startsWith("javascript:", ignoreCase = true)) {
            return fallbackUrl
        }
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> "$BASE_URL$trimmed"
            else -> "$BASE_URL/$trimmed"
        }
    }

    /**
     * Parses numeric counts with optional K/M/B suffixes:
     * e.g., "2.5M" -> 2_500_000L, "2.8K" -> 2_800L, "2451" -> 2451L.
     * Returns null if unparseable.
     */
    fun parseMetricCount(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().uppercase()
            .replace(",", "")
            .replace("VIEWS", "")
            .replace("CHAPTERS", "")
            .replace("BOOKMARKED", "")
            .trim()

        return try {
            when {
                cleaned.endsWith("M") -> {
                    val num = cleaned.removeSuffix("M").trim().toDouble()
                    (num * 1_000_000).toLong()
                }
                cleaned.endsWith("K") -> {
                    val num = cleaned.removeSuffix("K").trim().toDouble()
                    (num * 1_000).toLong()
                }
                cleaned.endsWith("B") -> {
                    val num = cleaned.removeSuffix("B").trim().toDouble()
                    (num * 1_000_000_000).toLong()
                }
                else -> {
                    val digits = cleaned.replace(Regex("[^0-9.]"), "")
                    if (digits.isBlank()) null else digits.toDouble().toLong()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Converts relative date strings ("16 hours ago", "2 days ago", "Updated 16 hours ago")
     * into an approximate Unix epoch timestamp (ms).
     *
     * Note: As documented in SPEC.md, this is an approximation for display and freshness hints only,
     * and should NOT be used for strict chronological ordering.
     */
    fun parseRelativeDate(raw: String?, nowMillis: Long = System.currentTimeMillis()): Long {
        if (raw.isNullOrBlank()) return nowMillis
        val cleaned = raw.lowercase()
            .replace("updated", "")
            .replace("ago", "")
            .trim()

        val pattern = Pattern.compile("(\\d+)\\s*(second|sec|minute|min|hour|hr|day|week|month|year)s?")
        val matcher = pattern.matcher(cleaned)

        if (matcher.find()) {
            val amount = matcher.group(1)?.toLongOrNull() ?: 0L
            val unit = matcher.group(2) ?: ""

            val offsetMs = when (unit) {
                "second", "sec" -> amount * 1000L
                "minute", "min" -> amount * 60 * 1000L
                "hour", "hr" -> amount * 60 * 60 * 1000L
                "day" -> amount * 24 * 60 * 60 * 1000L
                "week" -> amount * 7 * 24 * 60 * 60 * 1000L
                "month" -> amount * 30 * 24 * 60 * 60 * 1000L
                "year" -> amount * 365 * 24 * 60 * 60 * 1000L
                else -> 0L
            }
            return nowMillis - offsetMs
        }

        return nowMillis
    }

    /**
     * Checks if response body or HTML contains Cloudflare challenge signatures.
     */
    fun isCloudflareChallenge(html: String): Boolean {
        return html.contains("<title>Attention Required! | Cloudflare</title>", ignoreCase = true) ||
                html.contains("cf-mitigated", ignoreCase = true) ||
                (html.contains("cf-browser-verification", ignoreCase = true) && html.contains("cloudflare", ignoreCase = true))
    }
}
