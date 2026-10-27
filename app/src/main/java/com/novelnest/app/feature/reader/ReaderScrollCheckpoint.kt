package com.novelnest.app.feature.reader

internal data class ReaderScrollPosition(val chapter: Int, val item: Int, val offset: Int, val content: String)

/** Latest position stays in memory; Bundle writes are bounded during continuous movement. */
internal class ReaderScrollCheckpoint {
    private var pending: ReaderScrollPosition? = null
    private var lastSaved: ReaderScrollPosition? = null
    private var lastWriteMs: Long? = null

    fun record(position: ReaderScrollPosition, nowMs: Long): ReaderScrollPosition? {
        pending = position
        val last = lastWriteMs
        return if (last == null || nowMs - last >= 250L) flush(nowMs) else null
    }

    fun flush(nowMs: Long): ReaderScrollPosition? {
        val latest = pending ?: return null
        if (latest == lastSaved) return null
        lastWriteMs = nowMs
        lastSaved = latest
        return latest
    }
}
