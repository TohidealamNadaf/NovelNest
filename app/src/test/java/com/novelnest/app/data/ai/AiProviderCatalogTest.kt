package com.novelnest.app.data.ai

import org.junit.Assert.*
import org.junit.Test

class AiProviderCatalogTest {
    private fun old(model: String) = AiRoute(AiProviderId.OPEN_ROUTER, model)

    @Test fun savedGemmaRoutesAreRestoredWithoutLosingConsentOrPriority() {
        val first = old("google/gemma-4-31b:free")
        val second = old("google/gemma-4-26b-a4b:free")
        val original = AiSettings(listOf(second, first), listOf(first, second), setOf(AiProviderId.OPEN_ROUTER))
        val restored = AiProviderCatalog.restoreSavedSettings(original)
        assertEquals(listOf("google/gemma-4-26b-a4b-it:free", "google/gemma-4-31b-it:free"), restored.summaryRoutes.map { it.model })
        assertEquals(listOf("google/gemma-4-31b-it:free", "google/gemma-4-26b-a4b-it:free"), restored.rewriteRoutes.map { it.model })
        assertEquals(original.consentedProviders, restored.consentedProviders)
        assertEquals(AiCostPolicy.EXPLICIT_FREE_ONLY, restored.costPolicy)
        AiProviderCatalog.validate(restored)
    }
    @Test fun restoredAliasesDeduplicateWithoutReordering() {
        val original = configured(old("google/gemma-4-31b:free"), freeRoute, alternateFreeRoute)
        val restored = AiProviderCatalog.restoreSavedSettings(original)
        assertEquals(listOf(freeRoute, alternateFreeRoute), restored.summaryRoutes)
        assertEquals(restored, AiProviderCatalog.restoreSavedSettings(restored))
    }
    @Test fun nativeProvidersAndExistingBillingPolicyAreUnchanged() {
        val original = configured(geminiRoute, freeRoute)
        assertEquals(original, AiProviderCatalog.restoreSavedSettings(original))
        val wronglyScoped = AiRoute(AiProviderId.GROQ, "google/gemma-4-31b:free")
        assertEquals(wronglyScoped, AiProviderCatalog.restoreSavedSettings(configured(wronglyScoped)).summaryRoutes.single())
    }
    @Test fun paidVariantsAndUnknownIdsNeverBecomeFreeAliases() {
        for (id in listOf("google/gemma-4-31b-it", "google/gemma-4-31b", "unknown/model:free")) {
            val original = configured(old(id))
            val restored = AiProviderCatalog.restoreSavedSettings(original)
            assertEquals(original, restored)
            try { AiProviderCatalog.validate(restored); fail("Unsupported ID must be rejected") }
            catch (error: AiException) { assertEquals(AiErrorCode.MODEL_UNAVAILABLE, error.code) }
        }
    }
}
