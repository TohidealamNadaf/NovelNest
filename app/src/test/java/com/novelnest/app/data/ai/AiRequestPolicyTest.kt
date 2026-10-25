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
    @Test fun reportedUsageReleasesUnusedReservationWithoutWaitingAMinute() = runTest {
        val policy = AiRequestPolicy { testScheduler.currentTime }
        val first = policy.admit(AiProviderId.GROQ, prompt, 4_000) {}
        policy.completed(first, 150)
        policy.admit(AiProviderId.GROQ, prompt, 4_000) { fail("Unused output reservation must be released") }
        assertEquals(0L, testScheduler.currentTime)
    }
    @Test fun missingOrInvalidUsageDoesNotReleaseAmbiguousConsumption() = runTest {
        val policy = AiRequestPolicy { testScheduler.currentTime }
        val first = policy.admit(AiProviderId.GROQ, prompt, 4_000) {}
        policy.completed(first, -1)
        policy.completed(first, 0)
        expectAi(AiErrorCode.RATE_LIMIT) {
            policy.admit(AiProviderId.GROQ, prompt, 4_000, maxWaitMs = 10_000) { fail("Do not queue a quick summary for a minute") }
        }
        assertEquals(0L, testScheduler.currentTime)
    }
    @Test fun actualUsageCanIncreaseReservationAndKeepsReasoningTokensCharged() = runTest {
        val policy = AiRequestPolicy { testScheduler.currentTime }
        val first = policy.admit(AiProviderId.GROQ, prompt, 500) {}
        policy.completed(first, 7_500)
        expectAi(AiErrorCode.RATE_LIMIT) { policy.admit(AiProviderId.GROQ, prompt, 500, maxWaitMs = 10_000) {} }
    }
    @Test fun nonAsciiTextRetainsConservativeEstimate() {
        val ascii = AiPrompt("", "a".repeat(300), org.json.JSONObject())
        val cjk = ascii.copy(data = "界".repeat(100))
        assertEquals(200, AiRequestPolicy.estimatedTokens(cjk, 100) - AiRequestPolicy.estimatedTokens(ascii, 100))
    }
    @Test fun shortQuotaWaitReportsTheDelayAndResumesWithoutPolling() = runTest {
        val policy = AiRequestPolicy { testScheduler.currentTime }
        repeat(6) { policy.admit(AiProviderId.OPEN_ROUTER, prompt, 500) {} }
        advanceTimeBy(55_000)
        val waits = mutableListOf<Long>()
        policy.admit(AiProviderId.OPEN_ROUTER, prompt, 500, maxWaitMs = 10_000) { waits.add(it) }
        assertEquals(listOf(5_000L), waits)
        assertEquals(60_000L, testScheduler.currentTime)
    }
    @Test fun cancellingQuotaWaitDoesNotSpendAnotherRequestSlot() = runTest {
        val policy = AiRequestPolicy { testScheduler.currentTime }
        repeat(6) { policy.admit(AiProviderId.OPEN_ROUTER, prompt, 500) {} }
        val next = async { policy.admit(AiProviderId.OPEN_ROUTER, prompt, 500) {} }
        runCurrent()
        next.cancel(); next.join()
        advanceTimeBy(60_000)
        repeat(6) { policy.admit(AiProviderId.OPEN_ROUTER, prompt, 500) { fail("A cancelled wait must not reserve a request") } }
    }

    @Test fun repeatedCooldownChecksCannotExtendTheOriginalExpiry() = runTest {
        val policy = AiRequestPolicy { testScheduler.currentTime }
        policy.failed(AiProviderId.GROQ, AiException(AiErrorCode.RATE_LIMIT, "Remote quota", 60))
        repeat(30) {
            advanceTimeBy(1_001)
            expectAi(AiErrorCode.RATE_LIMIT) {
                try { policy.admit(AiProviderId.GROQ, prompt, 500) {} }
                catch (error: AiException) { policy.failed(AiProviderId.GROQ, error); throw error }
            }
        }
        advanceTimeBy(60_000 - testScheduler.currentTime)
        policy.admit(AiProviderId.GROQ, prompt, 500) { fail("The original cooldown must expire at 60 seconds") }
    }

    @Test fun groqModelBudgetsAreIndependentWithoutRotatingCredentials() = runTest {
        val models = AiProviderCatalog.models.filter { it.route.provider == AiProviderId.GROQ }
        val policy = AiRequestPolicy { testScheduler.currentTime }
        policy.admit(AiProviderId.GROQ, prompt, 4_000, model = models[0].route.model) {}
        policy.admit(AiProviderId.GROQ, prompt, 4_000, model = models[1].route.model) { fail("Another model's budget should not be charged") }
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test fun reportedDailyAllowanceStillBlocksAfterOneMinute() = runTest {
        val policy = AiRequestPolicy { testScheduler.currentTime }
        val first = policy.admit(AiProviderId.GROQ, prompt, 500) {}
        policy.observed(first, AiQuotaSnapshot(8_000, 8_000, 60_000, 0, 7_200_000))
        advanceTimeBy(60_000)
        try {
            policy.admit(AiProviderId.GROQ, prompt, 500, maxWaitMs = 10_000) {}; fail("Expected daily allowance error")
        } catch (error: AiException) {
            assertEquals(AiLimitKind.DAILY_REQUESTS, error.rateLimit?.kind)
            assertEquals(7_140L, error.retryAfterSeconds)
        }
        advanceTimeBy(7_140_000)
        policy.admit(AiProviderId.GROQ, prompt, 500) { fail("Expired daily snapshot must not block indefinitely") }
    }

    @Test fun providerRemainingTokensReplaceStaleLocalEstimatesWithoutDoubleCharging() = runTest {
        val policy = AiRequestPolicy { testScheduler.currentTime }
        val first = policy.admit(AiProviderId.GROQ, prompt, 4_000) {}
        policy.observed(first, AiQuotaSnapshot(8_000, 7_000, 60_000, 999, 86_400_000))
        policy.admit(AiProviderId.GROQ, prompt, 4_000) { fail("Reported remaining quota already accounts for the first request") }
        expectAi(AiErrorCode.RATE_LIMIT) { policy.admit(AiProviderId.GROQ, prompt, 4_000, maxWaitMs = 10_000) {} }
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test fun ambiguousRequestStillConsumesDailyReservationAfterMinuteWindowExpires() = runTest {
        val policy = AiRequestPolicy { testScheduler.currentTime }
        val first = policy.admit(AiProviderId.GROQ, prompt, 500) {}
        policy.observed(first, AiQuotaSnapshot(8_000, 8_000, 60_000, 1, 7_200_000))
        policy.admit(AiProviderId.GROQ, prompt, 500) {} // No response: possibly accepted remotely.
        advanceTimeBy(61_000)
        try {
            policy.admit(AiProviderId.GROQ, prompt, 500, maxWaitMs = 10_000) {}; fail("Ambiguous daily consumption must not expire with TPM")
        } catch (error: AiException) { assertEquals(AiLimitKind.DAILY_REQUESTS, error.rateLimit?.kind) }
    }

    @Test fun expiredRemoteRetryIsNotBlockedByItsOldDepletedQuotaSnapshot() = runTest {
        val policy = AiRequestPolicy { testScheduler.currentTime }
        val first = policy.admit(AiProviderId.GROQ, prompt, 500) {}
        policy.observed(first, AiQuotaSnapshot(8_000, 0, 60_000, 0, 7_200_000))
        policy.failed(AiProviderId.GROQ, AiException(AiErrorCode.RATE_LIMIT, "Daily requests", 60,
            AiRateLimitInfo(AiLimitKind.DAILY_REQUESTS)))
        advanceTimeBy(60_000)
        policy.admit(AiProviderId.GROQ, prompt, 500) { fail("Honor the explicit retry instead of reapplying a stale two-hour snapshot") }
    }

    @Test fun credentialReplacementRetiresOldAccountQuotaAndLateFeedback() = runTest {
        val policy = AiRequestPolicy { testScheduler.currentTime }
        policy.bindCredential(AiProviderId.GROQ, aiHash("old-offline-key"))
        val old = policy.admit(AiProviderId.GROQ, prompt, 500) {}
        policy.failed(AiProviderId.GROQ, AiException(AiErrorCode.RATE_LIMIT, "Old account", 7_200))
        policy.bindCredential(AiProviderId.GROQ, aiHash("new-offline-key"))
        policy.observed(old, AiQuotaSnapshot(8_000, 0, 60_000, 0, 7_200_000))
        policy.completed(old, 8_000)
        policy.admit(AiProviderId.GROQ, prompt, 500) { fail("Old key's quota must not contaminate the replacement") }
    }

    @Test fun rebindingSameCredentialDoesNotClearCooldowns() = runTest {
        val policy = AiRequestPolicy { testScheduler.currentTime }
        val fingerprint = aiHash("same-offline-key")
        policy.bindCredential(AiProviderId.GROQ, fingerprint)
        policy.failed(AiProviderId.GROQ, AiException(AiErrorCode.RATE_LIMIT, "Remote quota", 60))
        policy.bindCredential(AiProviderId.GROQ, fingerprint)
        expectAi(AiErrorCode.RATE_LIMIT) { policy.admit(AiProviderId.GROQ, prompt, 500) {} }
    }

    @Test fun cooldownExpiresWhenElapsedClockAdvancesWithoutCoroutineTimerTicks() = runTest {
        var elapsed = 0L
        val policy = AiRequestPolicy { elapsed }
        policy.failed(AiProviderId.GROQ, AiException(AiErrorCode.RATE_LIMIT, "Minute quota", 60))
        elapsed = 60_000L // Android elapsedRealtime includes device sleep; Handler timers need not run.
        policy.admit(AiProviderId.GROQ, prompt, 500) { fail("Sleeping must not freeze cooldown expiry") }
        assertEquals(0L, testScheduler.currentTime)
    }
}
