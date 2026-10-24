package com.novelnest.app.core.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.core.ui.animation.rememberAreAnimationsDisabled
import com.novelnest.app.data.narration.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * State-of-the-Art Narration Mini Player designed to professional UI/UX standards.
 * Features an animated live audio visualizer, luxury glassmorphic card container,
 * smooth tactile transport controls, speed selector, and a single-highlight scrubbable progress bar.
 */
@Composable
fun NarrationMiniPlayer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by NarrationStatus.state.collectAsStateWithLifecycle()
    val speed by NarrationSpeed.values.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf("") }
    var showSpeeds by remember { mutableStateOf(false) }
    var savingSpeed by remember { mutableStateOf(false) }

    fun command(action: String) {
        try {
            error = ""
            NarrationService.command(context, action)
        } catch (_: RuntimeException) {
            error = "Could not control narration. Please try again."
        }
    }

    Surface(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = NovelSurface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (state.playing) NovelPrimary.copy(alpha = 0.38f) else NovelSurfaceBorder.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Main Top Row: Badge + Title/Subtitle + Speed + Transport Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Animated Equalizer / Icon Badge
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(NovelPrimary.copy(alpha = 0.14f))
                        .border(1.dp, NovelPrimary.copy(alpha = 0.28f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        state.loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = NovelPrimary,
                                strokeWidth = 2.dp
                            )
                        }
                        state.playing -> {
                            AnimatedEqualizer(isPlaying = true)
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Rounded.Headphones,
                                contentDescription = null,
                                tint = NovelPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Center: Chapter Title and Live Passage Status
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                ) {
                    Text(
                        text = state.chapterTitle.ifBlank { "Cloud Narration" },
                        color = NovelTextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Live Pulse Dot
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        state.loading -> NovelPrimary
                                        state.playing -> Color(0xFF4CAF50)
                                        else -> NovelTextSecondary.copy(alpha = 0.45f)
                                    }
                                )
                        )

                        Spacer(modifier = Modifier.width(5.dp))

                        Text(
                            text = when {
                                state.phase == NarrationPhase.FAILED -> "Narration failed · Tap Play to retry"
                                state.phase == NarrationPhase.COMPLETED -> "Chapter narration complete"
                                state.loading -> state.phase.caption
                                state.total > 0 -> "${if (state.playing) "Playing" else "Paused"} · Passage ${state.passage}/${state.total}"
                                else -> "Narration paused"
                            },
                            color = if (state.playing) NovelPrimary else NovelTextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (state.playing) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Right: Controls Row (Speed pill + SkipPrev + Play/Pause circular FAB + SkipNext + Close)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Playback Speed Pill Button
                    Box {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = NovelPrimary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, NovelPrimary.copy(alpha = 0.25f)),
                            modifier = Modifier.bounceClick(
                                enabled = speed != null && !savingSpeed,
                                scaleDown = BounceScale.Small,
                                onClick = { showSpeeds = true }
                            )
                        ) {
                            Text(
                                text = NarrationSpeed.label(speed ?: 1f),
                                color = NovelPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSpeeds,
                            onDismissRequest = { showSpeeds = false },
                            modifier = Modifier.background(NovelSurface)
                        ) {
                            NarrationSpeed.choices.forEach { choice ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = NarrationSpeed.label(choice) + if (choice == speed) " ✓" else "",
                                            color = if (choice == speed) NovelPrimary else NovelTextPrimary,
                                            fontWeight = if (choice == speed) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        showSpeeds = false
                                        savingSpeed = true
                                        scope.launch {
                                            try {
                                                NarrationSpeed.set(context.applicationContext, choice)
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (_: Exception) {
                                                error = "Could not save playback speed."
                                            } finally {
                                                savingSpeed = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Skip Previous Passage
                    IconButton(
                        enabled = !state.loading && state.passage > 1,
                        onClick = { command(NarrationService.PREVIOUS) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous audio passage",
                            tint = if (!state.loading && state.passage > 1) NovelTextPrimary else NovelTextSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Central Play / Pause Action FAB
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NovelPrimary)
                            .bounceClick(
                                scaleDown = BounceScale.Medium,
                                onClick = {
                                    command(if (state.playing || state.loading) NarrationService.PAUSE else NarrationService.PLAY)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = NovelOnPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.playing) "Pause narration" else "Play narration",
                                tint = NovelOnPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Skip Next Passage
                    IconButton(
                        enabled = !state.loading && state.passage < state.total,
                        onClick = { command(NarrationService.NEXT) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next audio passage",
                            tint = if (!state.loading && state.passage < state.total) NovelTextPrimary else NovelTextSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Stop and Close Button
                    IconButton(
                        onClick = { command(NarrationService.STOP) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Stop narration and close player",
                            tint = NovelTextSecondary.copy(alpha = 0.65f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Bottom Progress Track & Passage Timeline
            Spacer(modifier = Modifier.height(7.dp))
            NarrationScrubbableProgressBar(
                onSeek = { position, mediaId ->
                    try {
                        NarrationService.seek(context, position, mediaId)
                    } catch (_: RuntimeException) {
                        error = "Could not seek this passage."
                    }
                }
            )

            // Optional Status or Error Banner
            val message = error.ifBlank { state.message }
            if (message.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (error.isNotBlank() || state.phase == NarrationPhase.FAILED) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (error.isNotBlank() || state.phase == NarrationPhase.FAILED) MaterialTheme.colorScheme.error else NovelTextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Animated 3-bar Audio Visualizer for live speech streaming.
 * Features asynchronous phase-shifted smooth bar bouncing.
 */
@Composable
private fun AnimatedEqualizer(
    isPlaying: Boolean,
    color: Color = NovelPrimary,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "EqualizerAnimation")

    val height1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EqualizerBar1"
    )
    val height2 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EqualizerBar2"
    )
    val height3 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EqualizerBar3"
    )

    Row(
        modifier = modifier.size(width = 17.dp, height = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val h1 = if (isPlaying) height1 else 0.30f
        val h2 = if (isPlaying) height2 else 0.50f
        val h3 = if (isPlaying) height3 else 0.25f

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(h1)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(h2)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(h3)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
    }
}

/**
 * Single-highlight scrubbable seekbar with zero ghost dots.
 * Supports smooth live timeline animation and tap/drag seeking.
 */
@Composable
private fun NarrationScrubbableProgressBar(
    modifier: Modifier = Modifier,
    onSeek: (Long, String) -> Unit
) {
    val timeline by NarrationStatus.timeline.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val reducedMotion = rememberAreAnimationsDisabled()
    val progress = remember { Animatable(0f) }
    var draggingFraction by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(timeline, owner, reducedMotion) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val position = (timeline.positionMs + if (timeline.playing)
                ((SystemClock.elapsedRealtime() - timeline.updatedAtMs).coerceAtLeast(0L) * timeline.speed).toLong() else 0L)
                .coerceIn(0L, timeline.durationMs.coerceAtLeast(0L))
            val fraction = if (timeline.durationMs > 0) position.toFloat() / timeline.durationMs else 0f
            progress.snapTo(fraction.coerceIn(0f, 1f))
            if (timeline.playing && timeline.speed > 0f && !reducedMotion && timeline.durationMs > position) {
                val remaining = ((timeline.durationMs - position) / timeline.speed).toLong().coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
                progress.animateTo(1f, tween(remaining, easing = LinearEasing))
            }
        }
    }

    // Only the draw phase observes every animation frame. Labels invalidate at
    // their displayed-second boundary, rather than formatting strings at 60/120Hz.
    val elapsedSeconds by remember(timeline.durationMs) {
        derivedStateOf {
            (((draggingFraction ?: progress.value).coerceIn(0f, 1f) * timeline.durationMs).toLong() / 1000L)
        }
    }
    val elapsedLabel = remember(elapsedSeconds) { formatDuration(elapsedSeconds * 1000L) }
    val durationLabel = remember(timeline.durationMs) { formatDuration(timeline.durationMs) }
    val fillBrush = remember { Brush.horizontalGradient(listOf(NovelPrimary, NovelPrimary.copy(alpha = 0.85f))) }
    val canSeek = timeline.canSeek && timeline.durationMs > 0L

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .pointerInput(canSeek, timeline.durationMs, timeline.mediaId) {
                    if (!canSeek) return@pointerInput
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeek((fraction * timeline.durationMs).toLong(), timeline.mediaId)
                    }
                }
                .pointerInput(canSeek, timeline.durationMs, timeline.mediaId) {
                    if (!canSeek) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            draggingFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            draggingFraction?.let { onSeek((it * timeline.durationMs).toLong(), timeline.mediaId) }
                            draggingFraction = null
                        },
                        onDragCancel = {
                            draggingFraction = null
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            draggingFraction = fraction
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val fraction = (draggingFraction ?: progress.value).coerceIn(0f, 1f)
                val height = 3.dp.toPx()
                val top = (size.height - height) / 2f
                val radius = CornerRadius(1.5.dp.toPx())
                drawRoundRect(NovelSurfaceBorder.copy(alpha = 0.5f), Offset(0f, top), Size(size.width, height), radius)
                if (fraction > 0.002f) {
                    drawRoundRect(fillBrush, Offset(0f, top), Size(size.width * fraction, height), radius)
                }
                if (draggingFraction != null && fraction in 0.01f..0.99f) {
                    val thumbRadius = 4.dp.toPx()
                    drawCircle(NovelPrimary, thumbRadius,
                        Offset((size.width * fraction - thumbRadius).coerceAtLeast(thumbRadius), size.height / 2f))
                }
            }
        }

        // Time indicator row if duration is available
        if (timeline.durationMs > 0L) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = elapsedLabel,
                    fontSize = 9.5.sp,
                    color = NovelTextSecondary.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = durationLabel,
                    fontSize = 9.5.sp,
                    color = NovelTextSecondary.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
