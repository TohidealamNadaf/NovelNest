package com.novelnest.app.data.notification

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class AppNotification(
    val id: String, val title: String, val message: String, val createdAt: Long,
    val novelId: String? = null, val reminder: Boolean = false, val read: Boolean = false
)
data class NotificationPreferences(val enabled: Boolean = false, val chapterUpdates: Boolean = true, val reminders: Boolean = true)

class NotificationStore(private val prefs: SharedPreferences) {
    private val _items = MutableStateFlow(load())
    val items = _items.asStateFlow()
    private val _settings = MutableStateFlow(NotificationPreferences(
        prefs.getBoolean("enabled", false), prefs.getBoolean("chapter_updates", true), prefs.getBoolean("reminders", true)))
    val settings = _settings.asStateFlow()
    val lastActive get() = prefs.getLong("last_active", 0)
    val remindedFor get() = prefs.getLong("reminded_for", -1)
    var cursor: Int
        get() = prefs.getInt("cursor", 0)
        set(value) { prefs.edit().putInt("cursor", value).apply() }

    @Synchronized fun configure(enabled: Boolean = _settings.value.enabled,
        chapters: Boolean = _settings.value.chapterUpdates, reminders: Boolean = _settings.value.reminders) {
        prefs.edit().putBoolean("enabled", enabled).putBoolean("chapter_updates", chapters).putBoolean("reminders", reminders).apply()
        _settings.value = NotificationPreferences(enabled, chapters, reminders)
    }
    fun recordActive(now: Long) { prefs.edit().putLong("last_active", now).apply() }
    fun recordReminder(activity: Long) { prefs.edit().putLong("reminded_for", activity).apply() }

    @Synchronized fun add(item: AppNotification): Boolean {
        if (_items.value.any { it.id == item.id }) return false
        save((listOf(item) + _items.value).take(100))
        return true
    }
    @Synchronized fun markRead(id: String) = save(_items.value.map { if (it.id == id) it.copy(read = true) else it })
    @Synchronized fun markAllRead() = save(_items.value.map { it.copy(read = true) })
    private fun save(items: List<AppNotification>) {
        val json = JSONArray()
        items.forEach { item -> json.put(JSONObject().put("id", item.id).put("title", item.title)
            .put("message", item.message).put("at", item.createdAt).put("novel", item.novelId)
            .put("reminder", item.reminder).put("read", item.read)) }
        prefs.edit().putString("inbox", json.toString()).apply()
        _items.value = items
    }
    private fun load(): List<AppNotification> = runCatching {
        val json = JSONArray(prefs.getString("inbox", "[]"))
        (0 until json.length()).mapNotNull { index -> runCatching {
            val item = json.getJSONObject(index)
            AppNotification(item.getString("id"), item.getString("title"), item.getString("message"), item.getLong("at"),
                item.optString("novel").takeIf { it.isNotBlank() && it != "null" }, item.optBoolean("reminder"), item.optBoolean("read"))
        }.getOrNull() }.take(100)
    }.getOrDefault(emptyList())

    @Synchronized fun markers(): Map<String, ChapterMarker> = runCatching {
        val obj = JSONObject(prefs.getString("markers", "{}"))
        obj.keys().asSequence().associateWith { key -> obj.getJSONObject(key).let { ChapterMarker(it.optInt("count"), it.optString("title")) } }
    }.getOrDefault(emptyMap())
    @Synchronized fun updateMarker(id: String, marker: ChapterMarker) {
        val current = markers().toMutableMap()
        current[id] = marker
        saveMarkers(current)
    }
    @Synchronized fun reconcile(markers: Map<String, ChapterMarker>) {
        val old = markers()
        saveMarkers(markers.mapValues { (id, marker) -> old[id] ?: marker })
    }
    private fun saveMarkers(markers: Map<String, ChapterMarker>) {
        val obj = JSONObject()
        markers.forEach { (id, marker) -> obj.put(id, JSONObject().put("count", marker.count).put("title", marker.title)) }
        prefs.edit().putString("markers", obj.toString()).apply()
    }
}
