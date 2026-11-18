package com.novelnest.app.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class AppAccess { FULL, EPUB_ONLY }

data class AppLockState(
    val ready: Boolean = false,
    val enabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val access: AppAccess? = null,
    val previousAccess: AppAccess? = null,
    val revision: Long = 0,
    val resetRootAfterUnlock: Boolean = false,
    val retryAtMillis: Long = 0,
    val error: String? = null,
    val pinLength: Int = AppPin.LENGTH,
    /** True when a replaced normal PIN is on file and can be used for Forgot PIN recovery. */
    val recoveryAvailable: Boolean = false,
    val recoveryPinLength: Int = AppPin.LENGTH
) {
    val blocked: Boolean get() = !ready || (enabled && access == null) || error != null
}

data class PinDigest(val salt: String, val value: String)
data class AppLockRecord(
    val full: PinDigest, val epub: PinDigest, val biometric: Boolean,
    val failures: Int = 0, val retryAtMillis: Long = 0,
    val pinLength: Int = AppPin.LENGTH,
    /** The most recently replaced normal PIN; the only credential accepted by Forgot PIN. */
    val previous: PinDigest? = null,
    val previousLength: Int = AppPin.LENGTH
) {
    init {
        require(AppPin.supportedLength(pinLength)) { "Invalid PIN length." }
        require(AppPin.supportedLength(previousLength)) { "Invalid previous PIN length." }
    }
}

interface AppLockStorage {
    fun read(): AppLockRecord?
    fun write(record: AppLockRecord)
    fun remove()
}

object AppPin {
    const val LENGTH = 4
    const val LEGACY_LENGTH = 6
    private const val ITERATIONS = 210_000
    fun supportedLength(length: Int) = length == LENGTH || length == LEGACY_LENGTH
    /** Keeps ASCII digits only and caps the input at [maxLength] (used by PIN text fields). */
    fun sanitizeInput(input: String, maxLength: Int = LENGTH) =
        input.filter { it in '0'..'9' }.take(maxLength)
    fun valid(pin: String, length: Int = LENGTH) =
        supportedLength(length) && pin.length == length && pin.all { it in '0'..'9' }
    fun create(pin: String): PinDigest {
        require(valid(pin)) { "Use a four-digit PIN." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return PinDigest(Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(derive(pin, salt)))
    }
    fun matches(pin: String, digest: PinDigest, length: Int = LENGTH): Boolean {
        if (!valid(pin, length)) return false
        val salt = Base64.getDecoder().decode(digest.salt)
        val expected = Base64.getDecoder().decode(digest.value)
        require(salt.size == 16 && expected.size == 32) { "Invalid lock credentials." }
        val actual = derive(pin, salt)
        return try { MessageDigest.isEqual(actual, expected) } finally { actual.fill(0) }
    }
    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val chars = pin.toCharArray()
        val spec = PBEKeySpec(chars, salt, ITERATIONS, 256)
        chars.fill('\u0000')
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}

data class AppUnlock(val access: AppAccess, val resetTask: Boolean)

object AppLockPolicy {
    fun resetTask(previous: AppAccess?, next: AppAccess) = previous == null || previous != next
    fun delayMillis(failures: Int): Long = when {
        failures < 5 -> 0
        failures < 10 -> 30_000
        else -> 60_000
    }
}