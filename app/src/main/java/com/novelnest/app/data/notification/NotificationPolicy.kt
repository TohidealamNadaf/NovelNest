package com.novelnest.app.data.notification

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.totalChapterCount

const val INACTIVITY_MILLIS = 24 * 60 * 60 * 1_000L
data class ChapterMarker(val count: Int, val title: String)
fun Novel.chapterMarker() = ChapterMarker(totalChapterCount(), latestChapter.trim())
fun hasNewChapters(old: ChapterMarker, fresh: ChapterMarker): Boolean {
    if (old.count > 0 && fresh.count > 0) return fresh.count > old.count
    val pattern = Regex("(?i)chapter\\s*([0-9]+)")
    val before = pattern.find(old.title)?.groupValues?.get(1)?.toLongOrNull()
    val after = pattern.find(fresh.title)?.groupValues?.get(1)?.toLongOrNull()
    return before != null && after != null && after > before
}
fun reminderDue(now: Long, lastActive: Long, remindedFor: Long, foreground: Boolean): Boolean =
    !foreground && lastActive > 0 && now - lastActive >= INACTIVITY_MILLIS && remindedFor != lastActive

val readingNudges = listOf(
    "Your bookmark called. It says the suspense is getting personal.",
    "Plot twist: your next chapter has been waiting for YOU.",
    "I tried to read your next chapter. The book said, 'Bring my human.'",
    "Your fictional friends asked where you went. I said: side quest.",
    "A chapter a day keeps the cliffhanger... slightly further away.",
    "The villain took a tea break. Perfect time to catch up."
)
