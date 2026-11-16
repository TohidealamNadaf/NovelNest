package com.novelnest.app.feature.reader.components

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import com.novelnest.app.data.dictionary.DictionaryException
import com.novelnest.app.data.dictionary.DictionaryWord

internal data class ReaderSelectionPoint(val paragraph: Int, val offset: Int) : Comparable<ReaderSelectionPoint> {
    override fun compareTo(other: ReaderSelectionPoint): Int =
        if (paragraph == other.paragraph) offset.compareTo(other.offset) else paragraph.compareTo(other.paragraph)
}
internal data class ReaderSelectionRange(val start: ReaderSelectionPoint, val end: ReaderSelectionPoint)
internal data class ReaderSelectionLayout(val text: TextLayoutResult, val bounds: Rect)

/** Chapter offsets outlive recycled rows; only composed rows retain layouts and coordinates. */
internal class ReaderChapterSelection(val paragraphs: List<String>) {
    var range by mutableStateOf<ReaderSelectionRange?>(null)
        private set
    val active by derivedStateOf { range != null }
    var dragging by mutableStateOf(false)
        private set
    var draggedHandle by mutableStateOf<Boolean?>(null)
        private set
    var pointer by mutableStateOf<Offset?>(null)
        private set
    var hostBounds by mutableStateOf(Rect.Zero)
    var viewport by mutableStateOf(Rect.Zero)
    val layouts = mutableStateMapOf<Int, ReaderSelectionLayout>()
    private var movingStart = false
    private var initialWord: ReaderSelectionRange? = null

