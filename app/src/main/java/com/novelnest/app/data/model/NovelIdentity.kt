package com.novelnest.app.data.model

import com.novelnest.app.data.remote.freewebnovel.FreeWebNovelUrls
import java.net.URI

/** A source URL identifies a novel; titles and ranking labels do not. */
object NovelIdentity {
    fun normalize(novel: Novel): Novel = runCatching {
        when (novel.source) {
            NovelSourceType.FREE_WEB_NOVEL -> novel.copy(
                id = FreeWebNovelUrls.novelId(novel.sourceUrl.ifBlank { novel.id }),
                sourceUrl = FreeWebNovelUrls.novelUrl(novel.sourceUrl.ifBlank { novel.id }))
            NovelSourceType.NOVEL_PHOENIX -> {
                val path = URI(novel.sourceUrl).path.orEmpty()
                if (!path.startsWith("/novel/")) return@runCatching novel
                val slug = path.removePrefix("/novel/").substringBefore('/').removePrefix("np-")
                if (!slug.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_-]*"))) return@runCatching novel
                novel.copy(id = "np-$slug", sourceUrl = "${novel.source.baseUrl}/novel/$slug")
            }
            else -> novel
        }
    }.getOrDefault(novel)

    fun distinct(novels: List<Novel>): List<Novel> {
        val merged = linkedMapOf<String, Novel>()
        for (record in novels) {
            val novel = normalize(record)
            val key = novel.source.name + ":" + novel.id
            val old = merged[key]
            merged[key] = if (old == null) novel else {
                val preferred = if (novel.title.length < old.title.length) novel else old
                preferred.copy(
                    synopsis = old.synopsis.ifBlank { novel.synopsis },
                    chapterCount = maxOf(old.chapterCount, novel.chapterCount),
                    genres = (old.genres + novel.genres).distinct(),
                    rank = listOf(old.rank, novel.rank).filter { it > 0 }.minOrNull() ?: 0)
            }
        }
        return merged.values.toList()
    }
}
