package com.novelnest.app.data.repository

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import kotlinx.coroutines.flow.Flow

interface NovelRepository {
    fun getHomeSections(source: NovelSourceType, forceRefresh: Boolean = false): Flow<Result<List<NovelSection>>>

    suspend fun searchNovels(source: NovelSourceType, query: String): Result<List<Novel>>

    suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String): Result<Novel>

    suspend fun getChapters(source: NovelSourceType, novelSlugOrUrl: String, forceRefresh: Boolean = false): Result<List<com.novelnest.app.feature.reader.Chapter>>
}

