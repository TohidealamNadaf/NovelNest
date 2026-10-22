package com.novelnest.app.data.progress

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.totalChapterCount

data class ReadingCompletion(val read: Int, val total: Int) {
    val fraction get() = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else 0f
    val label get() = if (total > 0) "$read / $total Chapters" else "$read chapters read"
}
fun Novel.readingCompletion(progress: NovelProgress?): ReadingCompletion {
    val total = totalChapterCount()
    val read = progress?.readChapterIds.orEmpty().map {
        it.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
    }.filter { it.isNotBlank() }.distinct().size
    return ReadingCompletion(if (total > 0) read.coerceAtMost(total) else read, total)
}
