package com.novelnest.app.core.ui.footer

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared layout dimension tokens for the docked navigation footer.
 */
object FooterTokens {
    val BarHeight: Dp = 64.dp
    val TotalFooterHeight: Dp = BarHeight
}

/**
 * Returns bottom contentPadding for scrollable containers displayed above the footer bar.
 * Calculates footer bar height + system navigation bar insets + safety margin.
 * Guarantees that the last item will never be hidden behind the footer on either
 * gesture navigation or 3-button navigation.
 */
@Composable
fun rememberFooterBottomPadding(extraMargin: Dp = 16.dp): Dp {
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return FooterTokens.BarHeight + navBarInset + extraMargin
}
