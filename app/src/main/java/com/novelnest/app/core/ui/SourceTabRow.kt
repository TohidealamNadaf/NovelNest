package com.novelnest.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.animation.rememberAreAnimationsDisabled
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.data.model.NovelSourceType

/** Equal, stable touch targets; indicator geometry derives from constraints without layout callbacks. */
@Composable
fun SourceTabRow(
    selectedSource: NovelSourceType,
    onSourceSelected: (NovelSourceType) -> Unit,
    modifier: Modifier = Modifier
) {
    val animationsDisabled = rememberAreAnimationsDisabled()
    val playHaptic = rememberNovelNestHaptic()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NovelSurfaceVariant)
            .border(1.dp, NovelSurfaceBorder, RoundedCornerShape(14.dp))
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val sources = NovelSourceType.entries
        val gap = 4.dp
        val slotWidth = (maxWidth - gap * (sources.size - 1)) / sources.size
        val selectedIndex = sources.indexOf(selectedSource).coerceAtLeast(0)

        val offset by animateDpAsState(
            targetValue = (slotWidth + gap) * selectedIndex,
            animationSpec = if (animationsDisabled) snap() else spring(dampingRatio = 0.85f, stiffness = 400f),
            label = "source_indicator_offset"
        )

        // Active animated indicator capsule
        Box(
            modifier = Modifier
                .offset(x = offset)
                .width(slotWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(11.dp))
                .background(NovelPrimary)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            sources.forEach { source ->
                val isSelected = source == selectedSource
                val color by animateColorAsState(
                    targetValue = if (isSelected) NovelOnPrimary else NovelTextSecondary,
                    animationSpec = if (animationsDisabled) snap() else spring(),
                    label = "source_color"
                )
                val icon = when (source) {
                    NovelSourceType.NOVEL_PHOENIX -> Icons.Rounded.LocalFireDepartment
                    NovelSourceType.FREE_WEB_NOVEL -> Icons.AutoMirrored.Rounded.MenuBook
                    NovelSourceType.ASURA_SCANS -> Icons.Rounded.Bolt
                }
                val label = source.shortName // "Phoenix", "FreeWeb", "Asura"

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(11.dp))
                        .semantics {
                            contentDescription = "${source.displayName} tab"
                        }
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = {
                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                onSourceSelected(source)
                            }
                        )
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = label,
                        color = color,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
