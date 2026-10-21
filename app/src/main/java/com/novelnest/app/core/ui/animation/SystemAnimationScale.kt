package com.novelnest.app.core.ui.animation

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Checks whether system animations are disabled (e.g., via Accessibility or Developer options).
 * When animations are disabled, transitions and spring physics should snap immediately.
 */
@Composable
fun rememberAreAnimationsDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        areAnimationsDisabled(context)
    }
}

fun areAnimationsDisabled(context: Context): Boolean {
    return try {
        val resolver = context.contentResolver
        val durationScale = Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        )
        val transitionScale = Settings.Global.getFloat(
            resolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1.0f
        )
        durationScale == 0f || transitionScale == 0f
    } catch (_: Exception) {
        false
    }
}
