package com.novelnest.app.data.remote

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.feature.reader.Chapter

interface NovelSource {
    val sourceType: NovelSourceType

    suspend fun getHomeSections(): Result<List<NovelSection>>

    suspend fun getBrowsePage(url: String? = null): Result<com.novelnest.app.data.model.BrowsePage> =
        getHomeSections().map { sections ->
            val novels = sections.flatMap { it.novels }.distinctBy { it.id }
            val categories = novels.flatMap { it.genres }.filter { it.isNotBlank() }.distinct()
                .map { com.novelnest.app.data.model.BrowseCategory(it, "local:$it") }
            val genre = url?.takeIf { it.startsWith("local:") }?.removePrefix("local:")
            com.novelnest.app.data.model.BrowsePage(
                if (genre == null) novels else novels.filter { novel -> novel.genres.any { it.equals(genre, true) } }, categories)
        }

    suspend fun search(query: String): Result<List<Novel>>

    /** Genuine source rankings, without placeholder fallback records. */
    suspend fun getPopularNovels(): Result<List<Novel>> = Result.success(emptyList())

    suspend fun getNovelDetails(novelUrl: String): Result<Novel>

    suspend fun getChapters(novelSlugOrUrl: String, forceRefresh: Boolean = false): Result<List<Chapter>> =
        Result.success(emptyList())

    fun getChaptersFlow(novelSlugOrUrl: String, forceRefresh: Boolean = false): kotlinx.coroutines.flow.Flow<Result<List<Chapter>>> =
        kotlinx.coroutines.flow.flow {
            emit(getChapters(novelSlugOrUrl, forceRefresh))
        }

    suspend fun getChapterContent(chapterUrlOrId: String, forceRefresh: Boolean = false): Result<Chapter> =
        Result.failure(UnsupportedOperationException("Chapter reading not implemented for $sourceType"))
}
