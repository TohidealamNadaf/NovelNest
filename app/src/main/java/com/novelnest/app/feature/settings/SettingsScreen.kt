package com.novelnest.app.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TouchApp
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
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSecondary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.rememberAreAnimationsDisabled
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic
import com.novelnest.app.core.ui.footer.rememberFooterBottomPadding
import com.novelnest.app.core.ui.header.NovelNestHeader
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.preferences.GeneralAppSettings
import com.novelnest.app.data.preferences.ReaderFont
import com.novelnest.app.data.preferences.ReaderPreferencesManager
import com.novelnest.app.data.preferences.ReaderScrollMode
import com.novelnest.app.data.preferences.ReaderSettings
import com.novelnest.app.data.preferences.ReaderTheme

/**
 * Dedicated sub-pages for categorized Settings navigation.
 */
enum class SettingsSubPage {
    MAIN,
    READING_DISPLAY,
    READING_BEHAVIOR,
    APP_EXPERIENCE,
    STORAGE_CACHE
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val readerSettings by viewModel.readerSettings.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val cacheSize by viewModel.cacheSizeFormatted.collectAsState()
    val clearSuccessMessage by viewModel.clearCacheSuccessMessage.collectAsState()

    var currentSubPage by remember { mutableStateOf(SettingsSubPage.MAIN) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val areAnimationsDisabled = rememberAreAnimationsDisabled()

    // Handle physical / gesture back navigation seamlessly
    BackHandler(enabled = currentSubPage != SettingsSubPage.MAIN) {
        currentSubPage = SettingsSubPage.MAIN
    }

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
        AnimatedContent(
            targetState = currentSubPage,
            transitionSpec = {
                if (areAnimationsDisabled) {
                    androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                } else if (targetState == SettingsSubPage.MAIN) {
                    (slideInHorizontally(animationSpec = tween(280)) { -it / 2 } + fadeIn(animationSpec = tween(280)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(280)) { it } + fadeOut(animationSpec = tween(200)))
                } else {
                    (slideInHorizontally(animationSpec = tween(280)) { it } + fadeIn(animationSpec = tween(280)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(280)) { -it / 2 } + fadeOut(animationSpec = tween(200)))
                }
            },
            label = "settings_subpage_transition"
        ) { page ->
            when (page) {
                SettingsSubPage.MAIN -> {
                    SettingsMainView(
                        readerSettings = readerSettings,
                        appSettings = appSettings,
                        cacheSize = cacheSize,
                        onNavigateTo = { currentSubPage = it },
                        onResetRequested = { showResetDialog = true }
                    )
                }

                SettingsSubPage.READING_DISPLAY -> {
                    ReadingDisplayPage(
                        readerSettings = readerSettings,
                        viewModel = viewModel,
                        onBack = { currentSubPage = SettingsSubPage.MAIN },
                        onResetRequested = { showResetDialog = true }
                    )
                }

                SettingsSubPage.READING_BEHAVIOR -> {
                    ReadingBehaviorPage(
                        readerSettings = readerSettings,
                        viewModel = viewModel,
                        onBack = { currentSubPage = SettingsSubPage.MAIN }
                    )
                }

                SettingsSubPage.APP_EXPERIENCE -> {
                    AppExperiencePage(
                        appSettings = appSettings,
                        viewModel = viewModel,
                        onBack = { currentSubPage = SettingsSubPage.MAIN }
                    )
                }

                SettingsSubPage.STORAGE_CACHE -> {
                    StorageCachePage(
                        cacheSize = cacheSize,
                        onBack = { currentSubPage = SettingsSubPage.MAIN },
                        onClearCacheRequested = { showClearCacheDialog = true }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = rememberFooterBottomPadding() + 8.dp)
        )

        // Reset Defaults Confirmation Dialog
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

/**
 * Categorized Master Settings view listing setting sections.
 */
@Composable
private fun SettingsMainView(
    readerSettings: ReaderSettings,
    appSettings: GeneralAppSettings,
    cacheSize: String,
    onNavigateTo: (SettingsSubPage) -> Unit,
    onResetRequested: () -> Unit
) {
    val playHaptic = rememberNovelNestHaptic()

    Column(modifier = Modifier.fillMaxSize()) {
        NovelNestHeader(
            title = "Settings",
            subtitle = "Preferences & reading controls",
            icon = Icons.Rounded.Settings
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = rememberFooterBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Reading & Display Category Tile
            item {
                SettingsCategoryTile(
                    title = "Reading & Display",
                    subtitle = "Themes, typography & live preview",
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    valueBadge = "${readerSettings.theme.displayName} • ${readerSettings.fontSizeSp.toInt()}sp",
                    onClick = {
                        playHaptic(NovelNestHapticType.LIGHT_TICK)
                        onNavigateTo(SettingsSubPage.READING_DISPLAY)
                    }
                )
            }

            // 2. Reading Behavior Category Tile
            item {
                SettingsCategoryTile(
                    title = "Reading Behavior",
                    subtitle = "Scroll mode, screen awake & volume keys",
                    icon = Icons.Rounded.TouchApp,
                    valueBadge = readerSettings.scrollMode.displayName,
                    onClick = {
                        playHaptic(NovelNestHapticType.LIGHT_TICK)
                        onNavigateTo(SettingsSubPage.READING_BEHAVIOR)
                    }
                )
            }

            // 3. App Experience & Haptics Category Tile
            item {
                SettingsCategoryTile(
                    title = "App Experience & Haptics",
                    subtitle = "Default source, tactile feedback & motion",
                    icon = Icons.Rounded.Tune,
                    valueBadge = if (appSettings.hapticFeedbackEnabled) "Haptics On" else "Haptics Off",
                    onClick = {
                        playHaptic(NovelNestHapticType.LIGHT_TICK)
                        onNavigateTo(SettingsSubPage.APP_EXPERIENCE)
                    }
                )
            }

            // 4. Storage & Cache Category Tile
            item {
                SettingsCategoryTile(
                    title = "Storage & Offline Cache",
                    subtitle = "Offline chapters & memory optimization",
                    icon = Icons.Rounded.Storage,
                    valueBadge = cacheSize,
                    onClick = {
                        playHaptic(NovelNestHapticType.LIGHT_TICK)
                        onNavigateTo(SettingsSubPage.STORAGE_CACHE)
                    }
                )
            }

            // 5. Reset All Preferences Action Card
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = NovelSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClick = {
                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                onResetRequested()
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NovelPrimary.copy(alpha = 0.12f))
                                .border(0.5.dp, NovelPrimary.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = null,
                                tint = NovelPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reset All Preferences",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NovelTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Restore reading and app settings to defaults",
                                style = MaterialTheme.typography.bodySmall,
                                color = NovelTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // 6. About Sanctuary Card
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
}

/**
 * Dedicated Sub-Page: Reading & Display Preferences (Live Reader View, Themes, Typography).
 */
@Composable
private fun ReadingDisplayPage(
    readerSettings: ReaderSettings,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onResetRequested: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
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

/**
 * Dedicated Sub-Page: Reading Behavior (Scroll Mode, Screen Awake, Volume Navigation).
 */
@Composable
private fun ReadingBehaviorPage(
    readerSettings: ReaderSettings,
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        NovelNestHeader(
            title = "Reading Behavior",
            subtitle = "Scroll mode, screen awake & volume keys",
            navigationIcon = {
                NovelNestIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to Settings",
                    onClick = onBack
                )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NovelSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                                        .selectable(
                                            selected = isSelected,
                                            role = Role.Tab,
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
        }
    }
}

/**
 * Dedicated Sub-Page: App Experience & Haptics (Source, Haptics, Reduced Motion).
 */
@Composable
private fun AppExperiencePage(
    appSettings: GeneralAppSettings,
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        NovelNestHeader(
            title = "App Experience",
            subtitle = "Tactile haptics & motion transitions",
            navigationIcon = {
                NovelNestIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to Settings",
                    onClick = onBack
                )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NovelSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                                        .selectable(
                                            selected = isSelected,
                                            role = Role.Tab,
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

                        SettingsToggleRow(
                            title = "Haptic Touch Feedback",
                            subtitle = "Subtle tactile vibrations on button clicks, tabs, and bookmark actions",
                            checked = appSettings.hapticFeedbackEnabled,
                            onCheckedChange = { viewModel.setHapticFeedback(it) }
                        )

                        HorizontalDivider(
                            color = NovelSurfaceBorder.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        SettingsToggleRow(
                            title = "Reduce Motion",
                            subtitle = "Simplify transitions and disable spring bouncing for accessibility",
                            checked = appSettings.reducedMotion,
                            onCheckedChange = { viewModel.setReducedMotion(it) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Sub-Page: Storage & Offline Cache Management.
 */
@Composable
private fun StorageCachePage(
    cacheSize: String,
    onBack: () -> Unit,
    onClearCacheRequested: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        NovelNestHeader(
            title = "Storage & Cache",
            subtitle = "Offline chapters & memory optimization",
            navigationIcon = {
                NovelNestIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to Settings",
                    onClick = onBack
                )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NovelSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Current storage usage: $cacheSize",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NovelTextSecondary
                                )
                            }

                            Button(
                                onClick = onClearCacheRequested,
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

                        HorizontalDivider(
                            color = NovelSurfaceBorder.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        Text(
                            text = "Clearing cache frees temporary device storage without affecting your saved library, bookmarks, or reading progress history.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NovelTextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clickable categorized tile on the main settings screen.
 */
@Composable
private fun SettingsCategoryTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    valueBadge: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NovelSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder),
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NovelPrimary.copy(alpha = 0.12f))
                    .border(0.5.dp, NovelPrimary.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NovelPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NovelTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NovelTextSecondary,
                    fontSize = 11.sp
                )
                if (valueBadge != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = valueBadge,
                        color = NovelSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = NovelTextSecondary.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
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
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onThemeSelected(theme) }
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                // Active checkmark badge
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
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
