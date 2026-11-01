package com.novelnest.app.data.remote.asura

internal data class AsuraCached<T>(val value: T, val fetchedAt: Long)

object AsuraCachePolicy {
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    const val METADATA_TTL_MS = 5 * 60 * 1_000L
    const val PAGE_URL_TTL_MS = 5 * 60 * 1_000L
    const val MAX_RESPONSE_BYTES = 8L * 1024 * 1024
}
