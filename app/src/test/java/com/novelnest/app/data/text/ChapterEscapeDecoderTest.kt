package com.novelnest.app.data.text

import com.novelnest.app.data.ai.AiChapterInput
import com.novelnest.app.data.model.Chapter
import com.novelnest.app.data.model.NovelSourceType
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ChapterEscapeDecoderTest {
    @Test fun untouchedTextReturnsTheSameStringAndChapter() {
        val text = "Ordinary text, literal emoji " + String(Character.toChars(0x1F600))
        assertSame(text, ChapterEscapeDecoder.decode(text))
        val chapter = Chapter("one", "Chapter", listOf(text))
        assertSame(chapter, ChapterEscapeDecoder.prepare(NovelSourceType.NOVEL_PHOENIX, chapter, true))
    }
    @Test fun decodesMixedUnicodeAndNumericEntities() {
        val original = "It\\u2019s &#x201C;ready&#8221; and \\u0041."
        assertEquals("It\u2019s \u201Cready\u201D and A.", ChapterEscapeDecoder.decode(original))
    }
    @Test fun decodesEscapesAdjacentToWordsAndOtherEscapesWithoutSpaces() {
        assertEquals("<Ability: None>", ChapterEscapeDecoder.decode("\\u003cAbility: None\\u003e"))
        assertEquals("<Ability:None>", ChapterEscapeDecoder.decode("&#x3c;Ability:None&#62;"))
        assertEquals("ABText", ChapterEscapeDecoder.decode("\\u0041\\u0042Text"))
    }
    @Test fun decodesSurrogatePairsAndSupplementaryNumericEntities() {
        val emoji = String(Character.toChars(0x1F600))
        assertEquals(emoji + emoji + emoji, ChapterEscapeDecoder.decode("\\uD83D\\uDE00&#128512;&#X1F600;"))
    }
    @Test fun leavesMalformedAndOutOfRangeInputIntact() {
        for (text in listOf("\\u", "\\u12", "\\u12zz", "\\U00000041", "\\uD800", "\\uDC00", "&#;", "&#x;",
            "&#x110000;", "&#-1;", "&#xD800;", "&#999999999999999999999999;", "&#65", "&#x41", "&# 65;"))
            assertSame(text, ChapterEscapeDecoder.decode(text))
    }
    @Test fun protectsControlAndDirectionalOverrideCharacters() {
        for (text in listOf("\\u0000", "\\u000A", "\\u000D", "&#9;", "&#x7F;", "\\u202E", "&#x2066;"))
            assertSame(text, ChapterEscapeDecoder.decode(text))
    }
    @Test fun preservesLiteralBackslashesAndSupportsOddRuns() {
        val slash = "\\"
        val literal = slash.repeat(2) + "u0041"
        assertSame(literal, ChapterEscapeDecoder.decode(literal))
        assertEquals(slash.repeat(2) + "u0041", ChapterEscapeDecoder.decode(slash.repeat(2) + "u0041"))
        assertEquals(slash.repeat(2) + "A", ChapterEscapeDecoder.decode(slash.repeat(3) + "u0041"))
        assertEquals("C:" + slash + "users" + slash + "name", ChapterEscapeDecoder.decode("C:" + slash + "users" + slash + "name"))
    }
    @Test fun decodingIsSingleLayerAndNeverParsesHtml() {
        assertEquals("\\u0041", ChapterEscapeDecoder.decode("\\u005Cu0041"))
        assertEquals("&#65;", ChapterEscapeDecoder.decode("\\u0026#65;"))
        assertEquals("&amp;#65;", ChapterEscapeDecoder.decode("&amp;#65;"))
        assertEquals("<b>Kept</b>", ChapterEscapeDecoder.decode("&#60;b&#62;Kept&#60;/b&#62;"))
    }
    @Test fun onlySupportedSourcesAreDecodedAndAllMetadataIsPreserved() {
        val chapter = Chapter("same-id", "Chapter \\u0031", listOf("One &#65;", "Unchanged", ""), "url", "previous", "next")
        for (source in listOf(NovelSourceType.NOVEL_PHOENIX, NovelSourceType.FREE_WEB_NOVEL)) {
            val prepared = ChapterEscapeDecoder.prepare(source, chapter, true)
            assertEquals("Chapter 1", prepared.title)
            assertEquals(listOf("One A", "Unchanged", ""), prepared.paragraphs)
            assertSame(chapter.paragraphs[1], prepared.paragraphs[1])
            assertEquals(chapter.id, prepared.id); assertEquals(chapter.url, prepared.url)
            assertEquals(chapter.prevUrl, prepared.prevUrl); assertEquals(chapter.nextUrl, prepared.nextUrl)
            assertEquals(listOf("One &#65;", "Unchanged", ""), chapter.paragraphs)
        }
        assertSame(chapter, ChapterEscapeDecoder.prepare(NovelSourceType.ASURA_SCANS, chapter, true))
        assertSame(chapter, ChapterEscapeDecoder.prepare(null, chapter, true)) // EPUB has no website source.
        assertSame(chapter, ChapterEscapeDecoder.prepare(NovelSourceType.NOVEL_PHOENIX, chapter, false))
    }
    @Test fun ignoresNonAsciiDigitsInEscapeSyntax() {
        val invalid = "&#" + 0xFF16.toChar() + 0xFF15.toChar() + ";"
        assertSame(invalid, ChapterEscapeDecoder.decode(invalid))
    }
    @Test fun scansLongEscapedInputWithBoundedCancellationIntervals() {
        var checks = 0
        val original = "x" + "\\u2019".repeat(20000)
        val decoded = ChapterEscapeDecoder.decode(original) { checks++ }
        assertEquals(20001, decoded.length)
        assertTrue(checks > 20)
    }
    @Test fun cancellationPropagatesRatherThanReturningPartialText() {
        var checks = 0
        assertThrows(CancellationException::class.java) {
            ChapterEscapeDecoder.decode("x".repeat(20000)) { if (++checks == 2) throw CancellationException() }
        }
    }
    @Test fun aiFingerprintChangesWithEffectiveTextButIdentityDoesNot() {
        val raw = Chapter("one", "Chapter", listOf("It\\u2019s done."))
        val prepared = ChapterEscapeDecoder.prepare(NovelSourceType.NOVEL_PHOENIX, raw, true)
        val first = AiChapterInput("NOVEL_PHOENIX", "book", raw.id, raw.title, raw.paragraphs)
        val second = first.copy(title = prepared.title, paragraphs = prepared.paragraphs)
        assertNotEquals(first.fingerprint(), second.fingerprint())
        assertEquals(first.chapterId, second.chapterId)
    }
}
