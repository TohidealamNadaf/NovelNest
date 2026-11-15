package com.novelnest.app.feature.home

import com.novelnest.app.data.model.*
import com.novelnest.app.data.progress.NovelProgress
import org.junit.Assert.*
import org.junit.Test

class HomeSourceContinueTest {
    private fun entry(source: NovelSourceType, time: Long): HomeReadingEntry {
        val novel = Novel(source.name, "Book", "", source, source.baseUrl + "/book")
        return HomeReadingEntry(novel, NovelProgress(novel.id, "chapter", 1, "Chapter", time))
    }
    @Test fun continueCardsAndHistoryStayInsideTheirSelectedSource() {
        val entries = NovelSourceType.entries.mapIndexed { index, source -> entry(source, index.toLong()) }
        for (source in NovelSourceType.entries) {
            val state = HomeUiState(selectedSource = source, readingHistory = entries)
            assertEquals(source, state.currentReading!!.novel.source)
            assertTrue(state.recentlyRead.all { it.novel.source == source })
        }
        assertNull(HomeUiState().currentReading)
    }
}
