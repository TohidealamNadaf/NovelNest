package com.novelnest.app.data.study

import com.novelnest.app.data.ai.*
import com.novelnest.app.feature.reader.components.markdownWrap
import com.novelnest.app.feature.reader.components.buildTermItems
import org.junit.Assert.*
import org.junit.Test

class StudyMergeTest {
    private val input = AiChapterInput("NOVEL_PHOENIX", "book", "chapter-1", "Chapter 1", listOf("Qi gathers in the Core."), "cultivation terms")
    private val term = AiTerm("Qi", "CONCEPT", "Gathered energy", 0, "Qi gathers")
    private fun result(input: AiChapterInput, terms: List<AiTerm> = listOf(term)) = AiResult(AiAction.EXTRACT, input.fingerprint(),
        AiProviderCatalog.models.first().route, extractedTerms = terms)

    @Test fun repeatedDiscoveryIsIdempotentWithoutDependingOnTimeOrProvider() {
        val first = StudyRepository.discoveries("collection", input, result(input)).single()
        val repeat = StudyRepository.discoveries("collection", input, result(input).copy(createdAtMs = 99)).single()
        assertEquals(first.id, repeat.id)
        assertEquals(1, StudyRepository.discoveries("collection", input, result(input, listOf(term, term))).size)
    }
    @Test fun newChaptersAndNewMeaningsAppendRatherThanOverwrite() {
        val first = StudyRepository.discoveries("collection", input, result(input)).single()
        val next = input.copy(chapterId = "chapter-2", title = "Chapter 2")
        val second = StudyRepository.discoveries("collection", next, result(next)).single()
        val detail = StudyRepository.discoveries("collection", input, result(input, listOf(term.copy(meaning = "Another grounded detail")))).single()
        assertEquals(first.termKey, second.termKey); assertEquals(first.termKey, detail.termKey)
        assertEquals(3, listOf(first, second, detail).distinctBy { it.id }.size)
        assertEquals("Qi gathers", first.evidence)
        val displayed = buildTermItems(listOf(first, second, detail))
        assertEquals(1, displayed.size)
        assertEquals(3, displayed.single().references.size)
    }
    @Test fun collectionsAreIsolatedAndUnicodeNamesHaveStableKeys() {
        assertEquals("golden core", StudyRepository.termKey("  ＧＯＬＤＥＮ  Core  "))
        val first = StudyRepository.discoveries("one", input, result(input)).single()
        val second = StudyRepository.discoveries("two", input, result(input)).single()
        assertNotEquals(first.id, second.id)
    }
    @Test fun formattingWrapsTheSelectionInsteadOfAppendingAtTheEnd() {
        assertEquals(Triple("A **chosen** word", 4, 10), markdownWrap("A chosen word", 2, 8, "**", "**"))
        assertEquals(Triple("A *chosen* word", 3, 9), markdownWrap("A chosen word", 8, 2, "*", "*"))
        assertEquals(Triple("A ``word", 3, 3), markdownWrap("A word", 2, 2, "`", "`"))
    }
}
