package com.novelnest.app.data.bookmark

import android.content.Context
import android.content.SharedPreferences
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.remote.freewebnovel.FreeWebNovelUrls
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
        return getBookmark(novelId) != null
    }

    fun getBookmark(novelId: String): Novel? {
        val list = _bookmarks.value
        // 1. Exact ID match
        list.find { it.id == novelId }?.let { return it }

        val source = when {
            novelId.startsWith("fwn-") -> NovelSourceType.FREE_WEB_NOVEL
            novelId.startsWith("asura-") -> NovelSourceType.ASURA_SCANS
            else -> NovelSourceType.NOVEL_PHOENIX
        }
        val sameSource = list.filter { it.source == source }
        if (source == NovelSourceType.FREE_WEB_NOVEL) {
            val canonicalId = runCatching { FreeWebNovelUrls.novelId(novelId) }.getOrNull() ?: return null
            return sameSource.find { normalize(it).id == canonicalId }
        }

        // 2. Legacy numeric ID match
        val mappedSlug = mapLegacyIdToSlug(novelId)
        if (mappedSlug != null) {
            sameSource.find { it.id == "np-$mappedSlug" || it.sourceUrl.endsWith("/$mappedSlug") }?.let { return it }
        }

        // 3. Match by slug
        val cleanSlug = novelId.removePrefix("np-").removePrefix("fwn-").removePrefix("asura-").trimEnd('/')
        if (cleanSlug.isNotBlank()) {
            sameSource.find { it.id.removePrefix("np-").removePrefix("asura-") == cleanSlug || it.sourceUrl.trimEnd('/').endsWith("/$cleanSlug") }?.let { return it }
        }

        return null
    }

    private fun normalize(novel: Novel): Novel = if (novel.source == NovelSourceType.FREE_WEB_NOVEL) {
        runCatching {
            val url = FreeWebNovelUrls.novelUrl(novel.sourceUrl.ifBlank { novel.id })
            novel.copy(id = FreeWebNovelUrls.novelId(url), sourceUrl = url,
                coverUrl = com.novelnest.app.data.remote.freewebnovel.FreeWebNovelUtils.posterUrl(novel.coverUrl))
        }.getOrDefault(novel)
    } else novel

    private fun sameNovel(left: Novel, right: Novel): Boolean {
        if (left.source != right.source) return false
        fun key(novel: Novel): String = if (novel.source == NovelSourceType.NOVEL_PHOENIX) {
            mapLegacyIdToSlug(novel.id)?.let { "np-$it" } ?: novel.id
        } else normalize(novel).id
        return key(left) == key(right)
    }

    private fun mapLegacyIdToSlug(id: String): String? = when (id) {
        "np-1" -> "shadow-slave"
        "np-2" -> "full-star-player-returns-as-a-godslayer"
        "np-3" -> "genius-martial-arts-trainer"
        "np-4" -> "the-mine-lord-a-dwarven-survival-base-builder"
        "np-5" -> "lord-of-the-mysteries"
        "np-6" -> "reverend-insanity"
        "np-7" -> "collecting-powerful-girlfriends-my-invincible-harem"
        "np-8" -> "tyrant-no-i-am-the-villain"
        "np-9" -> "the-beginning-after-the-end"
        "np-10" -> "mother-of-learning"
        else -> null
    }

    @Synchronized
    fun toggleBookmark(novel: Novel): Boolean {
        val current = _bookmarks.value.toMutableList()
        val normalized = normalize(novel)
        val existingIndex = current.indexOfFirst { sameNovel(it, normalized) }
        val isNowBookmarked: Boolean

        if (existingIndex >= 0) {
            current.removeAll { sameNovel(it, normalized) }
            isNowBookmarked = false
        } else {
            current.add(0, normalized) // Add to top of bookmarked list
            isNowBookmarked = true
        }

        saveBookmarks(current)
        _bookmarks.value = current
        return isNowBookmarked
    }

    @Synchronized
    fun addBookmark(novel: Novel) {
        val current = _bookmarks.value.toMutableList()
        val normalized = normalize(novel)
        val existing = current.indexOfFirst { sameNovel(it, normalized) }
        if (existing < 0) current.add(0, normalized) else current[existing] = normalized
        saveBookmarks(current)
        _bookmarks.value = current
    }

    @Synchronized
    fun removeBookmark(novelId: String) {
        val current = _bookmarks.value.toMutableList()
        val target = getBookmark(novelId)
        val removed = current.removeAll { it.id == novelId || (target != null && sameNovel(it, target)) }
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
                        chapterCount = obj.optInt("chapterCount", 0),
                        genres = genres,
                        status = obj.optString("status", "Ongoing"),
                        views = obj.optString("views", ""),
                        rank = obj.optInt("rank", 0)
                    )
                )
            }
            list.map(::normalize).distinctBy { it.source to it.id }
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
                put("chapterCount", novel.chapterCount)
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
