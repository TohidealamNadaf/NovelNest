package com.novelnest.app.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** No unowned generation scope. The caller's cancellation owns every chunk and fallback. */
class ChapterAiRepository(
    private val configuration: AiConfiguration,
    private val cache: AiCache,
    providers: List<AiProvider>,
    private val policy: AiRequestPolicy = AiRequestPolicy(),
    private val directory: AiModelDirectory = AiModelDirectory(providers)
) {
    private val providers = providers.associateBy { it.id }
    private val gate = Mutex()

    suspend fun generate(input: AiChapterInput, action: AiAction,
        progress: (AiProgress) -> Unit = {}): AiResult = withContext(Dispatchers.Default) {
        if (!gate.tryLock()) throw AiException(AiErrorCode.BUSY, "Another chapter AI request is already running.")
        try {
            withTimeout(300_000L) {
                validateInput(input)
                if (action == AiAction.EXTRACT && (input.extractionTarget.isNullOrBlank() || input.extractionTarget.length > 500))
                    throw AiException(AiErrorCode.INVALID_OUTPUT, "Enter what to extract (up to 500 characters).")
                val key = input.cacheKey(action)
                val fingerprint = input.fingerprint()
                val epoch = cache.generation
                progress(AiProgress(AiPhase.CHECKING_CACHE))
                cache.read(key, input, action)?.let { return@withTimeout it }
                val settings = configuration.settings()
                AiProviderCatalog.validate(settings)
                val routes = settings.routes(action)
                if (routes.isEmpty()) throw AiException(AiErrorCode.NOT_CONFIGURED, "Set up a provider and model in Settings > AI Reading Tools.")
                val paragraphs = input.paragraphs.mapIndexed { index, text -> AiParagraph(index, text) }
                val blockedProviders = mutableSetOf<AiProviderId>()
                var calls = 0
                var lastError = AiException(AiErrorCode.NOT_CONFIGURED, "No enabled provider is available.")
                for ((attempt, route) in routes.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    if (route.provider in blockedProviders) continue
                    try {
                        val spec = AiProviderCatalog.spec(route)
                        if (route.provider !in settings.consentedProviders)
                            throw AiException(AiErrorCode.CONSENT_REQUIRED, "Allow chapter sharing with this provider in AI Reading Tools first.")
                        if (settings.costPolicy == AiCostPolicy.EXPLICIT_FREE_ONLY && !spec.explicitlyFree)
                            throw AiException(AiErrorCode.BILLING_POLICY, "This route requires a free-account billing acknowledgement. Explicit free routes are enabled by default.")
                        progress(AiProgress(if (attempt == 0) AiPhase.PREPARING else AiPhase.SWITCHING_PROVIDER,
                            route.provider, message = if (attempt == 0) "Preparing chapter text" else "Trying the next enabled route"))
                        val chunks = when (action) {
                            AiAction.SUMMARY -> AiSummaryPlanner.chunks(input.title, paragraphs, spec)
                            AiAction.EXTRACT -> AiExtractionPlanner.chunks(input.title, paragraphs, requireNotNull(input.extractionTarget), spec)
                            AiAction.REWRITE -> chunk(paragraphs, spec.inputBytes)
                        }
                        val requiredCalls = chunks.size + if (action == AiAction.SUMMARY && chunks.size > 1) 1 else 0
                        if (requiredCalls > 12 - calls) throw AiException(AiErrorCode.TOO_LARGE,
                            "This chapter exceeds the remaining AI request budget. Select a route that can process it with fewer requests.")
                        val credential = configuration.credential(route.provider)
                        if (credential.isBlank()) throw AiException(AiErrorCode.CREDENTIALS, "Add an API key for this provider in AI Reading Tools.")
                        policy.bindCredential(route.provider, aiHash(credential))
                        policy.checkCooldown(route.provider, route.model)
                        val provider = providers[route.provider] ?: throw AiException(AiErrorCode.MODEL_UNAVAILABLE, "This provider is not supported.")
                        progress(AiProgress(AiPhase.CONNECTING, route.provider, message = "Checking model availability"))
                        if (route.model !in providerCall(route.provider) { directory.available(route.provider, credential) })
                            throw AiException(AiErrorCode.MODEL_UNAVAILABLE, "This model is unavailable or no longer eligible for free requests.")
                        suspend fun request(prompt: AiPrompt, refs: List<AiParagraph>, part: Int, total: Int): AiResult {
                            currentCoroutineContext().ensureActive()
                            val output = if (action == AiAction.SUMMARY) minOf(AiSummaryPlanner.OUTPUT_TOKENS, spec.outputTokens) else spec.outputTokens
                            val reservation = policy.admit(route.provider, prompt, output,
                                maxWaitMs = if (action == AiAction.REWRITE) 60_000 else 10_000, model = route.model) { waitMs ->
                                progress(AiProgress(AiPhase.WAITING_FOR_SLOT, route.provider, part, total,
                                    "Waiting for local request allowance (about ${(waitMs + 999) / 1000} seconds)"))
                            }
                            currentCoroutineContext().ensureActive()
                            calls++
                            progress(AiProgress(AiPhase.CONNECTING, route.provider, part, total))
                            val raw = providerCall(route.provider, route.model) {
                                provider.generateWithUsage(route, credential, prompt, output,
                                    usage = { policy.completed(reservation, it) }, quota = { policy.observed(reservation, it) }) {
                                    progress(AiProgress(AiPhase.GENERATING, route.provider, part, total))
                                }
                            }
                            currentCoroutineContext().ensureActive()
                            progress(AiProgress(AiPhase.VALIDATING, route.provider, part, total))
                            return AiResponseValidator.parse(raw, action, fingerprint, route, refs).also {
                                if (action == AiAction.EXTRACT && it.extractedTerms.size > 40)
                                    throw AiException(AiErrorCode.INVALID_OUTPUT, "The model exceeded the per-segment term limit.")
                            }
                        }
                        val parts = chunks.mapIndexed { index, group ->
                            request(AiPromptFactory.create(action, input.title, group, input.extractionTarget), group, index + 1, requiredCalls)
                        }
                        val result = when {
                            action == AiAction.REWRITE -> AiResult(action, fingerprint, route,
                                rewrittenParagraphs = parts.flatMap { it.rewrittenParagraphs })
                            action == AiAction.EXTRACT -> AiResult(action, fingerprint, route,
                                extractedTerms = parts.flatMap { it.extractedTerms }.distinctBy { listOf(it.name, it.paragraphId, it.meaning) })
                            parts.size == 1 -> parts.single()
                            else -> {
                                val prompt = AiPromptFactory.merge(input.title, parts.map { requireNotNull(it.summary) })
                                if (prompt.data.toByteArray(Charsets.UTF_8).size > spec.summaryInputBytes)
                                    throw AiException(AiErrorCode.TOO_LARGE, "The combined summary exceeds this model's supported merge budget.")
                                request(prompt, paragraphs, requiredCalls, requiredCalls)
                            }
                        }
                        // Validate the assembled result as a whole before publishing or caching anything.
                        val complete = AiResponseValidator.parse(AiResponseValidator.outputJson(result).toString(), action, fingerprint, route, paragraphs)
                        currentCoroutineContext().ensureActive()
                        cache.write(key, complete, epoch)
                        return@withTimeout complete
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: AiException) {
                        lastError = if (error.code == AiErrorCode.INVALID_OUTPUT)
                            AiException(error.code, "${AiProviderCatalog.spec(route).label}: ${error.message.orEmpty()}",
                                error.retryAfterSeconds, error.rateLimit)
                        else error
                        if (error.code == AiErrorCode.MODEL_UNAVAILABLE) directory.invalidate(route.provider)
                        if (error.code in setOf(AiErrorCode.NETWORK, AiErrorCode.REFUSED, AiErrorCode.STORAGE)) throw error
                        if (error.code in setOf(AiErrorCode.CREDENTIALS, AiErrorCode.BILLING_POLICY, AiErrorCode.CONSENT_REQUIRED) ||
                            (error.code in setOf(AiErrorCode.RATE_LIMIT, AiErrorCode.UNAVAILABLE) && error.rateLimit?.modelScoped != true))
                            blockedProviders.add(route.provider)
                    } catch (_: Exception) {
                        lastError = AiException(AiErrorCode.INVALID_OUTPUT, "The provider returned an unsupported chapter response. The original is unchanged.")
                    }
                }
                throw lastError
            }
        } catch (timeout: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw AiException(AiErrorCode.TIMEOUT, "The chapter AI request exceeded five minutes. The original is unchanged. Retry when ready.")
        } finally { gate.unlock() }
    }

    private suspend fun <T> providerCall(provider: AiProviderId, model: String? = null, block: suspend () -> T): T =
        try { block() } catch (error: AiException) {
            policy.failed(provider, error, model)
            // Metadata belongs to the provider, even if a response happens to contain model quota headers.
            if (model == null && error.rateLimit?.modelScoped == true)
                throw AiException(error.code, error.message.orEmpty(), error.retryAfterSeconds, error.rateLimit.copy(modelScoped = false))
            throw error
        }

    companion object {
        internal fun validateInput(input: AiChapterInput) {
            if (input.paragraphs.isEmpty() || input.paragraphs.none { it.isNotBlank() })
                throw AiException(AiErrorCode.INVALID_OUTPUT, "Load the original chapter before using AI Reading Tools.")
            if (input.paragraphs.size > 2_000 || input.title.length > 1_000 ||
                input.paragraphs.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() } > 96_000)
                throw AiException(AiErrorCode.TOO_LARGE, "This chapter exceeds the supported AI size limit. The original is unchanged.")
        }
        internal fun chunk(paragraphs: List<AiParagraph>, limit: Int): List<List<AiParagraph>> {
            val chunks = mutableListOf<List<AiParagraph>>()
            var current = mutableListOf<AiParagraph>(); var bytes = 0
            for (paragraph in paragraphs) {
                val size = paragraph.text.toByteArray(Charsets.UTF_8).size + 40
                if (size > limit) throw AiException(AiErrorCode.TOO_LARGE, "A paragraph is too long for this model. Try a larger-capacity model.")
                if (bytes + size > limit && current.isNotEmpty()) { chunks.add(current); current = mutableListOf(); bytes = 0 }
                current.add(paragraph); bytes += size
            }
            if (current.isNotEmpty()) chunks.add(current)
            if (chunks.size > 8) throw AiException(AiErrorCode.TOO_LARGE, "This chapter needs more than eight segments with this model. Select a larger-capacity model.")
            return chunks
        }
    }
}
