package com.novelnest.app.data.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface AiConfiguration {
    suspend fun settings(): AiSettings
    suspend fun credential(provider: AiProviderId): String
}

/** Settings operations expose only nonsecret status to their caller. */
interface AiSettingsConfiguration : AiConfiguration {
    suspend fun saveSettings(settings: AiSettings)
    suspend fun replaceCredential(provider: AiProviderId, replacement: String)
    suspend fun removeCredential(provider: AiProviderId)
    suspend fun status(): List<AiProviderStatus>
}

/** One application-owned instance. All preference/Keystore access is off the main thread. */
class AiConfigurationStore(context: Context) : AiSettingsConfiguration {
    private val prefs = context.applicationContext.getSharedPreferences("ai_reading_tools", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()

    override suspend fun settings(): AiSettings = withContext(Dispatchers.IO) { lock.withLock {
        try {
            val json = JSONObject(prefs.getString("settings", "{}").orEmpty())
            fun routes(key: String): List<AiRoute> {
                val values = json.optJSONArray(key) ?: return emptyList()
                require(values.length() <= 3)
                return (0 until values.length()).map { i -> values.getJSONObject(i).let {
                    AiRoute(AiProviderId.valueOf(it.getString("provider")), it.getString("model"))
                } }
            }
            val consent = json.optJSONArray("consent") ?: JSONArray()
            AiSettings(
                summaryRoutes = routes("summary"),
                rewriteRoutes = routes("rewrite"),
                extractRoutes = routes("extract"),
                consentedProviders = (0 until consent.length()).map {
                    AiProviderId.valueOf(consent.getString(it))
                }.toSet(),
                costPolicy = AiCostPolicy.valueOf(json.optString("costPolicy", AiCostPolicy.EXPLICIT_FREE_ONLY.name))
            )
                .let(AiProviderCatalog::restoreSavedSettings)
                .also(AiProviderCatalog::validate)
        } catch (_: Exception) {
            throw AiException(AiErrorCode.STORAGE, "AI settings could not be read. Save your configuration again.")
        }
    } }

    override suspend fun saveSettings(settings: AiSettings) = withContext(Dispatchers.IO) { lock.withLock {
        AiProviderCatalog.validate(settings)
        fun routes(values: List<AiRoute>) = JSONArray().apply { values.forEach {
            put(JSONObject().put("provider", it.provider.name).put("model", it.model))
        } }
        val json = JSONObject()
            .put("summary", routes(settings.summaryRoutes))
            .put("rewrite", routes(settings.rewriteRoutes))
            .put("extract", routes(settings.extractRoutes))
            .put("consent", JSONArray(settings.consentedProviders.map { it.name }))
            .put("costPolicy", settings.costPolicy.name)
        if (!prefs.edit().putString("settings", json.toString()).commit()) storageError()
        mutableRevision.value = mutableRevision.value + 1L
    } }

    /** Blank replacement preserves the existing key. UI never needs to receive decrypted values. */
    override suspend fun replaceCredential(provider: AiProviderId, replacement: String) = withContext(Dispatchers.IO) { lock.withLock {
        if (replacement.isBlank()) return@withLock
        val token = replacement.trim()
        require(token.length in 16..1024 && token.all { it.code in 33..126 }) { "Enter a valid provider API key." }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            if (!prefs.edit().putString(provider.name, value).commit()) storageError()
            mutableRevision.value = mutableRevision.value + 1L
        } catch (error: AiException) { throw error }
        catch (_: Exception) { storageError() }
    } }

    override suspend fun removeCredential(provider: AiProviderId) = withContext(Dispatchers.IO) { lock.withLock {
        if (!prefs.edit().remove(provider.name).commit()) storageError()
        mutableRevision.value = mutableRevision.value + 1L
    } }

    override suspend fun credential(provider: AiProviderId): String = withContext(Dispatchers.IO) { lock.withLock {
        decrypt(provider)
    } }

    override suspend fun status(): List<AiProviderStatus> = withContext(Dispatchers.IO) { lock.withLock {
        AiProviderId.entries.map { provider ->
            try { AiProviderStatus(provider, decrypt(provider).isNotBlank()) }
            catch (_: AiException) { AiProviderStatus(provider, false, unreadable = true) }
        }
    } }

    private fun decrypt(provider: AiProviderId): String {
        val value = prefs.getString(provider.name, null) ?: return ""
        try {
            val parts = value.split(':')
            require(parts.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            throw AiException(AiErrorCode.CREDENTIALS, "The stored API key is unavailable. Enter it again in AI Reading Tools.")
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun storageError(): Nothing = throw AiException(AiErrorCode.STORAGE, "AI configuration could not be saved securely.")
    companion object { private const val KEY_ALIAS = "novelnest_ai_reading" }
}
