package com.novelnest.app.data.cache

import com.novelnest.app.feature.reader.Chapter
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Retain a bounded recent working set. External StateFlow holders keep their own
 * snapshot alive, and the weak registry lets subsequent publishers find that same
 * object after LRU eviction. It never replaces a still-referenced observer's flow.
 */
internal class ChapterSnapshotStore(private val recentLimit: Int = 16) {
    private class Entry(
        val key: String,
        value: MutableStateFlow<List<Chapter>>,
        queue: ReferenceQueue<MutableStateFlow<List<Chapter>>>
    ) : WeakReference<MutableStateFlow<List<Chapter>>>(value, queue)

    private val queue = ReferenceQueue<MutableStateFlow<List<Chapter>>>()
    private val registry = HashMap<String, Entry>()
    private val recent = LinkedHashMap<String, MutableStateFlow<List<Chapter>>>(16, 0.75f, true)

    init { require(recentLimit > 0) }

    @Synchronized
    fun get(key: String): MutableStateFlow<List<Chapter>> {
        while (true) {
            val dead = queue.poll() as? Entry ?: break
            if (registry[dead.key] === dead) registry.remove(dead.key)
        }
        val snapshot = recent[key] ?: registry[key]?.get() ?: MutableStateFlow<List<Chapter>>(emptyList()).also {
            registry[key] = Entry(key, it, queue)
        }
        recent[key] = snapshot
        while (recent.size > recentLimit) recent.remove(recent.keys.first())
        return snapshot
    }

    internal val retainedCount: Int @Synchronized get() = recent.size
}
