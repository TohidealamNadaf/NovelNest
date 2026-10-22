package com.novelnest.app.data.remote.freewebnovel

import com.novelnest.app.feature.reader.Chapter

data class FreeWebNovelChapterPage(val chapters: List<Chapter>, val page: Int, val totalPages: Int, val totalChapters: Int) {
    init {
        if (totalPages !in 1..10_000 || page !in 1..totalPages || totalChapters < 0 || chapters.size > totalChapters) {
            throw FreeWebNovelException.ParseError("FreeWebNovel chapter pagination metadata is invalid")
        }
    }
}

internal data class FreeWebNovelCached<T>(val value: T, val fetchedAt: Long = System.currentTimeMillis()) {
    fun fresh(ttl: Long) = System.currentTimeMillis() - fetchedAt < ttl
}
