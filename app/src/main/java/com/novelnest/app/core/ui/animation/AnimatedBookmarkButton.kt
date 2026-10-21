package com.novelnest.app.core.ui.animation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novelnest.app.core.designsystem.NovelGold
import com.novelnest.app.core.designsystem.NovelTextSecondary

/**
 * Animated bookmark toggle button with scale burst, icon morph, and tactile haptic feedback.
 */
@Composable
fun AnimatedBookmarkButton(
    isBookmarked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
    activeColor: Color = NovelGold,
    inactiveColor: Color = NovelTextSecondary,
    hapticEnabled: Boolean = true
) {
    val areAnimationsDisabled = rememberAreAnimationsDisabled()
    val playHaptic = rememberNovelNestHaptic(hapticEnabled)

    val scale by animateFloatAsState(
        targetValue = if (isBookmarked) 1.22f else 1.0f,
        animationSpec = if (areAnimationsDisabled) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "bookmark_burst_scale"
    )

    val tintColor by animateColorAsState(
        targetValue = if (isBookmarked) activeColor else inactiveColor,
        animationSpec = if (areAnimationsDisabled) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "bookmark_color"
    )

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .semantics(mergeDescendants = true) {
                this.contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark"
            }
            .bounceClick(
                scaleDown = BounceScale.Small,
                role = Role.Button,
                onClickLabel = if (isBookmarked) "Remove bookmark" else "Add bookmark"
            ) {
                playHaptic(NovelNestHapticType.ACTION_CONFIRM)
                onToggle(!isBookmarked)
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier
                .size(iconSize)
                .scale(scale)
        )
    }
}
