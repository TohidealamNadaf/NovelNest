package com.novelnest.app.data.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiRouteSelectionTest {
    @get:Rule val temp = TemporaryFolder()
    private val superRoute = AiRoute(AiProviderId.NVIDIA, "nvidia/nemotron-3-super-120b-a12b")
    private val groq120 = AiRoute(AiProviderId.GROQ, "openai/gpt-oss-120b")
    private val groq20 = AiRoute(AiProviderId.GROQ, "openai/gpt-oss-20b")
    private val userOrder = listOf(superRoute, groq120, groq20)
    private fun settings(summary: List<AiRoute> = userOrder, rewrite: List<AiRoute> = userOrder,
        extract: List<AiRoute> = userOrder) = AiSettings(summary, rewrite,
        setOf(AiProviderId.NVIDIA, AiProviderId.GROQ), AiCostPolicy.FREE_ACCOUNT_PROVIDERS, extract)
    private fun input(id: String = "one") = chapter(id).copy(extractionTarget = "characters")
    private fun reply(prompt: AiPrompt): String = if (prompt.schema.getJSONObject("properties").has("terms") &&
        !prompt.schema.getJSONObject("properties").has("summary")) "{\"terms\":[]}" else validResponse(prompt)
    private fun providers(trace: MutableList<AiRoute>, reject: (AiRoute) -> Boolean = { false }) =
        listOf(AiProviderId.NVIDIA, AiProviderId.GROQ).map { id -> FakeProvider(id).apply {
            respond = { route, prompt ->
                trace.add(route)
                if (reject(route)) throw AiException(AiErrorCode.INVALID_OUTPUT, "Synthetic invalid result")
                reply(prompt)
            }
        } }

    @Test fun eachActionUsesItsOwnSavedPrimaryRatherThanAnotherActionsList() = runBlocking {
        val selected = settings(summary = listOf(superRoute), rewrite = listOf(groq20), extract = listOf(groq120))
        for ((action, expected) in listOf(AiAction.SUMMARY to superRoute, AiAction.REWRITE to groq20, AiAction.EXTRACT to groq120)) {
            val trace = mutableListOf<AiRoute>()
            val result = ChapterAiRepository(FakeConfiguration(selected), FakeCache(), providers(trace)).generate(input(), action)
            assertEquals(listOf(expected), trace)
            assertEquals(expected, result.route)
        }
    }

    @Test fun allActionsTryTheUsersExactThreeRouteOrderWhenEarlierOutputsFailValidation() = runBlocking {
        for (action in AiAction.entries) {
            val trace = mutableListOf<AiRoute>()
            val result = ChapterAiRepository(FakeConfiguration(settings()), FakeCache(),
                providers(trace) { it != groq20 }).generate(input(), action)
            assertEquals(userOrder, trace)
            assertEquals(groq20, result.route)
        }
    }

    @Test fun aSuccessfulPrimaryStopsFallbackForEveryAction() = runBlocking {
        for (action in AiAction.entries) {
            val trace = mutableListOf<AiRoute>()
            val result = ChapterAiRepository(FakeConfiguration(settings()), FakeCache(), providers(trace)).generate(input(), action)
            assertEquals(listOf(superRoute), trace)
            assertEquals(superRoute, result.route)
        }
    }

    @Test fun aReorderedListTakesEffectOnTheNextUncachedGenerationForEveryAction() = runBlocking {
        for (action in AiAction.entries) {
            val trace = mutableListOf<AiRoute>()
            val configuration = FakeConfiguration(settings())
            val repository = ChapterAiRepository(configuration, AiResultCache(temp.newFolder()), providers(trace))
            val first = repository.generate(input(), action)
            configuration.value = settings(summary = userOrder.reversed(), rewrite = userOrder.reversed(), extract = userOrder.reversed())
            // Existing validated results are reused without spending requests on a route change.
            assertEquals(first, repository.generate(input(), action))
            assertEquals(listOf(superRoute), trace)
            assertEquals(groq20, repository.generate(input("two"), action).route)
            assertEquals(listOf(superRoute, groq20), trace)
        }
    }
}
