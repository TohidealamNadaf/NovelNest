package com.novelnest.app.feature.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary

/**
 * Visual pull-to-transition cue for chapter navigation.
 * Displays stateful caption ("Pull for preview" vs "Release for chapter")
 * and smoothly animates the icon angle when threshold is reached.
 */
@Composable
fun PullChapterIndicator(
    isPullingDown: Boolean, // true = pulling down for previous chapter; false = pulling up for next chapter
    pullDistancePx: Float,
    thresholdPx: Float,
    modifier: Modifier = Modifier
) {
    val isVisible = pullDistancePx > 14f
    val isReadyToTransition = pullDistancePx >= thresholdPx

    // Arrow rotates 180° when threshold is crossed to signify release readiness
    val iconRotation by animateFloatAsState(
        targetValue = if (isReadyToTransition) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pull_indicator_rotation"
    )

    val caption = when {
        isReadyToTransition -> "Release"
        isPullingDown -> "Pull for prev"
        else -> "Pull for next"
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = NovelSurfaceVariant.copy(alpha = 0.94f),
            border = BorderStroke(
                width = 1.dp,
                color = if (isReadyToTransition) NovelPrimary.copy(alpha = 0.6f) else NovelSurfaceBorder
            ),
            shadowElevation = 8.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (isPullingDown) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                    contentDescription = null,
                    tint = if (isReadyToTransition) NovelPrimary else NovelTextSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = iconRotation }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = caption,
                    color = if (isReadyToTransition) NovelPrimary else NovelTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = if (isReadyToTransition) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
