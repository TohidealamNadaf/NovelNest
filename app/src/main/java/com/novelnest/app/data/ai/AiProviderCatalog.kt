package com.novelnest.app.data.ai

enum class AiResponseMode { JSON_SCHEMA, JSON_OBJECT, PROMPT_JSON }

data class AiModelSpec(val route: AiRoute, val label: String, val inputBytes: Int,
    val outputTokens: Int, val explicitlyFree: Boolean,
    val responseMode: AiResponseMode = AiResponseMode.JSON_SCHEMA,
    // Summaries compress output: they need not inherit the small rewrite/extraction segments.
    val summaryInputBytes: Int = 64_000,
    // Extraction returns bounded structured records, rather than a rewritten copy of the input.
    val extractionInputBytes: Int = 32_000,
    // Some OpenRouter models cannot disable thinking. The total completion remains bounded.
    val mandatoryReasoning: Boolean = false)

/** Deliberately curated. Arbitrary model IDs, paid routers and custom endpoints are not accepted. */
object AiProviderCatalog {
    val models = listOf(
        AiModelSpec(AiRoute(AiProviderId.OPEN_ROUTER, "google/gemma-4-31b-it:free"), "Gemma 4 31B (free)",
            16_000, 8_192, true, AiResponseMode.JSON_OBJECT),
        AiModelSpec(AiRoute(AiProviderId.OPEN_ROUTER, "google/gemma-4-26b-a4b-it:free"), "Gemma 4 26B A4B (free)",
            16_000, 8_192, true, AiResponseMode.JSON_OBJECT),
        AiModelSpec(AiRoute(AiProviderId.OPEN_ROUTER, "recursive/flint-v1.0:free"), "Flint (free; coding)",
            16_000, 8_192, true, AiResponseMode.JSON_OBJECT, mandatoryReasoning = true),
        AiModelSpec(AiRoute(AiProviderId.OPEN_ROUTER, "xingchen-agi/xing4.0-29b-a4b:free"), "Xing4.0 29B A4B (free)",
            16_000, 8_192, true),
        AiModelSpec(AiRoute(AiProviderId.OPEN_ROUTER, "nvidia/nemotron-3.5-super-vl:free"), "Nemotron 3.5 Super VL (free)",
            16_000, 8_192, true),
        AiModelSpec(AiRoute(AiProviderId.OPEN_ROUTER, "dots-studio/dots-3-note-preview:free"), "Dots3-Note (free until 2026-12-31)",
            16_000, 8_192, true),
        AiModelSpec(AiRoute(AiProviderId.OPEN_ROUTER, "liquid/lfm-2.5-2.6b:free"), "LFM2.5-2.6B (free)",
            16_000, 8_192, true, summaryInputBytes = 32_000, mandatoryReasoning = true),
        AiModelSpec(AiRoute(AiProviderId.OPEN_ROUTER, "nvidia/nemotron-3.5-lightning:free"), "Nemotron 3.5 Lightning (free)",
            16_000, 8_192, true, AiResponseMode.PROMPT_JSON),
        AiModelSpec(AiRoute(AiProviderId.OPEN_ROUTER, "poolside/laguna-s-2.1:free"), "Laguna S 2.1 (free until 2026-10-31; coding)",
            16_000, 8_192, true, AiResponseMode.PROMPT_JSON),
        AiModelSpec(AiRoute(AiProviderId.OPEN_ROUTER, "poolside/laguna-xs-2.1:free"), "Laguna XS 2.1 (free until 2026-10-31; coding)",
            16_000, 8_192, true, AiResponseMode.PROMPT_JSON),
        AiModelSpec(AiRoute(AiProviderId.OPEN_ROUTER, "cohere/north-mini-code:free"), "North Mini Code (free; coding)",
            16_000, 8_192, true, AiResponseMode.PROMPT_JSON),
        // Content Safety is a moderation classifier. Inkling's free endpoints require agentic
        // harnesses; they are not eligible for this reader's direct chapter requests.
        AiModelSpec(AiRoute(AiProviderId.OPEN_ROUTER, "nvidia/nemotron-3-ultra-550b-a55b:free"), "Nemotron 3 Ultra (free)",
            16_000, 8_192, true, AiResponseMode.PROMPT_JSON),
        AiModelSpec(AiRoute(AiProviderId.OPEN_ROUTER, "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free"), "Nemotron 3 Nano Omni (free)",
            16_000, 8_192, true, AiResponseMode.PROMPT_JSON),
        AiModelSpec(AiRoute(AiProviderId.GEMINI, "gemini-3.8-flash"), "Gemini 3.8 Flash", 16_000, 8_192, false),
        AiModelSpec(AiRoute(AiProviderId.GEMINI, "gemini-3.1-flash-lite"), "Gemini 3.1 Flash-Lite", 16_000, 8_192, false),
        AiModelSpec(AiRoute(AiProviderId.GROQ, "openai/gpt-oss-120b"), "GPT-OSS 120B", 2_000, 3_072, false),
        AiModelSpec(AiRoute(AiProviderId.GROQ, "openai/gpt-oss-20b"), "GPT-OSS 20B", 2_000, 3_072, false),
        AiModelSpec(AiRoute(AiProviderId.GROQ, "qwen/qwen3.8-27b"), "Qwen 3.8 27B", 2_000, 3_072, false),
        // NVIDIA's hosted free entitlement is for prototype/testing use, not unrestricted production.
        // These endpoint references do not promise response_format support; require schema via prompt
        // and validate locally rather than send an unsupported structured-output parameter.
        AiModelSpec(AiRoute(AiProviderId.NVIDIA, "google/gemma-4-31b-it"), "Gemma 4 31B IT (prototype)",
            16_000, 8_192, false, AiResponseMode.PROMPT_JSON),
        AiModelSpec(AiRoute(AiProviderId.NVIDIA, "nvidia/nemotron-3-super-120b-a12b"), "Nemotron 3 Super 120B A12B (prototype)",
            16_000, 8_192, false, AiResponseMode.PROMPT_JSON)
    )
    fun spec(route: AiRoute): AiModelSpec = models.firstOrNull { it.route == route }
        ?: throw AiException(AiErrorCode.MODEL_UNAVAILABLE, "Select a supported text model in AI Reading Tools.")

    /** Repair the two previously shipped IDs, retaining provider consent, cost policy and route priority. */
    fun restoreSavedSettings(settings: AiSettings): AiSettings {
        fun restore(routes: List<AiRoute>) = routes.map { route ->
            if (route.provider != AiProviderId.OPEN_ROUTER) route else when (route.model) {
                "google/gemma-4-31b:free" -> route.copy(model = "google/gemma-4-31b-it:free")
                "google/gemma-4-26b-a4b:free" -> route.copy(model = "google/gemma-4-26b-a4b-it:free")
                else -> route
            }
        }.distinct()
        return settings.copy(
            summaryRoutes = restore(settings.summaryRoutes),
            rewriteRoutes = restore(settings.rewriteRoutes),
            extractRoutes = restore(settings.extractRoutes)
        )
    }

    fun validate(settings: AiSettings) {
        for (routes in listOf(settings.summaryRoutes, settings.rewriteRoutes, settings.extractRoutes)) {
            require(routes.size <= 3 && routes.distinct().size == routes.size) { "Select up to three different routes per action." }
            routes.forEach { spec(it) }
        }
    }
}
