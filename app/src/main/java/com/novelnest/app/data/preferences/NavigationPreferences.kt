package com.novelnest.app.data.preferences

import android.content.SharedPreferences
import com.novelnest.app.data.model.NovelSourceType

/** Persists the Home source only. Navigation destinations belong to the live task. */
class NavigationPreferences(
    private val preferences: SharedPreferences,
    defaultSource: NovelSourceType = NovelSourceType.NOVEL_PHOENIX
) {
    private val savedName = preferences.getString("last_source", null)
    private val savedSource = NovelSourceType.entries.firstOrNull { it.name == savedName }
    private val legacySource = preferences.getString("reader_session", null)?.let { encoded ->
        val parts = encoded.split('|')
        val id = parts.firstOrNull().orEmpty()
        if (parts.size != 2 || parts[1].toIntOrNull()?.let { it >= 0 } != true) null
        else when {
            id.startsWith("fwn-") -> NovelSourceType.FREE_WEB_NOVEL
            id.startsWith("asura-") -> NovelSourceType.ASURA_SCANS
            id.startsWith("np-") -> NovelSourceType.NOVEL_PHOENIX
            else -> null
        }
    }
    var lastSource: NovelSourceType = savedSource ?: legacySource ?: defaultSource
        private set

    init {
        // Preserve a known source during upgrade, but discard the old reader route.
        if (preferences.contains("reader_session")) {
            preferences.edit().apply {
                if (savedSource == null && legacySource != null) putString("last_source", legacySource.name)
                remove("reader_session")
            }.apply()
        }
    }

    fun selectSource(source: NovelSourceType) {
        if (lastSource == source) return
        lastSource = source
        preferences.edit().putString("last_source", source.name).apply()
    }
}
