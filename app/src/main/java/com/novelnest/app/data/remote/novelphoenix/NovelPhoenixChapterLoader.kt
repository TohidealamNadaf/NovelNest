package com.novelnest.app.data.remote.novelphoenix

import com.novelnest.app.data.cache.MemoryLruCache
import com.novelnest.app.data.cache.MutexPool
import com.novelnest.app.data.remote.network.SourceCooldownException
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/** Resumable, bounded catalog loading, shared by detail, reader and full-list requests. */
internal class NovelPhoenixChapterLoader(
    private val fetchPage: suspend (String, Int) -> NovelPhoenixParser.PhoenixChapterPageResult,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private data class Session(val epoch: Long, val created: Long,
        var first: NovelPhoenixParser.PhoenixChapterPageResult, var complete: Boolean = false)
    private data class PageKey(val epoch: Long, val page: Int)
    private val sessions = MemoryLruCache<String, Session>(8)
    // No session holds all pages: global page and complete-list budgets bound retained chapter data.
    private val pages = MemoryLruCache<PageKey, Pair<Long, List<PhoenixChapterItem>>>(128)
    private val complete = MemoryLruCache<String, Pair<Long, List<PhoenixChapterItem>>>(4)
    private val locks = MutexPool()
    private val epochs = AtomicLong()
    private val ttl = 15 * 60 * 1000L
    private fun fresh(time: Long) = clock() - time in 0 until ttl

    fun chapters(slug: String, forceRefresh: Boolean): Flow<Result<List<PhoenixChapterItem>>> = flow {
        locks.forKey(slug).withLock {
            val cached = complete[slug]
            if (!forceRefresh && cached != null && fresh(cached.first)) {
                emit(Result.success(cached.second)); return@withLock
            }
            val previous = sessions[slug]?.takeIf { fresh(it.created) }
            var emittedCount = 0
            fun prefix(session: Session, batches: Map<Int, List<PhoenixChapterItem>>): List<PhoenixChapterItem> = buildList {
                val seen = HashSet<String>()
                for (page in 1..session.first.totalPages) {
                    val batch = batches[page] ?: break
                    if (batch.any { it.urlPath in seen }) break
                    batch.forEach { seen.add(it.urlPath) }
                    addAll(batch)
                }
            }
            val priorBatches = mutableMapOf<Int, List<PhoenixChapterItem>>()
            if (previous != null && !previous.complete) {
                priorBatches[1] = previous.first.chapters
                for (page in 2..previous.first.totalPages) {
                    val saved = pages[PageKey(previous.epoch, page)]?.takeIf { fresh(it.first) }?.second ?: continue
                    priorBatches[page] = saved
                }
                val available = prefix(previous, priorBatches)
                if (available.isNotEmpty()) { emit(Result.success(available)); emittedCount = available.size }
            }
            val session = try {
                if (!forceRefresh && previous != null) previous
                else {
                    val first = fetchPage(slug, 1)
                    validate(first, 1, first.ranges[1])
                    val compatible = previous != null && !previous.complete &&
                        previous.first.totalPages <= first.totalPages &&
                        previous.first.ranges.filterKeys { it < previous.first.totalPages } == first.ranges.filterKeys { it < previous.first.totalPages } &&
                        (first.ranges[first.totalPages]?.last ?: Int.MAX_VALUE) >= (previous.first.ranges[previous.first.totalPages]?.last ?: 0) &&
                        previous.first.chapters.map { it.urlPath } == first.chapters.map { it.urlPath }
                    if (compatible) previous!!.apply {
                        if (this.first.totalPages != first.totalPages || this.first.ranges[this.first.totalPages] != first.ranges[first.totalPages]) {
                            pages.remove(PageKey(epoch, this.first.totalPages))
                            pages.remove(PageKey(epoch, first.totalPages))
                        }
                        this.first = first
                    }
                    else Session(epochs.incrementAndGet(), clock(), first).also { sessions[slug] = it; emittedCount = 0 }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (emittedCount == 0 && cached != null) emit(Result.success(cached.second))
                emit(Result.failure(error)); return@withLock
            }
            val batches = mutableMapOf(1 to session.first.chapters)
            for (page in 2..session.first.totalPages) {
                pages[PageKey(session.epoch, page)]?.takeIf { fresh(it.first) }?.let { batches[page] = it.second }
            }
            val available = prefix(session, batches)
            if (available.size > emittedCount) { emit(Result.success(available)); emittedCount = available.size }
            val missing = (2..session.first.totalPages).filter { it !in batches }
            if (missing.isNotEmpty()) session.complete = false
            val failures = sortedMapOf<Int, Throwable>()
            var blocked: SourceCooldownException? = null
            try {
                remaining(slug, session, missing).collect { (page, result) ->
                    val error = result.exceptionOrNull()
                    if (error is SourceCooldownException) throw error // cancel queued/in-flight siblings on a source block
                    if (error != null) failures[page] = error else batches[page] = result.getOrThrow()
                    val ready = prefix(session, batches)
                    if (ready.size > emittedCount) { emit(Result.success(ready)); emittedCount = ready.size }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: SourceCooldownException) { blocked = error }
            if (blocked != null) { emit(Result.failure(blocked)); return@withLock }
            if (failures.isNotEmpty()) {
                emit(Result.failure(IOException("NovelPhoenix chapter page(s) " + failures.keys.take(5).joinToString() + (if (failures.size > 5) "..." else "") +
                    " of " + session.first.totalPages + " could not be loaded. " + emittedCount +
                    " chapters are available; Retry resumes missing pages.", failures.values.first())))
                return@withLock
            }
            val all = prefix(session, batches)
            if (batches.size != session.first.totalPages || all.size != batches.values.sumOf { it.size }) {
                for (page in 2..session.first.totalPages) pages.remove(PageKey(session.epoch, page))
                emit(Result.failure(IOException("NovelPhoenix returned overlapping or incomplete chapter pages. Retry to reload the missing ranges.")))
                return@withLock
            }
            session.complete = true
            // Large catalogs still load correctly; they do not occupy an unbounded complete-list cache.
            if (all.size <= 5000) complete[slug] = clock() to all else complete.remove(slug)
            emit(Result.success(all))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun firstPage(slug: String, forceRefresh: Boolean): List<PhoenixChapterItem> = locks.forKey(slug).withLock {
        val cached = sessions[slug]?.takeIf { fresh(it.created) }
        if (!forceRefresh && cached != null) return@withLock cached.first.chapters
        val first = fetchPage(slug, 1)
        validate(first, 1, first.ranges[1])
        first.chapters
    }

    private fun remaining(slug: String, session: Session, missing: List<Int>): Flow<Pair<Int, Result<List<PhoenixChapterItem>>>> = channelFlow {
        if (missing.isEmpty()) return@channelFlow
        val stopped = java.util.concurrent.atomic.AtomicBoolean(false)
        val queue = Channel<Int>(3)
        launch { try { missing.forEach { queue.send(it) } } finally { queue.close() } }
        repeat(minOf(3, missing.size)) {
            launch(Dispatchers.IO) {
                for (page in queue) {
                    if (stopped.get()) break
                    val result = try {
                        val parsed = fetchPage(slug, page)
                        validate(parsed, page, session.first.ranges[page])
                        pages[PageKey(session.epoch, page)] = clock() to parsed.chapters
                        Result.success(parsed.chapters)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        if (error is SourceCooldownException) stopped.set(true)
                        Result.failure(error)
                    }
                    send(page to result)
                    if (stopped.get()) break
                }
            }
        }
    }.buffer(3)

    private fun validate(page: NovelPhoenixParser.PhoenixChapterPageResult, expected: Int, range: IntRange?) {
        if (page.currentPage != expected) throw IOException("NovelPhoenix returned page " + page.currentPage + " instead of requested page " + expected)
        if (page.totalPages !in 1..NovelPhoenixParser.MAX_CHAPTER_PAGES) throw IOException("Invalid NovelPhoenix pagination size")
        if (page.chapters.isEmpty()) throw IOException("NovelPhoenix chapter page " + expected + " returned no chapters")
        if (page.chapters.distinctBy { it.urlPath }.size != page.chapters.size) throw IOException("Duplicate chapters on NovelPhoenix page " + expected)
        if (range != null && page.chapters.size != range.last - range.first + 1)
            throw IOException("NovelPhoenix chapter page " + expected + " is incomplete for range " + range)
    }
}
