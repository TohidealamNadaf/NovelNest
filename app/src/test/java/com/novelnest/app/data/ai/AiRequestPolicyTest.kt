package com.novelnest.app.data.ai

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiRequestPolicyTest {
    private val prompt = AiPromptFactory.create(AiAction.SUMMARY, "Title", listOf(AiParagraph(0, "A short chapter.")))
    @Test fun localRequestWindowWaitsWithoutBusyLoop() = runTest {
        val policy = AiRequestPolicy { testScheduler.currentTime }
        repeat(6) { policy.admit(AiProviderId.OPEN_ROUTER, prompt, 500) {} }
        var waiting = 0
        val next = async { policy.admit(AiProviderId.OPEN_ROUTER, prompt, 500) { waiting++ } }
        runCurrent()
        assertFalse(next.isCompleted); assertEquals(1, waiting)
        advanceTimeBy(60_000); runCurrent()
        next.await(); assertEquals(1, waiting)
    }
    @Test fun providerCooldownPreventsAutomaticRepeat() = runTest {
        val policy = AiRequestPolicy { testScheduler.currentTime }
        policy.failed(AiProviderId.OPEN_ROUTER, AiException(AiErrorCode.RATE_LIMIT, "Limited", 120))
        expectAi(AiErrorCode.RATE_LIMIT) { policy.admit(AiProviderId.OPEN_ROUTER, prompt, 500) {} }
        // Another provider's independently configured quota remains eligible.
        policy.admit(AiProviderId.GEMINI, prompt, 500) {}
        advanceTimeBy(120_000)
        policy.admit(AiProviderId.OPEN_ROUTER, prompt, 500) {}
    }
    @Test fun excessiveGroqTokenReservationFailsBeforePosting() = runTest {
        expectAi(AiErrorCode.TOO_LARGE) { AiRequestPolicy { testScheduler.currentTime }.admit(AiProviderId.GROQ, prompt, 8_000) {} }
    }
}
