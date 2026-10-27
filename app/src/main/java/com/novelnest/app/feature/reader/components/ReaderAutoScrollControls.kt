package com.novelnest.app.feature.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelError
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.data.preferences.AutoScrollSpeed
import com.novelnest.app.data.preferences.ReaderTheme
import com.novelnest.app.feature.reader.ReaderAutoScrollPhase
import com.novelnest.app.feature.reader.ReaderAutoScrollState
import java.util.Locale
import kotlin.math.abs

internal fun speedLabel(speed: Float): String = String.format(Locale.getDefault(), "%.2f×", speed)

internal fun phaseLabel(phase: ReaderAutoScrollPhase): String = when (phase) {
    ReaderAutoScrollPhase.RUNNING -> "Scrolling"
    ReaderAutoScrollPhase.PAUSED -> "Paused"
    ReaderAutoScrollPhase.FINISHED -> "Finished"
    ReaderAutoScrollPhase.STOPPED -> "Ready"
}

/**
 * Redesigned Auto Scroll Entry Card for the Quick Settings Sheet Main Page.
 * Follows NovelNest's obsidian/amber design tokens, providing rich visual status feedback,
 * tactile spring animations, refined typography, and 1-tap Start/Stop/Pause controls right on the card.
 */
@Composable
internal fun ReaderAutoScrollEntry(
    state: ReaderAutoScrollState,
    speed: Float,
    textColor: Color,
    secondaryColor: Color,
    theme: ReaderTheme = ReaderTheme.DARK,
    canStart: Boolean = true,
    onStart: () -> Unit = {},
    onPause: () -> Unit = {},
    onStop: () -> Unit = {},
    onClick: () -> Unit
) {
    val isRunning = state.phase == ReaderAutoScrollPhase.RUNNING
    val isPaused = state.phase == ReaderAutoScrollPhase.PAUSED
    val isActive = isRunning || isPaused

    val activeAccentColor = if (theme.isDark) NovelPrimary else Color(0xFFC78C4B)
    val cardBackground = if (isActive) {
        NovelPrimary.copy(alpha = if (theme.isDark) 0.12f else 0.08f)
    } else {
        secondaryColor.copy(alpha = if (theme.isDark) 0.07f else 0.04f)
    }
    val cardBorder = if (isActive) {
        NovelPrimary.copy(alpha = if (theme.isDark) 0.35f else 0.25f)
    } else {
        secondaryColor.copy(alpha = 0.16f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cardBackground)
            .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Left Area: Icon + Info (clickable to open subpage) ──
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .bounceClick(
                        scaleDown = BounceScale.Small,
                        onClick = onClick
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                // Leading Icon Badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                isRunning -> NovelPrimary
                                isPaused -> NovelPrimary.copy(alpha = 0.20f)
                                else -> secondaryColor.copy(alpha = 0.12f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isRunning -> Icons.Rounded.PlayArrow
                            isPaused -> Icons.Rounded.Pause
                            else -> Icons.Rounded.KeyboardDoubleArrowDown
                        },
                        contentDescription = null,
                        tint = when {
                            isRunning -> NovelOnPrimary
                            isPaused -> activeAccentColor
                            else -> textColor
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Title + Subtitle
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "Auto Scroll",
                            color = textColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (isRunning) {
                            // Pulsing / glowing live dot
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(NovelPrimary)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Dedicated un-cramped speed pill badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    if (isActive) NovelPrimary.copy(alpha = 0.20f)
                                    else secondaryColor.copy(alpha = 0.12f)
                                )
                                .padding(horizontal = 4.5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = speedLabel(speed),
                                color = if (isActive) activeAccentColor else secondaryColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        val subtitle = when {
                            isRunning -> "Scrolling"
                            isPaused -> "Paused"
                            !canStart -> "Needs Continuous"
                            else -> "Hands-free"
                        }
                        Text(
                            text = subtitle,
                            color = if (isActive) activeAccentColor else secondaryColor,
                            fontSize = 10.5.sp,
                            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ── Right Area: Action Buttons (Start / Pause / Resume / Stop) + Chevron ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isActive) {
                    // STOP BUTTON
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NovelError.copy(alpha = 0.12f))
                            .border(1.dp, NovelError.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .bounceClick(
                                scaleDown = BounceScale.Small,
                                onClick = onStop
                            )
                            .padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Stop auto scroll",
                            tint = NovelError,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Stop",
                            color = NovelError,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // PAUSE / RESUME BUTTON
                    if (isRunning) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(NovelPrimary)
                                .bounceClick(
                                    scaleDown = BounceScale.Small,
                                    onClick = onPause
                                )
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Pause,
                                contentDescription = "Pause auto scroll",
                                tint = NovelOnPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Pause",
                                color = NovelOnPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // isPaused -> Resume
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (canStart) NovelPrimary else NovelPrimary.copy(alpha = 0.4f))
                                .bounceClick(
                                    enabled = canStart,
                                    scaleDown = BounceScale.Small,
                                    onClick = onStart
                                )
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Resume auto scroll",
                                tint = NovelOnPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Resume",
                                color = NovelOnPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // STOPPED / FINISHED -> START BUTTON
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (canStart) NovelPrimary else NovelPrimary.copy(alpha = 0.4f))
                            .bounceClick(
                                enabled = canStart,
                                scaleDown = BounceScale.Small,
                                onClick = onStart
                            )
                            .padding(horizontal = 10.dp, vertical = 5.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Start auto scroll",
                            tint = NovelOnPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Start",
                            color = NovelOnPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Subpage navigation chevron
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .bounceClick(
                            scaleDown = BounceScale.Small,
                            onClick = onClick
                        )
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = "Open auto scroll settings",
                        tint = secondaryColor.copy(alpha = 0.45f),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

/**
 * Redesigned Dedicated Auto Scroll Subpage.
 * Modern layout featuring:
 * - Top header with Back navigation and Speed Reset
 * - Live state overview hero card
 * - Speed customization with 1-tap preset chips and precision slider
 * - High-contrast action dock with explicit [NovelOnPrimary] text on active amber gold button
 * - Helpful contextual guidance and continuous mode verification
 */
@Composable
internal fun ReaderAutoScrollSubPage(
    state: ReaderAutoScrollState,
    speed: Float,
    canStart: Boolean,
    continuousMode: Boolean,
    textColor: Color,
    secondaryColor: Color,
    theme: ReaderTheme = ReaderTheme.DARK,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onDismissSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var draftSpeed by remember(speed) { mutableFloatStateOf(AutoScrollSpeed.validated(speed)) }
    fun changeSpeed(value: Float) {
        draftSpeed = AutoScrollSpeed.validated(value)
        onSetSpeed(draftSpeed)
    }

    val isRunning = state.phase == ReaderAutoScrollPhase.RUNNING
    val isPaused = state.phase == ReaderAutoScrollPhase.PAUSED
    val activeAccentColor = if (theme.isDark) NovelPrimary else Color(0xFFC78C4B)
    val cardBackground = secondaryColor.copy(alpha = if (theme.isDark) 0.06f else 0.03f)
    val cardBorder = secondaryColor.copy(alpha = 0.16f)

    Column(modifier = modifier.fillMaxWidth()) {
        // ─── TOP SUBPAGE HEADER BAR ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NovelNestIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back to quick menu",
                onClick = onBack
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto Scroll",
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                val statusText = when (state.phase) {
                    ReaderAutoScrollPhase.RUNNING -> "Actively scrolling · ${speedLabel(draftSpeed)}"
                    ReaderAutoScrollPhase.PAUSED -> "Paused · Tap Resume to continue"
                    ReaderAutoScrollPhase.FINISHED -> "Chapter completed"
                    ReaderAutoScrollPhase.STOPPED -> "Hands-free continuous reading"
                }
                Text(
                    text = statusText,
                    color = if (isRunning) activeAccentColor else secondaryColor,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Reset speed quick action
            NovelNestIconButton(
                icon = Icons.Rounded.RestartAlt,
                contentDescription = "Reset speed to 1x",
                onClick = { changeSpeed(AutoScrollSpeed.DEFAULT) },
                enabled = draftSpeed != AutoScrollSpeed.DEFAULT,
                tint = if (draftSpeed != AutoScrollSpeed.DEFAULT) textColor else secondaryColor.copy(alpha = 0.35f)
            )
        }

        HorizontalDivider(
            color = secondaryColor.copy(alpha = 0.12f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )

        // ─── SCROLLABLE CONTENT BODY ───
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 380.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ─── 1. HERO STATUS CARD ───
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRunning) {
                        NovelPrimary.copy(alpha = if (theme.isDark) 0.10f else 0.06f)
                    } else if (isPaused) {
                        NovelPrimary.copy(alpha = if (theme.isDark) 0.08f else 0.04f)
                    } else {
                        cardBackground
                    }
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (isRunning || isPaused) NovelPrimary.copy(alpha = 0.30f) else cardBorder
                    )
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isRunning -> NovelPrimary
                                    isPaused -> NovelPrimary.copy(alpha = 0.20f)
                                    else -> secondaryColor.copy(alpha = 0.12f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isRunning -> Icons.Rounded.PlayArrow
                                isPaused -> Icons.Rounded.Pause
                                else -> Icons.Rounded.Speed
                            },
                            contentDescription = null,
                            tint = when {
                                isRunning -> NovelOnPrimary
                                isPaused -> activeAccentColor
                                else -> textColor
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (state.phase) {
                                ReaderAutoScrollPhase.RUNNING -> "Currently Scrolling at ${speedLabel(draftSpeed)}"
                                ReaderAutoScrollPhase.PAUSED -> "Auto Scroll Paused"
                                ReaderAutoScrollPhase.FINISHED -> "Chapter End Reached"
                                ReaderAutoScrollPhase.STOPPED -> "Continuous Reading Mode"
                            },
                            color = textColor,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (state.phase) {
                                ReaderAutoScrollPhase.RUNNING -> "Touching the screen pauses scrolling automatically."
                                ReaderAutoScrollPhase.PAUSED -> "Tap Resume below to continue from your current reading position."
                                ReaderAutoScrollPhase.FINISHED -> "Navigate to the next chapter to continue auto scrolling."
                                ReaderAutoScrollPhase.STOPPED -> "Scrolls smoothly at your chosen pace. Stops at chapter end."
                            },
                            color = secondaryColor,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // ─── 2. SPEED CONTROLS CARD ───
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(cardBorder)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header row: Title + Speed Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = activeAccentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "READING SPEED",
                                color = secondaryColor,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                        }

                        // Speed Pill Display
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(NovelPrimary.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${speedLabel(draftSpeed)} (${(draftSpeed * AutoScrollSpeed.BASE_DP_PER_SECOND).toInt()} dp/s)",
                                color = activeAccentColor,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Quick Preset Chips (1-tap selection)
                    val presets = listOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 2.5f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { preset ->
                            val isSelected = abs(draftSpeed - preset) < 0.05f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) NovelPrimary
                                        else secondaryColor.copy(alpha = 0.08f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) NovelPrimary
                                        else secondaryColor.copy(alpha = 0.15f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .bounceClick(
                                        scaleDown = BounceScale.Small,
                                        onClick = { changeSpeed(preset) }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = speedLabel(preset),
                                    color = if (isSelected) NovelOnPrimary else textColor,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Precision Slider with Minus / Plus Stepper Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val canDecrement = draftSpeed > AutoScrollSpeed.MIN
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(secondaryColor.copy(alpha = if (canDecrement) 0.10f else 0.04f))
                                .border(
                                    1.dp,
                                    secondaryColor.copy(alpha = if (canDecrement) 0.18f else 0.08f),
                                    RoundedCornerShape(10.dp)
                                )
                                .bounceClick(
                                    enabled = canDecrement,
                                    scaleDown = BounceScale.Small,
                                    onClick = { changeSpeed(draftSpeed - 0.25f) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "Slower speed",
                                tint = if (canDecrement) textColor else secondaryColor.copy(alpha = 0.35f),
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Slider(
                            value = draftSpeed,
                            onValueChange = { draftSpeed = AutoScrollSpeed.validated(it) },
                            onValueChangeFinished = { onSetSpeed(draftSpeed) },
                            valueRange = AutoScrollSpeed.MIN..AutoScrollSpeed.MAX,
                            steps = 10,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "Auto scroll speed" },
                            colors = SliderDefaults.colors(
                                thumbColor = NovelPrimary,
                                activeTrackColor = NovelPrimary,
                                inactiveTrackColor = secondaryColor.copy(alpha = 0.20f)
                            )
                        )

                        val canIncrement = draftSpeed < AutoScrollSpeed.MAX
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(secondaryColor.copy(alpha = if (canIncrement) 0.10f else 0.04f))
                                .border(
                                    1.dp,
                                    secondaryColor.copy(alpha = if (canIncrement) 0.18f else 0.08f),
                                    RoundedCornerShape(10.dp)
                                )
                                .bounceClick(
                                    enabled = canIncrement,
                                    scaleDown = BounceScale.Small,
                                    onClick = { changeSpeed(draftSpeed + 0.25f) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Faster speed",
                                tint = if (canIncrement) textColor else secondaryColor.copy(alpha = 0.35f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // Informational note
                    Text(
                        text = "1.00× moves 20 dp/s (standard reading pace). Adjust to match your reading rhythm.",
                        color = secondaryColor,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            // ─── 3. ACTION BUTTONS DOCK ───
            val isActionEnabled = isRunning || canStart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary Action Button: Start / Resume / Pause
                // High-contrast text & icon in NovelOnPrimary (Color(0xFF1B1307)) on amber gold
                val primaryActionColor = if (isActionEnabled) NovelOnPrimary else secondaryColor.copy(alpha = 0.45f)
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isActionEnabled) NovelPrimary
                            else secondaryColor.copy(alpha = 0.14f)
                        )
                        .bounceClick(
                            enabled = isActionEnabled,
                            scaleDown = BounceScale.Small,
                            onClick = {
                                if (isRunning) {
                                    onPause()
                                } else {
                                    onSetSpeed(draftSpeed)
                                    onStart()
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = primaryActionColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRunning) "Pause" else if (state.phase == ReaderAutoScrollPhase.PAUSED) "Resume" else "Start",
                            color = primaryActionColor,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Secondary Action Button: Stop
                val canStop = state.phase != ReaderAutoScrollPhase.STOPPED
                val stopColor = if (canStop) textColor else secondaryColor.copy(alpha = 0.35f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(secondaryColor.copy(alpha = if (canStop) 0.08f else 0.04f))
                        .border(
                            width = 1.dp,
                            color = secondaryColor.copy(alpha = if (canStop) 0.22f else 0.10f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .bounceClick(
                            enabled = canStop,
                            scaleDown = BounceScale.Small,
                            onClick = onStop
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = null,
                            tint = stopColor,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Stop",
                            color = stopColor,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ─── 4. NOTICES & PREREQUISITES ───
            if (!continuousMode || !canStart) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!continuousMode) NovelError.copy(alpha = 0.08f) else secondaryColor.copy(alpha = 0.06f)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (!continuousMode) NovelError.copy(alpha = 0.25f) else secondaryColor.copy(alpha = 0.16f)
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = if (!continuousMode) NovelError else secondaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (!continuousMode) {
                                "Auto Scroll is designed for Continuous Scroll mode. Switch to Continuous mode in Reading Settings to use auto scroll."
                            } else {
                                "Waiting for chapter text to load before starting auto scroll."
                            },
                            color = if (!continuousMode) textColor else secondaryColor,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
