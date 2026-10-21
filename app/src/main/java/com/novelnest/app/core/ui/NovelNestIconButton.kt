package com.novelnest.app.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.bounceClick

import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic

/**
 * Standard tactile icon button for NovelNest.
 * Guarantees a minimum 48dp touch target for accessibility, tactile spring bounce,
 * soft indication feedback, and mandatory contentDescription.
 */
@Composable
fun NovelNestIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = NovelTextPrimary,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true
) {
    val playHaptic = rememberNovelNestHaptic()

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
            }
            .bounceClick(
                scaleDown = BounceScale.Small,
                enabled = enabled,
                onClickLabel = contentDescription,
                role = Role.Button,
                onClick = {
                    playHaptic(NovelNestHapticType.LIGHT_TICK)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .clearAndSetSemantics { }
        )
    }
}
