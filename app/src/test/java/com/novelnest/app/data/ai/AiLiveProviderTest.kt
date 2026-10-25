package com.novelnest.app.data.ai

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Explicit opt-in only. Credentials stay in process environment; reports never contain keys or text. */
class AiLiveProviderTest {
    @Test fun configuredModelsGenerateValidatedChapterTools() = runBlocking {
        assumeTrue("Live provider tests require explicit opt-in", System.getenv("AI_LIVE_SMOKE") == "1")
        val report = JSONArray()
        val requests = AtomicInteger()
        val maxRequests = System.getenv("AI_LIVE_MAX_REQUESTS")?.toIntOrNull()?.coerceIn(1, 15) ?: 15
        val cases = System.getenv("AI_LIVE_CASES").orEmpty().split('|').filter { it.isNotBlank() }.toSet()
        val wire = mutableListOf<JSONObject>()
        val http = AiHttpClient(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.method == "POST" && requests.incrementAndGet() > maxRequests) throw java.io.IOException("Live request budget exhausted")
            val reply = try { chain.proceed(request) } catch (error: java.io.IOException) {
                wire.add(JSONObject().put("method", request.method).put("host", request.url.host)
                    .put("networkCauseTypes", JSONArray(generateSequence<Throwable>(error) { it.cause }.take(8)
                        .map { it.javaClass.simpleName }.toList())))
                throw error
            }
            val json = runCatching { JSONObject(reply.peekBody(512 * 1024).string()) }.getOrNull()
            val error = json?.optJSONObject("error")
            val usage = json?.optJSONObject("usage")
            val sentBody = request.body?.let { body -> JSONObject(okio.Buffer().also { body.writeTo(it) }.readUtf8()) }
            wire.add(JSONObject().put("http", reply.code).put("method", request.method)
                .put("host", request.url.host).put("path", request.url.encodedPath)
                .put("contentType", reply.header("Content-Type").orEmpty().substringBefore(';'))
                .put("model", sentBody?.optString("model").orEmpty())
                .put("strict", sentBody?.optJSONObject("response_format")?.optJSONObject("json_schema")?.opt("strict") ?: JSONObject.NULL)
                .put("diagnostics", diagnostics(sentBody, json))
                .put("errorCode", error?.optString("code").orEmpty().take(80))
                .put("errorType", error?.optString("type").orEmpty().take(80))
                .put("cost", usage?.opt("cost") ?: JSONObject.NULL)
                .put("promptTokens", usage?.opt("prompt_tokens") ?: JSONObject.NULL)
                .put("completionTokens", usage?.opt("completion_tokens") ?: JSONObject.NULL))
            reply
        }.build())
        val input = AiChapterInput("LIVE_TEST", "original-test-story", "one", "Chapter 1: The Copper Gate", listOf(
            "Mira stood at the Copper Gate with 12 spirit stones. Elder Ren explained that Qi Condensation was the first cultivation realm. A Golden Core could form only after the disciple learned to control spiritual energy. Mira placed 3 stones on the altar and kept 9 stones in her pouch.",
            "The elder taught her the Cloud Step technique. It allowed a trained disciple to move quietly, but Mira could manage only 2 steps. Beyond the gate stood the Azure Sect, the organization that protected their village. Mira promised to practice before entering the sect. That evening, she returned the borrowed Jade Compass to Elder Ren."
        ))
        val providers = listOf(ChatAiProvider(AiProviderId.OPEN_ROUTER, http), ChatAiProvider(AiProviderId.GROQ, http),
            ChatAiProvider(AiProviderId.NVIDIA, http))
        val credentials = mapOf(AiProviderId.OPEN_ROUTER to System.getenv("OPENROUTER_API_KEY").orEmpty(),
            AiProviderId.GROQ to System.getenv("GROQ_API_KEY").orEmpty(),
            AiProviderId.NVIDIA to System.getenv("NVIDIA_API_KEY").orEmpty())
        val configuration = object : AiConfiguration {
            var route = AiProviderCatalog.models.first().route
            override suspend fun settings() = AiSettings(listOf(route), listOf(route), setOf(route.provider),
                AiCostPolicy.FREE_ACCOUNT_PROVIDERS, listOf(route))
            override suspend fun credential(provider: AiProviderId) = credentials.getValue(provider)
        }
        val cache = object : AiCache {
            override val generation = 0L
            override suspend fun read(key: String, input: AiChapterInput, action: AiAction): AiResult? = null
            override suspend fun write(key: String, result: AiResult, expectedGeneration: Long) = Unit
        }
        val directory = AiModelDirectory(providers)
        val repository = ChapterAiRepository(configuration, cache, providers, directory = directory)
        try {
            for (provider in providers) {
                val credential = credentials.getValue(provider.id)
                if (credential.isBlank()) continue
                if (provider.id == AiProviderId.GROQ) check(System.getenv("GROQ_FREE_PLAN_CONFIRMED") == "1") {
                    "Confirm a Groq Free-plan account before live inference"
                }
                val available = try { directory.available(provider.id, credential) } catch (error: AiException) {
                    report.put(JSONObject().put("provider", provider.id.name).put("stage", "metadata")
                        .put("status", error.code.name).put("message", error.message))
                    continue
                }
                for (spec in AiProviderCatalog.models.filter { it.route.provider == provider.id }) {
                    val route = spec.route
                    if (cases.isNotEmpty() && cases.none { it.startsWith(route.model + ":") }) continue
                    if (route.model !in available) {
                        report.put(JSONObject().put("provider", provider.id.name).put("model", route.model)
                            .put("stage", "metadata").put("status", "MODEL_UNAVAILABLE"))
                        continue
                    }
                    configuration.route = route
                    for (action in AiAction.entries) {
                        if (cases.isNotEmpty() && route.model + ":" + action.name !in cases) continue
                        val row = JSONObject().put("provider", provider.id.name).put("model", route.model).put("action", action.name)
                        val start = System.nanoTime()
                        try {
                            val result = repository.generate(if (action == AiAction.EXTRACT) input.copy(extractionTarget = "Cultivation realms, energy and techniques") else input, action)
                            row.put("status", "VALIDATED").put("terms", result.extractedTerms.size)
                                .put("paragraphs", result.rewrittenParagraphs.size)
                            println("Live provider: ${route.model} / $action validated")
                        } catch (error: AiException) {
                            row.put("status", error.code.name).put("message", error.message)
                            println("Live provider: ${route.model} / $action: ${error.code}")
                        }
                        row.put("elapsedMs", (System.nanoTime() - start) / 1_000_000)
                        report.put(row)
                    }
                }
            }
        } finally {
            val target = File("build/reports/ai-live/" +
                if (credentials.getValue(AiProviderId.NVIDIA).isNotBlank()) "nvidia-report.json" else "report.json")
            requireNotNull(target.parentFile).mkdirs()
            target.writeText(JSONObject().put("fixture", "Two original paragraphs; protocol and evidence validation, not a full-novel quality evaluation")
                .put("results", report).put("wire", JSONArray(wire)).toString(2))
        }
        assertTrue("Live models failed checks; see the sanitized build/reports/ai-live reports", report.length() > 0 &&
            (0 until report.length()).all { report.getJSONObject(it).optString("status") == "VALIDATED" })
    }
}

