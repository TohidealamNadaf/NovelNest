package com.novelnest.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.footer.rememberFooterBottomPadding
import com.novelnest.app.core.ui.header.NovelNestHeader
import com.novelnest.app.data.preferences.ReaderFont
import com.novelnest.app.data.preferences.ReaderSettings
import com.novelnest.app.feature.settings.components.LiveReaderPreviewCard
import com.novelnest.app.feature.settings.components.SettingsSectionHeader
import com.novelnest.app.feature.settings.components.ThemeSelectorRow

@Composable
fun ReadingDisplayPage(
    readerSettings: ReaderSettings,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onResetRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        NovelNestHeader(
            title = "Reading & Display",
            subtitle = "Themes, typography & live preview",
            navigationIcon = {
                NovelNestIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to Settings",
                    onClick = onBack
                )
            },
            actions = {
                TextButton(
                    onClick = onResetRequested,
                    modifier = Modifier.sizeIn(minHeight = 44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = "Reset",
                        tint = NovelPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Reset",
                        color = NovelPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = rememberFooterBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Live Reader Preview Card
            item {
                SettingsSectionHeader(
                    title = "Live Reader Preview",
                    icon = Icons.AutoMirrored.Rounded.MenuBook
                )
                Spacer(modifier = Modifier.height(8.dp))
                LiveReaderPreviewCard(settings = readerSettings)
            }

            // Reading Themes Section
            item {
                SettingsSectionHeader(
                    title = "Reading Themes",
                    icon = Icons.Rounded.AutoStories
                )
                Spacer(modifier = Modifier.height(10.dp))
                ThemeSelectorRow(
                    selectedTheme = readerSettings.theme,
                    onThemeSelected = { viewModel.setTheme(it) }
                )
            }

            // Typography & Sizing Card
            item {
                SettingsSectionHeader(
                    title = "Typography & Sizing",
                    icon = Icons.AutoMirrored.Rounded.MenuBook
                )
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = NovelSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Font Size Stepper & Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Font Size",
                                style = MaterialTheme.typography.titleMedium,
                                color = NovelTextPrimary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                NovelNestIconButton(
                                    icon = Icons.Rounded.Remove,
                                    contentDescription = "Decrease font size",
                                    iconSize = 16.dp,
                                    onClick = { viewModel.setFontSize(readerSettings.fontSizeSp - 1f) }
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(NovelSurfaceVariant)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${readerSettings.fontSizeSp.toInt()} sp",
                                        color = NovelPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                NovelNestIconButton(
                                    icon = Icons.Rounded.Add,
                                    contentDescription = "Increase font size",
                                    iconSize = 16.dp,
                                    onClick = { viewModel.setFontSize(readerSettings.fontSizeSp + 1f) }
                                )
                            }
                        }

                        Slider(
                            value = readerSettings.fontSizeSp,
                            onValueChange = { viewModel.setFontSize(it) },
                            valueRange = 12f..28f,
                            steps = 15,
                            colors = SliderDefaults.colors(
                                thumbColor = NovelPrimary,
                                activeTrackColor = NovelPrimary,
                                inactiveTrackColor = NovelSurfaceBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider(
                            color = NovelSurfaceBorder.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        // Font Family Selector
                        Text(
                            text = "Font Family",
                            style = MaterialTheme.typography.titleMedium,
                            color = NovelTextPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReaderFont.entries.forEach { font ->
                                val isSelected = font == readerSettings.fontFamily
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .sizeIn(minHeight = 44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) NovelPrimary else NovelSurfaceVariant)
                                        .border(
                                            1.dp,
                                            if (isSelected) NovelPrimary else NovelSurfaceBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .selectable(
                                            selected = isSelected,
                                            role = Role.Tab,
                                            onClick = { viewModel.setFontFamily(font) }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (font) {
                                            ReaderFont.SYSTEM -> "System"
                                            ReaderFont.SERIF -> "Serif"
                                            ReaderFont.SANS_SERIF -> "Sans"
                                            ReaderFont.MONOSPACE -> "Mono"
                                        },
                                        fontFamily = font.fontFamily,
                                        color = if (isSelected) NovelOnPrimary else NovelTextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            color = NovelSurfaceBorder.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        // Line Height Spacing
                        Text(
                            text = "Line Height Spacing",
                            style = MaterialTheme.typography.titleMedium,
                            color = NovelTextPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(1.3f to "Compact", 1.5f to "Normal", 1.7f to "Relaxed", 2.0f to "Spacious").forEach { (multiplier, label) ->
                                val isSelected = kotlin.math.abs(readerSettings.lineHeightMultiplier - multiplier) < 0.05f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .sizeIn(minHeight = 44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) NovelPrimary else NovelSurfaceVariant)
                                        .border(
                                            1.dp,
                                            if (isSelected) NovelPrimary else NovelSurfaceBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .selectable(
                                            selected = isSelected,
                                            role = Role.Tab,
                                            onClick = { viewModel.setLineHeight(multiplier) }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) NovelOnPrimary else NovelTextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            color = NovelSurfaceBorder.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        // Text Contrast / Brightness
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Text Contrast / Softness",
                                style = MaterialTheme.typography.titleMedium,
                                color = NovelTextPrimary
                            )
                            Text(
                                text = "${(readerSettings.textBrightness * 100).toInt()}%",
                                color = NovelPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Slider(
                            value = readerSettings.textBrightness,
                            onValueChange = { viewModel.setTextBrightness(it) },
                            valueRange = 0.5f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = NovelPrimary,
                                activeTrackColor = NovelPrimary,
                                inactiveTrackColor = NovelSurfaceBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
