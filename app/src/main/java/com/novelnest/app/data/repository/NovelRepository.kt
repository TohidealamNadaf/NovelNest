package com.novelnest.app.data.repository

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface NovelRepository {
    fun getHomeSections(source: NovelSourceType, forceRefresh: Boolean = false): Flow<Result<List<NovelSection>>>

    suspend fun getBrowsePage(source: NovelSourceType, url: String? = null, forceRefresh: Boolean = false): Result<com.novelnest.app.data.model.BrowsePage> =
        Result.failure(UnsupportedOperationException("Catalog browsing is unavailable"))

    suspend fun searchNovels(source: NovelSourceType, query: String): Result<List<Novel>>

    suspend fun getPopularNovels(source: NovelSourceType, forceRefresh: Boolean = false): Result<List<Novel>> =
        Result.success(emptyList())

    suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String): Result<Novel>

    suspend fun getNovelById(novelId: String): Novel?

    /** Retain the exact clicked record before navigating to details. */
    fun rememberNovel(novel: Novel) {}

    suspend fun getChapters(source: NovelSourceType, novelSlugOrUrl: String, forceRefresh: Boolean = false): Result<List<com.novelnest.app.feature.reader.Chapter>>

    /**
     * Already fetched chapters for navigation; a snapshot does not imply all pages are loaded.
     * Partial refreshes preserve known chapters; removals require successful full completion.
     */
    fun observeChapterSnapshots(source: NovelSourceType, novelSlugOrUrl: String): StateFlow<List<com.novelnest.app.feature.reader.Chapter>> =
        MutableStateFlow(emptyList())

    fun getChaptersFlow(source: NovelSourceType, novelSlugOrUrl: String, forceRefresh: Boolean = false): Flow<Result<List<com.novelnest.app.feature.reader.Chapter>>> =
        kotlinx.coroutines.flow.flow {
            emit(getChapters(source, novelSlugOrUrl, forceRefresh))
        }

    /** Interactive startup: implementations can avoid warming an unrelated first chapter. */
    fun getReaderChaptersFlow(source: NovelSourceType, novelSlugOrUrl: String): Flow<Result<List<com.novelnest.app.feature.reader.Chapter>>> =
        getChaptersFlow(source, novelSlugOrUrl, forceRefresh = false)

    suspend fun getChapterContent(source: NovelSourceType, chapterUrlOrId: String, forceRefresh: Boolean = false): Result<com.novelnest.app.feature.reader.Chapter>

    /**
     * Resets circuit breaker for a specific source, allowing immediate retries.
     * Should be called when the user explicitly triggers a retry action.
     */
    fun resetSourceCircuitBreaker(source: NovelSourceType) {}
}
