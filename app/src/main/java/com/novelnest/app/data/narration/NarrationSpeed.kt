package com.novelnest.app.data.narration

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One persisted speed shared by all reader panels and the active playback service. */
object NarrationSpeed {
    val choices = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow<Float?>(null)
    val values = mutable.asStateFlow()

    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (mutable.value == null) {
                val saved = runCatching {
                    context.applicationContext.getSharedPreferences("cloud_narration", Context.MODE_PRIVATE)
                        .getFloat("playback_speed", 1f)
                }.getOrDefault(1f)
                mutable.value = saved.takeIf { it.isFinite() && it in 0.75f..2f } ?: 1f
            }
        }
    }

    suspend fun set(context: Context, speed: Float) = withContext(Dispatchers.IO) {
        require(speed.isFinite() && speed in 0.75f..2f) { "Choose a speed between 0.75× and 2×." }
        mutex.withLock { persist(context, speed) }
    }

    /** A decoder fallback must not overwrite a newer choice made by the user. */
    internal suspend fun fallback(context: Context, failed: Float) = withContext(Dispatchers.IO) {
        mutex.withLock { if (mutable.value == failed) persist(context, 1f) }
    }

    private fun persist(context: Context, speed: Float) {
        context.applicationContext.getSharedPreferences("cloud_narration", Context.MODE_PRIVATE)
            .edit().putFloat("playback_speed", speed).apply()
        mutable.value = speed
    }

    fun label(speed: Float): String = speed.toString().removeSuffix(".0") + "×"
}
