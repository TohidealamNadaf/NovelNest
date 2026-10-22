package com.novelnest.app.feature.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.bounceSelectable
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.data.preferences.ReaderFont
import com.novelnest.app.data.preferences.ReaderSettings
import com.novelnest.app.data.preferences.ReaderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderQuickSettingsSheet(
    settings: ReaderSettings,
    textColor: Color,
    secondaryColor: Color,
    onDismiss: () -> Unit,
    onSetTheme: (ReaderTheme) -> Unit,
    onSetFontSize: (Float) -> Unit,
    onSetLineHeight: (Float) -> Unit,
    onSetFontFamily: (ReaderFont) -> Unit,
    onSetParagraphSpacing: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        containerColor = settings.theme.surfaceColor,
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reading Customization",
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
                NovelNestIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close reader settings",
                    tint = secondaryColor,
                    onClick = onDismiss
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Theme selector
            Text("Theme Palette", color = secondaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.selectableGroup()
            ) {
                items(ReaderTheme.entries) { theme ->
                    val isSelected = theme == settings.theme
                    Box(
                        modifier = Modifier
                            .size(width = 62.dp, height = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.backgroundColor)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) NovelPrimary else Color(0x33888888),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .bounceSelectable(
                                selected = isSelected,
                                scaleDown = BounceScale.Small,
                                onClick = { onSetTheme(theme) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = NovelPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Typography font family selector
            Text("Typography & Font", color = secondaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.selectableGroup()
            ) {
                items(ReaderFont.entries) { font ->
                    val isSelected = font == settings.fontFamily
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) NovelPrimary else Color(0x22888888))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) NovelPrimary else Color(0x33888888),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .bounceSelectable(
                                selected = isSelected,
                                scaleDown = BounceScale.Small,
                                onClick = { onSetFontFamily(font) }
                            )
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = font.displayName,
                            fontFamily = font.fontFamily,
                            color = if (isSelected) NovelOnPrimary else textColor,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Font size controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Font Size", color = secondaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("${settings.fontSizeSp.toInt()} sp", color = NovelPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NovelNestIconButton(
                    icon = Icons.Rounded.Remove,
                    contentDescription = "Decrease font",
                    tint = secondaryColor,
                    onClick = { onSetFontSize((settings.fontSizeSp - 1f).coerceAtLeast(12f)) }
                )
                Slider(
                    value = settings.fontSizeSp,
                    onValueChange = onSetFontSize,
                    valueRange = 12f..28f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = NovelPrimary,
                        activeTrackColor = NovelPrimary,
                        inactiveTrackColor = secondaryColor.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                NovelNestIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Increase font",
                    tint = secondaryColor,
                    onClick = { onSetFontSize((settings.fontSizeSp + 1f).coerceAtMost(28f)) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Line height controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Line Spacing", color = secondaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(String.format("%.1fx", settings.lineHeightMultiplier), color = NovelPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = settings.lineHeightMultiplier,
                onValueChange = onSetLineHeight,
                valueRange = 1.2f..2.2f,
                steps = 9,
                colors = SliderDefaults.colors(
                    thumbColor = NovelPrimary,
                    activeTrackColor = NovelPrimary,
                    inactiveTrackColor = secondaryColor.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Paragraph padding / spacing controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Paragraph Padding", color = secondaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("${settings.paragraphSpacingDp.toInt()} dp", color = NovelPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = settings.paragraphSpacingDp,
                onValueChange = onSetParagraphSpacing,
                valueRange = 4f..24f,
                steps = 9,
                colors = SliderDefaults.colors(
                    thumbColor = NovelPrimary,
                    activeTrackColor = NovelPrimary,
                    inactiveTrackColor = secondaryColor.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(8f to "Compact", 12f to "Normal", 16f to "Relaxed", 20f to "Spacious").forEach { (spacing, label) ->
                    val isSelected = kotlin.math.abs(settings.paragraphSpacingDp - spacing) < 1f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) NovelPrimary else Color(0x22888888))
                            .border(
                                1.dp,
                                if (isSelected) NovelPrimary else Color(0x33888888),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onSetParagraphSpacing(spacing) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) NovelOnPrimary else textColor,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
