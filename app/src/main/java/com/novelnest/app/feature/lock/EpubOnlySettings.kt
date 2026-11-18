package com.novelnest.app.feature.lock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.footer.rememberPageBottomPadding
import com.novelnest.app.core.ui.header.NovelNestHeader
import com.novelnest.app.feature.settings.components.SettingsSectionHeader
import com.novelnest.app.feature.settings.components.SettingsToggleRow
import com.novelnest.app.feature.settings.components.ThemeSelectorRow
import java.util.Locale

/** Reading-only settings. No routes or callbacks to credentials, notifications, AI or storage. */
@Composable
fun EpubOnlySettings(app: NovelNestApplication, onBack: () -> Unit) {
    val preferences = app.preferencesManager
    val settings by preferences.readerSettings.collectAsStateWithLifecycle()
    var fontSize by remember(settings.fontSizeSp) { mutableFloatStateOf(settings.fontSizeSp) }
    var lineHeight by remember(settings.lineHeightMultiplier) { mutableFloatStateOf(settings.lineHeightMultiplier) }
    val bottomPadding = rememberPageBottomPadding() + 16.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NovelBackground)
    ) {
        NovelNestHeader(
            title = "EPUB Reading Preferences",
            subtitle = "Themes, typography & reader controls",
            navigationIcon = {
                NovelNestIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to EPUB library",
                    onClick = onBack
                )
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. Reading Themes Section (matches ReadingDisplayPage)
                item {
                    SettingsSectionHeader(
                        title = "Reading Themes",
                        icon = Icons.Rounded.AutoStories
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ThemeSelectorRow(
                        selectedTheme = settings.theme,
                        onThemeSelected = preferences::updateTheme
                    )
                }

                // 2. Typography & Sizing Card (matches ReadingDisplayPage)
                item {
                    SettingsSectionHeader(
                        title = "Typography & Sizing",
                        icon = Icons.AutoMirrored.Rounded.MenuBook
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NovelSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, NovelSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
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
                                        onClick = {
                                            val updated = (fontSize - 1f).coerceIn(12f, 32f)
                                            fontSize = updated
                                            preferences.updateFontSize(updated)
                                        }
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(NovelSurfaceVariant)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${fontSize.toInt()} sp",
                                            color = NovelPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    NovelNestIconButton(
                                        icon = Icons.Rounded.Add,
                                        contentDescription = "Increase font size",
                                        iconSize = 16.dp,
                                        onClick = {
                                            val updated = (fontSize + 1f).coerceIn(12f, 32f)
                                            fontSize = updated
                                            preferences.updateFontSize(updated)
                                        }
                                    )
                                }
                            }
                            Slider(
                                value = fontSize.coerceIn(12f, 32f),
                                valueRange = 12f..32f,
                                onValueChange = { fontSize = it },
                                onValueChangeFinished = { preferences.updateFontSize(fontSize) },
                                colors = SliderDefaults.colors(
                                    thumbColor = NovelPrimary,
                                    activeTrackColor = NovelPrimary,
                                    inactiveTrackColor = NovelSurfaceVariant
                                )
                            )

                            HorizontalDivider(
                                color = NovelSurfaceBorder.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = 10.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Line Spacing",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NovelTextPrimary
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(NovelSurfaceVariant)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.1fx", lineHeight),
                                        color = NovelPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Slider(
                                value = lineHeight.coerceIn(1.2f, 2.4f),
                                valueRange = 1.2f..2.4f,
                                onValueChange = { lineHeight = it },
                                onValueChangeFinished = { preferences.updateLineHeight(lineHeight) },
                                colors = SliderDefaults.colors(
                                    thumbColor = NovelPrimary,
                                    activeTrackColor = NovelPrimary,
                                    inactiveTrackColor = NovelSurfaceVariant
                                )
                            )
                        }
                    }
                }

                // 3. Reader Behavior Card (matches ReadingBehaviorPage)
                item {
                    SettingsSectionHeader(
                        title = "Reader Behavior",
                        icon = Icons.Rounded.TouchApp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NovelSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, NovelSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SettingsToggleRow(
                                title = "Keep screen on",
                                subtitle = "Prevent screen sleep while reading EPUBs",
                                checked = settings.keepScreenOn,
                                onCheckedChange = preferences::updateKeepScreenOn
                            )
                            HorizontalDivider(
                                color = NovelSurfaceBorder.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            SettingsToggleRow(
                                title = "Auto hide controls",
                                subtitle = "Hide EPUB reader controls while scrolling",
                                checked = settings.epubAutoHideControls,
                                onCheckedChange = preferences::updateEpubAutoHideControls
                            )
                            HorizontalDivider(
                                color = NovelSurfaceBorder.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                            Text(
                                text = "More EPUB display and illustration controls are available inside the reader.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NovelTextSecondary
                            )
                        }
                    }
                }

                // 4. Lock Session Action
                item {
                    OutlinedButton(
                        onClick = { app.appLock.lock() },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NovelSurfaceBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = NovelSurface,
                            contentColor = NovelTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = NovelPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lock app", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
