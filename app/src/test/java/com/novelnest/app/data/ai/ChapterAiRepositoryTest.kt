package com.novelnest.app.data.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

internal val freeRoute = AiProviderCatalog.models.first().route
internal val alternateFreeRoute = AiProviderCatalog.models[1].route
internal val geminiRoute = AiProviderCatalog.models.first { it.route.provider == AiProviderId.GEMINI }.route
internal fun chapter(id: String = "one") = AiChapterInput("NOVEL_PHOENIX", "book", id, "Chapter 1", listOf("Mira found 12 coins.", "She returned home."))
internal fun configured(vararg routes: AiRoute) = AiSettings(routes.toList(), routes.toList(), routes.map { it.provider }.toSet(),
    if (routes.any { it.provider != AiProviderId.OPEN_ROUTER }) AiCostPolicy.FREE_ACCOUNT_PROVIDERS else AiCostPolicy.EXPLICIT_FREE_ONLY)
internal class FakeConfiguration(var value: AiSettings) : AiConfiguration {
    var key = "not-a-real-key-for-offline-tests"
    override suspend fun settings() = value
    override suspend fun credential(provider: AiProviderId) = key
}
internal class FakeCache : AiCache {
    override var generation = 0L
    var result: AiResult? = null
    var writes = 0
    override suspend fun read(key: String, input: AiChapterInput, action: AiAction) = result
    override suspend fun write(key: String, result: AiResult, expectedGeneration: Long) {
        if (expectedGeneration == generation) { this.result = result; writes++ }
    }
}
internal class FakeProvider(override val id: AiProviderId) : AiProvider {
    val calls = AtomicInteger()
    val discoveries = AtomicInteger()
    var models = AiProviderCatalog.models.filter { it.route.provider == id }.map { it.route.model }.toSet()
    var respond: suspend (AiRoute, AiPrompt) -> String = { _, prompt -> validResponse(prompt) }
    override suspend fun available(credential: String, force: Boolean): Set<String> { discoveries.incrementAndGet(); return models }
    override suspend fun generate(route: AiRoute, credential: String, prompt: AiPrompt, outputTokens: Int, onSent: () -> Unit): String {
        calls.incrementAndGet(); onSent(); return respond(route, prompt)
    }
}
internal fun validResponse(prompt: AiPrompt): String {
    val input = JSONObject(prompt.data)
    if (input.has("segments")) return input.getJSONArray("segments").getJSONObject(0).toString()
    val paragraphs = input.getJSONArray("paragraphs")
    if (prompt.schema.getJSONObject("properties").has("paragraphs")) return JSONObject().put("paragraphs", paragraphs).toString()
    val first = paragraphs.getJSONObject(0)
    return JSONObject().put("summary", "The chapter events unfold.").put("terms", JSONArray())
        .put("events", JSONArray().put(JSONObject().put("text", "An event occurs.")
            .put("paragraphIds", JSONArray().put(first.getInt("id"))))).toString()
}
internal suspend fun expectAi(code: AiErrorCode, operation: suspend () -> Unit) {
    try { operation(); fail("Expected $code") }
    catch (error: AiException) { assertEquals(code, error.code) }
}