    fun clear() { range = null; endDrag() }
    fun beginWord(position: Offset): Boolean {
        if (!viewport.contains(position)) return false
        val entry = layouts.entries.firstOrNull { it.value.bounds.contains(position) } ?: return false
        val text = paragraphs[entry.key]
        if (text.isBlank()) return false
        val offset = entry.value.text.getOffsetForPosition(position - entry.value.bounds.topLeft).coerceIn(0, text.lastIndex)
        val word = entry.value.text.getWordBoundary(offset)
        val selected = ReaderSelectionRange(ReaderSelectionPoint(entry.key, word.start), ReaderSelectionPoint(entry.key, word.end))
        range = selected
        initialWord = selected
        draggedHandle = null
        dragging = true
        pointer = position
        return true
    }
    fun beginHandle(start: Boolean, position: Offset) {
        if (!active) return
        movingStart = start; draggedHandle = start; initialWord = null; dragging = true; pointer = position
    }
    fun dragTo(position: Offset) {
        if (!dragging) return
        pointer = position
        val point = hit(position) ?: return
        val word = initialWord
        val old = range ?: return
        range = if (word != null) {
            when { point < word.start -> ReaderSelectionRange(point, word.end)
                point > word.end -> ReaderSelectionRange(word.start, point)
                else -> word }
        } else if (movingStart) ReaderSelectionRange(minOf(point, old.end), old.end)
        else ReaderSelectionRange(old.start, maxOf(point, old.start))
    }
    fun endDrag() { dragging = false; draggedHandle = null; pointer = null; initialWord = null }
    private fun hit(position: Offset): ReaderSelectionPoint? {
        if (viewport.height <= 0) return null
        val y = position.y.coerceIn(viewport.top, viewport.bottom - 1f)
        val entry = layouts.entries.filter { it.value.bounds.bottom > viewport.top && it.value.bounds.top < viewport.bottom }
            .minByOrNull { (_, row) -> when {
                y < row.bounds.top -> row.bounds.top - y
                y > row.bounds.bottom -> y - row.bounds.bottom
                else -> 0f
            } } ?: return null
        val offset = entry.value.text.getOffsetForPosition(Offset(position.x - entry.value.bounds.left, y - entry.value.bounds.top))
        return ReaderSelectionPoint(entry.key, offset.coerceIn(0, paragraphs[entry.key].length))
    }
    fun selectedOffsets(index: Int): IntRange? {
        val r = range ?: return null
        if (index !in r.start.paragraph..r.end.paragraph) return null
        val start = if (index == r.start.paragraph) r.start.offset else 0
        val end = if (index == r.end.paragraph) r.end.offset else paragraphs[index].length
        return if (end > start) start until end else null
    }
    fun handle(start: Boolean): Offset? {
        val point = (if (start) range?.start else range?.end) ?: return null
        val row = layouts[point.paragraph] ?: return null
        val cursor = row.text.getCursorRect(point.offset.coerceIn(0, paragraphs[point.paragraph].length))
        val bounds = cursor.translate(row.bounds.topLeft)
        if (bounds.bottom <= viewport.top || bounds.top >= viewport.bottom) return null
        // A partially visible last line still needs a reachable endpoint at the viewport edge.
        return Offset(bounds.left, bounds.bottom.coerceIn(viewport.top, viewport.bottom))
    }
    fun selectionRect(): Rect? {
        val rows = layouts.entries.mapNotNull { (index, row) ->
            val offsets = selectedOffsets(index) ?: return@mapNotNull null
            if (row.bounds.bottom <= viewport.top || row.bounds.top >= viewport.bottom) return@mapNotNull null
            val bounds = row.text.getPathForRange(offsets.first, offsets.last + 1).getBounds().translate(row.bounds.topLeft)
            Rect(maxOf(bounds.left, viewport.left), maxOf(bounds.top, viewport.top),
                minOf(bounds.right, viewport.right), minOf(bounds.bottom, viewport.bottom))
        }.filter { it.width > 0 && it.height > 0 }
        return rows.reduceOrNull { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
    }
    fun selectAll() {
        if (paragraphs.isNotEmpty()) range = ReaderSelectionRange(ReaderSelectionPoint(0, 0),
            ReaderSelectionPoint(paragraphs.lastIndex, paragraphs.last().length))
        endDrag()
    }
    fun word(): String? = try { DictionaryWord.normalize(selectedText(512)) } catch (_: DictionaryException) { null }
    fun selectedText(limit: Int = Int.MAX_VALUE, selected: ReaderSelectionRange? = range): String? {
        val r = selected ?: return null
        val result = StringBuilder()
        for (i in r.start.paragraph..r.end.paragraph) {
            val from = if (i == r.start.paragraph) r.start.offset else 0
            val until = if (i == r.end.paragraph) r.end.offset else paragraphs[i].length
            if (result.length.toLong() + until - from + (if (i == r.start.paragraph) 0 else 2) > limit) return null
            if (i != r.start.paragraph) result.append("\n\n")
            result.append(paragraphs[i], from, until)
        }
        return result.toString()
    }
    fun edgeVelocity(edge: Float, maxSpeed: Float): Float {
        val p = pointer ?: return 0f
        if (!dragging || viewport.height <= 0) return 0f
        val band = minOf(edge, viewport.height / 3f)
        return when {
            p.y < viewport.top + band -> -maxSpeed * ((viewport.top + band - p.y) / band).coerceIn(0f, 1f)
            p.y > viewport.bottom - band -> maxSpeed * ((p.y - viewport.bottom + band) / band).coerceIn(0f, 1f)
            else -> 0f
        }
    }
    fun nudge(start: Boolean, forward: Boolean) {
        val r = range ?: return
        val p = if (start) r.start else r.end
        val text = paragraphs[p.paragraph]
        val next = when {
            forward && p.offset < text.length -> p.copy(offset = text.offsetByCodePoints(p.offset, 1))
            !forward && p.offset > 0 -> p.copy(offset = text.offsetByCodePoints(p.offset, -1))
            forward && p.paragraph < paragraphs.lastIndex -> ReaderSelectionPoint(p.paragraph + 1, 0)
            !forward && p.paragraph > 0 -> ReaderSelectionPoint(p.paragraph - 1, paragraphs[p.paragraph - 1].length)
            else -> p
        }
        range = if (start) ReaderSelectionRange(minOf(next, r.end), r.end) else ReaderSelectionRange(r.start, maxOf(next, r.start))
    }
}
