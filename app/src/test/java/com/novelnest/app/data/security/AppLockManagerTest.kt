package com.novelnest.app.data.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AppLockManagerTest {
    private class MemoryStorage : AppLockStorage {
        var value: AppLockRecord? = null
        var failReads = false
        var failWrites = false
        var afterWrite: (() -> Unit)? = null
        override fun read(): AppLockRecord? { check(!failReads); return value }
        override fun write(record: AppLockRecord) {
            check(!failWrites) { "Storage unavailable" }
            value = record
            afterWrite?.invoke()
        }
        override fun remove() { value = null }
    }
    private fun manager(storage: MemoryStorage, clock: () -> Long = { 10_000 }) =
        AppLockManager(storage, clock, Dispatchers.Unconfined)

    @Test fun freshInstallKeepsNormalAccessWithoutAnEnabledLock() = runBlocking {
        val manager = manager(MemoryStorage())
        assertTrue(manager.state.value.blocked)
        manager.initialize()
        assertFalse(manager.state.value.blocked)
        assertFalse(manager.state.value.enabled)
        assertEquals(AppAccess.FULL, manager.state.value.access)
    }

    @Test fun bothPinsSelectTheirOwnModeAndChangingModeRequiresANewRootTask() = runBlocking {
        val store = MemoryStorage()
        val manager = manager(store)
        manager.initialize()
        manager.configure("", "0012", "0098", false)
        manager.lock()
        assertEquals(AppUnlock(AppAccess.EPUB_ONLY, true), manager.unlock("0098"))
        manager.lock()
        assertEquals(AppUnlock(AppAccess.FULL, true), manager.unlock("0012"))
        manager.lock()
        assertEquals(AppUnlock(AppAccess.FULL, false), manager.unlock("0012"))
    }

    @Test fun processRestartNeverRestoresAnUnlockedSession() = runBlocking {
        val store = MemoryStorage()
        val first = manager(store)
        first.initialize()
        first.configure("", "1234", "4321", false)
        val restored = manager(store)
        restored.initialize()
        assertTrue(restored.state.value.blocked)
        assertNull(restored.state.value.access)
        assertEquals(AppUnlock(AppAccess.FULL, true), restored.unlock("1234"))
    }

    @Test fun pendingRootResetSurvivesUiRecreationButNotANewLock() = runBlocking {
        val store = MemoryStorage()
        val manager = manager(store)
        manager.initialize()
        manager.configure("", "1234", "4321", false)
        manager.lock()
        manager.unlock("4321")
        assertTrue(manager.state.value.resetRootAfterUnlock)
        manager.acknowledgeRootReset(manager.state.value.revision)
        assertFalse(manager.state.value.resetRootAfterUnlock)
        manager.lock()
        manager.unlock("1234")
        assertTrue(manager.state.value.resetRootAfterUnlock)
        manager.lock()
        assertFalse(manager.state.value.resetRootAfterUnlock)
        assertNull(manager.state.value.access)
    }

    @Test fun epubAccessCannotChangeCredentialsOrDisableLock() = runBlocking {
        val store = MemoryStorage()
        val manager = manager(store)
        manager.initialize()
        manager.configure("", "1234", "4321", false)
        manager.lock()
        manager.unlock("4321")
        val saved = store.value
        assertTrue(runCatching { manager.configure("4321", "1111", "2222", false) }.isFailure)
        assertTrue(runCatching { manager.disable("4321") }.isFailure)
        assertSame(saved, store.value)
        assertEquals(AppAccess.EPUB_ONLY, manager.state.value.access)
    }

    @Test fun fullSessionStillRequiresNormalPinForAdministration() = runBlocking {
        val store = MemoryStorage()
        val manager = manager(store)
        manager.initialize()
        manager.configure("", "1234", "4321", false)
        assertTrue(runCatching { manager.disable("4321") }.isFailure)
        assertTrue(store.value != null)
        manager.disable("1234")
        assertNull(store.value)
        assertFalse(manager.state.value.enabled)
        assertEquals(AppAccess.FULL, manager.state.value.access)
    }

    @Test fun duplicateOrIncompletePinsCannotReplaceWorkingCredentials() = runBlocking {
        val store = MemoryStorage()
        val manager = manager(store)
        manager.initialize()
        manager.configure("", "1234", "4321", false)
        val saved = store.value
        assertTrue(runCatching { manager.configure("1234", "1111", "1111", false) }.isFailure)
        assertTrue(runCatching { manager.configure("1234", "1111", "", false) }.isFailure)
        assertSame(saved, store.value)
        manager.lock()
        assertEquals(AppAccess.FULL, manager.unlock("1234").access)
    }

    @Test fun cooldownPersistsAcrossRestartAndExpiresWithoutExtraHashingRequests() = runBlocking {
        val store = MemoryStorage()
        var now = 10_000L
        val manager = manager(store) { now }
        manager.initialize()
        manager.configure("", "1234", "4321", false)
        manager.lock()
        repeat(5) { assertTrue(runCatching { manager.unlock("0000") }.isFailure) }
        assertEquals(40_000L, store.value!!.retryAtMillis)
        val restored = manager(store) { now }
        restored.initialize()
        assertTrue(runCatching { restored.unlock("1234") }.isFailure)
        assertEquals(5, store.value!!.failures)
        now = 40_001L
        assertEquals(AppAccess.FULL, restored.unlock("1234").access)
        assertEquals(0, store.value!!.failures)
        assertEquals(0L, store.value!!.retryAtMillis)
    }

    @Test fun epubPinCannotResetNormalPinBruteForceProtection() = runBlocking {
        val store = MemoryStorage()
        val manager = manager(store)
        manager.initialize()
        manager.configure("", "1234", "4321", false)
        manager.lock()
        repeat(4) { assertTrue(runCatching { manager.unlock("0000") }.isFailure) }
        manager.unlock("4321")
        assertEquals(4, store.value!!.failures)
        manager.lock()
        assertTrue(runCatching { manager.unlock("0000") }.isFailure)
        assertEquals(5, store.value!!.failures)
        assertTrue(runCatching { manager.unlock("4321") }.isFailure)
    }

    @Test fun corruptStorageNeverFallsBackToAnUnlockedApp() = runBlocking {
        val store = MemoryStorage().apply { failReads = true }
        val manager = manager(store)
        manager.initialize()
        assertTrue(manager.state.value.ready)
        assertTrue(manager.state.value.enabled)
        assertTrue(manager.state.value.blocked)
        assertNotNull(manager.state.value.error)
        assertTrue(runCatching { manager.unlock("1234") }.isFailure)
        assertTrue(runCatching { manager.disable("1234") }.isFailure)
    }

    @Test fun failedCheckpointWriteCannotGrantAccess() = runBlocking {
        val store = MemoryStorage()
        val manager = manager(store)
        manager.initialize()
        manager.configure("", "1234", "4321", false)
        manager.lock()
        store.failWrites = true
        assertTrue(runCatching { manager.unlock("1234") }.isFailure)
        assertTrue(manager.state.value.blocked)
        assertNull(manager.state.value.access)
    }

    @Test fun returningToBackgroundWhileUnlockIsSavingInvalidatesTheResult() = runBlocking {
        val store = MemoryStorage()
        val manager = manager(store)
        manager.initialize()
        manager.configure("", "1234", "4321", false)
        manager.lock()
        store.afterWrite = manager::lock
        assertTrue(runCatching { manager.unlock("1234") }.isFailure)
        assertTrue(manager.state.value.blocked)
        assertNull(manager.state.value.access)
    }

    @Test fun biometricCompletionFromAnOlderForegroundCannotUnlock() = runBlocking {
        val store = MemoryStorage()
        val manager = manager(store)
        manager.initialize()
        manager.configure("", "1234", "4321", true)
        manager.lock()
        val oldRevision = manager.state.value.revision
        manager.lock()
        assertTrue(runCatching { manager.unlockBiometric(oldRevision) }.isFailure)
        assertNull(manager.state.value.access)
        assertEquals(AppAccess.FULL, manager.unlockBiometric(manager.state.value.revision).access)
    }

    @Test fun hashesAreSaltedAndOnlyAcceptExactFourDigitPins() {
        val first = AppPin.create("0012")
        val second = AppPin.create("0012")
        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.value, second.value)
        assertTrue(AppPin.matches("0012", first))
        assertFalse(AppPin.matches("1234", first))
        assertFalse(AppPin.matches("001234", first))
        assertFalse(AppPin.valid("12345x"))
        assertFalse(AppPin.valid("1234567"))
        assertFalse(AppPin.valid(" 12345"))
    }

    // Immutable version-1 fixtures, generated with the original six-digit KDF parameters.
    private fun legacyStorage() = MemoryStorage().apply {
        value = AppLockRecord(
            PinDigest("AQEBAQEBAQEBAQEBAQEBAQ==", "I8raZlTKpo3G9f9lpFcOXh2u0WcXppAxI2qArb174t0="),
            PinDigest("AgICAgICAgICAgICAgICAg==", "+sASJt7dzIKlTSSDec3DGRnjuM5pmgRdc4dCOf7AHEY="), false, pinLength = AppPin.LEGACY_LENGTH
        )
    }

    @Test fun existingSixDigitPinsStillUnlockBothModesWithoutAcceptingPrefixes() = runBlocking {
        val store = legacyStorage()
        val manager = manager(store)
        manager.initialize()
        assertEquals(6, manager.state.value.pinLength)
        assertTrue(runCatching { manager.unlock("1234") }.isFailure)
        assertNull(manager.state.value.access)
        assertEquals(AppAccess.EPUB_ONLY, manager.unlock("654321").access)
        manager.lock()
        assertEquals(AppAccess.FULL, manager.unlock("123456").access)
    }

    @Test fun changingLegacyPinsRequiresOldNormalPinThenOnlyAcceptsNewFourDigitPins() = runBlocking {
        val store = legacyStorage()
        val manager = manager(store)
        manager.initialize()
        manager.unlock("123456")
        assertTrue(runCatching { manager.configure("1234", "0012", "0098", false) }.isFailure)
        assertEquals(6, store.value!!.pinLength)
        manager.configure("123456", "0012", "0098", false)
        assertEquals(4, store.value!!.pinLength)
        assertEquals(4, manager.state.value.pinLength)
        val restored = manager(store)
        restored.initialize()
        assertEquals(4, restored.state.value.pinLength)
        assertTrue(runCatching { restored.unlock("123456") }.isFailure)
        assertTrue(runCatching { restored.unlock("654321") }.isFailure)
        assertEquals(AppAccess.EPUB_ONLY, restored.unlock("0098").access)
        restored.lock()
        assertEquals(AppAccess.FULL, restored.unlock("0012").access)
    }

    @Test fun leavingReplacementPinsBlankPreservesLegacyCredentialsAndLength() = runBlocking {
        val store = legacyStorage()
        val original = store.value!!
        val manager = manager(store)
        manager.initialize()
        manager.unlock("123456")
        manager.configure("123456", "", "", true)
        assertEquals(original.full, store.value!!.full)
        assertEquals(original.epub, store.value!!.epub)
        assertEquals(6, store.value!!.pinLength)
        assertEquals(6, manager.state.value.pinLength)
        assertTrue(manager.state.value.biometricEnabled)
        val restored = manager(store)
        restored.initialize()
        assertEquals(AppAccess.EPUB_ONLY, restored.unlock("654321").access)
    }

    @Test fun legacyNormalPinCanDisableLockAndNextSetupUsesFourDigits() = runBlocking {
        val store = legacyStorage()
        val manager = manager(store)
        manager.initialize()
        manager.unlock("123456")
        manager.disable("123456")
        assertNull(store.value)
        assertEquals(4, manager.state.value.pinLength)
        manager.configure("", "1234", "4321", false)
        assertEquals(4, store.value!!.pinLength)
    }

    @Test fun newSetupRejectsSixDigitPinsAndUnsupportedLengths() = runBlocking {
        val store = MemoryStorage()
        val manager = manager(store)
        manager.initialize()
        assertTrue(runCatching { manager.configure("", "123456", "654321", false) }.isFailure)
        assertNull(store.value)
        assertFalse(AppPin.valid("123456"))
        assertFalse(AppPin.valid("123"))
        assertFalse(AppPin.valid("12345"))
        assertFalse(AppPin.valid("12a4"))
        assertFalse(AppPin.valid("\uFF11\uFF12\uFF13\uFF14"))
        assertFalse(AppPin.valid("1234", 5))
        assertTrue(runCatching { AppPin.create("123456") }.isFailure)
        val digest = AppPin.create("1234")
        assertTrue(runCatching { AppLockRecord(digest, digest, false, pinLength = 5) }.isFailure)
    }

    @Test fun forgotPinIsUnavailableUntilTheNormalPinHasBeenChanged() = runBlocking {
        val store = MemoryStorage()
        val manager = manager(store)
        manager.initialize()
        manager.configure("", "1111", "2222", false)
        assertFalse(manager.state.value.recoveryAvailable)
        // Changing only the EPUB PIN does not create a recovery credential.
        manager.configure("1111", "1111", "3333", false)
        assertFalse(manager.state.value.recoveryAvailable)
        manager.lock()
        assertTrue(runCatching { manager.recover("1111", "5555", "6666") }.isFailure)
        assertEquals(0, store.value!!.failures)
        assertNull(manager.state.value.access)
    }

    @Test fun forgotPinAcceptsOnlyThePreviousNormalPinAndReplacesBothPins() = runBlocking {
        val store = MemoryStorage()
        val manager = manager(store)
        manager.initialize()
        manager.configure("", "1111", "2222", false)
        manager.configure("1111", "3333", "4444", false)
        assertTrue(manager.state.value.recoveryAvailable)
        manager.lock()
        // Current normal PIN and the old EPUB PIN are not recovery credentials.
        assertTrue(runCatching { manager.recover("3333", "5555", "6666") }.isFailure)
        assertTrue(runCatching { manager.recover("2222", "5555", "6666") }.isFailure)
        assertEquals(2, store.value!!.failures)
        // Same mode as the locked session resumes it, exactly like a normal unlock.
        assertEquals(AppUnlock(AppAccess.FULL, false), manager.recover("1111", "5555", "6666"))
        assertEquals(AppAccess.FULL, manager.state.value.access)
        assertFalse(manager.state.value.recoveryAvailable)
        assertNull(store.value!!.previous)
        assertEquals(0, store.value!!.failures)
        manager.lock()
        assertTrue(runCatching { manager.unlock("3333") }.isFailure)
        assertEquals(AppAccess.EPUB_ONLY, manager.unlock("6666").access)
        manager.lock()
        assertEquals(AppAccess.FULL, manager.unlock("5555").access)
        // The used recovery credential cannot be replayed.
        manager.lock()
        assertTrue(runCatching { manager.recover("1111", "7777", "8888") }.isFailure)
    }

    @Test fun forgotPinSharesTheUnlockCooldown() = runBlocking {
        var now = 10_000L
        val store = MemoryStorage()
        val manager = manager(store) { now }
        manager.initialize()
        manager.configure("", "1111", "2222", false)
        manager.configure("1111", "3333", "4444", false)
        manager.lock()
        repeat(5) { assertTrue(runCatching { manager.recover("9999", "5555", "6666") }.isFailure) }
        val blocked = runCatching { manager.recover("1111", "5555", "6666") }
        assertTrue(blocked.exceptionOrNull()!!.message!!.startsWith("Too many attempts"))
        assertNotNull(store.value!!.previous)
        now += 30_000
        assertEquals(AppAccess.FULL, manager.recover("1111", "5555", "6666").access)
    }

    @Test fun forgotPinRejectsBadNewPinsWithoutCountingAFailure() = runBlocking {
        val store = MemoryStorage()
        val manager = manager(store)
        manager.initialize()
        manager.configure("", "1111", "2222", false)
        manager.configure("1111", "3333", "4444", false)
        manager.lock()
        assertTrue(runCatching { manager.recover("1111", "5555", "5555") }.isFailure)
        assertTrue(runCatching { manager.recover("1111", "555", "6666") }.isFailure)
        assertTrue(runCatching { manager.recover("111", "5555", "6666") }.isFailure)
        assertEquals(0, store.value!!.failures)
        assertNull(manager.state.value.access)
        assertTrue(manager.state.value.recoveryAvailable)
    }

    @Test fun forgotPinIsOnlyAvailableWhileLocked() = runBlocking {
        val store = MemoryStorage()
        val manager = manager(store)
        manager.initialize()
        manager.configure("", "1111", "2222", false)
        manager.configure("1111", "3333", "4444", false)
        assertTrue(runCatching { manager.recover("1111", "5555", "6666") }.isFailure)
        assertNotNull(store.value!!.previous)
    }

    @Test fun legacySixDigitPinBecomesTheRecoveryCredentialAfterMigration() = runBlocking {
        val store = legacyStorage()
        val manager = manager(store)
        manager.initialize()
        assertFalse(manager.state.value.recoveryAvailable)
        manager.unlock("123456")
        manager.configure("123456", "5678", "8765", false)
        assertEquals(AppPin.LEGACY_LENGTH, manager.state.value.recoveryPinLength)
        manager.lock()
        // Prefix of the six-digit PIN is rejected; the full old PIN resets.
        assertTrue(runCatching { manager.recover("1234", "1357", "2468") }.isFailure)
        assertEquals(AppAccess.FULL, manager.recover("123456", "1357", "2468").access)
        assertEquals(AppPin.LENGTH, store.value!!.pinLength)
    }

    @Test
    fun pinInputIsCappedAtTheStoredPinLength() {
        // Regression: the "Current PIN" field was capped at 4, blocking legacy 6-digit PINs.
        assertEquals("123456", AppPin.sanitizeInput("1234567", AppPin.LEGACY_LENGTH))
        assertEquals("1234", AppPin.sanitizeInput("123456", AppPin.LENGTH))
        assertEquals("1234", AppPin.sanitizeInput("12a3 4"))
        assertEquals("", AppPin.sanitizeInput("１２"))
    }

    @Test fun appSwitchLeavesBothGrantedAccessModesUnchanged() = runBlocking {
        val manager = manager(MemoryStorage())
        manager.initialize()
        manager.configure("", "1234", "4321", false)
        val before = manager.state.value.revision
        manager.onAppBackgrounded()
        assertEquals(AppAccess.FULL, manager.state.value.access)
        assertFalse(manager.state.value.blocked)
        assertEquals(before, manager.state.value.revision)
        manager.lock()
        manager.unlock("4321")
        manager.onAppBackgrounded()
        assertEquals(AppAccess.EPUB_ONLY, manager.state.value.access)
        assertFalse(manager.state.value.blocked)
        manager.lock()
        assertTrue(manager.state.value.blocked)
    }

    @Test fun appSwitchWhileUnlockIsSavingRejectsTheLateResult() = runBlocking {
        val store = MemoryStorage()
        val manager = manager(store)
        manager.initialize()
        manager.configure("", "1234", "4321", false)
        manager.lock()
        store.afterWrite = manager::onAppBackgrounded
        assertTrue(runCatching { manager.unlock("1234") }.isFailure)
        assertTrue(manager.state.value.blocked)
    }

    @Test fun appSwitchWhileBiometricsArePendingRejectsTheOldCallback() = runBlocking {
        val manager = manager(MemoryStorage())
        manager.initialize()
        manager.configure("", "1234", "4321", true)
        manager.lock()
        val revision = manager.state.value.revision
        manager.onAppBackgrounded()
        assertTrue(runCatching { manager.unlockBiometric(revision) }.isFailure)
        assertTrue(manager.state.value.blocked)
    }
}
