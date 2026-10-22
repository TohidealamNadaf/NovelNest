package com.novelnest.app.data.notification

import com.novelnest.app.data.bookmark.BookmarkManager
import com.novelnest.app.data.model.NovelIdentity
import com.novelnest.app.data.repository.NovelRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A bounded, source-agnostic update pass; failed fetches never advance a chapter baseline. */
class BookmarkUpdateChecker(
    private val repository: NovelRepository,
    private val bookmarks: BookmarkManager,
    private val store: NotificationStore,
    private val publish: (AppNotification) -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun check() {
        if (!store.settings.value.let { it.enabled && it.chapterUpdates }) return
        val books = bookmarks.bookmarks.value.map(NovelIdentity::normalize).distinctBy { it.id }
        if (books.isEmpty()) return
        val start = store.cursor.mod(books.size)
        for (offset in 0 until minOf(10, books.size)) {
            currentCoroutineContext().ensureActive()
            if (!store.settings.value.let { it.enabled && it.chapterUpdates }) return
            val novel = books[(start + offset) % books.size]
            val result = try { repository.getNovelDetails(novel.source, novel.sourceUrl) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            val freshNovel = result?.getOrNull()?.let(NovelIdentity::normalize)
            if (freshNovel != null && freshNovel.id == novel.id && bookmarks.isBookmarked(novel.id)) {
                val fresh = freshNovel.chapterMarker()
                val old = store.markers()[novel.id]
                if (old != null && hasNewChapters(old, fresh)) {
                    val version = if (fresh.count > 0) fresh.count.toString() else fresh.title
                    publish(AppNotification("chapter:${novel.id}:$version", "New chapters · ${freshNovel.title}",
                        freshNovel.latestChapter.ifBlank { "${fresh.count} chapters are now available." },
                        now(), novel.id))
                }
                if (old == null || fresh.count >= old.count) store.updateMarker(novel.id, fresh)
            }
            store.cursor = (start + offset + 1) % books.size
        }
    }
}
