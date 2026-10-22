package com.novelnest.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.novelnest.app.data.model.NovelSourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Personal search history, isolated by source and retained across app restarts. */
class SearchHistoryManager(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("novelnest_search_history", Context.MODE_PRIVATE))

    private val _history = MutableStateFlow(NovelSourceType.entries.associateWith { source ->
        runCatching {
            val saved = JSONArray(preferences.getString(source.name, "[]"))
            (0 until saved.length()).map { saved.getString(it) }.filter { it.isNotBlank() }.take(MAX_ENTRIES)
        }.getOrDefault(emptyList())
    })
    val history = _history.asStateFlow()

    @Synchronized
    fun record(source: NovelSourceType, query: String) {
        val normalized = query.trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) return
        save(source, (listOf(normalized) + _history.value[source].orEmpty().filterNot { it.equals(normalized, true) }).take(MAX_ENTRIES))
    }

    @Synchronized
    fun remove(source: NovelSourceType, query: String) =
        save(source, _history.value[source].orEmpty().filterNot { it.equals(query, true) })

    @Synchronized
    fun clear(source: NovelSourceType) = save(source, emptyList())

    private fun save(source: NovelSourceType, entries: List<String>) {
        preferences.edit().putString(source.name, JSONArray(entries).toString()).apply()
        _history.value = _history.value + (source to entries)
    }

    companion object { const val MAX_ENTRIES = 20 }
}
