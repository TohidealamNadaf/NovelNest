package com.novelnest.app.data.ai

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiExtractionTopicTest {
    @get:Rule val temp = TemporaryFolder()
    private val input = chapter().copy(extractionTarget = "cultivation levels", paragraphs = listOf(
        "Mira entered Iron Body. Iron Body is the first cultivation level.",
        "Rank Two beasts can break stone. Silver Fen borders the Western Sea."
    ))
    private val refs = input.paragraphs.mapIndexed(::AiParagraph)
    private fun result(name: String, kind: String, evidence: String, paragraph: Int = 0) =
        JSONObject().put("terms", JSONArray().put(JSONObject().put("name", name).put("kind", kind)
            .put("meaning", "A fact explicitly described in this chapter.").put("paragraphId", paragraph)
            .put("evidence", evidence))).toString()
    private val level get() = result("Iron Body", "CONCEPT", "Iron Body is the first cultivation level.")
    private val person get() = result("Mira", "CHARACTER", "Mira entered Iron Body.")
    private fun parse(raw: String, target: String = "cultivation levels") =
        AiResponseValidator.parse(raw, AiAction.EXTRACT, input.fingerprint(), freeRoute, refs, target)

    @Test fun sourceBackedButWrongTopicResultsAreRejected() = runBlocking {
        for (target in listOf("cultivation level", "cultivation realms", "monster levels", "MONSTERS  LEVEL", "beast ranks", "world building")) {
            try {
                parse(person, target)
                fail("A character is not a $target result")
            } catch (error: AiException) {
                assertEquals(AiErrorCode.INVALID_OUTPUT, error.code)
                assertTrue(error.message.orEmpty().contains("requested extraction topic"))
                assertFalse(error.message.orEmpty().contains("Mira"))
            }
        }
        assertEquals("Iron Body", parse(level).extractedTerms.single().name)
        assertEquals("Rank Two", parse(result("Rank Two", "CONCEPT", "Rank Two beasts can break stone.", 1),
            "monster levels").extractedTerms.single().name)
        assertEquals("Silver Fen", parse(result("Silver Fen", "LOCATION", "Silver Fen borders the Western Sea.", 1),
            "world building").extractedTerms.single().name)
    }

    @Test fun mixedAndCustomTopicsAreNotNarrowedByASubstringMatch() {
        for (target in listOf("characters and cultivation levels", "cultivation levels used by named characters",
            "politics of Silver Fen", "修炼境界与人物")) {
            assertEquals(AiExtractionPolicy.allKinds, AiExtractionPolicy.forTarget(target).kinds)
        }
        assertEquals("Mira", parse(person, "characters and cultivation levels").extractedTerms.single().name)
    }

    @Test fun evidenceMustContainTheSubjectNotJustAppearInTheSameParagraph() = runBlocking {
        expectAi(AiErrorCode.INVALID_OUTPUT) {
            parse(result("Mira", "CHARACTER", "Iron Body is the first cultivation level."), "characters")
        }
        assertTrue(parse("{\"terms\":[]}").extractedTerms.isEmpty())
    }

    @Test fun irrelevantProviderResultFallsBackBeforeAnythingIsCached() = runBlocking {
        val provider = FakeProvider(AiProviderId.OPEN_ROUTER).apply { respond = { route, _ ->
            if (route == freeRoute) person else level
        } }
        val config = FakeConfiguration(configured(freeRoute, alternateFreeRoute)
            .copy(extractRoutes = listOf(freeRoute, alternateFreeRoute)))
        val cache = FakeCache()
        val actual = ChapterAiRepository(config, cache, listOf(provider)).generate(input, AiAction.EXTRACT)
        assertEquals(alternateFreeRoute, actual.route)
        assertEquals("Iron Body", actual.extractedTerms.single().name)
        assertEquals(2, provider.calls.get())
        assertEquals(1, cache.writes)
    }

    @Test fun extractionCacheVersionChangesWithoutDiscardingSummaryOrRewrite() = runBlocking {
        val cache = AiResultCache(temp.newFolder())
        fun oldKey(action: AiAction): String {
            val fields = listOf(input.source, input.novelId, input.chapterId, input.fingerprint(), action.name, AiPromptFactory.VERSION)
            return aiHash((if (action == AiAction.EXTRACT) fields + input.extractionTarget else fields).joinToString("\u0000"))
        }
        assertNotEquals(oldKey(AiAction.EXTRACT), input.cacheKey(AiAction.EXTRACT))
        for (action in listOf(AiAction.SUMMARY, AiAction.REWRITE)) assertEquals(oldKey(action), input.cacheKey(action))
        cache.write(oldKey(AiAction.EXTRACT), parse(person, "characters"), cache.generation)
        assertNull(cache.read(input.cacheKey(AiAction.EXTRACT), input, AiAction.EXTRACT))
        cache.write(input.cacheKey(AiAction.EXTRACT), parse(level), cache.generation)
        assertEquals("Iron Body", cache.read(input.cacheKey(AiAction.EXTRACT), input, AiAction.EXTRACT)!!.extractedTerms.single().name)
    }

    @Test fun diskCacheRechecksTopicEvenWhenOutputAndFingerprintAreOtherwiseValid() = runBlocking {
        val directory = temp.newFolder()
        val writer = AiResultCache(directory)
        // Simulate a damaged/miswritten entry with correct current key but irrelevant output.
        writer.write(input.cacheKey(AiAction.EXTRACT), parse(person, "characters"), writer.generation)
        assertNull(AiResultCache(directory).read(input.cacheKey(AiAction.EXTRACT), input, AiAction.EXTRACT))
    }

    @Test fun clearedCollectionCannotReuseItsOldGeneratedResults() = runBlocking {
        val cache = AiResultCache(temp.newFolder())
        val old = input.copy(extractionCacheScope = "previous-collection")
        val cleared = input.copy(extractionCacheScope = "cleared-collection")
        cache.write(old.cacheKey(AiAction.EXTRACT), parse(level), cache.generation)
        assertNotNull(cache.read(old.cacheKey(AiAction.EXTRACT), old, AiAction.EXTRACT))
        assertNull(cache.read(cleared.cacheKey(AiAction.EXTRACT), cleared, AiAction.EXTRACT))
        assertEquals(old.fingerprint(), cleared.fingerprint())
        for (action in listOf(AiAction.SUMMARY, AiAction.REWRITE)) assertEquals(old.cacheKey(action), cleared.cacheKey(action))
        val prompt = AiPromptFactory.create(AiAction.EXTRACT, cleared.title, refs, cleared.extractionTarget)
        assertFalse(prompt.data.contains(cleared.extractionCacheScope))
    }
}
