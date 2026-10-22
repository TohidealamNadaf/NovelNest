package com.novelnest.app.data.remote.novelphoenix

/**
 * Domain and parsing models specific to the NovelPhoenix source.
 */

data class PhoenixNovelDetail(
    val id: String,
    val slug: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val synopsis: String,
    val status: String,
    val rating: Float,
    val ratingCount: Int,
    val rank: Int,
    val viewsRaw: String,
    val viewsApprox: Long?,
    val bookmarksRaw: String,
    val bookmarksApprox: Long?,
    val totalChapters: Int,
    val genres: List<String>,
    val tags: List<String>,
    val latestChapterTitle: String,
    val rawUpdatedAt: String,
    val approxUpdatedTimestamp: Long
)

data class PhoenixChapterItem(
    val id: String,
    val chapterNumber: String,
    val title: String,
    val urlPath: String,
    val rawReleaseDate: String,
    val approxReleaseTimestamp: Long,
    val isLocked: Boolean = false
)

data class PhoenixChapterContent(
    val id: String,
    val title: String,
    val paragraphs: List<String>,
    val prevChapterUrl: String?,
    val nextChapterUrl: String?
)

data class PhoenixListingItem(
    val slug: String,
    val title: String,
    val coverUrl: String,
    val latestChapter: String,
    val rating: Float,
    val rankBadge: String? = null // Display-only "R 1" badge from listing card
)
