package com.novelnest.app.data.security

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Session permissions are process memory only. Disk/Keystore/KDF work never runs on the UI thread. */
class AppLockManager(
    private val storage: AppLockStorage,
    private val clock: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val prepareBiometric: () -> Unit = {}
) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(AppLockState())
    val state = mutable.asStateFlow()
    private var record: AppLockRecord? = null

    suspend fun initialize() = withContext(dispatcher) {
        mutex.withLock {
            if (state.value.ready) return@withLock
            try {
                record = storage.read()
                val value = record
                // Bound restored cooldowns if the wall clock moved backwards.
                if (value != null && value.retryAtMillis > clock() + 60_000) {
                    record = value.copy(retryAtMillis = clock() + 60_000)
                    storage.write(record!!)
                }
                mutable.value = AppLockState(ready = true, enabled = value != null,
                    biometricEnabled = value?.biometric == true,
                    access = if (value == null) AppAccess.FULL else null,
                    retryAtMillis = record?.retryAtMillis ?: 0, pinLength = value?.pinLength ?: AppPin.LENGTH,
                    recoveryAvailable = value?.previous != null, recoveryPinLength = value?.previousLength ?: AppPin.LENGTH)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutable.value = AppLockState(ready = true, enabled = true,
                    error = "App lock storage could not be read. Access remains locked. Restart and try again.")
            }
        }
    }

    fun lock() {
        mutable.update { old ->
            old.copy(access = if (!old.ready || old.enabled) null else old.access,
                previousAccess = old.access ?: old.previousAccess, revision = old.revision + 1, resetRootAfterUnlock = false)
        }
    }

    /** Leaving the foreground invalidates in-flight authentication, preserving granted access. */
    fun onAppBackgrounded() {
        mutable.update { if (it.access == null) it.copy(revision = it.revision + 1) else it }
    }

    suspend fun unlock(pin: String): AppUnlock = withContext(dispatcher) {
        mutex.withLock {
            val before = state.value
            check(before.ready && before.enabled && before.access == null && before.error == null) { "App is not ready to unlock." }
            val value = record ?: error("Lock credentials are unavailable.")
            checkCooldown(value)
            require(AppPin.valid(pin, value.pinLength)) { "Enter your ${value.pinLength}-digit PIN." }
            // Evaluate both credentials, avoiding a fast path that reveals which PIN was tested.
            val full = AppPin.matches(pin, value.full, value.pinLength)
            val epub = AppPin.matches(pin, value.epub, value.pinLength)
            currentCoroutineContext().ensureActive()
            if (!full && !epub) failed(value)
            check(state.value.revision == before.revision) { "Unlock was interrupted. Please try again." }
            val access = if (full) AppAccess.FULL else AppAccess.EPUB_ONLY
            // The lower-privilege PIN must not clear attempts against the normal PIN.
            if (full) save(value.copy(failures = 0, retryAtMillis = 0))
            check(mutable.compareAndSet(state.value.takeIf { it.revision == before.revision && it.access == null }
                ?: error("Unlock was interrupted."), before.copy(access = access, previousAccess = access,
                    resetRootAfterUnlock = AppLockPolicy.resetTask(before.previousAccess, access),
                    retryAtMillis = if (full) 0 else value.retryAtMillis))) { "Unlock was interrupted." }
            AppUnlock(access, AppLockPolicy.resetTask(before.previousAccess, access))
        }
    }

    /** Called only after a successful Android Keystore-backed BiometricPrompt operation. */
    suspend fun unlockBiometric(revision: Long): AppUnlock = withContext(dispatcher) {
        mutex.withLock {
            val before = state.value
            check(before.ready && before.enabled && before.biometricEnabled && before.access == null &&
                before.error == null && before.revision == revision) { "Use your PIN to unlock." }
            val value = record ?: error("Lock credentials unavailable.")
            checkCooldown(value)
            save(value.copy(failures = 0, retryAtMillis = 0))
            check(mutable.compareAndSet(before, before.copy(access = AppAccess.FULL, previousAccess = AppAccess.FULL,
                resetRootAfterUnlock = AppLockPolicy.resetTask(before.previousAccess, AppAccess.FULL),
                retryAtMillis = 0))) { "Authentication was interrupted." }
            AppUnlock(AppAccess.FULL, AppLockPolicy.resetTask(before.previousAccess, AppAccess.FULL))
        }
    }

    suspend fun configure(currentPin: String, fullPin: String, epubPin: String, biometric: Boolean) = withContext(dispatcher) {
        mutex.withLock {
            val before = state.value
            check(before.ready && before.access == AppAccess.FULL && before.error == null) { "Normal access is required." }
            val old = record
            if (old != null) {
                checkCooldown(old)
                if (!AppPin.matches(currentPin, old.full, old.pinLength)) failed(old)
            }
            val replace = fullPin.isNotEmpty() || epubPin.isNotEmpty()
            require(old != null || replace) { "Set both PINs first." }
            val next = if (replace) {
                require(AppPin.valid(fullPin) && AppPin.valid(epubPin)) { "Use four digits for each PIN." }
                require(fullPin != epubPin) { "The two PINs must be different." }
                // Keep the replaced normal PIN as the single Forgot PIN credential.
                val normalChanged = old != null && !AppPin.matches(fullPin, old.full, old.pinLength)
                AppLockRecord(AppPin.create(fullPin), AppPin.create(epubPin), biometric,
                    previous = if (normalChanged) old!!.full else old?.previous,
                    previousLength = if (normalChanged) old!!.pinLength else old?.previousLength ?: AppPin.LENGTH)
            } else old!!.copy(biometric = biometric, failures = 0, retryAtMillis = 0)
            if (biometric) prepareBiometric()
            currentCoroutineContext().ensureActive()
            check(state.value.access == AppAccess.FULL && state.value.revision == before.revision) { "Settings were interrupted. Unlock and try again." }
            save(next)
            mutable.update { it.copy(enabled = true, biometricEnabled = biometric, retryAtMillis = 0, pinLength = next.pinLength,
                recoveryAvailable = next.previous != null, recoveryPinLength = next.previousLength,
                access = if (it.revision == before.revision) it.access else null) }
        }
    }

    /**
     * Forgot PIN: verifies the most recently replaced normal PIN (never the current one),
     * replaces both PINs, clears the recovery credential and grants normal access.
     * Wrong old PINs share the normal unlock cooldown.
     */
    suspend fun recover(oldPin: String, fullPin: String, epubPin: String): AppUnlock = withContext(dispatcher) {
        mutex.withLock {
            val before = state.value
            check(before.ready && before.enabled && before.access == null && before.error == null) { "App is not ready to reset the PIN." }
            val value = record ?: error("Lock credentials are unavailable.")
            val previous = value.previous ?: error("No previous PIN is available. PIN recovery is not possible.")
            checkCooldown(value)
            require(AppPin.valid(oldPin, value.previousLength)) { "Enter your previous ${value.previousLength}-digit PIN." }
            require(AppPin.valid(fullPin) && AppPin.valid(epubPin)) { "Use four digits for each new PIN." }
            require(fullPin != epubPin) { "The two PINs must be different." }
            if (!AppPin.matches(oldPin, previous, value.previousLength)) failed(value)
            val next = AppLockRecord(AppPin.create(fullPin), AppPin.create(epubPin), value.biometric)
            currentCoroutineContext().ensureActive()
            check(state.value.revision == before.revision) { "PIN reset was interrupted. Please try again." }
            save(next)
            val access = AppAccess.FULL
            val reset = AppLockPolicy.resetTask(before.previousAccess, access)
            check(mutable.compareAndSet(state.value.takeIf { it.revision == before.revision && it.access == null }
                ?: error("PIN reset was interrupted. Unlock with your new PIN."), before.copy(access = access,
                    previousAccess = access, resetRootAfterUnlock = reset, retryAtMillis = 0,
                    pinLength = next.pinLength, recoveryAvailable = false))) { "PIN reset was interrupted. Unlock with your new PIN." }
            AppUnlock(access, reset)
        }
    }

    suspend fun disable(currentPin: String) = withContext(dispatcher) {
        mutex.withLock {
            val before = state.value
            check(before.access == AppAccess.FULL && before.error == null) { "Normal access is required." }
            val value = record ?: return@withLock
            checkCooldown(value)
            if (!AppPin.matches(currentPin, value.full, value.pinLength)) failed(value)
            currentCoroutineContext().ensureActive()
            check(state.value.access == AppAccess.FULL && state.value.revision == before.revision) { "Unlock and try again." }
            storage.remove()
            record = null
            mutable.update { it.copy(enabled = false, biometricEnabled = false, access = AppAccess.FULL, retryAtMillis = 0,
                pinLength = AppPin.LENGTH, recoveryAvailable = false) }
        }
    }

    fun acknowledgeRootReset(revision: Long) {
        mutable.update { if (it.revision == revision) it.copy(resetRootAfterUnlock = false) else it }
    }

    private fun checkCooldown(value: AppLockRecord) {
        val remaining = value.retryAtMillis - clock()
        check(remaining <= 0) { "Too many attempts. Try again in " + ((remaining + 999) / 1000) + " seconds." }
    }
    private fun failed(value: AppLockRecord): Nothing {
        val failures = (value.failures + 1).coerceAtMost(100)
        val updated = value.copy(failures = failures, retryAtMillis = clock() + AppLockPolicy.delayMillis(failures))
        save(updated)
        mutable.update { it.copy(retryAtMillis = updated.retryAtMillis) }
        error(if (failures >= 5) "Too many attempts. Please wait before trying again." else "Incorrect PIN.")
    }
    private fun save(value: AppLockRecord) {
        storage.write(value)
        record = value
    }
}