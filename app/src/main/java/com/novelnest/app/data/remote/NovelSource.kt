package com.novelnest.app.data.remote

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType

interface NovelSource {
    val sourceType: NovelSourceType

    suspend fun getHomeSections(): Result<List<NovelSection>>

    suspend fun search(query: String): Result<List<Novel>>

    suspend fun getNovelDetails(novelUrl: String): Result<Novel>
}
