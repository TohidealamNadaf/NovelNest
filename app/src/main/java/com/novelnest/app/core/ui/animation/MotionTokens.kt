package com.novelnest.app.core.ui.animation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * Standardized Motion and Animation Tokens for NovelNest.
 * Ensures consistent damping, stiffness, duration, and curve rhythm across all features.
 */
object MotionTokens {
    const val DurationFast = 150
    const val DurationNormal = 250
    const val DurationMedium = 350
    const val DurationLong = 500

    val TweenFast: TweenSpec<Float> = tween(durationMillis = DurationFast, easing = FastOutSlowInEasing)
    val TweenNormal: TweenSpec<Float> = tween(durationMillis = DurationNormal, easing = FastOutSlowInEasing)
    val TweenMedium: TweenSpec<Float> = tween(durationMillis = DurationMedium, easing = FastOutSlowInEasing)

    /**
     * Fast, snappy spring for press-down state (high responsiveness, no latency).
     */
    val SpringSnappy: SpringSpec<Float> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessHigh
    )

    /**
     * Bouncy spring for touch release and selection pop.
     */
    val SpringBouncy: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /**
     * Gentle spring for indicator dots, sliding pills, and theme morphing.
     */
    val SpringGentle: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )
}

/**
 * Haptic feedback semantic event types.
 */
enum class NovelNestHapticType {
    LIGHT_TICK,     // Filter select, tab tap, stepper tick
    ACTION_CONFIRM, // Bookmark toggle, download, read now
    SUCCESS,        // Cache cleared, settings reset
    WARNING         // Delete, error
}

/**
 * Provides contextual haptic feedback respecting system capabilities.
 */
@Composable
fun rememberNovelNestHaptic(hapticEnabled: Boolean = true): (NovelNestHapticType) -> Unit {
    val hapticFeedback = LocalHapticFeedback.current
    return remember(hapticFeedback, hapticEnabled) {
        { type ->
            if (hapticEnabled) {
                when (type) {
                    NovelNestHapticType.LIGHT_TICK -> {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    NovelNestHapticType.ACTION_CONFIRM,
                    NovelNestHapticType.SUCCESS -> {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    NovelNestHapticType.WARNING -> {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            }
        }
    }
}

/**
 * Shimmer skeleton loading effect for feeds, cards, search results, and detail sections.
 * Displays a subtle, flowing gradient sweep to communicate asynchronous loading without blocking layout.
 */
fun Modifier.shimmerPlaceholder(
    visible: Boolean,
    shape: Shape = RoundedCornerShape(8.dp),
    baseColor: Color = Color(0xFF161B22),
    highlightColor: Color = Color(0xFF28323F)
): Modifier = composed {
    if (!visible) return@composed this

    val areAnimationsDisabled = rememberAreAnimationsDisabled()

    if (areAnimationsDisabled) {
        return@composed this
            .clip(shape)
            .background(baseColor)
    }

    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            baseColor,
            highlightColor,
            baseColor
        ),
        start = Offset(x = translateAnimation - 300f, y = translateAnimation - 300f),
        end = Offset(x = translateAnimation, y = translateAnimation)
    )

    this
        .clip(shape)
        .background(brush)
}
