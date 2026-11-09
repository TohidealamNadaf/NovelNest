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
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.novelnest.app.feature.reader.ReaderStudyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.core.ui.animation.bounceSelectable
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic
import com.novelnest.app.data.ai.AiAction
import com.novelnest.app.data.ai.AiFailure
import com.novelnest.app.data.ai.AiPhase
import com.novelnest.app.data.ai.AiProgress
import com.novelnest.app.data.narration.NarrationService
import com.novelnest.app.data.narration.NarrationStatus
import com.novelnest.app.data.narration.NarrationStore
import com.novelnest.app.data.preferences.ReaderFont
import com.novelnest.app.data.preferences.ReaderSettings
import com.novelnest.app.data.preferences.ReaderTheme
import com.novelnest.app.data.model.Chapter
import com.novelnest.app.feature.reader.ReaderAiState
import com.novelnest.app.feature.reader.aiErrorHelpText
import com.novelnest.app.feature.reader.retryAiFailure
import com.novelnest.app.feature.reader.ReaderContentVersion
import com.novelnest.app.feature.reader.ReaderUiState
import com.novelnest.app.feature.reader.ReaderAutoScrollState
import com.novelnest.app.data.preferences.ReaderScrollMode
import kotlin.math.roundToInt

/**
 * Dedicated Subpage within Quick Settings to view or trigger AI Chapter Summary.
 * Features a top navigation bar with [Back] to quick menu, [Re-summarize] action icon,
 * [Copy] action icon, live generation progress with cancel, and rich formatted summary display.
 */
@Composable
internal fun ReaderSummarySubPage(
    currentChapter: Chapter?,
    aiState: ReaderAiState?,
    theme: ReaderTheme,
    canGenerate: Boolean,
    textColor: Color,
    secondaryColor: Color,
    onBack: () -> Unit,
    onRequestSummary: () -> Unit,
    onCancelAi: () -> Unit,
    onDismissSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isMatchingChapter = currentChapter != null && aiState?.matches(currentChapter) == true
    val currentSummary = if (isMatchingChapter) aiState?.summary else null
    val isBusy = aiState?.busy == true
    val isSummarizing = isBusy && aiState?.action == AiAction.SUMMARY
    val failure = if (isMatchingChapter && aiState?.action == AiAction.SUMMARY) aiState?.failure else null
    val accentColor = if (theme.isDark) NovelPrimary else textColor
    val failureColor = if (theme.isDark) NovelError else textColor
    val copySuccessColor = if (theme.isDark) NovelSecondary else Color(0xFF2E6B4F)
    var summaryCopyTrigger by remember(currentChapter?.id) { mutableLongStateOf(0L) }
    val isSummaryCopied = summaryCopyTrigger > 0L
    LaunchedEffect(summaryCopyTrigger) {
        if (summaryCopyTrigger > 0L) {
            delay(2000L)
            summaryCopyTrigger = 0L
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // ─── SUBPAGE HEADER BAR ───
        // [ < Back ]  Chapter Summary / Title  [ ↻ Re-summarize ]  [ ❐ Copy ]
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
                    text = "Chapter Summary",
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                val chapterTitle = currentChapter?.title ?: "Chapter Overview"
                Text(
                    text = chapterTitle,
                    color = secondaryColor,
                    fontSize = 11.5.sp,
                    maxLines = 1
                )
            }

            // Re-summarize icon button
            NovelNestIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Re-summarize chapter",
                onClick = {
                    if (canGenerate && !isBusy) {
                        onRequestSummary()
                    }
                },
                enabled = canGenerate && !isBusy,
                tint = if (canGenerate && !isBusy) accentColor else secondaryColor.copy(alpha = 0.35f)
            )

            // Copy summary text button
            NovelNestIconButton(
                icon = if (isSummaryCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                contentDescription = if (isSummaryCopied) "Summary copied" else "Copy summary text",
                onClick = {
                    val text = currentSummary?.summary?.text
                    if (!text.isNullOrBlank()) {
                        try {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(
                                android.content.ClipData.newPlainText("Chapter Summary", text)
                            )
                            summaryCopyTrigger = System.currentTimeMillis()
                            Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {}
                    }
                },
                enabled = currentSummary?.summary?.text?.isNotBlank() == true,
                tint = if (isSummaryCopied) copySuccessColor else if (currentSummary?.summary?.text?.isNotBlank() == true) textColor else secondaryColor.copy(alpha = 0.35f)
            )
        }

        HorizontalDivider(
            color = secondaryColor.copy(alpha = 0.12f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )

        // ─── SUBPAGE SCROLLABLE CONTENT BODY ───
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Generation In-Progress Live Card
            if (isSummarizing) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surfaceColor),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(secondaryColor.copy(alpha = 0.2f))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = accentColor,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = aiPhaseTitle(aiState.progress.phase, AiAction.SUMMARY),
                                    color = textColor,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val detail = aiState.progress.message.ifBlank {
                                    if (aiState.progress.totalChunks > 1) {
                                        "Analyzing chapter (Chunk ${aiState.progress.chunk}/${aiState.progress.totalChunks})..."
                                    } else "Analyzing chapter narrative..."
                                }
                                Text(
                                    text = detail,
                                    color = secondaryColor,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onCancelAi,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = failureColor),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Cancel", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = failureColor)
                        }
                    }
                }
            }

            // 2. Actionable Failure State Card
            if (failure != null && !isBusy) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = failureColor.copy(alpha = 0.12f)),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(failureColor.copy(alpha = 0.35f))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = failureColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AI Summary Failed",
                                    color = failureColor,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = aiErrorHelpText(failure, AiAction.SUMMARY),
                                    color = textColor,
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(NovelPrimary)
                                .clickable { onRequestSummary() }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = NovelOnPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Retry",
                                color = NovelOnPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 3. Full Rich Summary Content
            if (currentSummary?.summary != null) {
                val summaryData = currentSummary.summary
                Text(
                    text = summaryData.text,
                    color = textColor,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp
                )

                if (summaryData.events.isNotEmpty()) {
                    HorizontalDivider(color = secondaryColor.copy(alpha = 0.2f))
                    Text(
                        text = "Key Events (${summaryData.events.size})",
                        color = textColor,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    summaryData.events.forEachIndexed { i, event ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(NovelPrimary.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${i + 1}",
                                    color = if (theme.isDark) NovelPrimary else textColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = event.text,
                                color = textColor.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                if (summaryData.terms.isNotEmpty()) {
                    HorizontalDivider(color = secondaryColor.copy(alpha = 0.2f))
                    Text(
                        text = "Important Terms & Glossary (${summaryData.terms.size})",
                        color = textColor,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    summaryData.terms.forEach { term ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(secondaryColor.copy(alpha = 0.08f))
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = term.name,
                                    color = textColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (term.kind.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(NovelPrimary.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = term.kind,
                                            color = NovelPrimary,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = term.meaning,
                                color = secondaryColor,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            } else if (!isSummarizing && failure == null) {
                // 4. Empty State
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = NovelPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Summarize this chapter",
                        color = textColor,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Get key events, plot points, and important terms.",
                        color = secondaryColor,
                        fontSize = 11.5.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(NovelPrimary)
                            .clickable(enabled = canGenerate && !isBusy) { onRequestSummary() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = NovelOnPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Generate Summary",
                            color = NovelOnPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarIconButton(label: String, tooltip: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(NovelPrimary.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = NovelPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

