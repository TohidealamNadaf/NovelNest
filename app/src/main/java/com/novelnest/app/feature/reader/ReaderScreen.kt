package com.novelnest.app.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.core.ui.animation.bounceSelectable
import com.novelnest.app.data.preferences.ReaderScrollMode
import com.novelnest.app.data.preferences.ReaderSettings
import com.novelnest.app.data.preferences.ReaderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.readerSettings.collectAsState()
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val currentTheme = settings.theme
    val textColor = currentTheme.textColor.copy(alpha = settings.textBrightness)
    val secondaryColor = currentTheme.secondaryTextColor.copy(alpha = settings.textBrightness)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.backgroundColor)
    ) {
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = NovelPrimary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Opening Sanctuary...",
                    color = secondaryColor,
                    fontSize = 13.sp
                )
            }
        } else if (uiState.chapters.isNotEmpty()) {
            val currentChapter = uiState.chapters[uiState.currentChapterIndex]

            // Main Reading Canvas
            if (settings.scrollMode == ReaderScrollMode.CONTINUOUS) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { viewModel.toggleChrome() }
                        ),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 80.dp,
                        bottom = 100.dp
                    )
                ) {
                    item {
                        Text(
                            text = currentChapter.title,
                            fontFamily = settings.fontFamily.fontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = (settings.fontSizeSp + 4f).sp,
                            lineHeight = ((settings.fontSizeSp + 4f) * settings.lineHeightMultiplier).sp,
                            color = textColor,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    items(currentChapter.paragraphs) { paragraph ->
                        Text(
                            text = paragraph,
                            fontFamily = settings.fontFamily.fontFamily,
                            fontSize = settings.fontSizeSp.sp,
                            lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
                            color = textColor,
                            modifier = Modifier.padding(bottom = settings.paragraphSpacingDp.dp)
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.prevChapter() },
                                enabled = uiState.currentChapterIndex > 0,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = currentTheme.surfaceColor,
                                    contentColor = textColor,
                                    disabledContainerColor = currentTheme.surfaceColor.copy(alpha = 0.4f),
                                    disabledContentColor = secondaryColor.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.sizeIn(minHeight = 44.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Previous", fontSize = 12.sp)
                            }

                            Text(
                                text = "${uiState.currentChapterIndex + 1} / ${uiState.chapters.size}",
                                color = secondaryColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Button(
                                onClick = { viewModel.nextChapter() },
                                enabled = uiState.currentChapterIndex < uiState.chapters.size - 1,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NovelPrimary,
                                    contentColor = NovelOnPrimary,
                                    disabledContainerColor = currentTheme.surfaceColor.copy(alpha = 0.4f),
                                    disabledContentColor = secondaryColor.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.sizeIn(minHeight = 44.dp)
                            ) {
                                Text("Next", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            } else {
                // Paged horizontal flip mode
                val pagerState = rememberPagerState(
                    initialPage = uiState.currentChapterIndex,
                    pageCount = { uiState.chapters.size }
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { viewModel.toggleChrome() }
                        )
                ) { page ->
                    val chapter = uiState.chapters[page]
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 80.dp,
                            bottom = 100.dp
                        )
                    ) {
                        item {
                            Text(
                                text = chapter.title,
                                fontFamily = settings.fontFamily.fontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = (settings.fontSizeSp + 4f).sp,
                                lineHeight = ((settings.fontSizeSp + 4f) * settings.lineHeightMultiplier).sp,
                                color = textColor,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }

                        items(chapter.paragraphs) { paragraph ->
                            Text(
                                text = paragraph,
                                fontFamily = settings.fontFamily.fontFamily,
                                fontSize = settings.fontSizeSp.sp,
                                lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
                                color = textColor,
                                modifier = Modifier.padding(bottom = settings.paragraphSpacingDp.dp)
                            )
                        }
                    }
                }
            }
        }

        // TOP IMMERSIVE CONTROL BAR
        AnimatedVisibility(
            visible = uiState.isChromeVisible,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = currentTheme.surfaceColor.copy(alpha = 0.95f),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        NovelNestIconButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Leave reading mode",
                            tint = textColor,
                            onClick = onBack
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = uiState.novel?.title ?: "NovelNest",
                                color = textColor,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (uiState.chapters.isNotEmpty()) {
                                Text(
                                    text = uiState.chapters[uiState.currentChapterIndex].title,
                                    color = secondaryColor,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Quick Settings Trigger
                    NovelNestIconButton(
                        icon = Icons.Rounded.FormatSize,
                        contentDescription = "Adjust reader settings",
                        tint = NovelPrimary,
                        onClick = { viewModel.showQuickSettings(true) }
                    )
                }
            }
        }

        // BOTTOM IMMERSIVE PROGRESSION BAR
        AnimatedVisibility(
            visible = uiState.isChromeVisible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = currentTheme.surfaceColor.copy(alpha = 0.95f),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NovelNestIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Previous chapter",
                        tint = if (uiState.currentChapterIndex > 0) textColor else secondaryColor.copy(alpha = 0.4f),
                        enabled = uiState.currentChapterIndex > 0,
                        onClick = { viewModel.prevChapter() }
                    )

                    Text(
                        text = "Chapter ${uiState.currentChapterIndex + 1} of ${uiState.chapters.size}",
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    NovelNestIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Next chapter",
                        tint = if (uiState.currentChapterIndex < uiState.chapters.size - 1) NovelPrimary else secondaryColor.copy(alpha = 0.4f),
                        enabled = uiState.currentChapterIndex < uiState.chapters.size - 1,
                        onClick = { viewModel.nextChapter() }
                    )
                }
            }
        }

        // QUICK READER SETTINGS BOTTOM SHEET
        if (uiState.showQuickSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.showQuickSettings(false) },
                sheetState = bottomSheetState,
                containerColor = currentTheme.surfaceColor,
                dragHandle = null
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
                            onClick = { viewModel.showQuickSettings(false) }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Theme selector in sheet
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
                                        onClick = { viewModel.setTheme(theme) }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "Active",
                                        tint = NovelPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Text("Aa", color = theme.textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Font Size quick stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Font Size: ${settings.fontSizeSp.toInt()}sp", color = textColor, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NovelNestIconButton(
                                icon = Icons.Rounded.Remove,
                                contentDescription = "Smaller font",
                                tint = textColor,
                                onClick = { viewModel.setFontSize(settings.fontSizeSp - 1f) }
                            )
                            NovelNestIconButton(
                                icon = Icons.Rounded.Add,
                                contentDescription = "Larger font",
                                tint = textColor,
                                onClick = { viewModel.setFontSize(settings.fontSizeSp + 1f) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Slider(
                        value = settings.fontSizeSp,
                        onValueChange = { viewModel.setFontSize(it) },
                        valueRange = 12f..28f,
                        colors = SliderDefaults.colors(
                            thumbColor = NovelPrimary,
                            activeTrackColor = NovelPrimary,
                            inactiveTrackColor = Color(0x33888888)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.showQuickSettings(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = NovelPrimary, contentColor = NovelOnPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Apply & Resume Reading", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
