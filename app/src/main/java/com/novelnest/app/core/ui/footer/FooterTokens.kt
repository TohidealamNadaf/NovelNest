package com.novelnest.app.core.ui.footer

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared layout dimension tokens for the floating capsule footer dock.
 */
object FooterTokens {
    val CapsuleHeight: Dp = 58.dp
    val VerticalPadding: Dp = 10.dp
    val TotalFooterHeight: Dp = CapsuleHeight + (VerticalPadding * 2) // 78.dp
}

/**
 * Returns bottom contentPadding for scrollable containers displayed above the floating footer.
 * Calculates footer height + outer vertical margins + current navigation bar insets + safety margin.
 * Guarantees that the last item will never be hidden behind the capsule on either
 * gesture navigation or 3-button navigation.
 */
@Composable
fun rememberFooterBottomPadding(extraMargin: Dp = 12.dp): Dp {
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return FooterTokens.TotalFooterHeight + navBarInset + extraMargin
}
