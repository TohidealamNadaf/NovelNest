package com.novelnest.app.data.repository

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.remote.AsuraSource
import com.novelnest.app.data.remote.FreeWebNovelSource
import com.novelnest.app.data.remote.NovelFireSource
import com.novelnest.app.data.remote.NovelSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap

class NovelRepositoryImpl(
    private val sources: Map<NovelSourceType, NovelSource> = mapOf(
        NovelSourceType.NOVEL_FIRE to NovelFireSource(),
        NovelSourceType.FREE_WEB_NOVEL to FreeWebNovelSource(),
        NovelSourceType.ASURA_SCANS to AsuraSource()
    )
) : NovelRepository {

    private val sectionCache = ConcurrentHashMap<NovelSourceType, List<NovelSection>>()

    override fun getHomeSections(
        source: NovelSourceType,
        forceRefresh: Boolean
    ): Flow<Result<List<NovelSection>>> = flow {
        // Emit cached data first if available (supports fast UI rendering & offline use)
        val cached = sectionCache[source]
        if (cached != null && !forceRefresh) {
            emit(Result.success(cached))
            return@flow
        }

        val remoteSource = sources[source]
        if (remoteSource == null) {
            emit(Result.failure(IllegalArgumentException("Source $source not found")))
            return@flow
        }

        val result = remoteSource.getHomeSections()
        result.onSuccess { sections ->
            sectionCache[source] = sections
        }
        emit(result)
    }

    override suspend fun searchNovels(source: NovelSourceType, query: String): Result<List<Novel>> {
        val remoteSource = sources[source] ?: return Result.failure(IllegalArgumentException("Unknown source $source"))
        return remoteSource.search(query)
    }

    override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String): Result<Novel> {
        val remoteSource = sources[source] ?: return Result.failure(IllegalArgumentException("Unknown source $source"))
        return remoteSource.getNovelDetails(novelUrl)
    }
}