class ChapterAiRepositoryTest {
    @Test fun cachedResultNeedsNoCredentialsOrNetwork() = runBlocking {
        val provider = FakeProvider(AiProviderId.OPEN_ROUTER)
        val cache = FakeCache()
        val config = FakeConfiguration(configured(freeRoute))
        val repo = ChapterAiRepository(config, cache, listOf(provider))
        val first = repo.generate(chapter(), AiAction.SUMMARY)
        config.key = ""; config.value = AiSettings()
        assertEquals(first, repo.generate(chapter(), AiAction.SUMMARY))
        assertEquals(1, provider.calls.get())
    }
    @Test fun configuredProviderFallbackSucceedsSequentially() = runBlocking {
        val first = FakeProvider(AiProviderId.OPEN_ROUTER).apply { respond = { _, _ -> throw AiException(AiErrorCode.UNAVAILABLE, "Unavailable") } }
        val second = FakeProvider(AiProviderId.GEMINI)
        val result = ChapterAiRepository(FakeConfiguration(configured(freeRoute, geminiRoute)), FakeCache(), listOf(first, second))
            .generate(chapter(), AiAction.SUMMARY)
        assertEquals(geminiRoute, result.route)
        assertEquals(1, first.calls.get()); assertEquals(1, second.calls.get())
    }
    @Test fun refusalDoesNotFallback() = runBlocking { noFallback(AiErrorCode.REFUSED) }
    @Test fun networkFailureDoesNotFallback() = runBlocking { noFallback(AiErrorCode.NETWORK) }
    private suspend fun noFallback(code: AiErrorCode) {
        val first = FakeProvider(AiProviderId.OPEN_ROUTER).apply { respond = { _, _ -> throw AiException(code, "Stopped") } }
        val second = FakeProvider(AiProviderId.GEMINI)
        expectAi(code) { ChapterAiRepository(FakeConfiguration(configured(freeRoute, geminiRoute)), FakeCache(), listOf(first, second))
            .generate(chapter(), AiAction.SUMMARY) }
        assertEquals(0, second.calls.get())
    }
    @Test fun invalidOutputCanUseAnotherFreeModel() = runBlocking {
        val provider = FakeProvider(AiProviderId.OPEN_ROUTER).apply { respond = { route, prompt ->
            if (route == freeRoute) "{\"summary\":\"incomplete\"}" else validResponse(prompt)
        } }
        val result = ChapterAiRepository(FakeConfiguration(configured(freeRoute, alternateFreeRoute)), FakeCache(), listOf(provider))
            .generate(chapter(), AiAction.SUMMARY)
        assertEquals(alternateFreeRoute, result.route); assertEquals(2, provider.calls.get())
        assertEquals(1, provider.discoveries.get())
    }
    @Test fun strictCostPolicyBlocksNativeProvidersBeforeDiscovery() = runBlocking {
        val provider = FakeProvider(AiProviderId.GEMINI)
        val settings = configured(geminiRoute).copy(costPolicy = AiCostPolicy.EXPLICIT_FREE_ONLY)
        expectAi(AiErrorCode.BILLING_POLICY) { ChapterAiRepository(FakeConfiguration(settings), FakeCache(), listOf(provider))
            .generate(chapter(), AiAction.SUMMARY) }
        assertEquals(0, provider.discoveries.get()); assertEquals(0, provider.calls.get())
    }
    @Test fun missingSharingConsentBlocksRequests() = runBlocking {
        val provider = FakeProvider(AiProviderId.OPEN_ROUTER)
        expectAi(AiErrorCode.CONSENT_REQUIRED) { ChapterAiRepository(FakeConfiguration(configured(freeRoute).copy(consentedProviders = emptySet())),
            FakeCache(), listOf(provider)).generate(chapter(), AiAction.SUMMARY) }
        assertEquals(0, provider.calls.get())
    }
    @Test fun removedModelDoesNotGenerate() = runBlocking {
        val provider = FakeProvider(AiProviderId.OPEN_ROUTER).apply { models = emptySet() }
        expectAi(AiErrorCode.MODEL_UNAVAILABLE) { ChapterAiRepository(FakeConfiguration(configured(freeRoute)), FakeCache(), listOf(provider))
            .generate(chapter(), AiAction.SUMMARY) }
        assertEquals(0, provider.calls.get())
    }
    @Test fun duplicateRequestsCannotStartAnotherGeneration() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val provider = FakeProvider(AiProviderId.OPEN_ROUTER).apply { respond = { _, prompt -> entered.complete(Unit); release.await(); validResponse(prompt) } }
        val repo = ChapterAiRepository(FakeConfiguration(configured(freeRoute)), FakeCache(), listOf(provider))
        val active = async { repo.generate(chapter(), AiAction.SUMMARY) }
        entered.await()
        expectAi(AiErrorCode.BUSY) { repo.generate(chapter(), AiAction.SUMMARY) }
        release.complete(Unit); active.await()
        assertEquals(1, provider.calls.get())
    }
    @Test fun cancellationDoesNotWriteOrFallbackAndReleasesAdmission() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val wait = CompletableDeferred<Unit>()
        val provider = FakeProvider(AiProviderId.OPEN_ROUTER).apply { respond = { _, prompt -> entered.complete(Unit); wait.await(); validResponse(prompt) } }
        val cache = FakeCache()
        val repo = ChapterAiRepository(FakeConfiguration(configured(freeRoute, alternateFreeRoute)), cache, listOf(provider))
        val active = async { repo.generate(chapter(), AiAction.REWRITE) }
        entered.await(); active.cancelAndJoin()
        assertEquals(0, cache.writes); assertEquals(1, provider.calls.get())
        provider.respond = { _, prompt -> validResponse(prompt) }
        assertEquals(chapter().paragraphs, repo.generate(chapter(), AiAction.REWRITE).rewrittenParagraphs)
    }
    @Test fun longRewriteIsAssembledOnlyWhenAllChunksSucceed() = runBlocking {
        val input = chapter().copy(paragraphs = List(3) { "Mira walked. ".repeat(700) })
        val provider = FakeProvider(AiProviderId.OPEN_ROUTER)
        val cache = FakeCache()
        val result = ChapterAiRepository(FakeConfiguration(configured(freeRoute)), cache, listOf(provider)).generate(input, AiAction.REWRITE)
        assertEquals(input.paragraphs, result.rewrittenParagraphs)
        assertEquals(3, provider.calls.get()); assertEquals(1, cache.writes)
    }
    @Test fun failedLaterChunkNeverPublishesPartialRewrite() = runBlocking {
        val input = chapter().copy(paragraphs = List(3) { "Mira walked. ".repeat(700) })
        val provider = FakeProvider(AiProviderId.OPEN_ROUTER).apply { respond = { _, prompt ->
            if (calls.get() == 2) throw AiException(AiErrorCode.UNAVAILABLE, "Unavailable") else validResponse(prompt)
        } }
        val cache = FakeCache()
        expectAi(AiErrorCode.UNAVAILABLE) { ChapterAiRepository(FakeConfiguration(configured(freeRoute)), cache, listOf(provider)).generate(input, AiAction.REWRITE) }
        assertEquals(0, cache.writes); assertNull(cache.result)
    }
    @Test fun summaryDoesNotInheritRewriteChunkLimits() = runBlocking {
        val input = chapter().copy(paragraphs = List(3) { "Mira walked. ".repeat(700) })
        val provider = FakeProvider(AiProviderId.OPEN_ROUTER)
        val result = ChapterAiRepository(FakeConfiguration(configured(freeRoute)), FakeCache(), listOf(provider)).generate(input, AiAction.SUMMARY)
        assertNotNull(result.summary); assertEquals(1, provider.calls.get())
    }
    @Test fun veryLongSummaryStillCoversEveryParagraphWithBoundedMerge() = runBlocking {
        val input = chapter().copy(paragraphs = List(3) { "Mira walked. ".repeat(2_400) })
        val seen = mutableListOf<Int>()
        val provider = FakeProvider(AiProviderId.OPEN_ROUTER).apply { respond = { _, prompt ->
            JSONObject(prompt.data).optJSONArray("paragraphs")?.let { values ->
                for (index in 0 until values.length()) seen.add(values.getJSONObject(index).getInt("id"))
            }
            validResponse(prompt)
        } }
        val result = ChapterAiRepository(FakeConfiguration(configured(freeRoute)), FakeCache(), listOf(provider))
            .generate(input, AiAction.SUMMARY)
        assertNotNull(result.summary); assertEquals(listOf(0, 1, 2), seen)
        assertEquals(3, provider.calls.get()) // Two large segments, one merge.
    }
    @Test fun sevenFormerGroqChunksUseOneSummaryRequestIncludingTheLastParagraph() = runBlocking {
        val input = chapter().copy(paragraphs = List(7) { "Mira walked home. ".repeat(95) })
        val refs = input.paragraphs.mapIndexed(::AiParagraph)
        val route = AiProviderCatalog.models.first { it.route.provider == AiProviderId.GROQ }.route
        assertEquals(7, ChapterAiRepository.chunk(refs, AiProviderCatalog.spec(route).inputBytes).size)
        val provider = FakeProvider(AiProviderId.GROQ).apply { respond = { _, prompt ->
            val sent = JSONObject(prompt.data).getJSONArray("paragraphs")
            assertEquals(7, sent.length())
            assertEquals(input.paragraphs.last(), sent.getJSONObject(6).getString("text"))
            validResponse(prompt)
        } }
        val progress = mutableListOf<AiProgress>()
        val cache = FakeCache()
        val result = ChapterAiRepository(FakeConfiguration(configured(route)), cache, listOf(provider))
            .generate(input, AiAction.SUMMARY) { progress.add(it) }
        assertNotNull(result.summary); assertEquals(1, provider.calls.get()); assertEquals(1, cache.writes)
        assertFalse(progress.any { it.phase == AiPhase.WAITING_FOR_SLOT })
    }
    @Test fun oversizedGroqSummaryFallsBackBeforeSpendingOnPartialResults() = runBlocking {
        val input = chapter().copy(paragraphs = List(10) { "Mira walked home. ".repeat(200) })
        val route = AiProviderCatalog.models.first { it.route.provider == AiProviderId.GROQ }.route
        val groq = FakeProvider(AiProviderId.GROQ)
        val fallback = FakeProvider(AiProviderId.OPEN_ROUTER)
        val result = ChapterAiRepository(FakeConfiguration(configured(route, freeRoute)), FakeCache(), listOf(groq, fallback))
            .generate(input, AiAction.SUMMARY)
        assertEquals(freeRoute, result.route)
        assertEquals(0, groq.calls.get()); assertEquals(0, groq.discoveries.get())
        assertEquals(1, fallback.calls.get())
    }
    @Test fun quotaLimitedQuickSummaryUsesEnabledFallbackWithoutMinuteLongQueue() = runBlocking {
        val route = AiProviderCatalog.models.first { it.route.provider == AiProviderId.GROQ }.route
        val policy = AiRequestPolicy { 0L }
        val small = AiPrompt("", "", JSONObject())
        repeat(6) { policy.admit(AiProviderId.GROQ, small, 10, model = route.model) {} }
        val groq = FakeProvider(AiProviderId.GROQ)
        val fallback = FakeProvider(AiProviderId.OPEN_ROUTER)
        val progress = mutableListOf<AiProgress>()
        val result = ChapterAiRepository(FakeConfiguration(configured(route, freeRoute)), FakeCache(), listOf(groq, fallback), policy)
            .generate(chapter(), AiAction.SUMMARY) { progress.add(it) }
        assertEquals(freeRoute, result.route)
        assertEquals(0, groq.calls.get()); assertEquals(1, fallback.calls.get())
        assertFalse(progress.any { it.phase == AiPhase.WAITING_FOR_SLOT })
    }
    @Test fun oversizedParagraphFailsWithoutNetwork() = runBlocking {
        val provider = FakeProvider(AiProviderId.OPEN_ROUTER)
        expectAi(AiErrorCode.TOO_LARGE) { ChapterAiRepository(FakeConfiguration(configured(freeRoute)), FakeCache(), listOf(provider))
            .generate(chapter().copy(paragraphs = listOf("x".repeat(20_000))), AiAction.REWRITE) }
        assertEquals(0, provider.discoveries.get())
    }
    @Test fun tooManyChunksFailWithoutNetwork() = runBlocking {
        val provider = FakeProvider(AiProviderId.GROQ)
        val route = AiProviderCatalog.models.first { it.route.provider == AiProviderId.GROQ }.route
        expectAi(AiErrorCode.TOO_LARGE) { ChapterAiRepository(FakeConfiguration(configured(route)), FakeCache(), listOf(provider))
            .generate(chapter().copy(paragraphs = List(9) { "x".repeat(1_500) }), AiAction.REWRITE) }
        assertEquals(0, provider.calls.get())
    }
    @Test fun keyOrModelChangesRefreshDirectory() = runBlocking {
        val provider = FakeProvider(AiProviderId.OPEN_ROUTER)
        val directory = AiModelDirectory(listOf(provider))
        directory.available(provider.id, "first"); directory.available(provider.id, "first")
        assertEquals(1, provider.discoveries.get())
        directory.available(provider.id, "second")
        assertEquals(2, provider.discoveries.get())
        directory.invalidate(provider.id); directory.available(provider.id, "second")
        assertEquals(3, provider.discoveries.get())
    }

    @Test fun knownGroqModelQuotaCanUseNextConfiguredGroqModel() = runBlocking {
        val models = AiProviderCatalog.models.filter { it.route.provider == AiProviderId.GROQ }
        val first = models[0].route; val second = models[1].route
        val provider = FakeProvider(AiProviderId.GROQ).apply { respond = { route, prompt ->
            if (route == first) throw AiException(AiErrorCode.RATE_LIMIT, "Groq daily token allowance", 3_600,
                AiRateLimitInfo(AiLimitKind.DAILY_TOKENS, modelScoped = true))
            validResponse(prompt)
        } }
        val result = ChapterAiRepository(FakeConfiguration(configured(first, second)), FakeCache(), listOf(provider))
            .generate(chapter(), AiAction.SUMMARY)
        assertEquals(second, result.route); assertEquals(2, provider.calls.get())
    }

    @Test fun unknownProviderQuotaDoesNotFanOutAcrossModels() = runBlocking {
        val models = AiProviderCatalog.models.filter { it.route.provider == AiProviderId.GROQ }.map { it.route }
        val provider = FakeProvider(AiProviderId.GROQ).apply { respond = { _, _ -> throw AiException(AiErrorCode.RATE_LIMIT, "Unknown scope", 60) } }
        expectAi(AiErrorCode.RATE_LIMIT) {
            ChapterAiRepository(FakeConfiguration(configured(*models.toTypedArray())), FakeCache(), listOf(provider))
                .generate(chapter(), AiAction.SUMMARY)
        }
        assertEquals(1, provider.calls.get())
    }

    @Test fun activeProviderCooldownDoesNotRefetchModelMetadata() = runBlocking {
        val routes = AiProviderCatalog.models.filter { it.route.provider == AiProviderId.GROQ }.map { it.route }
        val provider = FakeProvider(AiProviderId.GROQ)
        val policy = AiRequestPolicy { 0L }
        policy.failed(AiProviderId.GROQ, AiException(AiErrorCode.RATE_LIMIT, "Shared provider quota", 60))
        expectAi(AiErrorCode.RATE_LIMIT) {
            ChapterAiRepository(FakeConfiguration(configured(*routes.toTypedArray())), FakeCache(), listOf(provider), policy)
                .generate(chapter(), AiAction.SUMMARY)
        }
        assertEquals(0, provider.discoveries.get()); assertEquals(0, provider.calls.get())
    }
}
