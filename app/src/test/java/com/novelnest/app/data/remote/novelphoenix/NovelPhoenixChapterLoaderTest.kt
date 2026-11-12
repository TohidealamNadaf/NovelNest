package com.novelnest.app.data.remote.novelphoenix

import com.novelnest.app.data.remote.network.SourceCooldownException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class NovelPhoenixChapterLoaderTest {
    private fun page(number: Int, count: Int = 2224): NovelPhoenixParser.PhoenixChapterPageResult {
        val pages = (count + 99) / 100
        val ranges = (1..pages).associateWith { p -> ((p - 1) * 100 + 1)..minOf(p * 100, count) }
        val items = ranges.getValue(number).map { chapter -> PhoenixChapterItem("chapter-" + chapter,
            chapter.toString(), "Chapter " + chapter, "/novel/affinity-chaos/chapter-" + chapter, "", 0) }
        return NovelPhoenixParser.PhoenixChapterPageResult(items, pages, number, ranges)
    }
    @Test fun failureAt1900ResumesOnlyMissingPageAndNeverPromotesPartialCache() = runBlocking {
        val calls = ConcurrentHashMap<Int, AtomicInteger>()
        var failing = true
        val loader = NovelPhoenixChapterLoader(fetchPage = { _, p ->
            calls.computeIfAbsent(p) { AtomicInteger() }.incrementAndGet()
            if (p == 20 && failing) throw IOException("temporary outage")
            page(p)
        })
        val initial = loader.chapters("affinity-chaos", false).toList()
        assertTrue(initial.last().isFailure)
        assertEquals(1900, initial.last { it.isSuccess }.getOrThrow().size)
        assertEquals(23, calls.values.sumOf { it.get() })
        failing = false
        val before = calls.mapValues { it.value.get() }
        val resumed = loader.chapters("affinity-chaos", true).toList()
        assertEquals(1900, resumed.first().getOrThrow().size)
        val complete = resumed.last().getOrThrow()
        assertEquals((1..2224).map { "chapter-" + it }, complete.map { it.id })
        assertEquals(setOf(1, 20), calls.keys.filter { calls.getValue(it).get() > before.getValue(it) }.toSet())
        val total = calls.values.sumOf { it.get() }
        assertEquals(2224, loader.chapters("affinity-chaos", false).last().getOrThrow().size)
        assertEquals(total, calls.values.sumOf { it.get() })
    }
    @Test fun ordinaryRetryDoesNotRefetchSuccessfulPages() = runBlocking {
        val calls = ConcurrentHashMap<Int, AtomicInteger>()
        var fail = true
        val loader = NovelPhoenixChapterLoader(fetchPage = { _, p ->
            calls.computeIfAbsent(p) { AtomicInteger() }.incrementAndGet()
            if (p == 2 && fail) throw IOException("offline")
            page(p, 250)
        })
        assertTrue(loader.chapters("book", false).last().isFailure)
        fail = false
        assertEquals(250, loader.chapters("book", false).last().getOrThrow().size)
        assertEquals(mapOf(1 to 1, 2 to 2, 3 to 1), calls.mapValues { it.value.get() })
    }
    @Test fun cancelledCollectorKeepsFirstPageForTheNextRoute() = runBlocking {
        val calls = ConcurrentHashMap<Int, AtomicInteger>()
        val loader = NovelPhoenixChapterLoader(fetchPage = { _, p ->
            calls.computeIfAbsent(p) { AtomicInteger() }.incrementAndGet(); page(p, 250)
        })
        assertEquals(100, loader.chapters("book", false).first().getOrThrow().size)
        assertEquals(250, loader.chapters("book", false).last().getOrThrow().size)
        assertEquals(1, calls.getValue(1).get())
    }
    @Test fun concurrentCollectorsShareOnePaginationRun() = runBlocking {
        val calls = AtomicInteger()
        val loader = NovelPhoenixChapterLoader(fetchPage = { _, p -> calls.incrementAndGet(); delay(5); page(p, 250) })
        val results = listOf(async { loader.chapters("book", false).last().getOrThrow() },
            async { loader.chapters("book", false).last().getOrThrow() }).awaitAll()
        assertEquals(listOf(250, 250), results.map { it.size }); assertEquals(3, calls.get())
    }
    @Test fun wrongSelectedPageIsRejectedAndNotCachedAsTheRequestedPage() = runBlocking {
        var wrong = true
        val loader = NovelPhoenixChapterLoader(fetchPage = { _, p -> if (p == 2 && wrong) page(1, 250) else page(p, 250) })
        assertTrue(loader.chapters("book", false).last().exceptionOrNull()!!.cause!!.message!!.contains("instead of requested page 2"))
        wrong = false
        assertEquals(250, loader.chapters("book", false).last().getOrThrow().size)
    }
    @Test fun truncatedPageDoesNotBecomeACompleteCatalog() = runBlocking {
        var truncated = true
        val loader = NovelPhoenixChapterLoader(fetchPage = { _, p ->
            page(p, 250).let { if (p == 2 && truncated) it.copy(chapters = it.chapters.dropLast(1)) else it }
        })
        val partial = loader.chapters("book", false).toList()
        assertTrue(partial.last().isFailure); assertEquals(100, partial.last { it.isSuccess }.getOrThrow().size)
        truncated = false
        assertEquals(250, loader.chapters("book", true).last().getOrThrow().size)
    }
    @Test fun cooldownCancelsSiblingWorkAndKeepsItsOriginalError() = runBlocking {
        val starts = AtomicInteger()
        val cooldown = SourceCooldownException(60, 429, false)
        val loader = NovelPhoenixChapterLoader(fetchPage = { _, p ->
            starts.incrementAndGet()
            if (p == 2) throw cooldown
            if (p > 2) delay(10000)
            page(p)
        })
        val result = withTimeout(3000) { loader.chapters("book", false).last() }
        assertSame(cooldown, result.exceptionOrNull()); assertTrue(starts.get() <= 4)
    }
    @Test fun newChaptersOnlyInvalidateTheChangedTailDuringRetry() = runBlocking {
        val calls = ConcurrentHashMap<Int, AtomicInteger>()
        var count = 250
        var failing = true
        val loader = NovelPhoenixChapterLoader(fetchPage = { _, p ->
            calls.computeIfAbsent(p) { AtomicInteger() }.incrementAndGet()
            if (p == 2 && failing) throw IOException("offline")
            page(p, count)
        })
        loader.chapters("book", false).toList()
        count = 255; failing = false
        assertEquals(255, loader.chapters("book", true).last().getOrThrow().size)
        assertEquals(mapOf(1 to 2, 2 to 2, 3 to 2), calls.mapValues { it.value.get() })
    }
    @Test fun expiredCatalogRevalidatesAllPages() = runBlocking {
        val time = AtomicLong(0)
        val calls = AtomicInteger()
        val loader = NovelPhoenixChapterLoader(fetchPage = { _, p -> calls.incrementAndGet(); page(p, 250) }, clock = time::get)
        loader.chapters("book", false).toList()
        time.set(15 * 60 * 1000L + 1)
        assertEquals(250, loader.chapters("book", false).last().getOrThrow().size)
        assertEquals(6, calls.get())
    }
    @Test fun emptyFirstPageIsAnErrorRatherThanASuccessfulEmptyRefresh() = runBlocking {
        val loader = NovelPhoenixChapterLoader(fetchPage = { _, _ -> page(1, 250).copy(chapters = emptyList()) })
        assertTrue(loader.chapters("book", false).last().isFailure)
    }
    @Test fun overlappingPagesAreRejectedAndInvalidatedForRetry() = runBlocking {
        var overlap = true
        val loader = NovelPhoenixChapterLoader(fetchPage = { _, p ->
            page(p, 250).let { if (p == 2 && overlap) it.copy(chapters = page(1, 250).chapters) else it }
        })
        val partial = loader.chapters("book", false).toList()
        assertTrue(partial.last().isFailure)
        assertEquals(100, partial.last { it.isSuccess }.getOrThrow().size)
        overlap = false
        assertEquals(250, loader.chapters("book", true).last().getOrThrow().size)
    }
}
