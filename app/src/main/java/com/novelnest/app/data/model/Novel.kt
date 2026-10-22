package com.novelnest.app.data.model

data class Novel(
    val id: String,
    val title: String,
    val coverUrl: String,
    val source: NovelSourceType,
    val sourceUrl: String,
    val synopsis: String = "",
    val author: String = "Unknown",
    val rating: Float = 0f,
    val latestChapter: String = "Chapter 1",
    val genres: List<String> = emptyList(),
    val status: String = "Ongoing",
    val views: String = "",
    val rank: Int = 0,
    val chapterCount: Int = 0
)
