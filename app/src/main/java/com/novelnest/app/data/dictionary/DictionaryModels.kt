package com.novelnest.app.data.dictionary

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

internal data class DictionaryDefinition(val text: String, val example: String?, val synonyms: List<String>, val antonyms: List<String>)
internal data class DictionaryMeaning(val partOfSpeech: String, val definitions: List<DictionaryDefinition>, val synonyms: List<String>, val antonyms: List<String>)
internal data class DictionaryLicense(val name: String, val url: String?)
internal data class DictionaryEntry(val word: String, val phonetics: List<String>, val meanings: List<DictionaryMeaning>, val sources: List<String>, val license: DictionaryLicense?)
internal data class DictionaryResult(val query: String, val entries: List<DictionaryEntry>, val fetchedAtMs: Long) {
    fun isFresh(now: Long): Boolean = fetchedAtMs in 1..now && now - fetchedAtMs <= FRESH_MS
    companion object { const val FRESH_MS = 30L * 24 * 60 * 60 * 1000 }
}
internal enum class DictionaryError { INVALID_WORD, NOT_FOUND, RATE_LIMIT, NETWORK, TIMEOUT, UNAVAILABLE, INVALID_RESPONSE, BUSY }
internal class DictionaryException(val kind: DictionaryError, message: String, val retryAfterMs: Long = 0) : Exception(message)

internal object DictionaryWord {
    private val allowed = Regex("[\\p{L}\\p{M}]+(?:['-][\\p{L}\\p{M}]+)*")
    fun normalize(raw: String?): String {
        if (raw == null || raw.length > 512) invalid()
        val word = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC)
            .trim { it.isWhitespace() || it in ".,;:!?()[]{}" || it == '"' || it == '\'' || it in "\u201c\u201d\u2018\u2019" }
            .replace('\u2019', '\'').replace('\u2018', '\'').lowercase(Locale.ROOT)
        if (word.length !in 1..80 || !allowed.matches(word)) invalid()
        return word
    }
    fun key(word: String): String = MessageDigest.getInstance("SHA-256")
        .digest(("dictionaryapi:v2:cache1|en|" + word).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private fun invalid(): Nothing = throw DictionaryException(DictionaryError.INVALID_WORD,
        "Select a single word, up to 80 characters. Apostrophes and hyphens are supported.")
}
