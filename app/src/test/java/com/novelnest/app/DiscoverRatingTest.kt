package com.novelnest.app

import androidx.lifecycle.ViewModelStore
import com.novelnest.app.data.model.*
import com.novelnest.app.data.repository.*
import com.novelnest.app.data.remote.RatingParser
import com.novelnest.app.data.remote.freewebnovel.*
import com.novelnest.app.data.remote.novelphoenix.NovelPhoenixParser
import com.novelnest.app.feature.discover.DiscoverViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverRatingTest {
    private val np = NovelSourceType.NOVEL_PHOENIX
    private val fwn = NovelSourceType.FREE_WEB_NOVEL
    private fun novel(source: NovelSourceType, suffix: String = "book") = Novel(
        (if (source == fwn) "fwn-" else "np-") + suffix, suffix, "", source,
        source.baseUrl + "/novel/" + suffix, rating = 0f)
    private fun sections(novels: List<Novel>) = listOf(NovelSection("feed", "Feed", novels = novels),
        NovelSection("latest", "Latest", novels = novels))
    private fun fixture(source: String, name: String): String {
        val path = "docs/sources/" + source + "/fixtures/" + name
        return listOf(File(path), File("../" + path)).first { it.exists() }.readText()
    }

    @Test fun visibleDuplicateCardsShareOneLookupAndBothSourcesReceiveTheRealScore() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            for (source in listOf(np, fwn)) {
                val book = novel(source)
                var calls = 0
                val repo = object : NovelRepository by NovelRepositoryImpl(sources = emptyMap()) {
                    override fun getHomeSections(source: NovelSourceType, forceRefresh: Boolean) = flowOf(Result.success(sections(listOf(book))))
                    override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String): Result<Novel> {
                        calls++; delay(100)
                        return Result.success(book.copy(rating = 4.6f))
                    }
                }
                val vm = DiscoverViewModel(repo, source)
                store.put(source.name, vm)
                vm.ratingVisibility(book, true); vm.ratingVisibility(book, true)
                advanceTimeBy(650); runCurrent()
                assertEquals(1, calls)
                vm.ratingVisibility(book, false)
                advanceUntilIdle()
                assertTrue(vm.uiState.value.sections.all { it.novels.single().rating == 4.6f })
                vm.refresh(); advanceUntilIdle()
                assertTrue(vm.uiState.value.sections.all { it.novels.single().rating == 4.6f })
                assertEquals(1, calls)
            }
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun visibilityCancellationBoundsConcurrencyAndSkipsQueuedOffscreenCards() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val books = (1..6).map { novel(np, "book-" + it) }
            var calls = 0; var active = 0; var maximum = 0
            val repo = object : NovelRepository by NovelRepositoryImpl(sources = emptyMap()) {
                override fun getHomeSections(source: NovelSourceType, forceRefresh: Boolean) = flowOf(Result.success(sections(books)))
                override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String): Result<Novel> {
                    calls++; active++; maximum = maxOf(maximum, active)
                    try { delay(100); return Result.success(books.first { it.sourceUrl == novelUrl }.copy(rating = 4.3f)) }
                    finally { active-- }
                }
            }
            val vm = DiscoverViewModel(repo); store.put("vm", vm)
            books.forEach { vm.ratingVisibility(it, true) }
            books.drop(2).forEach { vm.ratingVisibility(it, false) }
            advanceUntilIdle()
            assertEquals(2, maximum); assertEquals(2, calls)
            assertEquals(2, vm.uiState.value.sections.first().novels.count { it.hasRating() })
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun failedRatingsDoNotFailTheFeedOrRetryOnEveryRecomposition() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val book = novel(fwn); var calls = 0
            val repo = object : NovelRepository by NovelRepositoryImpl(sources = emptyMap()) {
                override fun getHomeSections(source: NovelSourceType, forceRefresh: Boolean) = flowOf(Result.success(sections(listOf(book))))
                override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String): Result<Novel> {
                    calls++; return Result.failure(IOException("Source blocked"))
                }
            }
            val vm = DiscoverViewModel(repo, fwn); store.put("vm", vm)
            vm.ratingVisibility(book, true); advanceUntilIdle(); vm.ratingVisibility(book, false)
            repeat(5) { vm.ratingVisibility(book, true); vm.ratingVisibility(book, false) }
            advanceUntilIdle()
            assertEquals(1, calls); assertNull(vm.uiState.value.errorMessage)
            assertEquals(2, vm.uiState.value.sections.size)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun sourceBlockStopsFurtherCardRequestsAndRefreshCanRecover() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val books = (1..6).map { novel(fwn, "book-" + it) }
            var blocked = true; var calls = 0
            val repo = object : NovelRepository by NovelRepositoryImpl(sources = emptyMap()) {
                override fun getHomeSections(source: NovelSourceType, forceRefresh: Boolean) = flowOf(Result.success(sections(books)))
                override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String): Result<Novel> {
                    calls++
                    return if (blocked) Result.failure(FreeWebNovelException.Blocked())
                        else Result.success(books.first { it.sourceUrl == novelUrl }.copy(rating = 4.2f))
                }
            }
            val vm = DiscoverViewModel(repo, fwn); store.put("vm", vm)
            books.forEach { vm.ratingVisibility(it, true) }
            advanceUntilIdle()
            assertEquals(1, calls)
            blocked = false; vm.refresh(); advanceUntilIdle()
            assertTrue(vm.uiState.value.sections.first().novels.all { it.rating == 4.2f })
            assertNull(vm.uiState.value.errorMessage)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun fastScrollingAndBackgroundingSkipRequestsAndResumeVisibleCards() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val book = novel(fwn); var calls = 0
            val repo = object : NovelRepository by NovelRepositoryImpl(sources = emptyMap()) {
                override fun getHomeSections(source: NovelSourceType, forceRefresh: Boolean) = flowOf(Result.success(sections(listOf(book))))
                override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String): Result<Novel> {
                    calls++; return Result.success(book.copy(rating = 4.4f))
                }
            }
            val vm = DiscoverViewModel(repo, fwn); store.put("vm", vm)
            repeat(10) { vm.ratingVisibility(book, true); advanceTimeBy(100); vm.ratingVisibility(book, false) }
            advanceUntilIdle(); assertEquals(0, calls)
            vm.ratingVisibility(book, true); advanceTimeBy(300)
            vm.setRatingActive(false); advanceUntilIdle(); assertEquals(0, calls)
            vm.setRatingActive(true); advanceUntilIdle(); assertEquals(1, calls)
            assertEquals(4.4f, vm.uiState.value.sections.first().novels.single().rating, 0f)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun cancelledStartedLookupKeepsCooldownAcrossScrolling() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val book = novel(np); var calls = 0; var cancellations = 0
            val repo = object : NovelRepository by NovelRepositoryImpl(sources = emptyMap()) {
                override fun getHomeSections(source: NovelSourceType, forceRefresh: Boolean) = flowOf(Result.success(sections(listOf(book))))
                override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String): Result<Novel> {
                    calls++
                    try { awaitCancellation() } finally { cancellations++ }
                }
            }
            val vm = DiscoverViewModel(repo); store.put("vm", vm)
            vm.ratingVisibility(book, true); advanceTimeBy(650); runCurrent()
            assertEquals(1, calls)
            vm.ratingVisibility(book, false); runCurrent()
            vm.ratingVisibility(book, true); advanceUntilIdle()
            assertEquals(1, calls); assertEquals(1, cancellations)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun structuredRatingsSupportGraphsAndScalesWithoutFabricatedDefaults() {
        val html = "<script type='application/ld+json'>{\"@graph\":[{\"@type\":\"Book\",\"aggregateRating\":{\"ratingValue\":8.6,\"bestRating\":10,\"ratingCount\":20}}]}</script>"
        assertEquals(4.3f, RatingParser.structured(Jsoup.parse(html))!!, 0.001f)
        assertNull(RatingParser.score("Rank 5")); assertNull(RatingParser.score("0.0"))
        assertEquals(4.6f, RatingParser.score("4,6 / 5")!!, 0.001f)
        assertNull(RatingParser.structured(Jsoup.parse("<script type='application/ld+json'>bad json</script>")))
    }

    @Test fun phoenixDetailUsesWidgetAttributesWhenJsonLdAndVisibleTextAreMissing() {
        val doc = Jsoup.parse(fixture("novelphoenix", "detail_shadow_slave.html"))
        doc.select("script[type=application/ld+json], .rating strong.nub").remove()
        doc.select(".my-rating").attr("data-rating", "4.2")
        assertEquals(4.2f, NovelPhoenixParser.parseNovelDetail(doc.html()).rating, 0.001f)
        doc.select(".my-rating").removeAttr("data-rating")
        assertEquals(0f, NovelPhoenixParser.parseNovelDetail(doc.html()).rating, 0f)
    }

    @Test fun fwnDetailUsesStructuredScoreThenDataScoreWhenVisibleVoteIsAbsent() {
        val doc = Jsoup.parse(fixture("freewebnovel", "detail_shadow_slave.html"))
        doc.select(".score .vote").remove()
        val url = fwn.baseUrl + "/novel/shadow-slave"
        assertEquals(4.6f, FreeWebNovelParser.parseDetails(doc.html(), url).rating, 0.001f)
        doc.select("script[type=application/ld+json]").remove()
        doc.select("#indexListPage").attr("data-score", "4.2")
        assertEquals(4.2f, FreeWebNovelParser.parseDetails(doc.html(), url).rating, 0.001f)
    }

    @Test fun duplicateSectionCardsReuseRatingsWithoutMixingSources() {
        val books = listOf(novel(fwn), novel(np))
        val feeds = sections(books) + NovelSection("rated", "Rated", novels = listOf(books.first().copy(rating = 4.6f)))
        val resolved = feeds.reuseRatings()
        assertEquals(4.6f, resolved.first().novels.first().rating, 0f)
        assertEquals(0f, resolved.first().novels.last().rating, 0f)
    }
}
