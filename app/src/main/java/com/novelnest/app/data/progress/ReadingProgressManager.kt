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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val onChapterRead: (Long) -> Unit = {},
    private val onHistoryLoaded: (List<Long>) -> Unit = {}
) {

    private val _progressMap = MutableStateFlow<Map<String, NovelProgress>>(emptyMap())
    val progressMap: StateFlow<Map<String, NovelProgress>> = _progressMap.asStateFlow()

    private val loaded = CompletableDeferred<Unit>()
    private val writes = Mutex()

    suspend fun awaitLoaded() = loaded.await()

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
                runCatching { onHistoryLoaded(mapped.values.map { it.lastReadTimestamp }) }
                _progressMap.update { current ->
                    (mapped.keys + current.keys).associateWith { id ->
                        val stored = mapped[id]
                        val live = current[id]
                        when {
                            live == null -> stored!!
                            stored == null -> live
                            else -> (if (live.lastReadTimestamp >= stored.lastReadTimestamp) live else stored)
                                .copy(readChapterIds = live.readChapterIds + stored.readChapterIds)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Existing live updates remain available if loading fails.
            } finally {
                loaded.complete(Unit)
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

    @Synchronized
    fun markChapterRead(
        novelId: String,
        chapterId: String,
        chapterIndex: Int,
        chapterTitle: String
    ) {
        val timestamp = System.currentTimeMillis()
        // Derive from the current map inside the atomic update so hydration cannot
        // insert stored history between our read and write.
        _progressMap.update { current ->
            current + (novelId to NovelProgress(
                novelId = novelId, lastReadChapterId = chapterId,
                lastReadChapterIndex = chapterIndex, lastReadChapterTitle = chapterTitle,
                lastReadTimestamp = timestamp,
                readChapterIds = current[novelId]?.readChapterIds.orEmpty() + chapterId
            ))
        }

        runCatching { onChapterRead(timestamp) }

        // Persist to Room database asynchronously
        coroutineScope.launch {
            loaded.await()
            writes.withLock {
                try {
                    val latest = _progressMap.value[novelId] ?: return@withLock
                    readingProgressDao.insertOrUpdate(
                        ReadingProgressEntity(
                            novelId = novelId,
                            lastReadChapterId = latest.lastReadChapterId,
                            lastReadChapterIndex = latest.lastReadChapterIndex,
                            lastReadChapterTitle = latest.lastReadChapterTitle,
                            lastReadTimestamp = latest.lastReadTimestamp,
                            readChapterIds = latest.readChapterIds.toList()
                        )
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Database persistence error ignored to keep UI responsive
                }
            }
        }
    }
}
