package com.novelnest.app.data.ai

import java.security.MessageDigest

enum class AiAction { SUMMARY, REWRITE, EXTRACT }
enum class AiProviderId { OPEN_ROUTER, GEMINI, GROQ }
enum class AiCostPolicy { EXPLICIT_FREE_ONLY, FREE_ACCOUNT_PROVIDERS }
enum class AiPhase { IDLE, CHECKING_CACHE, PREPARING, WAITING_FOR_SLOT, CONNECTING, GENERATING, SWITCHING_PROVIDER, VALIDATING, READY, FAILED, CANCELLED }
enum class AiErrorCode {
    NOT_CONFIGURED, CONSENT_REQUIRED, CREDENTIALS, BILLING_POLICY, MODEL_UNAVAILABLE,
    RATE_LIMIT, UNAVAILABLE, NETWORK, TIMEOUT, INVALID_OUTPUT, REFUSED, TOO_LARGE,
    BUSY, STORAGE, CANCELLED
}

class AiException(val code: AiErrorCode, message: String, val retryAfterSeconds: Long? = null) : Exception(message)

data class AiRoute(val provider: AiProviderId, val model: String)
data class AiSettings(
    val summaryRoutes: List<AiRoute> = emptyList(),
    val rewriteRoutes: List<AiRoute> = emptyList(),
    val consentedProviders: Set<AiProviderId> = emptySet(),
    val costPolicy: AiCostPolicy = AiCostPolicy.EXPLICIT_FREE_ONLY,
    val extractRoutes: List<AiRoute> = emptyList()
) {
    fun routes(action: AiAction): List<AiRoute> = when (action) {
        AiAction.SUMMARY -> summaryRoutes
        AiAction.REWRITE -> rewriteRoutes
        AiAction.EXTRACT -> extractRoutes
    }
}

/** Contains source text only. Never build this from an AI display version. */
data class AiChapterInput(val source: String, val novelId: String, val chapterId: String,
    val title: String, val paragraphs: List<String>, val extractionTarget: String? = null) {
    fun fingerprint(): String = aiHash(buildString {
        append(title.length).append(':').append(title)
        paragraphs.forEach { append(it.length).append(':').append(it) }
    })
    fun cacheKey(action: AiAction): String {
        val fields = listOf(source, novelId, chapterId, fingerprint(), action.name, AiPromptFactory.VERSION)
        return aiHash((if (action == AiAction.EXTRACT) fields + extractionTarget.orEmpty().trim() else fields).joinToString("\u0000"))
    }
}

data class AiEvent(val text: String, val paragraphIds: List<Int>)
data class AiTerm(val name: String, val kind: String, val meaning: String, val paragraphId: Int, val evidence: String)
data class AiSummary(val text: String, val events: List<AiEvent>, val terms: List<AiTerm>)
data class AiResult(
    val action: AiAction, val sourceFingerprint: String, val route: AiRoute,
    val summary: AiSummary? = null, val rewrittenParagraphs: List<String> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis(), val extractedTerms: List<AiTerm> = emptyList()
)
data class AiProgress(val phase: AiPhase, val provider: AiProviderId? = null,
    val chunk: Int = 0, val totalChunks: Int = 0, val message: String = "")
data class AiFailure(val code: AiErrorCode, val message: String, val retryAfterSeconds: Long? = null)
data class AiProviderStatus(val provider: AiProviderId, val configured: Boolean, val unreadable: Boolean = false)
data class AiModelAvailability(val route: AiRoute, val available: Boolean)

internal fun aiHash(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
