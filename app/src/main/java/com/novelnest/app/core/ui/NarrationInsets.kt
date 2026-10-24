package com.novelnest.app.core.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/** Mini-player body only. Each page already owns its footer/navigation-bar spacing. */
val LocalNarrationBottomPadding = compositionLocalOf { 0.dp }
