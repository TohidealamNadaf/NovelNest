package com.novelnest.app.data.ai

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiValidationAndCacheTest {
    @get:Rule val temp = TemporaryFolder()
    private val input = chapter()
    private val refs = input.paragraphs.mapIndexed { index, text -> AiParagraph(index, text) }
    private fun rewrite(vararg values: Pair<Int, String>) = JSONObject().put("paragraphs", JSONArray().apply {
        values.forEach { put(JSONObject().put("id", it.first).put("text", it.second)) }
    }).toString()
    private fun parseRewrite(raw: String) = AiResponseValidator.parse(raw, AiAction.REWRITE, input.fingerprint(), freeRoute, refs)
    private fun invalid(raw: String) {
        try { parseRewrite(raw); fail("Expected invalid output") }
        catch (error: AiException) { assertEquals(AiErrorCode.INVALID_OUTPUT, error.code) }
    }

    @Test fun validRewritePreservesParagraphIdentity() {
        val result = parseRewrite(rewrite(0 to "Mira discovered 12 coins.", 1 to "She went back home."))
        assertEquals(2, result.rewrittenParagraphs.size)
        assertEquals(input.paragraphs[0], "Mira found 12 coins.")
    }
    @Test fun droppedParagraphIsRejected() { invalid(rewrite(0 to "Mira found 12 coins.")) }
    @Test fun duplicateParagraphIsRejected() { invalid(rewrite(0 to "Mira found 12 coins.", 0 to "She returned home.")) }
    @Test fun reorderedParagraphsAreRejected() { invalid(rewrite(1 to "She returned home.", 0 to "Mira found 12 coins.")) }
    @Test fun changedNumbersAreRejected() { invalid(rewrite(0 to "Mira found 20 coins.", 1 to "She returned home.")) }
    @Test fun fractionalParagraphIdIsRejected() {
        val json = JSONObject(rewrite(0 to "Mira found 12 coins.", 1 to "She returned home."))
        json.getJSONArray("paragraphs").getJSONObject(0).put("id", 0.5)
        invalid(json.toString())
    }
    @Test fun inventedEvidenceIsRejected() {
        val json = JSONObject(validResponse(AiPromptFactory.create(AiAction.SUMMARY, input.title, refs)))
        json.put("terms", JSONArray().put(JSONObject().put("name", "Mira").put("kind", "CHARACTER")
            .put("meaning", "A person in this chapter").put("paragraphId", 0).put("evidence", "Mira became queen")))
        try { AiResponseValidator.parse(json.toString(), AiAction.SUMMARY, input.fingerprint(), freeRoute, refs); fail("Expected failure") }
        catch (error: AiException) { assertEquals(AiErrorCode.INVALID_OUTPUT, error.code) }
    }
    @Test fun sourceBackedTermIsAccepted() {
        val json = JSONObject(validResponse(AiPromptFactory.create(AiAction.SUMMARY, input.title, refs)))
        json.put("terms", JSONArray().put(JSONObject().put("name", "Mira").put("kind", "CHARACTER")
            .put("meaning", "Finds coins").put("paragraphId", 0).put("evidence", "Mira found 12 coins.")))
        assertEquals("Mira", AiResponseValidator.parse(json.toString(), AiAction.SUMMARY, input.fingerprint(), freeRoute, refs).summary!!.terms.single().name)
    }
    @Test fun fingerprintIncludesBoundariesAndSourceIdentity() {
        assertNotEquals(input.copy(paragraphs = listOf("ab", "c")).fingerprint(), input.copy(paragraphs = listOf("a", "bc")).fingerprint())
        assertNotEquals(input.cacheKey(AiAction.SUMMARY), input.copy(source = "FREE_WEB_NOVEL").cacheKey(AiAction.SUMMARY))
        assertNotEquals(input.cacheKey(AiAction.SUMMARY), input.cacheKey(AiAction.REWRITE))
    }
    @Test fun cacheRoundTripAndChangedSourceMiss() = runBlocking {
        val cache = AiResultCache(temp.newFolder())
        val result = parseRewrite(rewrite(0 to "Mira found 12 coins.", 1 to "She returned home."))
        val key = input.cacheKey(AiAction.REWRITE)
        cache.write(key, result, cache.generation)
        assertEquals(result, cache.read(key, input, AiAction.REWRITE))
        assertNull(cache.read(input.copy(paragraphs = listOf("Changed.")).cacheKey(AiAction.REWRITE), input, AiAction.REWRITE))
    }
    @Test fun clearPreventsOldGenerationFromRepopulatingCache() = runBlocking {
        val directory = temp.newFolder()
        val cache = AiResultCache(directory)
        val epoch = cache.generation
        val result = parseRewrite(rewrite(0 to "Mira found 12 coins.", 1 to "She returned home."))
        val key = input.cacheKey(AiAction.REWRITE)
        cache.write(key, result, epoch)
        assertTrue(cache.clear())
        cache.write(key, result, epoch)
        assertNull(cache.read(key, input, AiAction.REWRITE))
        assertEquals(0, directory.listFiles()!!.size)
    }
    @Test fun corruptCacheIsDisposableAndNeverReplacesSource() = runBlocking {
        val directory = temp.newFolder()
        val cache = AiResultCache(directory)
        val key = input.cacheKey(AiAction.SUMMARY)
        java.io.File(directory, "$key.json").writeText("broken")
        assertNull(cache.read(key, input, AiAction.SUMMARY))
        assertFalse(java.io.File(directory, "$key.json").exists())
    }
    @Test fun unsupportedOrPaidModelCannotEnterSettings() {
        try { AiProviderCatalog.validate(configured(AiRoute(AiProviderId.OPEN_ROUTER, "paid/model"))); fail("Expected rejection") }
        catch (error: AiException) { assertEquals(AiErrorCode.MODEL_UNAVAILABLE, error.code) }
    }
}
