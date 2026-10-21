package com.novelnest.app.data.progress

import com.novelnest.app.data.database.dao.ReadingProgressDao
import com.novelnest.app.data.database.entity.ReadingProgressEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NovelProgress(
    val novelId: String,
    val lastReadChapterId: String,
    val lastReadChapterIndex: Int,
    val lastReadChapterTitle: String,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val readChapterIds: Set<String> = emptySet()
)

class ReadingProgressManager(
    private val readingProgressDao: ReadingProgressDao,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    private val _progressMap = MutableStateFlow<Map<String, NovelProgress>>(emptyMap())
    val progressMap: StateFlow<Map<String, NovelProgress>> = _progressMap.asStateFlow()

    init {
        // Load existing reading progress from Room on initialization
        coroutineScope.launch {
            try {
                val allEntities = readingProgressDao.getAllProgressOnce()
                val mapped = allEntities.associate { entity ->
                    entity.novelId to NovelProgress(
                        novelId = entity.novelId,
                        lastReadChapterId = entity.lastReadChapterId,
                        lastReadChapterIndex = entity.lastReadChapterIndex,
                        lastReadChapterTitle = entity.lastReadChapterTitle,
                        lastReadTimestamp = entity.lastReadTimestamp,
                        readChapterIds = entity.readChapterIds.toSet()
                    )
                }
                _progressMap.value = mapped
            } catch (_: Exception) {
                // Fallback to empty if initial query fails
            }
        }
    }

    fun getProgress(novelId: String): NovelProgress? {
        return _progressMap.value[novelId]
    }

    fun isChapterRead(novelId: String, chapterId: String): Boolean {
        val progress = _progressMap.value[novelId] ?: return false
        return progress.readChapterIds.contains(chapterId)
    }

    fun markChapterRead(
        novelId: String,
        chapterId: String,
        chapterIndex: Int,
        chapterTitle: String
    ) {
        val currentProgress = _progressMap.value[novelId]
        val updatedSet = (currentProgress?.readChapterIds ?: emptySet()) + chapterId
        val updatedProgress = NovelProgress(
            novelId = novelId,
            lastReadChapterId = chapterId,
            lastReadChapterIndex = chapterIndex,
            lastReadChapterTitle = chapterTitle,
            lastReadTimestamp = System.currentTimeMillis(),
            readChapterIds = updatedSet
        )

        // Instant UI update
        _progressMap.update { current ->
            current + (novelId to updatedProgress)
        }

        // Persist to Room database asynchronously
        coroutineScope.launch {
            try {
                readingProgressDao.insertOrUpdate(
                    ReadingProgressEntity(
                        novelId = novelId,
                        lastReadChapterId = chapterId,
                        lastReadChapterIndex = chapterIndex,
                        lastReadChapterTitle = chapterTitle,
                        lastReadTimestamp = updatedProgress.lastReadTimestamp,
                        readChapterIds = updatedSet.toList()
                    )
                )
            } catch (_: Exception) {
                // Database persistence error ignored to keep UI responsive
            }
        }
    }
}
