package com.novelnest.app.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Atomic, authenticated storage, excluded from both backup and device transfer by location. */
class AndroidAppLockStorage(context: Context) : AppLockStorage {
    private val file = AtomicFile(File(context.noBackupFilesDir, "app_lock.bin"))
    private val alias = "novelnest_app_lock_storage_v1"
    override fun read(): AppLockRecord? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val bytes = file.openRead().use { input ->
            require(input.available() <= 4096) { "Invalid lock storage." }
            input.readBytes().also { require(it.size in 29..4096) }
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        // Version 1 predates four-digit PINs. Never truncate its existing credentials.
        val pinLength = when (json.getInt("version")) {
            1 -> AppPin.LEGACY_LENGTH
            2 -> json.getInt("pinLength").also { require(AppPin.supportedLength(it)) }
            else -> error("Unsupported lock storage version.")
        }
        fun digest(name: String): PinDigest = json.getJSONObject(name).let {
            PinDigest(it.getString("salt"), it.getString("hash")).also { value ->
                require(Base64.getDecoder().decode(value.salt).size == 16)
                require(Base64.getDecoder().decode(value.value).size == 32)
            }
        }
        // Optional Forgot PIN credential; records written before it existed simply have none.
        val previous = if (json.has("previous")) digest("previous") else null
        val previousLength = if (previous != null) json.getInt("previousLength") else AppPin.LENGTH
        return AppLockRecord(digest("full"), digest("epub"), json.getBoolean("biometric"),
            json.getInt("failures").also { require(it in 0..100) },
            json.getLong("retryAt").also { require(it >= 0) }, pinLength = pinLength,
            previous = previous, previousLength = previousLength)
    }
    override fun write(record: AppLockRecord) {
        fun digest(value: PinDigest) = JSONObject().put("salt", value.salt).put("hash", value.value)
        val json = JSONObject().put("version", 2).put("pinLength", record.pinLength).put("full", digest(record.full)).put("epub", digest(record.epub))
            .put("biometric", record.biometric).put("failures", record.failures).put("retryAt", record.retryAtMillis)
        record.previous?.let { json.put("previous", digest(it)).put("previousLength", record.previousLength) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        val bytes = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    override fun remove() {
        // Delete only the lock record; never touch books, progress, notes or other preferences.
        file.delete()
        check(!file.baseFile.exists()) { "Could not disable app lock." }
    }
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "Lock key is unavailable. App data cannot be unlocked." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}