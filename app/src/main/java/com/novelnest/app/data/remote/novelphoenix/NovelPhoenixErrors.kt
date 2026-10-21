package com.novelnest.app.data.remote.novelphoenix

/**
 * Typed errors for NovelPhoenix extraction and networking.
 */
sealed class PhoenixException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * Thrown when NovelPhoenix returns 403, 429, or a Cloudflare bot/challenge interstitial.
     */
    class SourceBlockedError(
        val statusCode: Int? = null,
        val isCloudflareChallenge: Boolean = false,
        message: String = "NovelPhoenix is temporarily blocked by Cloudflare or rate limiting. Please try again later."
    ) : PhoenixException(message)

    /**
     * Thrown when required DOM or JSON elements cannot be parsed or format is invalid.
     */
    class ParseError(
        val fieldName: String,
        message: String,
        cause: Throwable? = null
    ) : PhoenixException("Failed to parse field '$fieldName': $message", cause)

    /**
     * Thrown when a novel or chapter resource does not exist (HTTP 404).
     */
    class ResourceNotFoundError(
        val resourceUrl: String,
        message: String = "Requested resource not found: $resourceUrl"
    ) : PhoenixException(message)
}
