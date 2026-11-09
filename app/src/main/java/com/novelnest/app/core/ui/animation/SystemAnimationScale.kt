package com.novelnest.app.core.ui.animation

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalContext

/**
 * Checks whether system animations are disabled (e.g., via Accessibility or Developer options).
 * When animations are disabled, transitions and spring physics should snap immediately.
 */
@Composable
fun rememberAreAnimationsDisabled(): Boolean {
    // Reuse Compose's lifecycle-owned, observable animator scale. No per-widget
    // settings observers or cached values that become stale after a Settings change.
    val motionScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    return motionScale?.let { it.scaleFactor == 0f }
        ?: areAnimationsDisabled(LocalContext.current)
}

fun areAnimationsDisabled(context: Context): Boolean {
    return try {
        val resolver = context.contentResolver
        val durationScale = Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        )
        // Window/activity transition scale does not control in-app animations.
        durationScale == 0f
    } catch (_: Exception) {
        false
    }
}