/** Reports boolean comparisons and numeric IDs only, never chapter or generated text. */
private fun diagnostics(sent: JSONObject?, received: JSONObject?): JSONObject {
    val result = JSONObject()
    try {
        if (sent == null || received == null) return result
        val data = JSONObject(sent.getJSONArray("messages").getJSONObject(1).getString("content"))
        val originals = data.getJSONArray("paragraphs")
        val refs = (0 until originals.length()).associate { originals.getJSONObject(it).let { p -> p.getInt("id") to p.getString("text") } }
        val choice = received.getJSONArray("choices").getJSONObject(0)
        val message = choice.getJSONObject("message")
        val rawContent = message.optString("content", "")
        result.put("finishReason", choice.optString("finish_reason").takeIf { it in setOf("stop", "length", "content_filter", "tool_calls") } ?: "OTHER")
            .put("contentLength", rawContent.length).put("fencedOutput", rawContent.trim().startsWith("```"))
            .put("thinkingPrefix", rawContent.trim().startsWith("<think>"))
            .put("separateReasoningLength", message.optString("reasoning_content", "").length)
        val content = JSONObject(rawContent)
        result.put("keys", JSONArray(content.keys().asSequence().toList()))
        content.optJSONArray("terms")?.let { terms -> result.put("terms", JSONArray().apply {
            for (i in 0 until terms.length()) {
                val term = terms.getJSONObject(i)
                val source = refs[term.optInt("paragraphId", -1)]
                val name = term.optString("name")
                val evidence = term.optString("evidence")
                put(JSONObject().put("index", i).put("kind", term.optString("kind").takeIf { it.matches(Regex("[A-Z_]{1,32}")) } ?: "OTHER")
                    .put("paragraphId", term.optInt("paragraphId", -1)).put("referenceExists", source != null)
                    .put("nameExact", source?.contains(name)).put("evidenceExact", source?.contains(evidence))
                    .put("nameIgnoringCase", source?.contains(name, ignoreCase = true))
                    .put("evidenceIgnoringCase", source?.contains(evidence, ignoreCase = true))
                    .put("nameLength", name.length).put("evidenceLength", evidence.length).put("meaningLength", term.optString("meaning").length))
            }
        }) }
        content.optJSONArray("events")?.let { events -> result.put("events", JSONArray().apply {
            for (i in 0 until events.length()) {
                val ids = events.getJSONObject(i).getJSONArray("paragraphIds")
                put(JSONObject().put("referenceIds", ids).put("allExist", (0 until ids.length()).all { ids.getInt(it) in refs }))
            }
        }) }
        content.optJSONArray("paragraphs")?.let { paragraphs -> result.put("rewrite", JSONArray().apply {
            val numbers = Regex("\\p{N}+(?:[.,]\\p{N}+)*")
            for (i in 0 until paragraphs.length()) {
                val paragraph = paragraphs.getJSONObject(i)
                val text = paragraph.optString("text")
                val original = refs[paragraph.optInt("id", -1)]
                put(JSONObject().put("id", paragraph.optInt("id", -1)).put("expectedId", originals.optJSONObject(i)?.optInt("id"))
                    .put("originalNumbers", JSONArray(numbers.findAll(original.orEmpty()).map { it.value }.toList()))
                    .put("resultNumbers", JSONArray(numbers.findAll(text).map { it.value }.toList()))
                    .put("originalLength", original?.length).put("resultLength", text.length))
            }
        }) }
    } catch (_: Exception) { result.put("diagnosticsUnavailable", true) }
    return result
}
