package com.novelnest.app.testsupport

import android.content.ContextWrapper
import android.content.SharedPreferences
import com.novelnest.app.data.database.dao.ReadingProgressDao
import com.novelnest.app.data.database.entity.ReadingProgressEntity
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.repository.NovelRepository
import com.novelnest.app.feature.reader.Chapter
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.flowOf

internal open class ReaderTestRepository(var local: Novel? = null) : NovelRepository {
    var chapterCalls = 0
    var contentCalls = 0
    override fun getHomeSections(source: NovelSourceType, forceRefresh: Boolean) = flowOf(Result.success(emptyList<NovelSection>()))
    override suspend fun searchNovels(source: NovelSourceType, query: String) = Result.success(emptyList<Novel>())
    override suspend fun getNovelById(novelId: String) = local
    override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String) = Result.success(checkNotNull(local))
    override suspend fun getChapters(source: NovelSourceType, novelSlugOrUrl: String, forceRefresh: Boolean): Result<List<Chapter>> {
        chapterCalls++
        return Result.success(emptyList())
    }
    override suspend fun getChapterContent(source: NovelSourceType, chapterUrlOrId: String, forceRefresh: Boolean): Result<Chapter> {
        contentCalls++
        return Result.failure(UnsupportedOperationException())
    }
}

internal class ReaderTestProgressDao : ReadingProgressDao {
    private val records = mutableMapOf<String, ReadingProgressEntity>()
    override fun getProgress(novelId: String) = flowOf(records[novelId])
    override suspend fun getProgressOnce(novelId: String) = records[novelId]
    override fun getAllProgress() = flowOf(records.values.toList())
    override suspend fun getAllProgressOnce() = records.values.toList()
    override suspend fun insertOrUpdate(progress: ReadingProgressEntity) { records[progress.novelId] = progress }
    override suspend fun deleteProgress(novelId: String) { records.remove(novelId) }
}

internal fun readerTestContext(): ContextWrapper {
    val values = mutableMapOf<String, Any?>()
    val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
        when {
            method.name.startsWith("put") -> { values[args[0] as String] = args[1]; proxy }
            method.name == "remove" -> { values.remove(args[0] as String); proxy }
            method.name == "apply" -> null
            method.name == "commit" -> true
            else -> error(method.name)
        }
    } as SharedPreferences.Editor
    val prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
        when {
            method.name.startsWith("get") -> values[args[0] as String] ?: args[1]
            method.name == "contains" -> values.containsKey(args[0] as String)
            method.name == "edit" -> editor
            else -> error(method.name)
        }
    } as SharedPreferences
    return object : ContextWrapper(null) {
        override fun getSharedPreferences(name: String, mode: Int) = prefs
    }
}
