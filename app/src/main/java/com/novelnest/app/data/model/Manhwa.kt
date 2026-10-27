package com.novelnest.app.data.model

/** Metadata only: no Bitmap, decoded pixels, or chapter images live in StateFlow. */
data class ManhwaPage(val index: Int, val url: String, val width: Int?, val height: Int?)
data class ManhwaChapter(
    val id: String, val title: String, val number: String, val url: String,
    val pageCount: Int = 0, val isPremium: Boolean = false,
    val publishedAt: String? = null, val unlockTime: String? = null
)
data class ManhwaDetails(
    val novel: Novel, val chapters: List<ManhwaChapter>,
    val sourceSeriesId: Long?, val artist: String, val alternativeTitles: List<String>,
    val type: String, val sourceRating: Float?, val ratingScale: Int = 10,
    val bookmarkCount: String = "", val bannerUrl: String = ""
)
data class ManhwaChapterContent(
    val chapter: ManhwaChapter, val pages: List<ManhwaPage>,
    val previousChapterUrl: String?, val nextChapterUrl: String?,
    /** Apply to image requests; never send account/API credentials. */
    val imageHeaders: Map<String, String>
)
