package com.novelnest.app.data.remote

import com.novelnest.app.data.model.ManhwaChapterContent
import com.novelnest.app.data.model.ManhwaDetails

/** Separate image-reader contract; image URLs must never be treated as prose paragraphs. */
interface ManhwaSource : NovelSource {
    suspend fun getManhwaDetails(seriesUrlOrId: String, forceRefresh: Boolean = false): Result<ManhwaDetails>
    suspend fun getManhwaChapter(chapterUrl: String, forceRefresh: Boolean = false): Result<ManhwaChapterContent>
}
