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
    @Test fun completeJsonWrappersAreAcceptedWithoutChangingTheValidatedContent() {
        for (action in AiAction.entries) {
            val prompt = AiPromptFactory.create(action, input.title, refs, "characters")
            val raw = if (action == AiAction.EXTRACT) "{\"terms\":[]}" else validResponse(prompt)
            val expected = AiResponseValidator.parse(raw, action, input.fingerprint(), freeRoute, refs)
            for (wrapped in listOf("\uFEFF$raw", "```json\n$raw\n```", "```JSON\r\n$raw\r\n```")) {
                val actual = AiResponseValidator.parse(wrapped, action, input.fingerprint(), freeRoute, refs)
                assertEquals(expected.summary, actual.summary)
                assertEquals(expected.rewrittenParagraphs, actual.rewrittenParagraphs)
                assertEquals(expected.extractedTerms, actual.extractedTerms)
            }
        }
    }
    @Test fun proseReasoningTruncationAndTrailingObjectsAreNeverRepairedIntoASummary() = runBlocking {
        val raw = validResponse(AiPromptFactory.create(AiAction.SUMMARY, input.title, refs))
        for (invalid in listOf("Here is the result: $raw", "<think>internal text</think>$raw", raw.dropLast(1),
            "$raw\n{}", "$raw\nextra text", "```json\n$raw")) {
            expectAi(AiErrorCode.INVALID_OUTPUT) {
                AiResponseValidator.parse(invalid, AiAction.SUMMARY, input.fingerprint(), freeRoute, refs)
            }
        }
    }
    @Test fun summaryEvidenceFailuresAreSpecificButNeverEchoModelText() {
        val json = JSONObject(validResponse(AiPromptFactory.create(AiAction.SUMMARY, input.title, refs)))
        json.put("terms", JSONArray().put(JSONObject().put("name", "Private invented name").put("kind", "CHARACTER")
            .put("meaning", "Private generated meaning").put("paragraphId", 0).put("evidence", "Private invented evidence")))
        try {
            AiResponseValidator.parse("```json\n$json\n```", AiAction.SUMMARY, input.fingerprint(), freeRoute, refs)
            fail("Invented evidence must still fail")
        } catch (error: AiException) {
            assertEquals(AiErrorCode.INVALID_OUTPUT, error.code)
            assertTrue(error.message.orEmpty().contains("Summary validation failed"))
            assertTrue(error.message.orEmpty().contains("does not exactly match"))
            assertFalse(error.message.orEmpty().contains("Private"))
        }
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

    @Test fun firstReadRecoversAbandonedTemporaryAndOversizedEntriesOnly() = runBlocking {
        val directory = temp.newFolder()
        val abandoned = java.io.File(directory, "${aiHash("abandoned")}.tmp").apply { writeText("interrupted") }
        val oversized = java.io.File(directory, "${input.cacheKey(AiAction.SUMMARY)}.json")
        java.io.RandomAccessFile(oversized, "rw").use { it.setLength(384 * 1024L + 1) }
        val foreign = java.io.File(directory, "notes.tmp").apply { writeText("not an AI cache entry") }
        assertNull(AiResultCache(directory).read(input.cacheKey(AiAction.SUMMARY), input, AiAction.SUMMARY))
        assertFalse(abandoned.exists()); assertFalse(oversized.exists()); assertTrue(foreign.exists())
    }

    @Test fun startupEnforcesExistingEntryCountWithoutSuccessfulGeneration() = runBlocking {
        val directory = temp.newFolder()
        repeat(100) { java.io.File(directory, "${aiHash("old-$it")}.json").writeText("{}") }
        assertNull(AiResultCache(directory).read(input.cacheKey(AiAction.SUMMARY), input, AiAction.SUMMARY))
        assertEquals(96, directory.listFiles()!!.size)
    }

    @Test fun startupEnforcesExistingDiskByteLimit() = runBlocking {
        val directory = temp.newFolder()
        repeat(46) { index ->
            java.io.RandomAccessFile(java.io.File(directory, "${aiHash("large-$index")}.json"), "rw").use { it.setLength(380 * 1024L) }
        }
        assertNull(AiResultCache(directory).read(input.cacheKey(AiAction.SUMMARY), input, AiAction.SUMMARY))
        assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 16 * 1024 * 1024L)
    }

    @Test fun memoryCacheEvictsOldEntriesAndRejectsMismatchedInputKeys() = runBlocking {
        val directory = temp.newFolder()
        val cache = AiResultCache(directory)
        val chapters = List(5) { chapter("chapter-$it") }
        val result = AiResponseValidator.parse(validResponse(AiPromptFactory.create(AiAction.SUMMARY, input.title, refs)),
            AiAction.SUMMARY, input.fingerprint(), freeRoute, refs)
        chapters.forEach { cache.write(it.cacheKey(AiAction.SUMMARY), result, cache.generation) }
        directory.listFiles()!!.forEach { assertTrue(it.delete()) }
        assertNull(cache.read(chapters[0].cacheKey(AiAction.SUMMARY), chapters[0], AiAction.SUMMARY))
        assertNotNull(cache.read(chapters[4].cacheKey(AiAction.SUMMARY), chapters[4], AiAction.SUMMARY))
        assertNull(cache.read(chapters[4].cacheKey(AiAction.SUMMARY), chapters[0], AiAction.SUMMARY))
    }
}
