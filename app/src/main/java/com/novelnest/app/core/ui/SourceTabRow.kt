package com.novelnest.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.data.model.NovelSourceType

@Composable
fun SourceTabRow(
    selectedSource: NovelSourceType,
    onSourceSelected: (NovelSourceType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NovelSurfaceVariant)
            .border(1.dp, NovelSurfaceBorder, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NovelSourceType.entries.forEach { source ->
            val isSelected = source == selectedSource

            val bgAnimation by animateColorAsState(
                targetValue = if (isSelected) NovelPrimary else Color.Transparent,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "tab_bg"
            )

            val textColorAnimation by animateColorAsState(
                targetValue = if (isSelected) NovelOnPrimary else NovelTextSecondary,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "tab_text"
            )

            val tabScale by animateFloatAsState(
                targetValue = if (isSelected) 1.02f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "tab_scale"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .scale(tabScale)
                    .clip(RoundedCornerShape(9.dp))
                    .background(bgAnimation)
                    .bounceClick(scaleDown = 0.94f) { onSourceSelected(source) },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
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
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
