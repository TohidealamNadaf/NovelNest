package com.novelnest.app.core.designsystem

import android.app.Activity
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NovelPrimary,
    onPrimary = NovelOnPrimary,
    primaryContainer = NovelPrimaryVariant,
    onPrimaryContainer = NovelOnPrimary,
    secondary = NovelSecondary,
    onSecondary = NovelOnSecondary,
    tertiary = NovelTertiary,
    background = NovelBackground,
    onBackground = NovelTextPrimary,
    surface = NovelSurface,
    onSurface = NovelTextPrimary,
    surfaceVariant = NovelSurfaceVariant,
    onSurfaceVariant = NovelTextSecondary,
    outline = NovelSurfaceBorder,
    outlineVariant = NovelSurfaceBorder.copy(alpha = 0.5f),
    error = NovelError
)

@Composable
fun NovelNestTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = NovelBackground.toArgb()
                window.navigationBarColor = NovelBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(
        LocalIndication provides NovelNestIndication
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = NovelTypography,
            content = content
        )
    }
}
