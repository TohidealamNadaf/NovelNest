package com.novelnest.app.core.ui.footer

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared layout dimension tokens for the pill-shaped navigation footer.
 */
object FooterTokens {
    val PillHeight: Dp = 58.dp
    val VerticalMargin: Dp = 10.dp
    val TotalFooterHeight: Dp = PillHeight + (VerticalMargin * 2) // 78.dp
}

/**
 * Returns bottom contentPadding for scrollable containers displayed above the pill footer.
 * Calculates footer pill height + outer vertical margins + system navigation bar insets + safety margin.
 * Guarantees that the last item will never be hidden behind the footer on either
 * gesture navigation or 3-button navigation.
 */
@Composable
fun rememberFooterBottomPadding(extraMargin: Dp = 16.dp): Dp {
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return FooterTokens.TotalFooterHeight + navBarInset + extraMargin
}
