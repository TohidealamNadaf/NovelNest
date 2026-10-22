package com.novelnest.app.data.profile

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant

data class ProfileData(val nickname: String = "Reader", val photoUri: String? = null, val readingDays: Set<Long> = emptySet())
data class ReadingStreak(val current: Int, val best: Int, val days: Int)

fun readingStreak(days: Set<Long>, today: Long = LocalDate.now().toEpochDay()): ReadingStreak {
    val valid = days.filter { it <= today }.sorted()
    var best = 0
    var run = 0
    var previous: Long? = null
    for (day in valid) {
        run = if (previous != null && day == previous + 1) run + 1 else 1
        best = maxOf(best, run)
        previous = day
    }
    var end = if (today in days) today else today - 1
    var current = 0
    while (end in days) { current++; end-- }
    return ReadingStreak(current, best, valid.size)
}

class ProfileManager(private val prefs: SharedPreferences) {
    private val _profile = MutableStateFlow(ProfileData(
        prefs.getString("nickname", "Reader").orEmpty().ifBlank { "Reader" },
        prefs.getString("photo", null),
        prefs.getStringSet("reading_days", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    ))
    val profile = _profile.asStateFlow()

    @Synchronized fun saveNickname(value: String) {
        val name = value.trim().take(40).ifBlank { "Reader" }
        prefs.edit().putString("nickname", name).apply()
        _profile.value = _profile.value.copy(nickname = name)
    }
    @Synchronized fun savePhoto(uri: String?) {
        prefs.edit().putString("photo", uri).apply()
        _profile.value = _profile.value.copy(photoUri = uri)
    }
    @Synchronized fun recordReading(timestamp: Long) {
        val day = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        if (day in _profile.value.readingDays) return
        val days = _profile.value.readingDays + day
        prefs.edit().putStringSet("reading_days", days.map { it.toString() }.toSet()).apply()
        _profile.value = _profile.value.copy(readingDays = days)
    }
}
