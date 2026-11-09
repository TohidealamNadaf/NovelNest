package com.novelnest.app.data.model

/** Source-neutral chapter metadata/content shared by repositories, caches and readers. */
data class Chapter(
    val id: String,
    val title: String,
    val paragraphs: List<String> = emptyList(),
    val url: String = "",
    val prevUrl: String? = null,
    val nextUrl: String? = null
)
