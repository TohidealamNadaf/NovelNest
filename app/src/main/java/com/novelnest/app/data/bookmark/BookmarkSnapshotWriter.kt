package com.novelnest.app.data.bookmark

import com.novelnest.app.data.model.Novel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BookmarkWriteState(val pending: Boolean = false, val failed: Boolean = false)

/** Keeps the active write and only the latest queued immutable snapshot. Application owns the scope. */
internal class BookmarkSnapshotWriter(
    scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val write: (List<Novel>) -> Unit
) {
    private data class Snapshot(val revision: Long, val novels: List<Novel>)
    private val queue = Channel<Snapshot>(Channel.CONFLATED)
    private var revision = 0L
    private var failed: Snapshot? = null
    private val mutableState = MutableStateFlow(BookmarkWriteState())
    val state = mutableState.asStateFlow()

    init {
        scope.launch(dispatcher) {
            for (first in queue) {
                var snapshot = first
                for (attempt in 0..2) {
                    queue.tryReceive().getOrNull()?.let { snapshot = it }
                    try {
                        write(snapshot.novels)
                        synchronized(this@BookmarkSnapshotWriter) {
                            if (revision == snapshot.revision) {
                                failed = null
                                mutableState.value = BookmarkWriteState()
                            }
                        }
                        break
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        synchronized(this@BookmarkSnapshotWriter) {
                            if (revision == snapshot.revision) {
                                failed = snapshot
                                mutableState.value = BookmarkWriteState(pending = true, failed = true)
                            }
                        }
                        android.util.Log.w("Bookmarks", "Bookmark save failed: " + error.javaClass.simpleName)
                        if (attempt < 2) delay(250L * (attempt + 1))
                    }
                }
            }
        }
    }

    @Synchronized
    fun submit(novels: List<Novel>) {
        failed = null
        mutableState.value = BookmarkWriteState(pending = true)
        queue.trySend(Snapshot(++revision, novels.toList()))
    }

    @Synchronized
    fun retry() { failed?.takeIf { it.revision == revision }?.let { submit(it.novels) } }
}
