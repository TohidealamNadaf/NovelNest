package com.novelnest.app.feature.home

import com.novelnest.app.data.local.epub.*
import com.novelnest.app.data.model.*
import com.novelnest.app.data.progress.NovelProgress
import org.junit.Assert.*
import org.junit.Test

class HomeEpubContinueTest {
    private fun local(time: Long?) = LocalBookEntry(LocalBookEntity("epub-abc", "abc", "Local", "Author", "", 1, 3),
        time?.let { EpubPositionEntity("epub-abc", "{}", 0.3, "Section", it) })
    private fun online(time: Long) = HomeReadingEntry(Novel("np-book", "Online", "", NovelSourceType.NOVEL_PHOENIX, "https://novelphoenix.com/novel/book"),
        NovelProgress("np-book", "ch", 1, "Chapter", time))
    @Test fun unreadImportDoesNotReplaceContinueReading() {
        assertEquals(HomeContinueBook.Online(online(10)), selectContinueBook(online(10), listOf(local(null))))
    }
    @Test fun moreRecentLocalBookUsesLocalIdentity() {
        assertEquals(HomeContinueBook.Local(local(20)), selectContinueBook(online(10), listOf(local(20))))
    }
    @Test fun newerOnlineReadingKeepsExistingRoute() {
        assertEquals(HomeContinueBook.Online(online(30)), selectContinueBook(online(30), listOf(local(20))))
    }
    @Test fun localHistoryWorksWithoutBookmarksOrWebsiteHistory() {
        assertEquals(HomeContinueBook.Local(local(20)), selectContinueBook(null, listOf(local(20), local(10))))
        assertNull(selectContinueBook(null, listOf(local(null))))
    }
}
