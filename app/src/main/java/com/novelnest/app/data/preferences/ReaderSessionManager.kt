package com.novelnest.app.data.preferences

import android.content.SharedPreferences

data class ReaderSession(val novelId: String, val chapterIndex: Int)

/** Small durable fallback for devices which discard the activity's saved task state. */
class ReaderSessionManager(private val preferences: SharedPreferences) {
    private var encoded = preferences.getString("reader_session", null)
    val session: ReaderSession? get() {
        val parts = encoded?.split('|') ?: return null
        if (parts.size != 2 || !parts[0].matches(Regex("[a-zA-Z0-9_-]+"))) return null
        val index = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return ReaderSession(parts[0], index)
    }
    fun record(novelId: String, chapterIndex: Int) {
        if (!novelId.matches(Regex("[a-zA-Z0-9_-]+")) || chapterIndex < 0) return
        save("$novelId|$chapterIndex")
    }
    fun clear() = save(null)
    private fun save(value: String?) {
        if (encoded == value) return
        encoded = value
        preferences.edit().putString("reader_session", value).apply()
    }
}
