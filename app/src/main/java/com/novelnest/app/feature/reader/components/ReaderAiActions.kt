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

@Composable
internal fun AiReadingActionSection(
    aiState: ReaderAiState?,
    currentChapter: Chapter?,
    theme: ReaderTheme,
    canGenerate: Boolean,
    textColor: Color,
    secondaryColor: Color,
    onRequestSummary: () -> Unit,
    onRequestRewrite: () -> Unit,
    onCancelAi: () -> Unit,
    onShowVersion: (ReaderContentVersion) -> Unit,
    onOpenSummary: () -> Unit,
    onOpenNotes: () -> Unit = {},
    onOpenExtract: () -> Unit = {},
    onRetryExtract: () -> Unit = onOpenExtract
) {
    val accentColor = if (theme.isDark) NovelPrimary else textColor
    val successColor = if (theme.isDark) NovelSecondary else textColor
    val failureColor = if (theme.isDark) NovelError else textColor
    val hasText = canGenerate && currentChapter?.paragraphs?.any { it.isNotBlank() } == true
    val isMatchingChapter = currentChapter != null && aiState?.matches(currentChapter) == true
    val currentSummary = if (isMatchingChapter) aiState?.summary else null
    val currentRewrite = if (isMatchingChapter) aiState?.rewrite else null
    val currentVersion = aiState?.version ?: ReaderContentVersion.ORIGINAL
    val isBusy = aiState?.busy == true
    val isSummarizing = isBusy && aiState?.action == AiAction.SUMMARY
    val isRewriting = isBusy && aiState?.action == AiAction.REWRITE
    val failure = if (isMatchingChapter) aiState?.failure else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 4 Square-Shaped Action Tiles in One Grid Row (Summary and Rewrite in the middle)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ─── 1. NOTES TILE ───
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(secondaryColor.copy(alpha = if (theme.isDark) 0.08f else 0.05f))
                    .border(
                        1.dp,
                        secondaryColor.copy(alpha = 0.16f),
                        RoundedCornerShape(12.dp)
                    )
                    .bounceClick(
                        enabled = true,
                        scaleDown = BounceScale.Small,
                        onClick = onOpenNotes
                    )
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EditNote,
                        contentDescription = null,
                        tint = if (theme.isDark) NovelPrimary else textColor,
                        modifier = Modifier.size(23.dp)
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "Notes",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ─── 2. SUMMARY TILE ───
            val summaryActive = currentSummary != null
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (summaryActive) accentColor.copy(alpha = 0.14f)
                        else secondaryColor.copy(alpha = if (hasText && !isBusy) 0.08f else 0.04f)
                    )
                    .border(
                        1.dp,
                        if (summaryActive) accentColor.copy(alpha = 0.4f)
                        else secondaryColor.copy(alpha = if (hasText && !isBusy) 0.16f else 0.08f),
                        RoundedCornerShape(12.dp)
                    )
                    .bounceClick(
                        enabled = hasText && (!isBusy || summaryActive),
                        scaleDown = BounceScale.Small,
                        onClick = {
                            if (!summaryActive && !isBusy) {
                                onRequestSummary()
                            }
                            onOpenSummary()
                        }
                    )
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isSummarizing) {
                        CircularProgressIndicator(
                            color = accentColor,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = if (summaryActive || (hasText && !isBusy)) accentColor else secondaryColor.copy(alpha = 0.35f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "Summary",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (summaryActive) accentColor else if (hasText && !isBusy) textColor else secondaryColor.copy(alpha = 0.4f),
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ─── 3. REWRITE TILE ───
            val rewriteActive = currentRewrite != null
            val isShowingRewrite = currentVersion == ReaderContentVersion.REWRITE
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (rewriteActive) successColor.copy(alpha = 0.14f)
                        else secondaryColor.copy(alpha = if (hasText && !isBusy) 0.08f else 0.04f)
                    )
                    .border(
                        1.dp,
                        if (rewriteActive) successColor.copy(alpha = 0.4f)
                        else secondaryColor.copy(alpha = if (hasText && !isBusy) 0.16f else 0.08f),
                        RoundedCornerShape(12.dp)
                    )
                    .bounceClick(
                        enabled = hasText && (!isBusy || rewriteActive),
                        scaleDown = BounceScale.Small,
                        onClick = {
                            if (rewriteActive) {
                                onShowVersion(if (isShowingRewrite) ReaderContentVersion.ORIGINAL else ReaderContentVersion.REWRITE)
                            } else if (!isBusy) {
                                onRequestRewrite()
                            }
                        }
                    )
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isRewriting) {
                        CircularProgressIndicator(
                            color = successColor,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = if (rewriteActive || (hasText && !isBusy)) successColor else secondaryColor.copy(alpha = 0.35f),
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "Rewrite",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (rewriteActive) successColor else if (hasText && !isBusy) textColor else secondaryColor.copy(alpha = 0.4f),
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ─── 4. EXTRACT TILE ───
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(secondaryColor.copy(alpha = if (theme.isDark) 0.08f else 0.05f))
                    .border(
                        1.dp,
                        secondaryColor.copy(alpha = 0.16f),
                        RoundedCornerShape(12.dp)
                    )
                    .bounceClick(
                        enabled = true,
                        scaleDown = BounceScale.Small,
                        onClick = onOpenExtract
                    )
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoFixHigh,
                        contentDescription = null,
                        tint = if (theme.isDark) NovelSecondary else textColor,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "Extract",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }


        // Active Generation Status Card
        if (isBusy) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surfaceColor),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(secondaryColor.copy(alpha = 0.2f))
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = accentColor,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = aiPhaseTitle(aiState.progress.phase, aiState.action),
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val detail = aiState.progress.message.ifBlank {
                                if (aiState.progress.totalChunks > 1) {
                                    "Chunk ${aiState.progress.chunk}/${aiState.progress.totalChunks}"
                                } else "Processing request"
                            }
                            Text(
                                text = detail,
                                color = secondaryColor,
                                fontSize = 10.5.sp
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

        // Actionable Error Card with Retry
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
                                text = "AI Request Failed",
                                color = failureColor,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = aiErrorHelpText(failure, aiState?.action),
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
                            .clickable {
                                retryAiFailure(aiState?.action, onRequestSummary, onRequestRewrite, onRetryExtract)
                            }
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

        // Rewrite Version Switcher [ Original | Rewrite ] (when rewrite ready)
        if (currentRewrite != null && !isBusy) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(secondaryColor.copy(alpha = 0.12f))
                    .padding(2.dp)
            ) {
                val isOriginal = currentVersion == ReaderContentVersion.ORIGINAL
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isOriginal) NovelPrimary else Color.Transparent)
                        .clickable { onShowVersion(ReaderContentVersion.ORIGINAL) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Original",
                        color = if (isOriginal) NovelOnPrimary else textColor,
                        fontSize = 11.sp,
                        fontWeight = if (isOriginal) FontWeight.Bold else FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (!isOriginal) NovelPrimary else Color.Transparent)
                        .clickable { onShowVersion(ReaderContentVersion.REWRITE) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Rewrite (${currentRewrite.rewrittenParagraphs.size}p)",
                        color = if (!isOriginal) NovelOnPrimary else textColor,
                        fontSize = 11.sp,
                        fontWeight = if (!isOriginal) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

internal fun aiPhaseTitle(phase: AiPhase, action: AiAction?): String {
    val actionWord = when (action) { AiAction.REWRITE -> "Rewrite"; AiAction.EXTRACT -> "Extraction"; else -> "Summary" }
    return when (phase) {
        AiPhase.CHECKING_CACHE -> "Checking Cache..."
        AiPhase.PREPARING -> "Preparing Chapter..."
        AiPhase.WAITING_FOR_SLOT -> "Waiting for Request Slot..."
        AiPhase.CONNECTING -> "Connecting to AI Model..."
        AiPhase.GENERATING -> "Generating $actionWord..."
        AiPhase.SWITCHING_PROVIDER -> "Switching to Fallback Provider..."
        AiPhase.VALIDATING -> "Validating Output..."
        AiPhase.READY -> "$actionWord Ready"
        AiPhase.FAILED -> "$actionWord Failed"
        AiPhase.CANCELLED -> "$actionWord Cancelled"
        else -> "AI Processing..."
    }
}

