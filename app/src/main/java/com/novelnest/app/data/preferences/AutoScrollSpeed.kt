package com.novelnest.app.data.preferences

/** Density-independent movement; 1x is 20 dp per second, not a words-per-minute estimate. */
object AutoScrollSpeed {
    const val MIN = 0.25f
    const val MAX = 3f
    const val DEFAULT = 1f
    const val BASE_DP_PER_SECOND = 20f

    fun validated(value: Float): Float = if (value.isFinite()) value.coerceIn(MIN, MAX) else DEFAULT
}
