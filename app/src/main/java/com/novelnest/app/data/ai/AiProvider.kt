package com.novelnest.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

interface AiProvider {
    val id: AiProviderId
    /** Checks availability, not successful generation, remaining allowance or account billing tier. */
    suspend fun available(credential: String, force: Boolean = false): Set<String>
    suspend fun generate(route: AiRoute, credential: String, prompt: AiPrompt, outputTokens: Int,
        onSent: () -> Unit): String
}

class GeminiAiProvider(private val http: AiHttpClient) : AiProvider {
    override val id = AiProviderId.GEMINI
    override suspend fun available(credential: String, force: Boolean): Set<String> {
        val wanted = AiProviderCatalog.models.filter { it.route.provider == id }.map { it.route.model }.toSet()
        val found = mutableSetOf<String>()
        val seen = mutableSetOf<String>()
        var token = ""
        repeat(4) {
            val url = okhttp3.HttpUrl.Builder().scheme("https").host("generativelanguage.googleapis.com")
                .addPathSegments("v1beta/models").addQueryParameter("pageSize", "100")
            if (token.isNotBlank()) url.addQueryParameter("pageToken", token)
            val json = http.json(url.build().toString(), credential, gemini = true)
            val models = json.optJSONArray("models") ?: JSONArray()
            for (index in 0 until models.length()) {
                val model = models.getJSONObject(index)
                val name = model.getString("name").removePrefix("models/")
                val methods = model.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                if (name in wanted && (0 until methods.length()).any { methods.optString(it) == "generateContent" }) found.add(name)
            }
            token = json.optString("nextPageToken")
            if (found.containsAll(wanted) || token.isBlank() || !seen.add(token)) return found
        }
        return found
    }
    override suspend fun generate(route: AiRoute, credential: String, prompt: AiPrompt, outputTokens: Int, onSent: () -> Unit): String {
        AiProviderCatalog.spec(route)
        val body = JSONObject().put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt.system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", prompt.data)))))
            .put("generationConfig", JSONObject().put("maxOutputTokens", outputTokens)
                .put("responseMimeType", "application/json").put("responseJsonSchema", prompt.schema))
        val json = http.json("https://generativelanguage.googleapis.com/v1beta/models/${route.model}:generateContent", credential,
            gemini = true, body = body, sent = onSent)
        return withContext(Dispatchers.Default) {
            val block = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            if (block.isNotBlank() && block != "BLOCK_REASON_UNSPECIFIED") refused()
            val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
                ?: throw AiException(AiErrorCode.INVALID_OUTPUT, "The model returned no chapter result.")
            when (candidate.optString("finishReason")) {
                "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY" -> refused()
                "STOP" -> Unit
                else -> throw AiException(AiErrorCode.INVALID_OUTPUT, "The model did not finish the chapter result.")
            }
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            buildString { for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (!part.optBoolean("thought", false)) append(part.optString("text"))
            } }
        }
    }
}

/** Shared wire format only; each provider retains separate endpoints, eligibility and request options. */
class ChatAiProvider(override val id: AiProviderId, private val http: AiHttpClient) : AiProvider {
    init { require(id == AiProviderId.GROQ || id == AiProviderId.OPEN_ROUTER) }
    private val root = if (id == AiProviderId.GROQ) "https://api.groq.com/openai/v1" else "https://openrouter.ai/api/v1"
    override suspend fun available(credential: String, force: Boolean): Set<String> {
        val json = http.json("$root/models", credential)
        return withContext(Dispatchers.Default) {
            val models = json.optJSONArray("data") ?: JSONArray()
            val allowed = AiProviderCatalog.models.filter { it.route.provider == id }.map { it.route.model }.toSet()
            (0 until models.length()).mapNotNull { index -> models.getJSONObject(index).let { model ->
                val name = model.optString("id")
                if (name !in allowed) null
                else if (id == AiProviderId.OPEN_ROUTER) {
                    val price = model.optJSONObject("pricing")
                    // Unknown, missing or nonzero prices are ineligible. Never resolve to a paid alias.
                    val free = price != null && listOf("prompt", "completion", "request").all { key ->
                        val value = price.optString(key, if (key == "request") "0" else "")
                        value.toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO) == 0
                    }
                    val spec = AiProviderCatalog.spec(AiRoute(id, name))
                    val parameters = model.optJSONArray("supported_parameters") ?: JSONArray()
                    val required = if (spec.responseMode == AiResponseMode.JSON_SCHEMA) "structured_outputs" else "response_format"
                    val supportsOutput = (0 until parameters.length()).any { parameters.optString(it) == required }
                    if (free && name.endsWith(":free") && supportsOutput) name else null
                } else if (!model.optBoolean("active", true)) null else name
            } }.toSet()
        }
    }
    override suspend fun generate(route: AiRoute, credential: String, prompt: AiPrompt, outputTokens: Int, onSent: () -> Unit): String {
        val spec = AiProviderCatalog.spec(route)
        require(route.provider == id)
        val schemaOutput = spec.responseMode == AiResponseMode.JSON_SCHEMA
        // JSON mode endpoints do not enforce a schema. Supply the fixed schema as an instruction;
        // AiResponseValidator still checks every completed result before it reaches the reader/cache.
        val system = if (schemaOutput) prompt.system else
            "${prompt.system}\nReturn a JSON object conforming to this schema, with no additional fields:\n${prompt.schema}"
        val responseFormat = if (schemaOutput) JSONObject().put("type", "json_schema").put("json_schema", JSONObject()
            .put("name", "chapter_result").put("strict", true).put("schema", prompt.schema))
        else JSONObject().put("type", "json_object")
        val body = JSONObject().put("model", route.model).put("stream", false)
            .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", prompt.data)))
            .put("response_format", responseFormat)
        if (id == AiProviderId.GROQ) body.put("max_completion_tokens", outputTokens).put("temperature", 0.2)
            .put("reasoning_effort", if (route.model.startsWith("qwen/")) "none" else "low")
        else {
            require(route.model.endsWith(":free"))
            body.put("max_tokens", outputTokens).put("reasoning", JSONObject().put("enabled", false))
                .put("provider", JSONObject().put("allow_fallbacks", false).put("require_parameters", true)
                    .put("max_price", JSONObject().put("prompt", 0).put("completion", 0).put("request", 0)))
        }
        val json = http.json("$root/chat/completions", credential, body = body, sent = onSent)
        return withContext(Dispatchers.Default) {
            val error = json.optJSONObject("error")
            if (error != null) throw AiException(AiErrorCode.UNAVAILABLE, "The AI provider could not complete the request.")
            val choice = json.optJSONArray("choices")?.optJSONObject(0)
                ?: throw AiException(AiErrorCode.INVALID_OUTPUT, "The model returned no chapter result.")
            val message = choice.optJSONObject("message")
                ?: throw AiException(AiErrorCode.INVALID_OUTPUT, "The model returned no chapter text.")
            if (choice.optString("finish_reason") == "content_filter" || !message.optString("refusal", "").let { it.isBlank() || it == "null" }) refused()
            if (choice.optString("finish_reason") != "stop")
                throw AiException(AiErrorCode.INVALID_OUTPUT, "The model did not finish the chapter result.")
            if (id == AiProviderId.OPEN_ROUTER && json.optString("model").removeSuffix(":free") != route.model.removeSuffix(":free"))
                throw AiException(AiErrorCode.MODEL_UNAVAILABLE, "The provider returned a different model than requested.")
            message.getString("content")
        }
    }
}

private fun refused(): Nothing = throw AiException(AiErrorCode.REFUSED, "The provider declined this content. The original chapter is unchanged.")
