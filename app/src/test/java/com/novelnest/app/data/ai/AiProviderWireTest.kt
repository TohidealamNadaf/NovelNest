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

/** Interceptors return synthetic responses before any socket or provider is contacted. */
class AiProviderWireTest {
    private val prompt = AiPromptFactory.create(AiAction.SUMMARY, chapter().title,
        chapter().paragraphs.mapIndexed { index, text -> AiParagraph(index, text) })
    private fun client(status: Int = 200, reply: (okhttp3.Request) -> String) = AiHttpClient(OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Synthetic")
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
}
