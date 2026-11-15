package com.novelnest.app.feature.epub

import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Activity-window ownership only. The image dialog continues to own its separate window. */
@Composable
internal fun EpubReaderSystemBars(activity: ComponentActivity, hideBars: Boolean, darkIcons: Boolean, suspended: Boolean) {
    val lifecycle = activity.lifecycle
    DisposableEffect(activity, lifecycle, hideBars, darkIcons, suspended) {
        val decor = activity.window.decorView
        val controller = WindowCompat.getInsetsController(activity.window, decor)
        fun apply() {
            if (suspended || activity.isFinishing || activity.isDestroyed || !decor.hasWindowFocus() ||
                !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            controller.isAppearanceLightStatusBars = darkIcons
            controller.isAppearanceLightNavigationBars = darkIcons
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hideBars) controller.hide(WindowInsetsCompat.Type.systemBars())
            else controller.show(WindowInsetsCompat.Type.systemBars())
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) apply() }
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus -> if (hasFocus) apply() }
        val tree = decor.viewTreeObserver
        tree.addOnWindowFocusChangeListener(focusListener)
        lifecycle.addObserver(observer)
        apply()
        onDispose {
            lifecycle.removeObserver(observer)
            if (tree.isAlive) tree.removeOnWindowFocusChangeListener(focusListener)
            else if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
        }
    }
}

/** Positioned over Readium; animation never changes the publication viewport height. */
@Composable
internal fun EpubReaderChromeOverlay(visible: Boolean, fromTop: Boolean, reducedMotion: Boolean,
    modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val duration = if (reducedMotion) 0 else 180
    AnimatedVisibility(visible = visible, modifier = modifier,
        enter = slideInVertically(tween(duration)) { height -> if (fromTop) -height else height } + fadeIn(tween(duration)),
        exit = slideOutVertically(tween(duration)) { height -> if (fromTop) -height else height } + fadeOut(tween(duration))) {
        content()
    }
}

/** Reserve the same chrome insets through transient bar visibility/overlay animation changes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun epubChromeInsets(): WindowInsets =
    WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
