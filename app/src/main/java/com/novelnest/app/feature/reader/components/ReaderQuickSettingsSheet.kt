package com.novelnest.app.feature.reader.components

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.core.ui.animation.bounceSelectable
import com.novelnest.app.data.narration.NarrationService
import com.novelnest.app.data.narration.NarrationStatus
import com.novelnest.app.data.narration.NarrationStore
import com.novelnest.app.data.preferences.ReaderFont
import com.novelnest.app.data.preferences.ReaderSettings
import com.novelnest.app.data.preferences.ReaderTheme
import com.novelnest.app.feature.reader.ReaderUiState
import kotlin.math.roundToInt

/**
 * Compact Half-Page Quick Menu bottom sheet for the Novel Reader screen.
 * Firmly anchored to the bottom of the screen with a streamlined action bar:
 * [ ❐ Copy ] [ ≪ Prev ] ( ▶ Play ) [ Next ≫ ]
 * followed by a segmented 2-tab switcher for Theme/Typography and Sizing/Spacing.
 */
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
    reader: ReaderUiState? = null,
    hasPrevChapter: Boolean = false,
    hasNextChapter: Boolean = false,
    onPrevChapter: () -> Unit = {},
    onNextChapter: () -> Unit = {},
    onCopyChapter: () -> Unit = {},
    canCopyChapter: Boolean = false,
    narrationContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Theme & Font, 1: Sizing & Spacing

    // Narration state handling
    val store = remember { NarrationStore(context.applicationContext) }
    val narrationState by NarrationStatus.state.collectAsStateWithLifecycle()
    val configuredNarration = store.endpoint.isNotBlank()

    val currentChapter = reader?.chapters?.getOrNull(reader.currentChapterIndex)
    val currentNovel = reader?.novel
    val isCurrentAudioSession = narrationState.novelId == currentNovel?.id && narrationState.chapterId == currentChapter?.id
    val isAudioPlaying = isCurrentAudioSession && narrationState.playing
    val isAudioLoading = isCurrentAudioSession && narrationState.loading
    val hasPlayableAudio = currentNovel != null && currentChapter?.paragraphs?.any { it.isNotBlank() } == true

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        containerColor = settings.theme.surfaceColor,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // Modern Subtle Drag Handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(secondaryColor.copy(alpha = 0.35f))
                )
            }

            // ─── STREAMLINED QUICK ACTION BAR ───
            // [ ❐ Copy ]  [ << Prev ]  ( ▶ Play )  [ Next >> ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Copy Chapter Button
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(secondaryColor.copy(alpha = if (canCopyChapter) 0.12f else 0.05f))
                        .border(
                            1.dp,
                            secondaryColor.copy(alpha = if (canCopyChapter) 0.18f else 0.08f),
                            RoundedCornerShape(14.dp)
                        )
                        .bounceClick(
                            enabled = canCopyChapter,
                            scaleDown = BounceScale.Small,
                            onClick = onCopyChapter
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy chapter",
                        tint = if (canCopyChapter) textColor else secondaryColor.copy(alpha = 0.35f),
                        modifier = Modifier.size(19.dp)
                    )
                }

                // 2. Previous Chapter Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(secondaryColor.copy(alpha = if (hasPrevChapter) 0.12f else 0.05f))
                        .border(
                            1.dp,
                            secondaryColor.copy(alpha = if (hasPrevChapter) 0.18f else 0.08f),
                            RoundedCornerShape(14.dp)
                        )
                        .bounceClick(
                            enabled = hasPrevChapter,
                            scaleDown = BounceScale.Small,
                            onClick = onPrevChapter
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FastRewind,
                            contentDescription = "Previous chapter",
                            tint = if (hasPrevChapter) textColor else secondaryColor.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Prev",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasPrevChapter) textColor else secondaryColor.copy(alpha = 0.35f)
                        )
                    }
                }

                // 3. Play / Pause Narration Circular Button (Center)
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasPlayableAudio) NovelPrimary else secondaryColor.copy(alpha = 0.25f)
                        )
                        .bounceClick(
                            enabled = hasPlayableAudio,
                            scaleDown = BounceScale.Medium,
                            onClick = {
                                if (!configuredNarration) {
                                    Toast.makeText(context, "Configure Cloud Narration in Settings > Audio Narration", Toast.LENGTH_LONG).show()
                                } else {
                                    try {
                                        if (isCurrentAudioSession) {
                                            NarrationService.command(
                                                context,
                                                if (narrationState.playing || narrationState.loading) NarrationService.PAUSE else NarrationService.PLAY
                                            )
                                        } else {
                                            if (currentNovel != null && currentChapter != null) {
                                                NarrationService.start(
                                                    context,
                                                    currentNovel.id,
                                                    currentChapter.id,
                                                    currentChapter.url.ifBlank { currentChapter.id },
                                                    currentChapter.title
                                                )
                                            }
                                        }
                                        onDismiss()
                                    } catch (_: RuntimeException) {
                                        Toast.makeText(context, "Could not start audio playback", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isAudioLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = NovelOnPrimary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isAudioPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isAudioPlaying) "Pause narration" else "Play narration",
                            tint = NovelOnPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // 4. Next Chapter Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(secondaryColor.copy(alpha = if (hasNextChapter) 0.12f else 0.05f))
                        .border(
                            1.dp,
                            secondaryColor.copy(alpha = if (hasNextChapter) 0.18f else 0.08f),
                            RoundedCornerShape(14.dp)
                        )
                        .bounceClick(
                            enabled = hasNextChapter,
                            scaleDown = BounceScale.Small,
                            onClick = onNextChapter
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Next",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasNextChapter) textColor else secondaryColor.copy(alpha = 0.35f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.FastForward,
                            contentDescription = "Next chapter",
                            tint = if (hasNextChapter) textColor else secondaryColor.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }


            Spacer(modifier = Modifier.height(6.dp))

            // Segmented Tab Selector with Sliding Pill Indicator
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(secondaryColor.copy(alpha = 0.08f))
                    .border(1.dp, secondaryColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .padding(3.dp)
            ) {
                val tabWidth = maxWidth / 2
                val indicatorOffset by animateDpAsState(
                    targetValue = if (selectedTab == 0) 0.dp else tabWidth,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "tab_pill_offset"
                )

                // Sliding Active Pill Background
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .height(42.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(NovelPrimary)
                )

                // Tab Items Row
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val tab0Color by animateColorAsState(
                        targetValue = if (selectedTab == 0) NovelOnPrimary else secondaryColor,
                        animationSpec = tween(200),
                        label = "tab0_color"
                    )
                    val tab1Color by animateColorAsState(
                        targetValue = if (selectedTab == 1) NovelOnPrimary else secondaryColor,
                        animationSpec = tween(200),
                        label = "tab1_color"
                    )

                    // Tab 0: Theme & Font
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .bounceClick(
                                scaleDown = BounceScale.Small,
                                onClick = { selectedTab = 0 }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = tab0Color,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "Theme & Font",
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = tab0Color
                            )
                        }
                    }

                    // Tab 1: Size & Spacing
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .bounceClick(
                                scaleDown = BounceScale.Small,
                                onClick = { selectedTab = 1 }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FormatSize,
                                contentDescription = null,
                                tint = tab1Color,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "Size & Spacing",
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = tab1Color
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Animated Tab Content with smooth sliding transitions
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(
                            animationSpec = tween(280, easing = FastOutSlowInEasing),
                            initialOffsetX = { fullWidth -> (fullWidth * 0.35f).toInt() }
                        ) + fadeIn(animationSpec = tween(220))).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(240, easing = FastOutSlowInEasing),
                                targetOffsetX = { fullWidth -> -(fullWidth * 0.35f).toInt() }
                            ) + fadeOut(animationSpec = tween(180))
                        )
                    } else {
                        (slideInHorizontally(
                            animationSpec = tween(280, easing = FastOutSlowInEasing),
                            initialOffsetX = { fullWidth -> -(fullWidth * 0.35f).toInt() }
                        ) + fadeIn(animationSpec = tween(220))).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(240, easing = FastOutSlowInEasing),
                                targetOffsetX = { fullWidth -> (fullWidth * 0.35f).toInt() }
                            ) + fadeOut(animationSpec = tween(180))
                        )
                    }.using(SizeTransform(clip = false))
                },
                modifier = Modifier.fillMaxWidth(),
                label = "QuickMenuTabTransition"
            ) { tab ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    if (tab == 0) {
                        // ─── TAB 0: THEME & FONT ───
                        Text(
                            text = "THEME PALETTE",
                            color = secondaryColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReaderTheme.entries.forEach { theme ->
                                val isSelected = theme == settings.theme
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(68.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(theme.backgroundColor)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) NovelPrimary else if (theme.isDark) secondaryColor.copy(alpha = 0.22f) else Color(0xFFD6C8B4),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .bounceSelectable(
                                            selected = isSelected,
                                            scaleDown = BounceScale.Small,
                                            onClick = { onSetTheme(theme) }
                                        )
                                        .padding(horizontal = 6.dp, vertical = 7.dp)
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(NovelPrimary)
                                                .align(Alignment.TopEnd),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = "Selected",
                                                tint = NovelOnPrimary,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.align(Alignment.BottomStart)
                                    ) {
                                        Text(
                                            text = "Aa",
                                            color = theme.textColor,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = theme.displayName,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) theme.textColor else theme.secondaryTextColor,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "TYPOGRAPHY",
                            color = secondaryColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.selectableGroup()
                        ) {
                            items(ReaderFont.entries) { font ->
                                val isSelected = font == settings.fontFamily
                                Box(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) NovelPrimary else secondaryColor.copy(alpha = 0.09f))
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) NovelPrimary else secondaryColor.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .bounceSelectable(
                                            selected = isSelected,
                                            scaleDown = BounceScale.Small,
                                            onClick = { onSetFontFamily(font) }
                                        )
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = font.displayName,
                                        fontFamily = font.fontFamily,
                                        color = if (isSelected) NovelOnPrimary else textColor,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        // ─── TAB 1: SIZING & SPACING ───
                        // 1. Font Size
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Font Size",
                                color = textColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = NovelPrimary.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "${settings.fontSizeSp.toInt()} sp",
                                    color = NovelPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NovelNestIconButton(
                                icon = Icons.Rounded.Remove,
                                contentDescription = "Decrease font",
                                tint = secondaryColor,
                                iconSize = 16.dp,
                                onClick = { onSetFontSize((settings.fontSizeSp - 1f).coerceAtLeast(12f)) }
                            )
                            Slider(
                                value = settings.fontSizeSp,
                                onValueChange = { onSetFontSize(it.roundToInt().toFloat()) },
                                valueRange = 12f..28f,
                                colors = SliderDefaults.colors(
                                    thumbColor = NovelPrimary,
                                    activeTrackColor = NovelPrimary,
                                    inactiveTrackColor = secondaryColor.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            )
                            NovelNestIconButton(
                                icon = Icons.Rounded.Add,
                                contentDescription = "Increase font",
                                tint = secondaryColor,
                                iconSize = 16.dp,
                                onClick = { onSetFontSize((settings.fontSizeSp + 1f).coerceAtMost(28f)) }
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 2. Line Spacing
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Line Spacing",
                                color = textColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = NovelPrimary.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = String.format("%.1fx", settings.lineHeightMultiplier),
                                    color = NovelPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Slider(
                            value = settings.lineHeightMultiplier,
                            onValueChange = onSetLineHeight,
                            valueRange = 1.2f..2.2f,
                            colors = SliderDefaults.colors(
                                thumbColor = NovelPrimary,
                                activeTrackColor = NovelPrimary,
                                inactiveTrackColor = secondaryColor.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // 3. Paragraph Padding
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Paragraph Padding",
                                color = textColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = NovelPrimary.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "${settings.paragraphSpacingDp.toInt()} dp",
                                    color = NovelPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(14f to "Compact", 18f to "Normal", 22f to "Relaxed", 28f to "Spacious").forEach { (spacing, label) ->
                                val isSelected = kotlin.math.abs(settings.paragraphSpacingDp - spacing) < 1f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) NovelPrimary else secondaryColor.copy(alpha = 0.08f))
                                        .border(
                                            1.dp,
                                            if (isSelected) NovelPrimary else secondaryColor.copy(alpha = 0.16f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .bounceClick(
                                            scaleDown = BounceScale.Small,
                                            onClick = { onSetParagraphSpacing(spacing) }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) NovelOnPrimary else textColor,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Slider(
                            value = settings.paragraphSpacingDp.coerceIn(14f, 28f),
                            onValueChange = onSetParagraphSpacing,
                            valueRange = 14f..28f,
                            colors = SliderDefaults.colors(
                                thumbColor = NovelPrimary,
                                activeTrackColor = NovelPrimary,
                                inactiveTrackColor = secondaryColor.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
