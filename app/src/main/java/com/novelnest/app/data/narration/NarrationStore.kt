package com.novelnest.app.data.narration

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class NarrationPhase(val caption: String) {
    IDLE("Narration stopped"), LOADING_CHAPTER("Loading chapter text…"),
    CHECKING_CACHE("Checking downloaded audio…"), USING_CACHE("Opening downloaded audio…"),
    CONNECTING("Connecting to cloud narration…"), WAITING_FOR_AUDIO("Waiting for Cloudflare to generate audio…"),
    DOWNLOADING("Downloading generated audio…"), PREPARING_PLAYER("Opening audio on this device…"),
    PLAYING("Playing"), PAUSED("Paused — tap Play to continue"),
    FAILED("Narration failed — tap Play to retry"), COMPLETED("Chapter narration complete")
}

data class NarrationState(
    val novelId: String = "", val chapterTitle: String = "", val playing: Boolean = false,
    val loading: Boolean = false, val passage: Int = 0, val total: Int = 0, val message: String = "",
    val chapterId: String = "", val phase: NarrationPhase = NarrationPhase.IDLE
)

/** Process-wide observable state contains no Activity or engine references. */
data class NarrationTimeline(
    val positionMs: Long = 0, val durationMs: Long = 0,
    val speed: Float = 0f, val updatedAtMs: Long = 0, val playing: Boolean = false, val canSeek: Boolean = false,
    val mediaId: String = ""
)

object NarrationStatus {
    internal val mutable = MutableStateFlow(NarrationState())
    val state = mutable.asStateFlow()
    internal val mutableTimeline = MutableStateFlow(NarrationTimeline())
    val timeline = mutableTimeline.asStateFlow()
}

class NarrationStore(context: Context) {
    private val prefs = context.getSharedPreferences("cloud_narration", Context.MODE_PRIVATE)
    val endpoint: String get() = prefs.getString("endpoint", "").orEmpty()
    fun token(): String = synchronized(credentialsLock) { runCatching {
        val parts = prefs.getString("token", "")!!.split(":")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrDefault("") }

    fun credentials(): Pair<String, String> = synchronized(credentialsLock) { endpoint to token() }

    fun configure(url: String, token: String) = synchronized(credentialsLock) {
        val parsed = validateEndpoint(url)
        require(token.length in 24..512 && token.none { it.isWhitespace() }) { "Use an access token of at least 24 characters." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        prefs.edit().putString("endpoint", parsed.toString()).putString("token", encrypted).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("novelnest_narration", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("novelnest_narration", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun checkpoint(novelId: String): JSONObject? = runCatching {
        JSONObject(prefs.getString("checkpoint", "")!!).takeIf { it.optString("novelId") == novelId }
    }.getOrNull()
    fun saveCheckpoint(value: JSONObject) { prefs.edit().putString("checkpoint", value.toString()).apply() }

    companion object {
        private val credentialsLock = Any()
        fun validateEndpoint(url: String): okhttp3.HttpUrl {
            val parsed = url.toHttpUrlOrNull()
            require(parsed != null && parsed.isHttps && parsed.username.isEmpty() && parsed.password.isEmpty() &&
                parsed.query == null && parsed.fragment == null && parsed.encodedPath == "/v1/narration") {
                "Enter the HTTPS Worker URL ending in /v1/narration, without query parameters."
            }
            return parsed
        }
    }
}
