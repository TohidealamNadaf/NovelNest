package com.novelnest.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.novelnest.app.data.preferences.ReaderScrollMode
import com.novelnest.app.data.preferences.ReaderSettings
import com.novelnest.app.feature.settings.components.SettingsToggleRow

@Composable
fun ReadingBehaviorPage(
    readerSettings: ReaderSettings,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
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
