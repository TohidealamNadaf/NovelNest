package com.novelnest.app.feature.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic
import com.novelnest.app.core.ui.footer.rememberFooterBottomPadding
import com.novelnest.app.core.ui.header.NovelNestHeader
import com.novelnest.app.data.preferences.GeneralAppSettings
import com.novelnest.app.data.preferences.ReaderSettings
import com.novelnest.app.feature.settings.components.SettingsCategoryTile

/**
 * Main Settings overview screen.
 * Designed with UI/UX Pro Max principles:
 * - Solid, borderless card architecture (zero glassmorphism, zero stroke clutter)
 * - Cohesive obsidian surface layers on NovelBackground
 * - Solid icon badges and chips without borders
 * - Responsive micro-bounce feedback and haptic ticks
 */
@Composable
fun SettingsMainView(
    readerSettings: ReaderSettings,
    appSettings: GeneralAppSettings,
    cacheSize: String,
    onNavigateTo: (SettingsSubPage) -> Unit,
    onResetRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playHaptic = rememberNovelNestHaptic()
    val cardShape = RoundedCornerShape(18.dp)

    Column(modifier = modifier.fillMaxSize()) {
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

            // 5. Reset All Preferences Action Card (Solid card, no borders)
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = NovelSurface),
                    shape = cardShape,
                    border = null,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .bounceClick(
                            scaleDown = BounceScale.Medium,
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
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Solid danger icon badge with no border
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF281E1E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = null,
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reset All Preferences",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
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

            // 6. About Sanctuary Card (Solid card, no borders or glassmorphism)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NovelSurface),
                    shape = cardShape,
                    border = null,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(NovelSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = NovelPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "NovelNest v1.0",
                                style = MaterialTheme.typography.titleMedium,
                                color = NovelTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Calm Reading Sanctuary • Offline Cache Enabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = NovelTextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(NovelSurfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "PRO",
                                color = NovelPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
