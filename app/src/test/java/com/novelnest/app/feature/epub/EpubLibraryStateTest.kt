package com.novelnest.app.feature.epub

import com.novelnest.app.data.local.epub.*
import org.junit.Assert.*
import org.junit.Test

class EpubLibraryStateTest {
    private fun book(id: String, group: String? = null, order: Long = 0, read: Long? = null,
        added: Long = 1, title: String = id) = LocalBookEntry(
        LocalBookEntity(id, id, title, "Author", "", added, 10, group, null, order),
        read?.let { EpubPositionPreview(id, 0.4, "Chapter", it) })
    private fun group(id: String) = EpubCollectionEntity(id, id, id, 1)
    @Test fun groupedVolumesAppearOnceAndResumeTheirLastReadVolume() {
        val books = listOf(book("v1", "series", 0, 10), book("v2", "series", 1, 20), book("solo", read = 15))
        val state = buildEpubLibraryState(EpubLibrarySnapshot(books, listOf(group("series")), true), null, "")
        assertEquals(listOf("series", "solo"), state.items.map { it.key })
        assertEquals(listOf("series", "solo"), state.recentlyRead.map { it.key })
        assertEquals("v2", state.continueBook!!.book.id)
        assertEquals("v2", (state.items.first() as EpubShelfItem.Series).representative!!.book.id)
        assertEquals(3, state.bookCount)
        assertTrue(state.continueSeriesName!!.contains("Volume 2"))
    }
    @Test fun unreadImportsDoNotReplaceContinueAndHistoryIsLimitedToTen() {
        val books = (1..15).map { book(it.toString(), read = it.toLong()) } + book("unread", added = 1000)
        val state = buildEpubLibraryState(EpubLibrarySnapshot(books, loaded = true), null, "")
        assertEquals("15", state.continueBook!!.book.id)
        assertEquals(10, state.recentlyRead.size)
        assertFalse(state.recentlyRead.any { it.key == "unread" })
    }
    @Test fun folderUsesSavedVolumeOrderInsteadOfRecentReadingOrder() {
        val books = listOf(book("second", "a", 1, 20), book("first", "a", 0, 30), book("other", "b", 0, 100))
        val state = buildEpubLibraryState(EpubLibrarySnapshot(books, listOf(group("a"), group("b")), true), "a", "")
        assertEquals(listOf("first", "second"), state.items.map { it.key })
        assertEquals("first", state.continueBook!!.book.id)
        assertEquals(mapOf("first" to 1, "second" to 2), state.volumeNumbers)
        assertTrue(state.recentlyRead.isEmpty())
    }
    @Test fun searchMatchesVolumesWithoutScatteringThemOutsideTheirSeries() {
        val books = listOf(book("v1", "series", title = "Moon adventure"), book("solo", title = "Other"))
        val snapshot = EpubLibrarySnapshot(books, listOf(group("series"), group("empty")), true)
        assertEquals(listOf("series"), buildEpubLibraryState(snapshot, null, "MOON").visibleItems.map { it.key })
        assertTrue(buildEpubLibraryState(snapshot, null, "").items.any { it.key == "empty" })
        assertEquals(2, snapshot.books.size)
    }
    @Test fun deletedSeriesAndUnreadLibrariesHaveExplicitStates() {
        val snapshot = EpubLibrarySnapshot(listOf(book("unread")), loaded = true)
        val state = buildEpubLibraryState(snapshot, null, "")
        assertNull(state.continueBook)
        assertTrue(state.recentlyRead.isEmpty())
        assertTrue(buildEpubLibraryState(snapshot, "missing", "").collectionMissing)
        assertFalse(buildEpubLibraryState(EpubLibrarySnapshot(), "missing", "").collectionMissing)
    }
    @Test fun progressIsFiniteClampedAndNeverUsesVolumeAverages() {
        assertEquals(0f, epubProgress(null), 0f)
        val entry = book("a", read = 1)
        assertEquals(0.4f, epubProgress(entry), 0f)
        assertEquals(0f, epubProgress(entry.copy(position = entry.position!!.copy(progression = Double.NaN))), 0f)
        assertEquals(1f, epubProgress(entry.copy(position = entry.position!!.copy(progression = 2.0))), 0f)
    }
}
