package com.novelnest.app.data.ai

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Interceptors return synthetic responses before any socket or provider is contacted. */
class AiProviderWireTest {
    private val prompt = AiPromptFactory.create(AiAction.SUMMARY, chapter().title,
        chapter().paragraphs.mapIndexed { index, text -> AiParagraph(index, text) })
    private fun client(status: Int = 200, headers: Map<String, String> = emptyMap(), reply: (okhttp3.Request) -> String) = AiHttpClient(OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Synthetic")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .body(reply(chain.request()).toResponseBody("application/json".toMediaType())).build()
    }.build())
    private fun response(model: String, content: String = validResponse(prompt), finish: String = "stop") = JSONObject().put("model", model)
        .put("choices", JSONArray().put(JSONObject().put("finish_reason", finish)
            .put("message", JSONObject().put("content", content)))).toString()

    @Test fun openRouterEnforcesZeroPriceAndNoHiddenFallback() = runBlocking {
        val http = client { request ->
            assertEquals("Bearer offline-test-key", request.header("Authorization"))
            assertFalse(request.url.toString().contains("offline-test-key"))
            val body = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
            assertEquals(freeRoute.model, body.getString("model"))
            assertFalse(body.getBoolean("stream"))
            assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
            assertFalse(body.getJSONObject("response_format").has("json_schema"))
            assertTrue(body.getJSONArray("messages").getJSONObject(0).getString("content").contains(prompt.schema.toString()))
            val routing = body.getJSONObject("provider")
            assertFalse(routing.getBoolean("allow_fallbacks"))
            assertEquals(0, routing.getJSONObject("max_price").getInt("prompt"))
            assertEquals(0, routing.getJSONObject("max_price").getInt("completion"))
            assertEquals(0, routing.getJSONObject("max_price").getInt("request"))
            response(freeRoute.model.removeSuffix(":free"))
        }
        val result = ChatAiProvider(AiProviderId.OPEN_ROUTER, http).generate(freeRoute, "offline-test-key", prompt, 1_000) {}
        assertEquals(validResponse(prompt), result)
    }
    @Test fun openRouterDiscoveryRejectsUnknownOrNonzeroPrice() = runBlocking {
        val values = JSONArray().put(JSONObject().put("id", freeRoute.model).put("pricing", JSONObject().put("prompt", "0").put("completion", "0"))
            .put("supported_parameters", JSONArray().put("response_format")))
            .put(JSONObject().put("id", alternateFreeRoute.model).put("pricing", JSONObject().put("prompt", "0.01").put("completion", "0")))
        val available = ChatAiProvider(AiProviderId.OPEN_ROUTER, client { JSONObject().put("data", values).toString() }).available("offline-key")
        assertEquals(setOf(freeRoute.model), available)
    }
    private fun gemmaMetadata(): String = requireNotNull(javaClass.getResourceAsStream("/ai/openrouter-gemma-free-models.json"))
        .bufferedReader().use { it.readText() }

    private fun expandedMetadata(): String = requireNotNull(javaClass.getResourceAsStream("/ai/openrouter-free-models-2026-10-26.json"))
        .bufferedReader().use { it.readText() }

    @Test fun expandedPublicSnapshotExcludesModerationAndHarnessRestrictedModels() = runBlocking {
        val available = ChatAiProvider(AiProviderId.OPEN_ROUTER, client { expandedMetadata() }).available("offline-key")
        assertEquals(13, available.size)
        assertEquals(AiProviderCatalog.models.filter { it.route.provider == AiProviderId.OPEN_ROUTER }
            .map { it.route.model }.toSet(), available)
        assertFalse("nvidia/nemotron-3.5-content-safety:free" in available)
        assertFalse("thinkingmachines/inkling:free" in available)
        assertFalse("thinkingmachines/inkling-small:free" in available)
    }

    @Test fun promptOnlyModelStillRejectsChangedOrMissingPricing() = runBlocking {
        val data = JSONObject(expandedMetadata())
        val values = data.getJSONArray("data")
        val rejected = setOf("poolside/laguna-s-2.1:free", "nvidia/nemotron-3.5-lightning:free")
        for (i in 0 until values.length()) {
            val entry = values.getJSONObject(i)
            when (entry.getString("id")) {
                "poolside/laguna-s-2.1:free" -> entry.getJSONObject("pricing").put("request", "0.001")
                "nvidia/nemotron-3.5-lightning:free" -> entry.remove("pricing")
            }
        }
        val available = ChatAiProvider(AiProviderId.OPEN_ROUTER, client { data.toString() }).available("offline-key")
        assertEquals(11, available.size)
        assertTrue(available.intersect(rejected).isEmpty())
    }

    @Test fun expandedModelsUseSupportedWireOptionsAndRetainFreeRouting() = runBlocking {
        for (spec in AiProviderCatalog.models.filter { it.route.provider == AiProviderId.OPEN_ROUTER }) {
            val provider = ChatAiProvider(AiProviderId.OPEN_ROUTER, client { request ->
                val body = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
                when (spec.responseMode) {
                    AiResponseMode.JSON_SCHEMA -> assertTrue(body.getJSONObject("response_format").getJSONObject("json_schema").getBoolean("strict"))
                    AiResponseMode.JSON_OBJECT -> assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
                    AiResponseMode.PROMPT_JSON -> {
                        assertFalse(body.has("response_format"))
                        assertTrue(body.getJSONArray("messages").getJSONObject(0).getString("content").contains(prompt.schema.toString()))
                    }
                }
                val reasoning = body.getJSONObject("reasoning")
                assertTrue(reasoning.getBoolean("exclude"))
                if (spec.mandatoryReasoning) assertFalse(reasoning.has("enabled")) else assertFalse(reasoning.getBoolean("enabled"))
                assertEquals(1_000, body.getInt("max_tokens"))
                val routing = body.getJSONObject("provider")
                assertFalse(routing.getBoolean("allow_fallbacks"))
                assertTrue(routing.getBoolean("require_parameters"))
                for (unit in listOf("prompt", "completion", "request")) assertEquals(0, routing.getJSONObject("max_price").getInt(unit))
                response(spec.route.model)
            })
            assertEquals(validResponse(prompt), provider.generate(spec.route, "offline-key", prompt, 1_000) {})
        }
    }

    @Test fun mandatoryReasoningSummaryHasRoomForFinalJsonAndRetainsValidation() = runBlocking {
        val route = AiRoute(AiProviderId.OPEN_ROUTER, "recursive/flint-v1.0:free")
        var posted = false
        val provider = ChatAiProvider(AiProviderId.OPEN_ROUTER, client { request ->
            if (request.method == "GET") expandedMetadata() else {
                posted = true
                val body = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
                assertEquals(8_192, body.getInt("max_tokens"))
                // Neither a successful HTTP response nor mandatory reasoning makes invalid JSON safe.
                response(route.model, "{\"summary\":\"Incomplete\"}")
            }
        })
        val cache = FakeCache()
        expectAi(AiErrorCode.INVALID_OUTPUT) {
            ChapterAiRepository(FakeConfiguration(configured(route)), cache, listOf(provider)).generate(chapter(), AiAction.SUMMARY)
        }
        assertTrue(posted)
        assertEquals(0, cache.writes)
    }

    @Test fun realCatalogSnapshotFindsBothFreeGemmaInstructionModels() = runBlocking {
        val provider = ChatAiProvider(AiProviderId.OPEN_ROUTER, client { gemmaMetadata() })
        assertEquals(setOf("google/gemma-4-31b-it:free", "google/gemma-4-26b-a4b-it:free"), provider.available("offline-key"))
    }
    @Test fun missingJsonModeCapabilityFailsAvailabilityWithoutRelaxingPricing() = runBlocking {
        val data = JSONObject(gemmaMetadata())
        val values = data.getJSONArray("data")
        for (index in 0 until values.length()) values.getJSONObject(index).remove("supported_parameters")
        val provider = ChatAiProvider(AiProviderId.OPEN_ROUTER, client { data.toString() })
        assertTrue(provider.available("offline-key").isEmpty())
    }
    @Test fun jsonModeOutputStillRequiresFullLocalValidationBeforeCaching() = runBlocking {
        val provider = ChatAiProvider(AiProviderId.OPEN_ROUTER, client { request ->
            if (request.method == "GET") gemmaMetadata() else response(freeRoute.model, "{\"summary\":\"Incomplete\"}")
        })
        val cache = FakeCache()
        expectAi(AiErrorCode.INVALID_OUTPUT) {
            ChapterAiRepository(FakeConfiguration(configured(freeRoute)), cache, listOf(provider)).generate(chapter(), AiAction.SUMMARY)
        }
        assertEquals(0, cache.writes)
    }
    @Test fun groqRetainsStrictSchemaOutput() = runBlocking {
        val route = AiProviderCatalog.models.first { it.route.provider == AiProviderId.GROQ }.route
        val provider = ChatAiProvider(AiProviderId.GROQ, client { request ->
            val body = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
            assertEquals("json_schema", body.getJSONObject("response_format").getString("type"))
            assertTrue(body.getJSONObject("response_format").getJSONObject("json_schema").getBoolean("strict"))
            response(route.model)
        })
        assertEquals(validResponse(prompt), provider.generate(route, "offline-key", prompt, 1_000) {})
    }
    @Test fun truncatedCompletionIsNeverAccepted() = runBlocking {
        val provider = ChatAiProvider(AiProviderId.OPEN_ROUTER, client { response(freeRoute.model, finish = "length") })
        expectAi(AiErrorCode.INVALID_OUTPUT) { provider.generate(freeRoute, "offline-key", prompt, 1_000) {} }
    }
    @Test fun groqReportsTotalUsageIncludingReasoningBeforeRejectingTruncatedOutput() = runBlocking {
        val route = AiProviderCatalog.models.first { it.route.provider == AiProviderId.GROQ }.route
        val provider = ChatAiProvider(AiProviderId.GROQ, client {
            JSONObject(response(route.model, finish = "length")).put("usage", JSONObject()
                .put("prompt_tokens", 400).put("completion_tokens", 900).put("total_tokens", 1_300)
                .put("completion_tokens_details", JSONObject().put("reasoning_tokens", 700))).toString()
        })
        var reported = 0
        expectAi(AiErrorCode.INVALID_OUTPUT) {
            provider.generateWithUsage(route, "offline-key", prompt, 2_048, { reported = it }) {}
        }
        assertEquals(1_300, reported)
    }
    @Test fun geminiReportsTotalUsageWithoutLeakingResponseContent() = runBlocking {
        val provider = GeminiAiProvider(client {
            JSONObject().put("usageMetadata", JSONObject().put("totalTokenCount", 800))
                .put("candidates", JSONArray().put(JSONObject().put("finishReason", "STOP")
                    .put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", validResponse(prompt))))))).toString()
        })
        var reported = 0
        assertEquals(validResponse(prompt), provider.generateWithUsage(geminiRoute, "offline-key", prompt, 2_048, { reported = it }) {})
        assertEquals(800, reported)
    }
    @Test fun explicitRefusalIsNotAnEmptySuccess() = runBlocking {
        val provider = ChatAiProvider(AiProviderId.OPEN_ROUTER, client { response(freeRoute.model, finish = "content_filter") })
        expectAi(AiErrorCode.REFUSED) { provider.generate(freeRoute, "offline-key", prompt, 1_000) {} }
    }
    @Test fun structuredHttpRefusalIsPreserved() = runBlocking {
        val provider = ChatAiProvider(AiProviderId.OPEN_ROUTER, client(400) { "{\"error\":{\"code\":\"content_policy_violation\"}}" })
        expectAi(AiErrorCode.REFUSED) { provider.generate(freeRoute, "offline-key", prompt, 1_000) {} }
    }
    @Test fun http503BecomesTypedUnavailableWithoutRawProviderText() = runBlocking {
        val provider = ChatAiProvider(AiProviderId.OPEN_ROUTER, client(503) { "private upstream diagnostics" })
        expectAi(AiErrorCode.UNAVAILABLE) { provider.generate(freeRoute, "offline-key", prompt, 1_000) {} }
    }
    @Test fun geminiUsesHeaderKeyAndStructuredOutput() = runBlocking {
        val provider = GeminiAiProvider(client { request ->
            assertEquals("offline-test-key", request.header("x-goog-api-key"))
            assertNull(request.url.queryParameter("key"))
            val body = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
            assertEquals("application/json", body.getJSONObject("generationConfig").getString("responseMimeType"))
            assertTrue(body.getJSONObject("generationConfig").has("responseJsonSchema"))
            JSONObject().put("candidates", JSONArray().put(JSONObject().put("finishReason", "STOP")
                .put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", validResponse(prompt))))))).toString()
        })
        assertEquals(validResponse(prompt), provider.generate(geminiRoute, "offline-test-key", prompt, 1_000) {})
    }
    @Test fun geminiSafetyBlockDoesNotFallbackAsMalformedJson() = runBlocking {
        val provider = GeminiAiProvider(client { "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}" })
        expectAi(AiErrorCode.REFUSED) { provider.generate(geminiRoute, "offline-key", prompt, 1_000) {} }
    }
    @Test fun geminiFindsCuratedModelsOnLaterMetadataPages() = runBlocking {
        var calls = 0
        val provider = GeminiAiProvider(client { request ->
            calls++
            if (request.url.queryParameter("pageToken") == null) "{\"models\":[],\"nextPageToken\":\"next page\"}"
            else {
                assertEquals("next page", request.url.queryParameter("pageToken"))
                JSONObject().put("models", JSONArray().put(JSONObject().put("name", "models/${geminiRoute.model}")
                    .put("supportedGenerationMethods", JSONArray().put("generateContent")))).toString()
            }
        })
        assertEquals(setOf(geminiRoute.model), provider.available("offline-key"))
        assertEquals(2, calls)
    }
    @Test fun redirectIsNotFollowed() = runBlocking {
        var calls = 0
        val provider = ChatAiProvider(AiProviderId.OPEN_ROUTER, client(302) { calls++; "redirect" })
        expectAi(AiErrorCode.INVALID_OUTPUT) { provider.generate(freeRoute, "offline-key", prompt, 1_000) {} }
        assertEquals(1, calls)
    }

    @Test fun groqSchemaFailureIsActionableAndNeverExposesFailedGeneration() = runBlocking {
        val route = AiProviderCatalog.models.first { it.route.provider == AiProviderId.GROQ }.route
        val provider = ChatAiProvider(AiProviderId.GROQ, client(400) {
            JSONObject().put("error", JSONObject().put("code", "json_validate_failed")
                .put("type", "invalid_request_error").put("message", "private provider details")
                .put("failed_generation", "private chapter content")).toString()
        })
        try {
            provider.generate(route, "offline-test-key", prompt, 1_000) {}
            fail("Schema failure should reject the result")
        } catch (error: AiException) {
            assertEquals(AiErrorCode.INVALID_OUTPUT, error.code)
            assertTrue(error.message.orEmpty().contains("JSON schema"))
            assertFalse(error.message.orEmpty().contains("private"))
        }
    }

    @Test fun everyGroqActionAndModelSendsStrictSchemaAndKeepsLocalValidation() = runBlocking {
        val refs = chapter().paragraphs.mapIndexed(::AiParagraph)
        for (spec in AiProviderCatalog.models.filter { it.route.provider == AiProviderId.GROQ }) {
            for (action in AiAction.entries) {
                val task = AiPromptFactory.create(action, chapter().title, refs, "cultivation terms")
                val content = if (action == AiAction.EXTRACT) "{\"terms\":[]}" else validResponse(task)
                val provider = ChatAiProvider(AiProviderId.GROQ, client { request ->
                    val body = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
                    val output = body.getJSONObject("response_format")
                    assertEquals(0.2, body.getDouble("temperature"), 0.0)
                    assertEquals("json_schema", output.getString("type"))
                    assertTrue(output.getJSONObject("json_schema").getBoolean("strict"))
                    assertEquals(task.schema.toString(), output.getJSONObject("json_schema").getJSONObject("schema").toString())
                    response(spec.route.model, content)
                })
                val actual = provider.generate(spec.route, "offline-test-key", task, 1_000) {}
                assertEquals(action, AiResponseValidator.parse(actual, action, chapter().fingerprint(), spec.route, refs).action)
            }
        }
    }

    @Test fun extractionUsesWholeChapterAndRetainsFullGroqOutputBudgetOnWire() = runBlocking {
        val spec = AiProviderCatalog.models.first { it.route.provider == AiProviderId.GROQ }
        val input = chapter().copy(extractionTarget = "cultivation terms", paragraphs = List(7) { "Mira walked. ".repeat(90) })
        var posts = 0
        val provider = ChatAiProvider(AiProviderId.GROQ, client { request ->
            if (request.method == "GET") JSONObject().put("data", JSONArray().put(JSONObject().put("id", spec.route.model))).toString()
            else {
                posts++
                val body = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
                assertEquals(spec.outputTokens, body.getInt("max_completion_tokens"))
                val data = JSONObject(body.getJSONArray("messages").getJSONObject(1).getString("content"))
                assertEquals(input.extractionTarget, data.getString("extractionTarget"))
                assertEquals(7, data.getJSONArray("paragraphs").length())
                response(spec.route.model, "{\"terms\":[]}")
            }
        })
        val settings = configured(spec.route).copy(extractRoutes = listOf(spec.route))
        val result = ChapterAiRepository(FakeConfiguration(settings), FakeCache(), listOf(provider)).generate(input, AiAction.EXTRACT)
        assertEquals(1, posts); assertTrue(result.extractedTerms.isEmpty())
    }

    @Test fun groqDailyTokenErrorUsesServerRetryHintAndHidesPrivateDiagnostics() = runBlocking {
        val route = AiProviderCatalog.models.first { it.route.provider == AiProviderId.GROQ }.route
        val provider = ChatAiProvider(AiProviderId.GROQ, client(429) {
            JSONObject().put("error", JSONObject().put("message",
                "Rate limit reached in private-org on tokens per day (TPD). Please try again in 1h2m3.5s. private chapter text")).toString()
        })
        try {
            provider.generate(route, "offline-key", prompt, 1_000) {}; fail("Expected a quota error")
        } catch (error: AiException) {
            assertEquals(3_724L, error.retryAfterSeconds)
            assertEquals(AiLimitKind.DAILY_TOKENS, error.rateLimit?.kind)
            assertTrue(error.rateLimit?.modelScoped == true)
            assertTrue(error.message.orEmpty().contains("daily token"))
            assertFalse(error.message.orEmpty().contains("private"))
        }
    }

    @Test fun explicitProviderRetryTakesPriorityOverFullDailyCounterReset() = runBlocking {
        val route = AiProviderCatalog.models.first { it.route.provider == AiProviderId.GROQ }.route
        val provider = ChatAiProvider(AiProviderId.GROQ, client(429, mapOf(
            "Retry-After" to "60", "x-ratelimit-remaining-requests" to "0", "x-ratelimit-reset-requests" to "2h"
        )) { "{\"error\":{\"message\":\"limited\"}}" })
        try {
            provider.generate(route, "offline-key", prompt, 1_000) {}; fail("Expected daily quota")
        } catch (error: AiException) {
            assertEquals(60L, error.retryAfterSeconds)
            assertEquals(AiLimitKind.DAILY_REQUESTS, error.rateLimit?.kind)
        }
    }

    @Test fun groqSuccessReportsQuotaCountersWithFractionalResetDurations() = runBlocking {
        val route = AiProviderCatalog.models.first { it.route.provider == AiProviderId.GROQ }.route
        val provider = ChatAiProvider(AiProviderId.GROQ, client(headers = mapOf(
            "x-ratelimit-limit-tokens" to "8000", "x-ratelimit-remaining-tokens" to "1200",
            "x-ratelimit-reset-tokens" to "7.66s", "x-ratelimit-remaining-requests" to "999",
            "x-ratelimit-reset-requests" to "23h59m59.5s"
        )) { response(route.model) })
        var quota: AiQuotaSnapshot? = null
        provider.generateWithUsage(route, "offline-key", prompt, 1_000, {}, { quota = it }) {}
        assertEquals(1_200, quota?.tokensRemaining)
        assertEquals(7_660L, quota?.tokenResetMs)
        assertEquals(999, quota?.requestsRemaining)
        assertEquals(86_399_500L, quota?.requestResetMs)
    }

    @Test fun rejectedOversizedResponseClosesTheBodyWithoutReadingItsPayload() = runBlocking {
        val closed = java.util.concurrent.atomic.AtomicBoolean()
        val body = object : okhttp3.ResponseBody() {
            private val content = object : okio.ForwardingSource(Buffer().writeUtf8("not read")) {
                override fun close() { closed.set(true); super.close() }
            }.buffer()
            override fun contentType() = "application/json".toMediaType()
            override fun contentLength() = 512 * 1024L + 1
            override fun source() = content
        }
        val http = AiHttpClient(OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Synthetic").body(body).build()
        }.build())
        expectAi(AiErrorCode.INVALID_OUTPUT) { ChatAiProvider(AiProviderId.OPEN_ROUTER, http).generate(freeRoute, "offline-key", prompt, 1_000) {} }
        assertTrue(closed.get())
    }

    @Test fun singleRequestOverMinuteTokenBudgetIsNotARetryableCooldown() = runBlocking {
        val route = AiProviderCatalog.models.first { it.route.provider == AiProviderId.GROQ }.route
        for (status in listOf(413, 429)) {
            val provider = ChatAiProvider(AiProviderId.GROQ, client(status) {
                JSONObject().put("error", JSONObject().put("message",
                    "Request too large on tokens per minute (TPM): Limit 8,000, Used 0, Requested 9,100. private-org")).toString()
            })
            try {
                provider.generate(route, "offline-key", prompt, 1_000) {}; fail("Waiting does not repair an oversized request")
            } catch (error: AiException) {
                assertEquals(AiErrorCode.TOO_LARGE, error.code)
                assertTrue(error.message.orEmpty().contains("Waiting one minute cannot"))
                assertFalse(error.message.orEmpty().contains("private-org"))
            }
        }
    }

    @Test fun usedMinuteQuotaWithIndividuallyValidRequestRemainsARateLimit() = runBlocking {
        val route = AiProviderCatalog.models.first { it.route.provider == AiProviderId.GROQ }.route
        val provider = ChatAiProvider(AiProviderId.GROQ, client(429) {
            JSONObject().put("error", JSONObject().put("message",
                "Rate limit on tokens per minute (TPM): Limit 8000, Used 7000, Requested 2000. Please try again in 8.5s.")).toString()
        })
        try {
            provider.generate(route, "offline-key", prompt, 1_000) {}; fail("Expected per-minute usage limit")
        } catch (error: AiException) {
            assertEquals(AiErrorCode.RATE_LIMIT, error.code)
            assertEquals(AiLimitKind.MINUTE_TOKENS, error.rateLimit?.kind)
            assertEquals(9L, error.retryAfterSeconds)
        }
    }
}
