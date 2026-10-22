package com.novelnest.app.feature.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderStabilityTest {
    @Test fun repeatedParagraphsHaveDistinctStableKeys() {
        val paragraphs = listOf("...", "Same line", "...", "Same line")
        val keys = paragraphs.mapIndexed { index, _ -> readerParagraphKey("chapter-1", index) }
        assertEquals(paragraphs.size, keys.toSet().size)
        assertEquals(keys, paragraphs.mapIndexed { index, _ -> readerParagraphKey("chapter-1", index) })
    }

    @Test fun differentTextWithSameJavaHashCannotCollideAsReaderRows() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(readerParagraphKey("chapter-1", 0), readerParagraphKey("chapter-1", 1))
        assertNotEquals(readerParagraphKey("chapter-1", 0), readerParagraphKey("chapter-2", 0))
    }

    @Test fun longSessionKeepsAllChapterMetadataButOnlyNearbyText() {
        val chapters = (0 until 1_000).map {
            Chapter("chapter-$it", "Chapter $it", listOf("Text $it"), url = "/novel/book/chapter-$it")
        }
        val retained = retainReaderContent(chapters, 500)
        assertEquals(chapters.map { it.id }, retained.map { it.id })
        assertEquals(chapters.map { it.url }, retained.map { it.url })
        assertEquals((498..502).toList(), retained.indices.filter { retained[it].paragraphs.isNotEmpty() })
        assertEquals(listOf("Text 500"), retained[500].paragraphs)
        assertTrue(retained.first().paragraphs.isEmpty())
        assertEquals(1_000, chapters.count { it.paragraphs.isNotEmpty() })
    }

    @Test fun retentionAtBeginningAndEndDoesNotDropCurrentChapter() {
        val chapters = (0..9).map { Chapter("c$it", "Chapter $it", listOf("Text")) }
        assertEquals(3, retainReaderContent(chapters, 0).count { it.paragraphs.isNotEmpty() })
        assertEquals(3, retainReaderContent(chapters, 9).count { it.paragraphs.isNotEmpty() })
        assertTrue(retainReaderContent(chapters, 9)[9].paragraphs.isNotEmpty())
    }

    @Test fun onlyCurrentLoadedChapterAcceptsSwipeNavigation() {
        val chapters = listOf(Chapter("one", "One", listOf("Content")), Chapter("two", "Two", listOf("Content")))
        val state = ReaderUiState(chapters = chapters, currentChapterIndex = 1)
        assertTrue(state.canNavigateFrom(1))
        assertFalse(state.canNavigateFrom(0))
        assertFalse(state.copy(isChapterLoading = true).canNavigateFrom(1))
        assertFalse(state.copy(isLoading = true).canNavigateFrom(1))
    }

    @Test fun emptyOrUnavailableChapterDoesNotAcceptSwipeNavigation() {
        assertFalse(ReaderUiState().canNavigateFrom(0))
        assertFalse(ReaderUiState(chapters = listOf(Chapter("one", "One"))).canNavigateFrom(0))
        assertFalse(ReaderUiState(chapters = listOf(Chapter("one", "One", listOf("Content"))),
            currentChapterIndex = 5).canNavigateFrom(5))
    }
}
