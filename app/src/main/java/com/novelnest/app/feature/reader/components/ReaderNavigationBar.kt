package com.novelnest.app.feature.reader.components

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.novelnest.app.core.designsystem.NovelBackground
import com.novelnest.app.data.preferences.ReaderTheme

/** Opaque scrolling mask for edge-to-edge, plus matching bars on older Android versions. */
@Suppress("DEPRECATION")
@Composable
internal fun ReaderNavigationBar(theme: ReaderTheme, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    if (!view.isInEditMode && window != null) {
        DisposableEffect(window, view) {
            val previousContrast = if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced else false
            onDispose {
                window.navigationBarColor = NovelBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
                if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = previousContrast
            }
        }
        SideEffect {
            window.navigationBarColor = theme.backgroundColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !theme.isDark
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        }
    }
    Box(modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars)
        .background(theme.backgroundColor))
}
