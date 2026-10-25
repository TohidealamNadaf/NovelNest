package com.novelnest.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.bounceClick

/**
 * Standard primary amber gold button for NovelNest.
 * Globally enforces high-contrast [NovelOnPrimary] text and icon colors on amber backgrounds,
 * eliminating grey/muted text issues across the entire application.
 */
@Composable
fun NovelNestButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = NovelPrimary,
        contentColor = NovelOnPrimary,
        disabledContainerColor = NovelPrimary.copy(alpha = 0.35f),
        disabledContentColor = NovelOnPrimary.copy(alpha = 0.5f)
    ),
    content: @Composable RowScope.() -> Unit
) {
    val currentContentColor = if (enabled) colors.contentColor else colors.disabledContentColor
    Button(
        onClick = onClick,
        modifier = modifier.bounceClick(scaleDown = BounceScale.Small, onClick = onClick, enabled = enabled),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding
    ) {
        CompositionLocalProvider(LocalContentColor provides currentContentColor) {
            ProvideTextStyle(
                value = MaterialTheme.typography.labelLarge.copy(
                    color = currentContentColor,
                    fontWeight = FontWeight.Bold
                )
            ) {
                content()
            }
        }
    }
}
