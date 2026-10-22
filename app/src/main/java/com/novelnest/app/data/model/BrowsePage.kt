package com.novelnest.app.data.model

data class BrowseCategory(val name: String, val url: String)
data class BrowsePage(
    val novels: List<Novel>,
    val categories: List<BrowseCategory>,
    val nextPageUrl: String? = null,
    val isStale: Boolean = false
)

private val explicitChapterTotal = Regex("^([0-9][0-9,]*)\\s+Chapters?$", RegexOption.IGNORE_CASE)

fun Novel.totalChapterCount(): Int = chapterCount.takeIf { it > 0 }
    ?: explicitChapterTotal.matchEntire(latestChapter.trim())?.groupValues?.get(1)
        ?.replace(",", "")?.toIntOrNull()?.coerceAtLeast(0) ?: 0

fun Novel.chapterCountLabel(): String = totalChapterCount().takeIf { it > 0 }?.let { "$it Chapters" }.orEmpty()
