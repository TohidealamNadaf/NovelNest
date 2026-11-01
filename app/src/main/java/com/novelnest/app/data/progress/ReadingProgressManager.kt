package com.novelnest.app.data.progress

import com.novelnest.app.data.database.dao.ReadingProgressDao
import com.novelnest.app.data.database.entity.ReadingProgressEntity
import com.novelnest.app.data.model.ManhwaChapter
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelIdentity
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
    val readChapterIds: Set<String> = emptySet(),
    val manhwaPosition: ManhwaPagePosition? = null
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
                        readChapterIds = entity.readChapterIds.toSet(),
                        manhwaPosition = ManhwaPagePosition.validated(entity.manhwaPageIndex, entity.manhwaPageOffset, entity.manhwaPageCount)
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
        chapterTitle: String,
        completed: Boolean = true
    ) {
        val timestamp = System.currentTimeMillis()
        // Derive from the current map inside the atomic update so hydration cannot
        // insert stored history between our read and write.
        _progressMap.update { current ->
            current + (novelId to NovelProgress(
                novelId = novelId, lastReadChapterId = chapterId,
                lastReadChapterIndex = chapterIndex, lastReadChapterTitle = chapterTitle,
                lastReadTimestamp = timestamp,
                readChapterIds = current[novelId]?.readChapterIds.orEmpty() + if (completed) setOf(chapterId) else emptySet()
            ))
        }

        runCatching { onChapterRead(timestamp) }

        persistProgress(novelId)
    }

    /** Match prose read-status semantics after a valid chapter manifest opens, using a separate API. */
    @Synchronized
    fun recordManhwaChapterOpened(novel: Novel, chapter: ManhwaChapter, chapterIndex: Int, pageCount: Int): ManhwaPagePosition? {
        if (!novel.source.isManhwa || chapter.id.isBlank() || chapterIndex < 0 || pageCount <= 0) return null
        val id = NovelIdentity.normalize(novel).id
        val old = _progressMap.value[id]
        val saved = old?.takeIf { it.lastReadChapterId == chapter.id }?.manhwaPosition
        val position = ManhwaPagePosition.validated(saved?.pageIndex?.coerceIn(0, pageCount - 1) ?: 0,
            saved?.offsetFraction ?: 0f, pageCount) ?: return null
        val timestamp = System.currentTimeMillis()
        _progressMap.update { current ->
            current + (id to NovelProgress(id, chapter.id, chapterIndex, chapter.title, timestamp,
                current[id]?.readChapterIds.orEmpty() + chapter.id, position))
        }
        runCatching { onChapterRead(timestamp) }
        persistProgress(id)
        return position
    }

    /** Called at scroll idle/background, never for every animation frame. Reject outgoing chapters. */
    @Synchronized
    fun saveManhwaPagePosition(novelId: String, chapterId: String, pageIndex: Int, offsetFraction: Float, pageCount: Int) {
        val position = ManhwaPagePosition.validated(pageIndex, offsetFraction, pageCount) ?: return
        val old = _progressMap.value[novelId] ?: return
        if (old.manhwaPosition == null || old.lastReadChapterId != chapterId || old.manhwaPosition == position) return
        val timestamp = System.currentTimeMillis()
        _progressMap.update { current ->
            val latest = current[novelId] ?: return@update current
            current + (novelId to latest.copy(lastReadTimestamp = timestamp, manhwaPosition = position))
        }
        runCatching { onChapterRead(timestamp) }
        persistProgress(novelId)
    }

    /** End-of-chapter position is independent of the opened/read badge; never add earlier chapter IDs. */
    @Synchronized
    fun markManhwaChapterCompleted(novelId: String, chapterId: String) {
        val old = _progressMap.value[novelId] ?: return
        val position = old.manhwaPosition ?: return
        if (old.lastReadChapterId != chapterId) return
        val finished = position.copy(pageIndex = position.pageCount - 1, offsetFraction = 1f)
        if (chapterId in old.readChapterIds && position == finished) return
        val timestamp = System.currentTimeMillis()
        _progressMap.update { current ->
            val latest = current[novelId] ?: return@update current
            current + (novelId to latest.copy(lastReadTimestamp = timestamp,
                readChapterIds = latest.readChapterIds + chapterId,
                manhwaPosition = finished))
        }
        runCatching { onChapterRead(timestamp) }
        persistProgress(novelId)
    }

    private fun persistProgress(novelId: String) {
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
                            readChapterIds = latest.readChapterIds.toList(),
                            manhwaPageIndex = latest.manhwaPosition?.pageIndex ?: -1,
                            manhwaPageOffset = latest.manhwaPosition?.offsetFraction ?: 0f,
                            manhwaPageCount = latest.manhwaPosition?.pageCount ?: 0
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
