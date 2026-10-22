package com.novelnest.app.data.cache

import kotlinx.coroutines.sync.Mutex

/** Bounded request locks. Use separate pools for operations that acquire nested locks. */
internal class MutexPool(size: Int = 16) {
    private val locks = List(size) { Mutex() }
    fun forKey(key: String): Mutex = locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]
}
