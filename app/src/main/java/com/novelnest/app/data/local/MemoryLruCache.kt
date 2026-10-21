package com.novelnest.app.data.local

/**
 * Thread-safe pure Kotlin LRU Cache.
 * Provides high-performance in-memory caching without framework dependencies on android.util.LruCache.
 */
class MemoryLruCache<K, V>(
    private val maxSize: Int
) {
    private val map = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }
    private val lock = Any()

    fun get(key: K): V? = synchronized(lock) {
        map[key]
    }

    fun put(key: K, value: V): V? = synchronized(lock) {
        map.put(key, value)
    }

    fun remove(key: K): V? = synchronized(lock) {
        map.remove(key)
    }

    fun clear() = synchronized(lock) {
        map.clear()
    }
}
