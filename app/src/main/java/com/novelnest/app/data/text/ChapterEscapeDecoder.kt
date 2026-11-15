package com.novelnest.app.data.text

import com.novelnest.app.data.model.Chapter
import com.novelnest.app.data.model.NovelSourceType

/** Opt-in, single-layer plain-text decoding. Never edits chapter identity, URLs, or cached input. */
object ChapterEscapeDecoder {
    const val VERSION = 1
    fun supports(source: NovelSourceType?) = source == NovelSourceType.NOVEL_PHOENIX || source == NovelSourceType.FREE_WEB_NOVEL

    fun prepare(source: NovelSourceType?, chapter: Chapter, enabled: Boolean, checkCancelled: () -> Unit = {}): Chapter {
        if (!enabled || !supports(source)) return chapter
        val title = decode(chapter.title, checkCancelled)
        var paragraphs: MutableList<String>? = null
        chapter.paragraphs.forEachIndexed { index, original ->
            checkCancelled()
            val decoded = decode(original, checkCancelled)
            if (decoded !== original) {
                if (paragraphs == null) paragraphs = chapter.paragraphs.toMutableList()
                paragraphs!![index] = decoded
            }
        }
        return if (title === chapter.title && paragraphs == null) chapter
            else chapter.copy(title = title, paragraphs = paragraphs ?: chapter.paragraphs)
    }

    fun decode(text: String, checkCancelled: () -> Unit = {}): String {
        var output: StringBuilder? = null
        var copied = 0
        var index = 0
        var nextCancellationCheck = 0L
        while (index < text.length) {
            if (index >= nextCancellationCheck) { checkCancelled(); nextCancellationCheck = index.toLong() + 4096 }
            var code = -1
            var end = index
            if (text[index] == '\\') {
                // A pair of backslashes is literal; do not reinterpret its second slash as an escape.
                if (index + 1 < text.length && text[index + 1] == '\\') { index += 2; continue }
                if (index + 6 <= text.length && text[index + 1] == 'u') {
                    val unit = hex4(text, index + 2)
                    if (unit in 0xD800..0xDBFF) {
                        if (index + 12 <= text.length && text[index + 6] == '\\' && text[index + 7] == 'u') {
                            val low = hex4(text, index + 8)
                            if (low in 0xDC00..0xDFFF) {
                                code = Character.toCodePoint(unit.toChar(), low.toChar()); end = index + 12
                            }
                        }
                    } else if (unit >= 0 && unit !in 0xDC00..0xDFFF) { code = unit; end = index + 6 }
                }
            } else if (text[index] == '&' && index + 2 < text.length && text[index + 1] == '#') {
                var cursor = index + 2
                val hex = cursor < text.length && (text[cursor] == 'x' || text[cursor] == 'X')
                if (hex) cursor++
                val start = cursor
                val radix = if (hex) 16 else 10
                val maxDigits = if (hex) 8 else 10
                var value = 0L
                while (cursor < text.length && cursor - start < maxDigits) {
                    val digit = digit(text[cursor], radix)
                    if (digit < 0) break
                    value = value * radix + digit
                    cursor++
                }
                if (cursor > start && cursor < text.length && text[cursor] == ';' && value <= 0x10FFFF) {
                    code = value.toInt(); end = cursor + 1
                }
            }
            if (safeScalar(code)) {
                val builder = output ?: StringBuilder(text.length).also { output = it }
                builder.append(text, copied, index).appendCodePoint(code)
                copied = end
                index = end
            } else index++
        }
        checkCancelled()
        return output?.append(text, copied, text.length)?.toString() ?: text
    }
    private fun hex4(text: String, start: Int): Int {
        var result = 0
        for (index in start until start + 4) {
            val value = digit(text[index], 16)
            if (value < 0) return -1
            result = result * 16 + value
        }
        return result
    }
    private fun digit(char: Char, radix: Int): Int = when (char) {
        in '0'..'9' -> (char - '0').takeIf { it < radix } ?: -1
        in 'a'..'f' -> if (radix == 16) char - 'a' + 10 else -1
        in 'A'..'F' -> if (radix == 16) char - 'A' + 10 else -1
        else -> -1
    }
    private fun safeScalar(code: Int) = code in 0..0x10FFFF && code !in 0xD800..0xDFFF &&
        !Character.isISOControl(code) && code !in 0x202A..0x202E && code !in 0x2066..0x2069
}
