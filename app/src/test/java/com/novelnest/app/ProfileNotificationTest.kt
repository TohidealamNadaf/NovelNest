package com.novelnest.app

import android.content.SharedPreferences
import com.novelnest.app.data.bookmark.BookmarkManager
import com.novelnest.app.data.database.dao.ReadingProgressDao
import com.novelnest.app.data.database.entity.ReadingProgressEntity
import com.novelnest.app.data.model.*
import com.novelnest.app.data.notification.*
import com.novelnest.app.data.profile.*
import com.novelnest.app.data.progress.ReadingProgressManager
import com.novelnest.app.data.remote.bookmarkContext
import com.novelnest.app.data.repository.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.ZoneId

class ProfileNotificationTest {
    private fun prefs(): SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when {
                method.name.startsWith("put") -> { values[args[0] as String] = args[1]; proxy }
                method.name == "apply" -> null
                method.name == "commit" -> true
                else -> error(method.name)
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when {
                method.name.startsWith("get") -> values[args[0] as String] ?: args[1]
                method.name == "edit" -> editor
                else -> error(method.name)
            }
        } as SharedPreferences
    }
    private fun book(index: Int = 1, source: NovelSourceType = NovelSourceType.NOVEL_PHOENIX) =
        Novel((if (source == NovelSourceType.FREE_WEB_NOVEL) "fwn-" else "np-") + "book-$index",
            "Book $index", "", source, "${source.baseUrl}/novel/book-$index",
            latestChapter = "Chapter 100: Old", chapterCount = 100)

    @Test fun bookmarkAddsEmitOnceAndRestoringOrRefreshingMetadataEmitsNothing() {
        val context = bookmarkContext()
        val emitted = mutableListOf<AppNotification>()
        val bookmarks = BookmarkManager(context) { emitted += bookmarkConfirmation(it, 1000) }
        val novel = book(42)
        assertTrue(bookmarks.toggleBookmark(novel))
        assertEquals(1, emitted.size)
        bookmarks.addBookmark(novel.copy(chapterCount = 110))
        assertEquals(1, emitted.size)
        val restored = BookmarkManager(context) { emitted += bookmarkConfirmation(it, 1000) }
        assertTrue(restored.isBookmarked(novel.id))
        assertEquals(1, emitted.size)
        assertFalse(restored.toggleBookmark(novel))
        assertEquals(1, emitted.size)
        restored.addBookmark(novel)
        assertEquals(2, emitted.size)
        assertNotEquals(emitted[0].id, emitted[1].id)
        assertTrue(emitted.all { it.bookmark && !it.reminder && it.novelId == novel.id })
    }

    @Test fun bookmarkConfirmationSettingsAreIndependentAndPersistWithTheirEventType() {
        val storage = prefs()
        val store = NotificationStore(storage)
        val confirmation = bookmarkConfirmation(book(), 1000)
        val chapter = AppNotification("chapter", "Update", "New chapter", 1000, book().id)
        val reminder = AppNotification("reminder", "Reminder", "Read", 1000, reminder = true)
        assertFalse(store.settings.value.allows(confirmation))
        store.configure(enabled = true, chapters = false, reminders = false)
        assertTrue(store.settings.value.allows(confirmation))
        assertFalse(store.settings.value.allows(chapter))
        assertFalse(store.settings.value.allows(reminder))
        store.add(confirmation)
        val restored = NotificationStore(storage)
        assertTrue(restored.items.value.single().bookmark)
        assertTrue(restored.settings.value.bookmarkConfirmations)
        restored.configure(bookmarks = false)
        assertFalse(NotificationStore(storage).settings.value.allows(confirmation))
    }

    @Test fun newBookmarksFromEverySourceProduceConfirmationEvents() {
        val emitted = mutableListOf<AppNotification>()
        val bookmarks = BookmarkManager(bookmarkContext()) { emitted += bookmarkConfirmation(it) }
        NovelSourceType.entries.forEachIndexed { index, source -> bookmarks.addBookmark(book(index + 200, source)) }
        assertEquals(NovelSourceType.entries.size, emitted.size)
        assertEquals(emitted.size, emitted.map { it.id }.toSet().size)
        assertTrue(emitted.all { it.bookmark && it.message.contains("saved to your library") })
    }

    @Test fun notificationCallbackFailureCannotUndoASuccessfulBookmark() {
        val bookmarks = BookmarkManager(bookmarkContext()) { throw IllegalStateException("Platform unavailable") }
        assertTrue(bookmarks.toggleBookmark(book(50)))
        assertTrue(bookmarks.isBookmarked(book(50).id))
    }

    @Test fun profileNamePhotoAndReadingDaysPersistWithoutDuplicateDays() {
        val prefs = prefs()
        val manager = ProfileManager(prefs)
        manager.saveNickname("   Page Wanderer   ")
        manager.savePhoto("content://photos/chosen")
        val yesterday = LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        manager.recordReading(yesterday)
        manager.recordReading(yesterday + 1_000)
        val restored = ProfileManager(prefs)
        assertEquals("Page Wanderer", restored.profile.value.nickname)
        assertEquals("content://photos/chosen", restored.profile.value.photoUri)
        assertEquals(1, restored.profile.value.readingDays.size)
        restored.saveNickname(" ")
        assertEquals("Reader", restored.profile.value.nickname)
        restored.saveNickname("x".repeat(60))
        assertEquals(40, restored.profile.value.nickname.length)
        restored.savePhoto(null)
        assertNull(ProfileManager(prefs).profile.value.photoUri)
    }

    @Test fun streakSupportsTodayYesterdayGraceGapsAndHistoricalBest() {
        val today = LocalDate.of(2026, 10, 22).toEpochDay()
        assertEquals(ReadingStreak(0, 0, 0), readingStreak(emptySet(), today))
        val days = setOf(today, today - 1, today - 2, today - 5, today - 6, today - 7, today - 8)
        assertEquals(ReadingStreak(3, 4, 7), readingStreak(days, today))
        assertEquals(ReadingStreak(2, 4, 6), readingStreak(days - today, today))
        assertEquals(0, readingStreak(setOf(today - 2), today).current)
        assertEquals(0, readingStreak(setOf(today + 1), today).days)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun successfulChapterReadsAndStoredDatesFeedStreakWithoutOpeningProfile() = runTest {
        val profile = ProfileManager(prefs())
        val yesterday = LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dao = MemoryProgressDao()
        dao.rows["np-book"] = ReadingProgressEntity("np-book", "chapter-1", 0, "One", yesterday, listOf("chapter-1"))
        val reading = ReadingProgressManager(dao, this,
            onChapterRead = profile::recordReading, onHistoryLoaded = profile::recordReadings)
        runCurrent()
        reading.markChapterRead("np-book", "chapter-2", 1, "Two")
        reading.markChapterRead("np-book", "chapter-3", 2, "Three")
        advanceUntilIdle()
        assertEquals(2, profile.profile.value.readingDays.size)
        assertEquals(2, readingStreak(profile.profile.value.readingDays).current)
    }

    @Test fun markerNeedsActualForwardMovementNotRenameOrRollback() {
        assertTrue(hasNewChapters(ChapterMarker(100, "Old"), ChapterMarker(101, "New")))
        assertFalse(hasNewChapters(ChapterMarker(100, "Old"), ChapterMarker(100, "Renamed")))
        assertFalse(hasNewChapters(ChapterMarker(100, "Old"), ChapterMarker(99, "Older")))
        assertTrue(hasNewChapters(ChapterMarker(0, "Chapter 80: Before"), ChapterMarker(0, "Chapter 81: Next")))
        assertFalse(hasNewChapters(ChapterMarker(0, "Unknown"), ChapterMarker(0, "Something else")))
    }

    @Test fun remindersRequire24HoursBackgroundAndOnePerInactivePeriod() {
        val active = 1_000L
        assertFalse(reminderDue(active + INACTIVITY_MILLIS - 1, active, -1, false))
        assertTrue(reminderDue(active + INACTIVITY_MILLIS, active, -1, false))
        assertFalse(reminderDue(active + INACTIVITY_MILLIS, active, -1, true))
        assertFalse(reminderDue(active + INACTIVITY_MILLIS * 5, active, active, false))
        assertFalse(reminderDue(active, 0, -1, false))
        assertTrue(reminderDue(active + INACTIVITY_MILLIS * 2, active + INACTIVITY_MILLIS, active, false))
    }

    @Test fun inboxIsDurableDeduplicatedBoundedAndReadStateIsShared() {
        val prefs = prefs()
        val store = NotificationStore(prefs)
        repeat(105) { i -> assertTrue(store.add(AppNotification("id-$i", "Title", "Message", i.toLong(), "np-book"))) }
        assertEquals(100, store.items.value.size)
        assertFalse(store.add(store.items.value.first()))
        store.markRead("id-104")
        assertTrue(NotificationStore(prefs).items.value.first().read)
        store.markAllRead()
        assertTrue(NotificationStore(prefs).items.value.all { it.read })
        store.configure(true, chapters = false, reminders = true)
        assertEquals(NotificationPreferences(true, false, true), NotificationStore(prefs).settings.value)
    }

    @Test fun unknownInitialBookmarkGetsBaselineWithoutAnnouncingOldChapters() = runTest {
        val bookmarks = BookmarkManager(bookmarkContext())
        val novel = book().copy(chapterCount = 0, latestChapter = "Chapter 1")
        bookmarks.addBookmark(novel)
        val store = NotificationStore(prefs())
        store.configure(true)
        store.reconcile(mapOf(novel.id to novel.chapterMarker()))
        assertTrue(store.markers().isEmpty())
        var fresh = novel.copy(chapterCount = 500, latestChapter = "Chapter 500")
        val base = NovelRepositoryImpl(sources = emptyMap())
        val repo = object : NovelRepository by base {
            override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String) = Result.success(fresh)
        }
        val events = mutableListOf<AppNotification>()
        val checker = BookmarkUpdateChecker(repo, bookmarks, store, { if (store.add(it)) events += it })
        checker.check()
        assertTrue(events.isEmpty())
        fresh = fresh.copy(chapterCount = 501, latestChapter = "Chapter 501")
        checker.check(); checker.check()
        assertEquals(1, events.size)
        assertEquals(novel.id, events.single().novelId)
        assertEquals(501, store.markers()[novel.id]!!.count)
    }

    @Test fun updateFailuresRollbackAndRemovedBookmarksDoNotNotify() = runTest {
        val bookmarks = BookmarkManager(bookmarkContext())
        val novel = book()
        bookmarks.addBookmark(novel)
        val store = NotificationStore(prefs())
        store.configure(true)
        store.reconcile(mapOf(novel.id to novel.chapterMarker()))
        var mode = 0
        val repo = object : NovelRepository by NovelRepositoryImpl(sources = emptyMap()) {
            override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String): Result<Novel> = when(mode) {
                0 -> Result.failure(java.io.IOException("Offline"))
                1 -> Result.success(novel.copy(chapterCount = 90))
                2 -> { bookmarks.removeBookmark(novel.id); Result.success(novel.copy(chapterCount = 110)) }
                else -> error("Unexpected")
            }
        }
        val events = mutableListOf<AppNotification>()
        val checker = BookmarkUpdateChecker(repo, bookmarks, store, { events += it })
        checker.check()
        assertEquals(100, store.markers()[novel.id]!!.count)
        mode = 1; checker.check()
        assertEquals(100, store.markers()[novel.id]!!.count)
        mode = 2; checker.check()
        assertTrue(events.isEmpty())
        store.reconcile(emptyMap())
        assertTrue(store.markers().isEmpty())
    }

    @Test fun boundedRoundRobinChecksAllBookmarksAndPreservesSourceIdentity() = runTest {
        val bookmarks = BookmarkManager(bookmarkContext())
        val books = (0 until 25).map { book(it, if (it % 2 == 0) NovelSourceType.FREE_WEB_NOVEL else NovelSourceType.NOVEL_PHOENIX) }
        books.forEach(bookmarks::addBookmark)
        val store = NotificationStore(prefs())
        store.configure(true)
        store.reconcile(books.associate { it.id to it.chapterMarker() })
        val requested = mutableListOf<String>()
        val repo = object : NovelRepository by NovelRepositoryImpl(sources = emptyMap()) {
            override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String): Result<Novel> {
                val book = books.single { it.source == source && it.sourceUrl == novelUrl }
                requested += book.id
                return Result.success(book.copy(chapterCount = 101, latestChapter = "Chapter 101"))
            }
        }
        val checker = BookmarkUpdateChecker(repo, bookmarks, store, { store.add(it) })
        checker.check()
        assertEquals(10, requested.size)
        checker.check(); checker.check()
        assertEquals(25, requested.toSet().size)
        assertEquals(25, store.items.value.size)
        assertEquals(books.map { it.id }.toSet(), store.items.value.map { it.novelId }.toSet())
    }

    @Test fun disabledNotificationChecksDoNoNetworkWork() = runTest {
        val bookmarks = BookmarkManager(bookmarkContext())
        bookmarks.addBookmark(book())
        val store = NotificationStore(prefs())
        val repo = object : NovelRepository by NovelRepositoryImpl(sources = emptyMap()) {
            override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String): Result<Novel> = error("Disabled checks must not fetch")
        }
        val checker = BookmarkUpdateChecker(repo, bookmarks, store, { error("Disabled checks must not publish") })
        checker.check()
        store.configure(true, chapters = false)
        checker.check()
    }

    private class MemoryProgressDao : ReadingProgressDao {
        val rows = mutableMapOf<String, ReadingProgressEntity>()
        override fun getProgress(novelId: String) = flowOf(rows[novelId])
        override suspend fun getProgressOnce(novelId: String) = rows[novelId]
        override fun getAllProgress() = flowOf(rows.values.toList())
        override suspend fun getAllProgressOnce() = rows.values.toList()
        override suspend fun insertOrUpdate(progress: ReadingProgressEntity) { rows[progress.novelId] = progress }
        override suspend fun deleteProgress(novelId: String) { rows.remove(novelId) }
    }

    @Test fun reminderCooldownPersistsAndReturningStartsNewInactivePeriod() {
        val prefs = prefs()
        val store = NotificationStore(prefs)
        store.recordActive(1_000)
        store.recordReminder(1_000)
        val restored = NotificationStore(prefs)
        assertFalse(reminderDue(INACTIVITY_MILLIS * 3, restored.lastActive, restored.remindedFor, false))
        restored.recordActive(10_000)
        assertFalse(reminderDue(10_000 + INACTIVITY_MILLIS - 1, restored.lastActive, restored.remindedFor, false))
        assertTrue(reminderDue(10_000 + INACTIVITY_MILLIS, restored.lastActive, restored.remindedFor, false))
    }

    @Test fun bookmarkMetadataRefreshDoesNotEraseUnreadUpdateBaseline() {
        val store = NotificationStore(prefs())
        store.reconcile(mapOf("np-book" to ChapterMarker(100, "Chapter 100")))
        store.reconcile(mapOf("np-book" to ChapterMarker(110, "Chapter 110")))
        assertEquals(100, store.markers()["np-book"]!!.count)
        store.updateMarker("np-book", ChapterMarker(110, "Chapter 110"))
        store.reconcile(emptyMap())
        assertTrue(store.markers().isEmpty())
        store.reconcile(mapOf("np-book" to ChapterMarker(120, "Chapter 120")))
        assertEquals(120, store.markers()["np-book"]!!.count)
    }
}
