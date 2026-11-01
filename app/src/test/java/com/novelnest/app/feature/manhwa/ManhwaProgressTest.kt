package com.novelnest.app.feature.manhwa

import com.novelnest.app.data.database.dao.ReadingProgressDao
import com.novelnest.app.data.database.entity.ReadingProgressEntity
import com.novelnest.app.data.model.ManhwaChapter
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.progress.ManhwaPagePosition
import com.novelnest.app.data.progress.ReadingProgressManager
import com.novelnest.app.data.progress.readingCompletion
import com.novelnest.app.testsupport.ReaderTestProgressDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManhwaProgressTest {
    private val book = Novel("asura-book", "Manhwa", "", NovelSourceType.ASURA_SCANS,
        "https://asurascans.com/comics/book", chapterCount = 2000)
    private val chapter = ManhwaChapter("asura-book:chapter:83", "Chapter 83", "83", "https://asurascans.com/comics/book/chapter/83")

    @Test fun pageAndOffsetSurviveManagerRecreationAndOpenedCountIsExact() = runTest {
        val dao = ReaderTestProgressDao()
        val manager = ReadingProgressManager(dao, this)
        manager.awaitLoaded()
        manager.recordManhwaChapterOpened(book, chapter, 82, 14)
        assertEquals(1, book.readingCompletion(manager.getProgress(book.id)).read)
        assertEquals(setOf(chapter.id), manager.getProgress(book.id)!!.readChapterIds)
        manager.saveManhwaPagePosition(book.id, chapter.id, 7, .42f, 14)
        advanceUntilIdle()
        val reopened = ReadingProgressManager(dao, this)
        reopened.awaitLoaded()
        assertEquals(82, reopened.getProgress(book.id)!!.lastReadChapterIndex)
        assertEquals(ManhwaPagePosition(7, .42f, 14), reopened.getProgress(book.id)!!.manhwaPosition)
        reopened.markManhwaChapterCompleted(book.id, chapter.id)
        reopened.markManhwaChapterCompleted(book.id, chapter.id)
        advanceUntilIdle()
        assertEquals(setOf(chapter.id), reopened.getProgress(book.id)!!.readChapterIds)
        assertEquals(1, book.readingCompletion(reopened.getProgress(book.id)).read)
        assertEquals(ManhwaPagePosition(13, 1f, 14), reopened.getProgress(book.id)!!.manhwaPosition)
        reopened.recordManhwaChapterOpened(book, chapter, 82, 14)
        assertEquals(1, book.readingCompletion(reopened.getProgress(book.id)).read)
    }

    @Test fun novelMethodsKeepTheirBehaviorAndManhwaMethodsRejectProse() = runTest {
        val manager = ReadingProgressManager(ReaderTestProgressDao(), this)
        manager.awaitLoaded()
        for (source in listOf(NovelSourceType.NOVEL_PHOENIX, NovelSourceType.FREE_WEB_NOVEL)) {
            val novel = Novel("${source.name}-book", "Novel", "", source, "${source.baseUrl}/novel/book")
            manager.markChapterRead(novel.id, "chapter-87", 86, "Chapter 87")
            val before = manager.getProgress(novel.id)
            assertNull(before!!.manhwaPosition)
            assertEquals(setOf("chapter-87"), before.readChapterIds)
            assertNull(manager.recordManhwaChapterOpened(novel, chapter, 82, 14))
            manager.saveManhwaPagePosition(novel.id, "chapter-87", 2, .5f, 14)
            manager.markManhwaChapterCompleted(novel.id, "chapter-87")
            assertEquals(before, manager.getProgress(novel.id))
        }
    }

    @Test fun staleAndInvalidPageCallbacksCannotReplaceTheActiveChapter() = runTest {
        val manager = ReadingProgressManager(ReaderTestProgressDao(), this)
        manager.awaitLoaded()
        manager.recordManhwaChapterOpened(book, chapter, 82, 14)
        manager.saveManhwaPagePosition(book.id, chapter.id, 7, .5f, 14)
        val next = chapter.copy(id = "asura-book:chapter:84", title = "Chapter 84")
        assertEquals(ManhwaPagePosition(0, 0f, 4), manager.recordManhwaChapterOpened(book, next, 83, 4))
        val before = manager.getProgress(book.id)
        manager.saveManhwaPagePosition(book.id, chapter.id, 7, .5f, 14)
        manager.markManhwaChapterCompleted(book.id, chapter.id)
        manager.saveManhwaPagePosition(book.id, next.id, 5, .5f, 4)
        manager.saveManhwaPagePosition(book.id, next.id, 1, Float.NaN, 4)
        assertEquals(before, manager.getProgress(book.id))
    }

    @Test fun unchangedPagePositionDoesNotProduceAnotherDatabaseWrite() = runTest {
        val backing = ReaderTestProgressDao()
        var writes = 0
        val dao = object : ReadingProgressDao by backing {
            override suspend fun insertOrUpdate(progress: ReadingProgressEntity) { writes++; backing.insertOrUpdate(progress) }
        }
        val manager = ReadingProgressManager(dao, this)
        manager.awaitLoaded()
        manager.recordManhwaChapterOpened(book, chapter, 82, 14)
        manager.saveManhwaPagePosition(book.id, chapter.id, 7, .5f, 14)
        advanceUntilIdle()
        val count = writes
        repeat(10) { manager.saveManhwaPagePosition(book.id, chapter.id, 7, .5f, 14) }
        advanceUntilIdle()
        assertEquals(count, writes)
    }

    @Test fun footerRemainsAValidEndWhenTheLastImageHasLeftTheViewport() {
        assertFalse(manhwaChapterEndReached(0, 0, null, 1000, true))
        assertFalse(manhwaChapterEndReached(14, 12, null, 1000, false))
        assertFalse(manhwaChapterEndReached(14, 13, 1600, 1000, false))
        assertTrue(manhwaChapterEndReached(14, 13, 999, 1000, false))
        assertTrue(manhwaChapterEndReached(14, 14, null, 1000, true))
    }

    @Test fun pageGeometryUsesImageFractionsAndClampsFooterToTheFinalImage() {
        assertEquals(ManhwaPagePosition(2, .5f, 14), manhwaPagePosition(2, 5000, 10000, 14))
        assertEquals(ManhwaPagePosition(13, 1f, 14), manhwaPagePosition(14, 100, 400, 14))
        assertNull(manhwaPagePosition(2, 500, 0, 14))
        assertNull(manhwaPagePosition(0, 0, 0, 0))
        assertNull(ManhwaPagePosition.validated(2, Float.POSITIVE_INFINITY, 14))
    }
}
