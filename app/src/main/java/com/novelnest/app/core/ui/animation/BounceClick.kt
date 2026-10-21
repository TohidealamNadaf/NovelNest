package com.novelnest.app.core.ui.animation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/**
 * Standard tactile bounce scales across NovelNest UI elements.
 */
object BounceScale {
    const val Small = 0.92f      // Chips, icon buttons, tabs, footer items
    const val Medium = 0.96f     // Search bar, action buttons
    const val Large = 0.97f      // Novel poster cards
    const val ExtraLarge = 0.98f // Hero carousel backdrop card
}

/**
 * Adds a responsive, tactile bouncy spring effect when clicked or touched.
 * - Press-down uses a fast, nearly critically damped spring (0.8f damping, high stiffness).
 * - Release uses a bouncy spring (MediumBouncy, Medium stiffness) so only release overshoots.
 * - Scale is applied via graphicsLayer lambda to avoid recomposing every frame.
 * - Supports keyboard and accessibility focus feedback.
 * - Respects system "remove animations" setting.
 */
fun Modifier.bounceClick(
    scaleDown: Float = BounceScale.Small,
    enabled: Boolean = true,
    indication: Indication? = null,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val areAnimationsDisabled = rememberAreAnimationsDisabled()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = if (areAnimationsDisabled) {
            snap()
        } else if (isPressed) {
            spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessHigh
            )
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        },
        label = "bounce_scale"
    )

    val focusAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.12f else 0f,
        animationSpec = if (areAnimationsDisabled) snap() else spring(),
        label = "bounce_focus"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (focusAlpha > 0f) {
                drawRect(color = Color.White.copy(alpha = focusAlpha))
            }
        }
        .clickable(
            interactionSource = interactionSource,
            indication = indication,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick
        )
}

/**
 * Adds bounce physics to selectable elements (e.g. tabs, navigation items).
 */
fun Modifier.bounceSelectable(
    selected: Boolean,
    scaleDown: Float = BounceScale.Small,
    enabled: Boolean = true,
    indication: Indication? = null,
    role: Role? = Role.Tab,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val areAnimationsDisabled = rememberAreAnimationsDisabled()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = if (areAnimationsDisabled) {
            snap()
        } else if (isPressed) {
            spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessHigh
            )
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        },
        label = "bounce_selectable_scale"
    )

    val focusAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.12f else 0f,
        animationSpec = if (areAnimationsDisabled) snap() else spring(),
        label = "bounce_selectable_focus"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (focusAlpha > 0f) {
                drawRect(color = Color.White.copy(alpha = focusAlpha))
            }
        }
        .selectable(
            selected = selected,
            interactionSource = interactionSource,
            indication = indication,
            enabled = enabled,
            role = role,
            onClick = onClick
        )
}
