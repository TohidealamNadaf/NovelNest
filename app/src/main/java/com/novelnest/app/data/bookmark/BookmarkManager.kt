package com.novelnest.app.data.bookmark

import android.content.Context
import android.content.SharedPreferences
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists and reactively exposes user bookmarked novels.
 * Ensures bookmarks survive app restarts and are accessible everywhere (Home, Detail, Reader).
 */
class BookmarkManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _bookmarks = MutableStateFlow<List<Novel>>(loadBookmarks())
    val bookmarks: StateFlow<List<Novel>> = _bookmarks.asStateFlow()

    fun isBookmarked(novelId: String): Boolean {
        return _bookmarks.value.any { it.id == novelId }
    }

    @Synchronized
    fun toggleBookmark(novel: Novel): Boolean {
        val current = _bookmarks.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == novel.id }
        val isNowBookmarked: Boolean

        if (existingIndex >= 0) {
            current.removeAt(existingIndex)
            isNowBookmarked = false
        } else {
            current.add(0, novel) // Add to top of bookmarked list
            isNowBookmarked = true
        }

        saveBookmarks(current)
        _bookmarks.value = current
        return isNowBookmarked
    }

    @Synchronized
    fun addBookmark(novel: Novel) {
        val current = _bookmarks.value.toMutableList()
        if (current.none { it.id == novel.id }) {
            current.add(0, novel)
            saveBookmarks(current)
            _bookmarks.value = current
        }
    }

    @Synchronized
    fun removeBookmark(novelId: String) {
        val current = _bookmarks.value.toMutableList()
        val removed = current.removeAll { it.id == novelId }
        if (removed) {
            saveBookmarks(current)
            _bookmarks.value = current
        }
    }

    private fun loadBookmarks(): List<Novel> {
        val jsonStr = prefs.getString(KEY_BOOKMARKS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<Novel>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val sourceName = obj.optString("source", NovelSourceType.NOVEL_PHOENIX.name)
                val source = try {
                    NovelSourceType.valueOf(sourceName)
                } catch (_: Exception) {
                    NovelSourceType.NOVEL_PHOENIX
                }

                val genresArray = obj.optJSONArray("genres")
                val genres = mutableListOf<String>()
                if (genresArray != null) {
                    for (g in 0 until genresArray.length()) {
                        genres.add(genresArray.getString(g))
                    }
                }

                list.add(
                    Novel(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        coverUrl = obj.optString("coverUrl"),
                        source = source,
                        sourceUrl = obj.optString("sourceUrl"),
                        synopsis = obj.optString("synopsis"),
                        author = obj.optString("author", "Unknown"),
                        rating = obj.optDouble("rating", 4.5).toFloat(),
                        latestChapter = obj.optString("latestChapter", "Chapter 1"),
                        genres = genres,
                        status = obj.optString("status", "Ongoing"),
                        views = obj.optString("views", ""),
                        rank = obj.optInt("rank", 0)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveBookmarks(novels: List<Novel>) {
        val jsonArray = JSONArray()
        novels.forEach { novel ->
            val obj = JSONObject().apply {
                put("id", novel.id)
                put("title", novel.title)
                put("coverUrl", novel.coverUrl)
                put("source", novel.source.name)
                put("sourceUrl", novel.sourceUrl)
                put("synopsis", novel.synopsis)
                put("author", novel.author)
                put("rating", novel.rating.toDouble())
                put("latestChapter", novel.latestChapter)
                val genresArray = JSONArray()
                novel.genres.forEach { genresArray.put(it) }
                put("genres", genresArray)
                put("status", novel.status)
                put("views", novel.views)
                put("rank", novel.rank)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_BOOKMARKS, jsonArray.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "novelnest_bookmarks"
        private const val KEY_BOOKMARKS = "saved_bookmarks_json"
    }
}
