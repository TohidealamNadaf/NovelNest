package com.novelnest.app.data.model

import java.util.Locale

/** Zero is the source's unrated/missing sentinel, not a reader score. */
fun Novel.hasRating(): Boolean = rating.isFinite() && rating > 0f && rating <= 5f
fun Novel.ratingLabel(): String = if (hasRating()) String.format(Locale.ROOT, "%.1f", rating) else "—"

/** Reuse real scores for the same source/novel across sections without extra requests. */
fun List<NovelSection>.reuseRatings(): List<NovelSection> {
    val known = flatMap { it.novels }.filter { it.hasRating() }.associateBy { it.source to it.id }
    return map { section -> section.copy(novels = section.novels.map { novel ->
        if (novel.hasRating()) novel else known[novel.source to novel.id]?.let { novel.copy(rating = it.rating) } ?: novel
    }) }
}
