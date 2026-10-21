package com.novelnest.app.data.remote

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.feature.reader.Chapter

interface NovelSource {
    val sourceType: NovelSourceType

    suspend fun getHomeSections(): Result<List<NovelSection>>

    suspend fun search(query: String): Result<List<Novel>>

    suspend fun getNovelDetails(novelUrl: String): Result<Novel>

    suspend fun getChapters(novelSlugOrUrl: String, forceRefresh: Boolean = false): Result<List<Chapter>> =
        Result.success(emptyList())

    suspend fun getChapterContent(chapterUrlOrId: String, forceRefresh: Boolean = false): Result<Chapter> =
        Result.failure(UnsupportedOperationException("Chapter reading not implemented for $sourceType"))
}
