package com.novelnest.app.feature.reader.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** Material 3 has its own window Back callback. Disable that callback at the
 * sheet and register with the dialog dispatcher, rather than the underlying Activity. */
@Composable
internal fun ReaderSheetBackHandler(onBack: () -> Unit) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
    val owner = window?.decorView?.findViewTreeOnBackPressedDispatcherOwner()
        ?: view.findViewTreeOnBackPressedDispatcherOwner()
        ?: LocalOnBackPressedDispatcherOwner.current
    if (owner != null) {
        CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
            BackHandler(onBack = onBack)
        }
    }
}
