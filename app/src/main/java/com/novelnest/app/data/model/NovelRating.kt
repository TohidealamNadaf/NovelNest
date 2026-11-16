package com.novelnest.app.data.model

import java.util.Locale

/** Zero is the source's unrated/missing sentinel, not a reader score. */
fun Novel.hasRating(): Boolean = rating.isFinite() && rating > 0f && rating <= 5f
fun Novel.ratingLabel(): String = if (hasRating()) String.format(Locale.ROOT, "%.1f", rating) else "—"

/** Formats raw view/read counts (e.g. "38392232") into compact human-readable badges ("38.4M"). */
fun Novel.formattedViews(): String = formatCompactCount(views)

fun formatCompactCount(raw: String): String {
    val trimmed = raw.trim()
        .replace(Regex("(?i)\\s*(views|reads)$"), "")
        .trim()
    if (trimmed.isEmpty()) return ""
    if (trimmed.last().uppercaseChar() in charArrayOf('K', 'M', 'B')) {
        return trimmed.dropLast(1).trim() + trimmed.last().uppercaseChar()
    }
    val numeric = trimmed.replace(",", "").replace("_", "").toDoubleOrNull() ?: return trimmed
    return when {
        numeric >= 1_000_000_000.0 -> String.format(Locale.ROOT, "%.1fB", numeric / 1_000_000_000.0).replace(".0B", "B")
        numeric >= 1_000_000.0 -> String.format(Locale.ROOT, "%.1fM", numeric / 1_000_000.0).replace(".0M", "M")
        numeric >= 1_000.0 -> String.format(Locale.ROOT, "%.1fK", numeric / 1_000.0).replace(".0K", "K")
        else -> numeric.toLong().toString()
    }
}

/** Reuse real scores for the same source/novel across sections without extra requests. */
fun List<NovelSection>.reuseRatings(): List<NovelSection> {
    val known = flatMap { it.novels }.filter { it.hasRating() }.associateBy { it.source to it.id }
    return map { section -> section.copy(novels = section.novels.map { novel ->
        if (novel.hasRating()) novel else known[novel.source to novel.id]?.let { novel.copy(rating = it.rating) } ?: novel
    }) }
}
