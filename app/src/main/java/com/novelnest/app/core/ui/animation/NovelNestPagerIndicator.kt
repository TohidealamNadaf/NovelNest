package com.novelnest.app.core.ui.animation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * High-performance, silky-smooth animated pager indicator.
 * - Interpolates width and color in real-time as the user swipes or as auto-advance animates.
 * - Morphing pill effect: expands from circular dot to rounded pill indicator with spring-like visual inertia.
 * - Clickable: tapping any dot smoothly navigates the pager directly to that slide.
 * - Fully reusable across any horizontal pager throughout NovelNest.
 */
@Composable
fun NovelNestPagerIndicator(
    pagerState: PagerState,
    pageCount: Int = pagerState.pageCount,
    modifier: Modifier = Modifier,
    activeColor: Color = NovelPrimary,
    inactiveColor: Color = NovelSurfaceBorder.copy(alpha = 0.55f),
    dotHeight: Dp = 6.dp,
    activeDotWidth: Dp = 20.dp,
    inactiveDotWidth: Dp = 6.dp,
    spacing: Dp = 6.dp,
    enableTapNavigation: Boolean = true
) {
    if (pageCount <= 1) return

    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (index in 0 until pageCount) {
            val clickModifier = if (enableTapNavigation) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Tab,
                    onClickLabel = "Go to slide ${index + 1}"
                ) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                }
            } else {
                Modifier
            }

            Box(
                modifier = Modifier
                    .semantics {
                        role = Role.Tab
                        contentDescription = "Slide ${index + 1} of $pageCount"
                    }
                    .layout { measurable, _ ->
                        val distance = ((pagerState.currentPage - index) + pagerState.currentPageOffsetFraction)
                            .absoluteValue.coerceIn(0f, 1f)
                        val widthPx = lerp(inactiveDotWidth, activeDotWidth, 1f - distance).roundToPx()
                        val heightPx = dotHeight.roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx))
                        layout(widthPx, heightPx) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                    .drawBehind {
                        val distance = ((pagerState.currentPage - index) + pagerState.currentPageOffsetFraction)
                            .absoluteValue.coerceIn(0f, 1f)
                        val color = lerp(inactiveColor, activeColor, 1f - distance)
                        val radius = size.height / 2f
                        drawRoundRect(color = color, cornerRadius = CornerRadius(radius, radius))
                    }
                    .then(clickModifier)
            )
        }
    }
}
