package com.novelnest.app.data.security

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class AndroidAppLockStorageTest {
    @Test fun keystoreRecordRoundTripsAndTamperingFailsAuthentication() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "lock-storage-test-" + UUID.randomUUID()).apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = directory
        }
        try {
            val store = AndroidAppLockStorage(context)
            assertNull(store.read())
            val record = AppLockRecord(AppPin.create("1234"), AppPin.create("4321"), false, 5, 90000)
            store.write(record)
            assertEquals(record, AndroidAppLockStorage(context).read())
            val file = File(directory, "app_lock.bin")
            val bytes = file.readBytes()
            assertFalse(String(bytes, Charsets.ISO_8859_1).contains(record.full.value))
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            file.writeBytes(bytes)
            assertTrue(runCatching { store.read() }.isFailure)
            store.remove()
            assertNull(store.read())
        } finally {
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
        }
    }

    @Test fun versionOneCredentialsRemainSixDigitsWhenRewritten() = withIsolatedStorage { store, directory ->
        // Create the encryption key without touching the application's actual lock record.
        val initial = AppLockRecord(
            PinDigest("AQEBAQEBAQEBAQEBAQEBAQ==", "I8raZlTKpo3G9f9lpFcOXh2u0WcXppAxI2qArb174t0="),
            PinDigest("AgICAgICAgICAgICAgICAg==", "+sASJt7dzIKlTSSDec3DGRnjuM5pmgRdc4dCOf7AHEY="),
            false, pinLength = AppPin.LEGACY_LENGTH
        )
        store.write(initial)
        val legacy = jsonRecord(initial, version = 1).put("pinLength", 4)
        writeEncrypted(directory, legacy)
        // Version 1 always meant six digits, even if an unrelated length field exists.
        val restored = store.read()!!
        assertEquals(6, restored.pinLength)
        assertEquals(initial.full, restored.full)
        assertEquals(initial.epub, restored.epub)
        assertTrue(AppPin.matches("123456", restored.full, restored.pinLength))
        assertTrue(AppPin.matches("654321", restored.epub, restored.pinLength))
        assertFalse(AppPin.matches("1234", restored.full, restored.pinLength))
        store.write(restored)
        assertEquals(restored, store.read())
        assertEquals(6, store.read()!!.pinLength)
    }

    @Test fun invalidOrMissingVersionTwoLengthFailsClosed() = withIsolatedStorage { store, directory ->
        val initial = AppLockRecord(AppPin.create("1234"), AppPin.create("4321"), false)
        store.write(initial)
        val invalid = jsonRecord(initial, version = 2).put("pinLength", 5)
        writeEncrypted(directory, invalid)
        assertTrue(runCatching { store.read() }.isFailure)
        invalid.remove("pinLength")
        writeEncrypted(directory, invalid)
        assertTrue(runCatching { store.read() }.isFailure)
        invalid.put("version", 3).put("pinLength", 4)
        writeEncrypted(directory, invalid)
        assertTrue(runCatching { store.read() }.isFailure)
    }

    @Test fun previousPinForRecoveryRoundTripsAndInvalidLengthFailsClosed() = withIsolatedStorage { store, directory ->
        val previous = AppPin.create("1111")
        val record = AppLockRecord(AppPin.create("1234"), AppPin.create("4321"), false,
            previous = previous, previousLength = AppPin.LEGACY_LENGTH)
        store.write(record)
        assertEquals(record, store.read())
        // Records without the optional field (written before Forgot PIN) still load.
        store.write(record.copy(previous = null, previousLength = AppPin.LENGTH))
        assertNull(store.read()!!.previous)
        val invalid = jsonRecord(record, version = 2).put("pinLength", 4)
            .put("previous", JSONObject().put("salt", previous.salt).put("hash", previous.value)).put("previousLength", 5)
        writeEncrypted(directory, invalid)
        assertTrue(runCatching { store.read() }.isFailure)
    }

    private fun jsonRecord(record: AppLockRecord, version: Int): JSONObject {
        fun digest(value: PinDigest) = JSONObject().put("salt", value.salt).put("hash", value.value)
        return JSONObject().put("version", version).put("full", digest(record.full))
            .put("epub", digest(record.epub)).put("biometric", record.biometric)
            .put("failures", record.failures).put("retryAt", record.retryAtMillis)
    }

    private fun writeEncrypted(directory: File, json: JSONObject) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = keyStore.getKey("novelnest_app_lock_storage_v1", null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        File(directory, "app_lock.bin").writeBytes(cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8)))
    }

    private fun withIsolatedStorage(test: (AndroidAppLockStorage, File) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "lock-storage-test-" + UUID.randomUUID()).apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = directory
        }
        try { test(AndroidAppLockStorage(context), directory) }
        finally {
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
        }
    }
}
