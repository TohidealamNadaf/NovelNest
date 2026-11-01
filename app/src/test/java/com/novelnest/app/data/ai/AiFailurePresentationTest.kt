package com.novelnest.app.data.ai

import com.novelnest.app.feature.reader.aiErrorHelpText
import com.novelnest.app.feature.reader.retryAiFailure
import org.junit.Assert.*
import org.junit.Test

class AiFailurePresentationTest {
    @Test fun failureRetryDispatchesTheSameActionIncludingExtraction() {
        for (action in AiAction.entries) {
            val calls = mutableListOf<AiAction>()
            retryAiFailure(action, { calls.add(AiAction.SUMMARY) }, { calls.add(AiAction.REWRITE) }, { calls.add(AiAction.EXTRACT) })
            assertEquals(listOf(action), calls)
        }
    }

    @Test fun missingFailedActionCannotAccidentallyRequestSummary() {
        retryAiFailure(null, { fail("Unexpected Summary") }, { fail("Unexpected Rewrite") }, { fail("Unexpected Extract") })
    }

    @Test fun summaryRetainsTheSpecificWholeChapterBudgetExplanation() {
        val message = "This whole chapter exceeds Groq's quick-summary token budget. Enable another provider with a larger allowance in AI Reading Tools."
        val text = aiErrorHelpText(AiFailure(AiErrorCode.TOO_LARGE, message), AiAction.SUMMARY)
        assertEquals(message, text)
        assertFalse(text.contains("rewrite", ignoreCase = true))
    }

    @Test fun emptySizeErrorsDescribeTheActionThatActuallyFailed() {
        for ((action, word) in listOf(AiAction.SUMMARY to "summary", AiAction.REWRITE to "rewrite", AiAction.EXTRACT to "term-extraction")) {
            val text = aiErrorHelpText(AiFailure(AiErrorCode.TOO_LARGE, ""), action)
            assertTrue(text.contains(word))
            if (action != AiAction.REWRITE) assertFalse(text.contains("rewrite", ignoreCase = true))
        }
    }

    @Test fun readerPreservesDailyVersusMinuteQuotaDetailsAndRetryHints() {
        for (detail in listOf("Groq's per-minute token allowance was reached.", "Groq's daily token allowance was reached.")) {
            val text = aiErrorHelpText(AiFailure(AiErrorCode.RATE_LIMIT, detail, 120), AiAction.SUMMARY)
            assertTrue(text.startsWith(detail)); assertTrue(text.contains("120"))
        }
    }

    @Test fun providerRetryHintIsNotDuplicated() {
        val detail = "Groq's per-minute token allowance was reached. Provider retry delay: 9 seconds."
        assertEquals(detail, aiErrorHelpText(AiFailure(AiErrorCode.RATE_LIMIT, detail, 9), AiAction.EXTRACT))
    }

    @Test fun missingActionNeverDefaultsToARewriteWarning() {
        val text = aiErrorHelpText(AiFailure(AiErrorCode.TOO_LARGE, ""), null)
        assertTrue(text.contains("AI request budget")); assertFalse(text.contains("rewrite", ignoreCase = true))
    }

    @Test fun modelUnavailableFallbackDoesNotClaimQuotaIsExhausted() {
        val text = aiErrorHelpText(AiFailure(AiErrorCode.MODEL_UNAVAILABLE, ""), AiAction.SUMMARY)
        assertFalse(text.contains("quota", ignoreCase = true))
    }
}
