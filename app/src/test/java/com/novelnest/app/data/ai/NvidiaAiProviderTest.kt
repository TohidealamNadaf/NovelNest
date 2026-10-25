package com.novelnest.app.data.ai

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** No socket, real key or inference call: intercept the production adapter's requests. */
class NvidiaAiProviderTest {
    private val specs = AiProviderCatalog.models.filter { it.route.provider == AiProviderId.NVIDIA }
    private val refs = chapter().paragraphs.mapIndexed(::AiParagraph)
    private fun metadata() = JSONObject().put("data", JSONArray().apply {
        specs.forEach { put(JSONObject().put("id", it.route.model)) }
        put(JSONObject().put("id", "unlisted/model"))
    }).toString()
    private fun response(model: String, content: String, finish: String = "stop") = JSONObject()
        .put("model", model).put("usage", JSONObject().put("total_tokens", 600))
        .put("choices", JSONArray().put(JSONObject().put("finish_reason", finish)
            .put("message", JSONObject().put("content", content)))).toString()
    private fun provider(status: Int = 200, headers: Map<String, String> = emptyMap(),
        reply: (okhttp3.Request) -> String): ChatAiProvider = ChatAiProvider(AiProviderId.NVIDIA,
        AiHttpClient(OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Synthetic")
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .body(reply(chain.request()).toResponseBody("application/json".toMediaType())).build()
        }.build()))

    @Test fun discoveryUsesFixedHostAndOnlyCuratedActiveModels() = runBlocking {
        val adapter = provider { request ->
            assertEquals("https://integrate.api.nvidia.com/v1/models", request.url.toString())
            assertEquals("GET", request.method)
            assertEquals("Bearer offline-key", request.header("Authorization"))
            assertFalse(request.url.toString().contains("offline-key"))
            metadata()
        }
        assertEquals(specs.map { it.route.model }.toSet(), adapter.available("offline-key"))
        val inactive = provider {
            JSONObject(metadata()).apply { getJSONArray("data").getJSONObject(0).put("active", false) }.toString()
        }
        assertEquals(setOf(specs[1].route.model), inactive.available("offline-key"))
    }

    @Test fun everyModelAndActionUsesBoundedNonStreamingRequestsAndLocalValidation() = runBlocking {
        for (spec in specs) for (action in AiAction.entries) {
            val task = AiPromptFactory.create(action, chapter().title, refs, "cultivation terms")
            val expected = if (action == AiAction.EXTRACT) "{\"terms\":[]}" else validResponse(task)
            val adapter = provider { request ->
                assertEquals("https://integrate.api.nvidia.com/v1/chat/completions", request.url.toString())
                assertEquals("POST", request.method)
                val body = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
                assertEquals(spec.route.model, body.getString("model"))
                assertFalse(body.getBoolean("stream"))
                assertEquals(2_048, body.getInt("max_tokens"))
                assertFalse(body.has("provider")) // OpenRouter routing options never leak across adapters.
                assertFalse(body.has("max_completion_tokens"))
                assertFalse(body.has("response_format")) // Not promised by these hosted endpoint references.
                assertFalse(body.getJSONObject("chat_template_kwargs").getBoolean("enable_thinking"))
                val messages = body.getJSONArray("messages")
                assertTrue(messages.getJSONObject(0).getString("content").contains(task.schema.toString()))
                assertEquals(task.data, messages.getJSONObject(1).getString("content"))
                if (spec.route.model.startsWith("nvidia/")) {
                    assertEquals("none", body.getString("reasoning_effort"))
                    assertEquals(1.0, body.getDouble("temperature"), 0.0)
                } else assertFalse(body.getJSONObject("chat_template_kwargs").getBoolean("enable_thinking"))
                response(spec.route.model, expected)
            }
            var tokens = 0
            val raw = adapter.generateWithUsage(spec.route, "offline-key", task, 2_048, { tokens = it }) {}
            assertEquals(600, tokens)
            assertEquals(action, AiResponseValidator.parse(raw, action, chapter().fingerprint(), spec.route, refs).action)
        }
    }

    @Test fun explicitFreePolicyAndMissingConsentBlockNvidiaBeforeAnyNetwork() = runBlocking {
        val route = specs.first().route
        val adapter = provider { error("Policy failure must not contact NVIDIA") }
        for ((settings, expected) in listOf(
            configured(route).copy(costPolicy = AiCostPolicy.EXPLICIT_FREE_ONLY) to AiErrorCode.BILLING_POLICY,
            configured(route).copy(consentedProviders = emptySet()) to AiErrorCode.CONSENT_REQUIRED
        )) {
            expectAi(expected) { ChapterAiRepository(FakeConfiguration(settings), FakeCache(), listOf(adapter))
                .generate(chapter(), AiAction.SUMMARY) }
        }
        assertTrue(specs.all { !it.explicitlyFree && it.responseMode == AiResponseMode.PROMPT_JSON })
    }

    @Test fun largeSummaryFallsBackFromGroqWithoutSendingGroqChunksThenCachesNvidiaResult() = runBlocking {
        val route = specs.first().route
        val groqRoute = AiProviderCatalog.models.first { it.route.provider == AiProviderId.GROQ }.route
        val groq = FakeProvider(AiProviderId.GROQ)
        val input = chapter().copy(paragraphs = List(7) { "Mira returned home. ".repeat(240) })
        var posts = 0
        val adapter = provider { request ->
            if (request.method == "GET") metadata() else {
                posts++
                val body = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
                val sent = JSONObject(body.getJSONArray("messages").getJSONObject(1).getString("content"))
                assertEquals(7, sent.getJSONArray("paragraphs").length())
                val task = AiPromptFactory.create(AiAction.SUMMARY, input.title, input.paragraphs.mapIndexed(::AiParagraph))
                response(route.model, validResponse(task))
            }
        }
        val cache = FakeCache()
        val repo = ChapterAiRepository(FakeConfiguration(configured(groqRoute, route)), cache, listOf(groq, adapter))
        val result = repo.generate(input, AiAction.SUMMARY)
        assertEquals(route, result.route)
        assertEquals(result, repo.generate(input, AiAction.SUMMARY))
        assertEquals(0, groq.calls.get()); assertEquals(0, groq.discoveries.get())
        assertEquals(1, posts); assertEquals(1, cache.writes)
    }

    @Test fun everyActionRejectsMalformedOutputWithoutPublishingPartialResults() = runBlocking {
        val route = specs.first().route
        for (action in AiAction.entries) {
            val cache = FakeCache()
            val settings = configured(route).copy(extractRoutes = listOf(route))
            val adapter = provider { request ->
                if (request.method == "GET") metadata() else response(route.model, "{\"terms\":[{\"kind\":\"REALM\"}]}")
            }
            expectAi(AiErrorCode.INVALID_OUTPUT) {
                ChapterAiRepository(FakeConfiguration(settings), cache, listOf(adapter))
                    .generate(chapter().copy(extractionTarget = "cultivation terms"), action)
            }
            assertEquals(0, cache.writes)
        }
    }

    @Test fun allActionsPublishOnlyCompletedValidatedResultsThroughRepository() = runBlocking {
        val route = specs.first().route
        for (action in AiAction.entries) {
            val task = AiPromptFactory.create(action, chapter().title, refs, "characters")
            val content = if (action == AiAction.EXTRACT) JSONObject().put("terms", JSONArray().put(JSONObject()
                .put("name", "Mira").put("kind", "CHARACTER").put("meaning", "A character who found coins.")
                .put("paragraphId", 0).put("evidence", "Mira found 12 coins."))).toString() else validResponse(task)
            var posts = 0
            val adapter = provider { request ->
                if (request.method == "GET") metadata() else { posts++; response(route.model, content) }
            }
            val settings = configured(route).copy(extractRoutes = listOf(route))
            val cache = FakeCache()
            val result = ChapterAiRepository(FakeConfiguration(settings), cache, listOf(adapter))
                .generate(chapter().copy(extractionTarget = "characters"), action)
            assertEquals(action, result.action)
            assertEquals(1, posts); assertEquals(1, cache.writes)
            if (action == AiAction.EXTRACT) assertEquals("Mira", result.extractedTerms.single().name)
            if (action == AiAction.REWRITE) assertEquals(chapter().paragraphs, result.rewrittenParagraphs)
            if (action == AiAction.SUMMARY) assertNotNull(result.summary)
        }
    }

    @Test fun truncationRefusalAndDifferentModelNeverBecomeSuccessfulResults() = runBlocking {
        val route = specs.first().route
        val task = AiPromptFactory.create(AiAction.SUMMARY, chapter().title, refs)
        for ((finish, expected) in listOf("length" to AiErrorCode.INVALID_OUTPUT, "content_filter" to AiErrorCode.REFUSED)) {
            expectAi(expected) { provider { response(route.model, validResponse(task), finish) }
                .generate(route, "offline-key", task, 2_048) {} }
        }
        expectAi(AiErrorCode.MODEL_UNAVAILABLE) { provider { response("unrequested/model", validResponse(task)) }
            .generate(route, "offline-key", task, 2_048) {} }
    }

    @Test fun remoteErrorsStayTypedAndRetryHintsArePreservedWithoutLeakingDiagnostics() = runBlocking {
        val route = specs.first().route
        val task = AiPromptFactory.create(AiAction.SUMMARY, chapter().title, refs)
        for ((status, expected) in listOf(401 to AiErrorCode.CREDENTIALS, 403 to AiErrorCode.CREDENTIALS, 404 to AiErrorCode.MODEL_UNAVAILABLE,
            429 to AiErrorCode.RATE_LIMIT, 503 to AiErrorCode.UNAVAILABLE, 202 to AiErrorCode.UNAVAILABLE)) {
            try {
                provider(status, mapOf("Retry-After" to "9")) { "{\"error\":{\"message\":\"private diagnostics\"}}" }
                    .generate(route, "offline-key", task, 2_048) {}
                fail("Expected HTTP failure")
            } catch (error: AiException) {
                assertEquals(expected, error.code)
                assertFalse(error.message.orEmpty().contains("private"))
                if (status == 429) assertEquals(9L, error.retryAfterSeconds)
                if (status == 404) {
                    assertTrue(error.message.orEmpty().contains("HTTP 404"))
                    assertTrue(error.message.orEmpty().contains("catalog listing"))
                }
            }
        }
    }

    @Test fun providerWide429SkipsSecondNvidiaModelAndUsesConsentedFallback() = runBlocking {
        var calls = 0
        val first = provider(429, mapOf("Retry-After" to "60")) { calls++; "{}" }
        val second = FakeProvider(AiProviderId.OPEN_ROUTER)
        val result = ChapterAiRepository(FakeConfiguration(configured(specs[0].route, specs[1].route, freeRoute)),
            FakeCache(), listOf(first, second)).generate(chapter(), AiAction.SUMMARY)
        assertEquals(freeRoute, result.route)
        assertEquals(1, calls); assertEquals(1, second.calls.get())
    }
}
