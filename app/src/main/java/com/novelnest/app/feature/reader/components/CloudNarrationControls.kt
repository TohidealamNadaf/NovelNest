package com.novelnest.app.feature.reader.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.data.narration.*
import com.novelnest.app.feature.reader.ReaderUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Premium embedded media player card for Cloud Narration in the Reader Quick Menu.
 * Composed in the reader quick settings bottom sheet with full media controls.
 */
@Composable
fun CloudNarrationControls(
    reader: ReaderUiState,
    textColor: Color = NovelTextPrimary,
    secondaryColor: Color = NovelTextSecondary,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember { NarrationStore(context.applicationContext) }
    val state by NarrationStatus.state.collectAsStateWithLifecycle()
    val playbackSpeed by NarrationSpeed.values.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var configure by remember { mutableStateOf(false) }
    var configured by remember { mutableStateOf(store.endpoint.isNotBlank()) }
    val chapter = reader.chapters.getOrNull(reader.currentChapterIndex)
    val novel = reader.novel
    val currentSession = state.novelId == novel?.id && state.chapterId == chapter?.id
    var error by remember { mutableStateOf("") }
    var showSpeeds by remember { mutableStateOf(false) }
    var savingSpeed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { NarrationSpeed.load(context.applicationContext) }

    val isPlaying = currentSession && state.playing
    val isLoading = currentSession && state.loading

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(secondaryColor.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = if (isPlaying) NovelPrimary.copy(alpha = 0.45f) else secondaryColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Main Compact Player Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Icon + Chapter Title & Passage Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(NovelPrimary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = NovelPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(9.dp))

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (currentSession && state.chapterTitle.isNotBlank()) state.chapterTitle else chapter?.title ?: "Cloud Narration",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // Dynamic Live Status Pill
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    isLoading -> NovelPrimary.copy(alpha = 0.2f)
                                    isPlaying -> Color(0x334CAF50)
                                    currentSession && state.passage > 0 -> secondaryColor.copy(alpha = 0.15f)
                                    !configured -> NovelPrimary.copy(alpha = 0.18f)
                                    else -> secondaryColor.copy(alpha = 0.12f)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isPlaying) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF4CAF50))
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = when {
                                            isLoading -> "Preparing…"
                                            isPlaying -> "Playing"
                                            currentSession && state.passage > 0 -> "Paused"
                                            !configured -> "Connect"
                                            else -> "MeloTTS"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when {
                                            isLoading -> NovelPrimary
                                            isPlaying -> Color(0xFF81C784)
                                            !configured -> NovelPrimary
                                            else -> secondaryColor
                                        }
                                    )
                                }
                            }
                        }

                        // Subtitle: Passage count or quick status
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentSession && state.total > 0) {
                                Text(
                                    text = "Passage ${state.passage}/${state.total}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NovelPrimary
                                )
                                Text(
                                    text = " · ",
                                    fontSize = 11.sp,
                                    color = secondaryColor
                                )
                            }
                            Text(
                                text = when {
                                    isPlaying -> "Speech streaming"
                                    isLoading -> "Fetching audio…"
                                    currentSession && state.passage > 0 -> "Narration paused"
                                    configured -> "Tap Play to listen"
                                    else -> "Setup credentials"
                                },
                                fontSize = 11.sp,
                                color = secondaryColor.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right Actions: Stop + Play/Pause Button + Quick Setup
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (state.novelId.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(secondaryColor.copy(alpha = 0.12f))
                                .bounceClick(
                                    scaleDown = BounceScale.Small,
                                    onClick = {
                                        runCatching {
                                            NarrationService.command(context, NarrationService.STOP)
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = "Stop narration",
                                tint = secondaryColor,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // Play / Pause Circular Button
                    val hasPlayableContent = novel != null && chapter?.paragraphs?.any { it.isNotBlank() } == true
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (hasPlayableContent) NovelPrimary else secondaryColor.copy(alpha = 0.3f)
                            )
                            .bounceClick(
                                enabled = hasPlayableContent,
                                scaleDown = BounceScale.Medium,
                                onClick = {
                                    if (!configured) {
                                        configure = true
                                    } else {
                                        try {
                                            error = ""
                                            if (currentSession) {
                                                NarrationService.command(
                                                    context,
                                                    if (state.playing || state.loading) NarrationService.PAUSE else NarrationService.PLAY
                                                )
                                            } else {
                                                NarrationService.start(
                                                    context,
                                                    novel!!.id,
                                                    chapter!!.id,
                                                    chapter.url.ifBlank { chapter.id },
                                                    chapter.title
                                                )
                                            }
                                        } catch (_: RuntimeException) {
                                            error = "Could not start audio. Keep the app open and try again."
                                        }
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = NovelOnPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = NovelOnPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    NovelNestIconButton(
                        icon = Icons.Rounded.Tune,
                        contentDescription = "Configure cloud narration",
                        tint = if (!configured) NovelPrimary else secondaryColor,
                        iconSize = 16.dp,
                        onClick = { configure = true }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Voice: MeloTTS default · English", color = secondaryColor, fontSize = 11.sp,
                    modifier = Modifier.weight(1f))
                Box {
                    TextButton(enabled = playbackSpeed != null && !savingSpeed,
                        onClick = { showSpeeds = true }) {
                        Text("Speed ${NarrationSpeed.label(playbackSpeed ?: 1f)}", color = NovelPrimary)
                    }
                    DropdownMenu(expanded = showSpeeds, onDismissRequest = { showSpeeds = false }) {
                        NarrationSpeed.choices.forEach { speed ->
                            DropdownMenuItem(
                                text = { Text(NarrationSpeed.label(speed) + if (speed == playbackSpeed) " ✓" else "") },
                                onClick = {
                                    showSpeeds = false
                                    savingSpeed = true
                                    error = ""
                                    scope.launch {
                                        try { NarrationSpeed.set(context.applicationContext, speed) }
                                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                        catch (_: Exception) { error = "Could not save playback speed. Please try again." }
                                        finally { savingSpeed = false }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Single Highlight Progress Bar (Fixes double highlight bug!)
            if (currentSession && state.total > 0) {
                Spacer(modifier = Modifier.height(7.dp))
                val progress = (state.passage.toFloat() / state.total.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(secondaryColor.copy(alpha = 0.2f))
                ) {
                    if (progress > 0.002f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = progress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(NovelPrimary)
                        )
                    }
                }
            }

            // Informational message or status from NarrationService
            if (state.message.isNotBlank()) {
                Text(
                    text = state.message,
                    fontSize = 10.5.sp,
                    color = secondaryColor,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

            // Error display
            if (error.isNotBlank()) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }

    // Connect / Setup Dialog
    if (configure) {
        var url by remember { mutableStateOf(store.endpoint) }
        var token by remember { mutableStateOf("") }
        var setupError by remember { mutableStateOf("") }
        var saving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!saving) configure = false },
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.CloudSync,
                        contentDescription = null,
                        tint = NovelPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Connect Cloud Narration", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter your personal Cloudflare Worker endpoint URL and private access token.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Worker URL") },
                        placeholder = { Text("https://...workers.dev/v1/narration") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Access Token") },
                        placeholder = { Text("Private worker secret") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (setupError.isNotBlank()) {
                        Text(
                            text = setupError,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !saving && url.isNotBlank() && token.isNotBlank(),
                    onClick = {
                        saving = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { store.configure(url.trim(), token.trim()) }
                            }
                            saving = false
                            result.onSuccess {
                                configured = true
                                configure = false
                                Toast.makeText(context, "Cloud narration configured!", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                setupError = it.message ?: "Could not save configuration."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NovelPrimary,
                        contentColor = NovelOnPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (saving) "Saving…" else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { configure = false },
                    enabled = !saving
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
