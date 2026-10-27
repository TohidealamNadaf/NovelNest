package com.novelnest.app.feature.manhwa.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Only the resumed reader owns system bars; background/outgoing destinations restore their state. */
@Composable
internal fun ManhwaSystemBars(controlsVisible: Boolean) {
    val view = LocalView.current
    val window = view.context.findActivity()?.window ?: return
    if (view.isInEditMode) return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focused = LocalWindowInfo.current.isWindowFocused
    val visible by rememberUpdatedState(controlsVisible)
    val bars = remember(window, view) { ManhwaWindowBars(window, view) }

    DisposableEffect(bars, lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) bars.apply(visible)
            else bars.restore()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            bars.restore()
        }
    }
    // React to visibility/focus changes, not page-position updates or every recomposition.
    LaunchedEffect(bars, lifecycle, controlsVisible, focused) {
        if (focused && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) bars.apply(visible)
    }
}

@Suppress("DEPRECATION") // Bar color remains needed below Android 15; newer versions draw edge-to-edge.
private class ManhwaWindowBars(private val window: Window, private val view: View) {
    private val controller = WindowCompat.getInsetsController(window, view)
    private var previous: PreviousBars? = null

    fun apply(visible: Boolean) {
        if (previous == null) {
            val insets = ViewCompat.getRootWindowInsets(view)
            previous = PreviousBars(
                window.statusBarColor, controller.systemBarsBehavior,
                controller.isAppearanceLightStatusBars, controller.isAppearanceLightNavigationBars,
                insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true,
                insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
            )
        }
        window.statusBarColor = Color.TRANSPARENT
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val types = WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
        if (visible) controller.show(types) else controller.hide(types)
    }

    fun restore() {
        val saved = previous ?: return
        previous = null
        window.statusBarColor = saved.statusBarColor
        controller.isAppearanceLightStatusBars = saved.lightStatusIcons
        controller.isAppearanceLightNavigationBars = saved.lightNavigationIcons
        controller.systemBarsBehavior = saved.behavior
        val status = WindowInsetsCompat.Type.statusBars()
        val navigation = WindowInsetsCompat.Type.navigationBars()
        if (saved.statusVisible) controller.show(status) else controller.hide(status)
        if (saved.navigationVisible) controller.show(navigation) else controller.hide(navigation)
    }

    private data class PreviousBars(
        val statusBarColor: Int,
        val behavior: Int,
        val lightStatusIcons: Boolean,
        val lightNavigationIcons: Boolean,
        val statusVisible: Boolean,
        val navigationVisible: Boolean
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.findActivity() else null
    else -> null
}
