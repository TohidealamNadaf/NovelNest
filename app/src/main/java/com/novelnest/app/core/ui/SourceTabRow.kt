package com.novelnest.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.bounceSelectable
import com.novelnest.app.core.ui.animation.rememberAreAnimationsDisabled
import com.novelnest.app.data.model.NovelSourceType

/**
 * Premium source selector tab row.
 * Features a single sliding indicator pill with spring physics,
 * fixed 44dp height container, 38dp tab minimum height, and accessible selectable semantics.
 */
@Composable
fun SourceTabRow(
    selectedSource: NovelSourceType,
    onSourceSelected: (NovelSourceType) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val areAnimationsDisabled = rememberAreAnimationsDisabled()

    // Store measured (xOffset, width) in Dp for each tab in parent coordinates
    var tabBounds by remember { mutableStateOf(mapOf<NovelSourceType, Pair<Dp, Dp>>()) }

    val currentBounds = tabBounds[selectedSource]
    val targetOffset = currentBounds?.first ?: 0.dp
    val targetWidth = currentBounds?.second ?: 0.dp

    val indicatorOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = if (areAnimationsDisabled) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "source_tab_indicator_offset"
    )

    val indicatorWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = if (areAnimationsDisabled) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "source_tab_indicator_width"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NovelSurfaceVariant)
            .border(1.dp, NovelSurfaceBorder, RoundedCornerShape(12.dp))
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Single sliding pill indicator behind tabs
        if (currentBounds != null) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(indicatorWidth)
                    .height(38.dp)
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(NovelPrimary)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NovelSourceType.entries.forEach { source ->
                val isSelected = source == selectedSource

                val textColorAnimation by animateColorAsState(
                    targetValue = if (isSelected) NovelOnPrimary else NovelTextSecondary,
                    animationSpec = if (areAnimationsDisabled) {
                        snap()
                    } else {
                        spring(stiffness = Spring.StiffnessMediumLow)
                    },
                    label = "tab_text_color"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .onGloballyPositioned { coordinates ->
                            val xDp = with(density) { coordinates.positionInParent().x.toDp() }
                            val widthDp = with(density) { coordinates.size.width.toDp() }
                            val existing = tabBounds[source]
                            if (existing == null || existing.first != xDp || existing.second != widthDp) {
                                tabBounds = tabBounds + (source to (xDp to widthDp))
                            }
                        }
                        .bounceSelectable(
                            selected = isSelected,
                            scaleDown = BounceScale.Small,
                            role = Role.Tab,
                            onClick = { onSourceSelected(source) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        val iconEmoji = when (source) {
                            NovelSourceType.NOVEL_PHOENIX -> "🔥"
                            NovelSourceType.FREE_WEB_NOVEL -> "📖"
                            NovelSourceType.ASURA_SCANS -> "⚔️"
                        }

                        Text(
                            text = iconEmoji,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )

                        Text(
                            text = when (source) {
                                NovelSourceType.NOVEL_PHOENIX -> "NovelPhoenix"
                                NovelSourceType.FREE_WEB_NOVEL -> "FWN"
                                NovelSourceType.ASURA_SCANS -> "Asura Scans"
                            },
                            color = textColorAnimation,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Tab - NovelPhoenix", showBackground = true, backgroundColor = 0xFF0F1216)
@Composable
private fun SourceTabRowNovelPhoenixPreview() {
    SourceTabRow(
        selectedSource = NovelSourceType.NOVEL_PHOENIX,
        onSourceSelected = {}
    )
}

@androidx.compose.ui.tooling.preview.Preview(name = "Tab - FWN", showBackground = true, backgroundColor = 0xFF0F1216)
@Composable
private fun SourceTabRowFreeWebNovelPreview() {
    SourceTabRow(
        selectedSource = NovelSourceType.FREE_WEB_NOVEL,
        onSourceSelected = {}
    )
}

@androidx.compose.ui.tooling.preview.Preview(name = "Tab - Asura Scans", showBackground = true, backgroundColor = 0xFF0F1216)
@Composable
private fun SourceTabRowAsuraScansPreview() {
    SourceTabRow(
        selectedSource = NovelSourceType.ASURA_SCANS,
        onSourceSelected = {}
    )
}

@androidx.compose.ui.tooling.preview.Preview(name = "Tab - Font Scale 1.5x", fontScale = 1.5f, showBackground = true, backgroundColor = 0xFF0F1216)
@Composable
private fun SourceTabRowLargeFontPreview() {
    SourceTabRow(
        selectedSource = NovelSourceType.NOVEL_PHOENIX,
        onSourceSelected = {}
    )
}
