package com.novelnest.app.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleanHands
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelBackground
import com.novelnest.app.core.designsystem.NovelError
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSecondary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.core.ui.animation.bounceSelectable
import com.novelnest.app.core.ui.footer.rememberFooterBottomPadding
import com.novelnest.app.core.ui.header.NovelNestHeader
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.preferences.ReaderFont
import com.novelnest.app.data.preferences.ReaderPreferencesManager
import com.novelnest.app.data.preferences.ReaderScrollMode
import com.novelnest.app.data.preferences.ReaderSettings
import com.novelnest.app.data.preferences.ReaderTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val readerSettings by viewModel.readerSettings.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val cacheSize by viewModel.cacheSizeFormatted.collectAsState()
    val clearSuccessMessage by viewModel.clearCacheSuccessMessage.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(clearSuccessMessage) {
        clearSuccessMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissClearCacheMessage()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NovelBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            NovelNestHeader(
                title = "Settings",
                subtitle = "Personalize your sanctuary",
                icon = Icons.Rounded.Settings
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = rememberFooterBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. LIVE READING PREVIEW CARD
                item {
                    SettingsSectionHeader(
                        title = "Live Reader Preview",
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        action = {
                            TextButton(
                                onClick = { showResetDialog = true },
                                modifier = Modifier.sizeIn(minHeight = 44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestartAlt,
                                    contentDescription = "Reset reading preferences",
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
                    Spacer(modifier = Modifier.height(8.dp))
                    LiveReaderPreviewCard(settings = readerSettings)
                }

                // 2. NOVEL READING PREFERENCES SECTION
                item {
                    SettingsSectionHeader(
                        title = "Reading Themes",
                        icon = Icons.Rounded.Palette
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ThemeSelectorRow(
                        selectedTheme = readerSettings.theme,
                        onThemeSelected = { viewModel.setTheme(it) }
                    )
                }

                item {
                    SettingsSectionHeader(
                        title = "Typography & Sizing",
                        icon = Icons.Rounded.FormatSize
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

                            // Font Family
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
                                            .bounceSelectable(
                                                selected = isSelected,
                                                scaleDown = BounceScale.Small,
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

                            // Line Spacing
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
                                            .bounceSelectable(
                                                selected = isSelected,
                                                scaleDown = BounceScale.Small,
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
                        }
                    }
                }

                // 3. READING EXPERIENCE & CONTROLS
                item {
                    SettingsSectionHeader(
                        title = "Reading Progression & Display",
                        icon = Icons.Rounded.Tune
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NovelSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Scroll Mode Segmented
                            Text(
                                text = "Page Progression Style",
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
                                ReaderScrollMode.entries.forEach { mode ->
                                    val isSelected = mode == readerSettings.scrollMode
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
                                            .bounceSelectable(
                                                selected = isSelected,
                                                scaleDown = BounceScale.Small,
                                                onClick = { viewModel.setScrollMode(mode) }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = mode.displayName,
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

                            // Text Brightness
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

                            HorizontalDivider(
                                color = NovelSurfaceBorder.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )

                            // Toggles
                            SettingsToggleRow(
                                title = "Keep Screen Awake",
                                subtitle = "Prevents device timeout during active reading sessions",
                                checked = readerSettings.keepScreenOn,
                                onCheckedChange = { viewModel.setKeepScreenOn(it) }
                            )

                            HorizontalDivider(
                                color = NovelSurfaceBorder.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            SettingsToggleRow(
                                title = "Volume Keys Page Turn",
                                subtitle = "Use physical volume up/down buttons to turn pages",
                                checked = readerSettings.volumeKeyNavigation,
                                onCheckedChange = { viewModel.setVolumeKeyNavigation(it) }
                            )
                        }
                    }
                }

                // 4. GENERAL APP SETTINGS & DATA
                item {
                    SettingsSectionHeader(
                        title = "App Preferences & Storage",
                        icon = Icons.Rounded.Storage
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NovelSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Default Source
                            Text(
                                text = "Default Source Provider",
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
                                NovelSourceType.entries.forEach { source ->
                                    val isSelected = source.ordinal == appSettings.defaultSourceOrdinal
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
                                            .bounceSelectable(
                                                selected = isSelected,
                                                scaleDown = BounceScale.Small,
                                                onClick = { viewModel.setDefaultSource(source.ordinal) }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = when (source) {
                                                NovelSourceType.NOVEL_PHOENIX -> "Phoenix"
                                                NovelSourceType.FREE_WEB_NOVEL -> "FWN"
                                                NovelSourceType.ASURA_SCANS -> "Asura"
                                            },
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

                            // Clear Cache Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Offline Image & Page Cache",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = NovelTextPrimary
                                    )
                                    Text(
                                        text = "Current disk and memory size: $cacheSize",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NovelTextSecondary
                                    )
                                }

                                Button(
                                    onClick = { showClearCacheDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NovelSurfaceVariant,
                                        contentColor = NovelPrimary
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.sizeIn(minHeight = 44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 5. ABOUT SANCTUARY
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NovelSurfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "NovelNest v1.0",
                                style = MaterialTheme.typography.titleMedium,
                                color = NovelTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Calm Reading Sanctuary • Offline Cache Enabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = NovelTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = rememberFooterBottomPadding() + 8.dp)
        )

        // Reset Defaults Dialog
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                containerColor = NovelSurface,
                title = {
                    Text(
                        text = "Reset Reading Settings?",
                        color = NovelTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "This will restore font size (17sp), Classic Serif, balanced line spacing, and Obsidian dark theme.",
                        color = NovelTextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.resetReadingDefaults()
                            showResetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NovelPrimary, contentColor = NovelOnPrimary)
                    ) {
                        Text("Reset Defaults")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel", color = NovelTextSecondary)
                    }
                }
            )
        }

        // Clear Cache Confirmation Dialog
        if (showClearCacheDialog) {
            AlertDialog(
                onDismissRequest = { showClearCacheDialog = false },
                containerColor = NovelSurface,
                title = {
                    Text(
                        text = "Clear Cache?",
                        color = NovelTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "This will remove cached covers and scraping artifacts to free space. Saved novels and bookmarks will not be affected.",
                        color = NovelTextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearCache()
                            showClearCacheDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NovelPrimary, contentColor = NovelOnPrimary)
                    ) {
                        Text("Clear Cache")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheDialog = false }) {
                        Text("Cancel", color = NovelTextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    icon: ImageVector,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NovelPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = NovelTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        action?.invoke()
    }
}

@Composable
private fun LiveReaderPreviewCard(settings: ReaderSettings) {
    val theme = settings.theme
    val textColor = theme.textColor.copy(alpha = settings.textBrightness)
    val secondaryColor = theme.secondaryTextColor.copy(alpha = settings.textBrightness)

    Card(
        colors = CardDefaults.cardColors(containerColor = theme.backgroundColor),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, if (theme.isDark) NovelSurfaceBorder else Color(0xFFD6C8B4)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Live Preview Header Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.surfaceColor)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "LIVE PREVIEW • ${theme.displayName}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NovelPrimary
                    )
                }

                Text(
                    text = "${settings.fontFamily.displayName} • ${settings.fontSizeSp.toInt()}sp",
                    fontSize = 11.sp,
                    color = secondaryColor
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Chapter Title
            Text(
                text = "Chapter 1: The Whispering Sanctuary",
                fontFamily = settings.fontFamily.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (settings.fontSizeSp + 3f).sp,
                lineHeight = ((settings.fontSizeSp + 3f) * settings.lineHeightMultiplier).sp,
                color = textColor
            )

            Spacer(modifier = Modifier.height(settings.paragraphSpacingDp.dp))

            // Paragraph 1
            Text(
                text = "The silver lantern cast long, dancing shadows across the mahogany desk. Ancient parchment whispered secrets of epochs past, each delicate stroke of ink holding memories that kingdoms had risen and fallen to protect.",
                fontFamily = settings.fontFamily.fontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = settings.fontSizeSp.sp,
                lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
                color = textColor
            )

            Spacer(modifier = Modifier.height(settings.paragraphSpacingDp.dp))

            // Paragraph 2
            Text(
                text = "Sunny drew a quiet breath, turning the aged page with reverent care. In this quiet refuge, the relentless storms of the mortal world faded into distant echoes.",
                fontFamily = settings.fontFamily.fontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = settings.fontSizeSp.sp,
                lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
                color = textColor
            )
        }
    }
}

@Composable
private fun ThemeSelectorRow(
    selectedTheme: ReaderTheme,
    onThemeSelected: (ReaderTheme) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
        modifier = Modifier.selectableGroup()
    ) {
        items(ReaderTheme.entries) { theme ->
            val isSelected = theme == selectedTheme
            Box(
                modifier = Modifier
                    .size(width = 100.dp, height = 76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.backgroundColor)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) NovelPrimary else if (theme.isDark) NovelSurfaceBorder else Color(0xFFD6C8B4),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .bounceSelectable(
                        selected = isSelected,
                        scaleDown = BounceScale.Small,
                        onClick = { onThemeSelected(theme) }
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                // Active checkmark badge
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(NovelPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = NovelOnPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = "Aa",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = theme.displayName,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = theme.secondaryTextColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = NovelTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NovelTextSecondary,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NovelOnPrimary,
                checkedTrackColor = NovelPrimary,
                uncheckedThumbColor = NovelTextSecondary,
                uncheckedTrackColor = NovelSurfaceVariant
            )
        )
    }
}

@Preview(name = "Settings - Dark Obsidian", showBackground = true, backgroundColor = 0xFF0F1216)
@Composable
private fun SettingsScreenPreview() {
    SettingsScreen(
        viewModel = SettingsViewModel(
            preferencesManager = ReaderPreferencesManager(
                androidx.compose.ui.platform.LocalContext.current
            )
        )
    )
}
