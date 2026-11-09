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
internal fun ReaderExtractListSubPage(
    currentChapter: Chapter?,
    categories: List<ExtractionCategoryUi>,
    textColor: Color,
    secondaryColor: Color,
    theme: ReaderTheme,
    aiState: ReaderAiState?,
    onCancelAi: () -> Unit,
    studyMessage: String?,
    onDismissStudyMessage: () -> Unit,
    onBack: () -> Unit,
    onSelectCategory: (ExtractionCategoryUi) -> Unit,
    onAddCategory: (name: String, prompt: String) -> Unit,
    onSendExtract: (String) -> Unit,
    canSend: Boolean,
    saving: Boolean,
    savedRevision: Long,
    onDeleteCategory: (String) -> Unit,
    onClearCategory: (String) -> Unit,
    onDismissSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Clear or delete this collection?", fontWeight = FontWeight.Bold) },
            text = { Text("Clear terms removes this collection's saved discoveries from all chapters and keeps its topic. Delete collection also removes the topic. Neither action can be undone.") },
            confirmButton = {
                Column {
                    TextButton(enabled = !saving && categories.any { it.id == id && it.termCount > 0 },
                        onClick = { onClearCategory(id); pendingDelete = null }) {
                        Text("Clear saved terms", color = NovelError, fontWeight = FontWeight.Bold)
                    }
                    TextButton(enabled = !saving, onClick = { onDeleteCategory(id); pendingDelete = null }) {
                        Text("Delete collection", color = NovelError, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = secondaryColor)
                }
            }
        )
    }
    val accentColor = if (theme.isDark) NovelPrimary else textColor
    val failureColor = NovelError
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var newCategoryName by rememberSaveable { mutableStateOf("") }
    var awaitingSaveRevision by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(savedRevision) {
        if (awaitingSaveRevision > 0 && savedRevision >= awaitingSaveRevision) {
            newCategoryName = ""; showAddDialog = false; awaitingSaveRevision = 0
        }
    }

    val extractionState = aiState?.takeIf { state ->
        state.action == AiAction.EXTRACT && currentChapter?.let(state::matches) == true &&
            categories.any { it.id == state.extractionCollectionId }
    }
    val isExtracting = extractionState?.busy == true
    val extractFailure = extractionState?.failure

    Column(modifier = modifier.fillMaxWidth()) {
        // Top Subpage Header Bar
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
                    text = "Extract Terms",
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "AI Knowledge & Codex Extractor",
                    color = secondaryColor,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            NovelNestIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Add extraction target",
                onClick = { showAddDialog = true },
                tint = accentColor
            )
        }

        HorizontalDivider(
            color = secondaryColor.copy(alpha = 0.12f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )

        // 1. Contextual AI Progress Card (Properly Positioned Inside Subpage)
        if (isExtracting) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = NovelPrimary.copy(alpha = 0.12f)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(NovelPrimary.copy(alpha = 0.3f))
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
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
                            color = NovelPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = aiPhaseTitle(extractionState.progress.phase, AiAction.EXTRACT),
                                color = textColor,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val detail = extractionState.progress.message.ifBlank {
                                if (extractionState.progress.totalChunks > 1) {
                                    "Analyzing terms (Chunk ${extractionState.progress.chunk}/${extractionState.progress.totalChunks})..."
                                } else "Analyzing chapter terms..."
                            }
                            Text(
                                text = detail,
                                color = secondaryColor,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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

        // Keep failures associated with the collection that owns the request.
        if (extractFailure != null && !isExtracting) {
            ReaderExtractionFailureCard(
                failure = extractFailure,
                canRetry = canSend,
                onRetry = { extractionState.extractionCollectionId?.let(onSendExtract) }
            )
        }

        // 3. Saved Category / Collection Notification Message
        studyMessage?.let { message ->
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = NovelPrimary.copy(alpha = 0.12f)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(NovelPrimary.copy(alpha = 0.3f))
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = NovelPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message,
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    NovelNestIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        onClick = onDismissStudyMessage,
                        tint = secondaryColor
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 360.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Add Category Dialog/Form Card
            if (showAddDialog) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surfaceColor),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(NovelPrimary.copy(alpha = 0.45f))
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "What to extract from chapters?",
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = newCategoryName,
                                onValueChange = { if (it.length <= 160) newCategoryName = it },
                                textStyle = TextStyle(
                                    color = textColor,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                placeholder = {
                                    Text(
                                        "e.g. Cultivation terms, Alchemy pills, Beast ranks...",
                                        color = secondaryColor.copy(alpha = 0.5f),
                                        fontSize = 11.5.sp
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NovelPrimary,
                                    unfocusedBorderColor = secondaryColor.copy(alpha = 0.25f),
                                    focusedTextColor = textColor,
                                    unfocusedTextColor = textColor,
                                    cursorColor = NovelPrimary,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showAddDialog = false; newCategoryName = "" }) {
                                    Text("Cancel", color = secondaryColor, fontSize = 11.5.sp)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                val canSaveCategory = !saving && newCategoryName.isNotBlank()
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (canSaveCategory) NovelPrimary else NovelPrimary.copy(alpha = 0.35f))
                                        .clickable(enabled = canSaveCategory) {
                                            if (newCategoryName.isNotBlank()) {
                                                awaitingSaveRevision = savedRevision + 1
                                                onAddCategory(newCategoryName.trim(), "Extract all ${newCategoryName.trim().lowercase()}")
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (saving) {
                                        CircularProgressIndicator(
                                            color = NovelOnPrimary,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = if (saving) "Saving…" else "Save Category",
                                        color = NovelOnPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (categories.isEmpty() && !showAddDialog) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(NovelPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = NovelPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No extraction collections yet",
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap + to describe what terms or lore to extract from the novel.",
                            color = secondaryColor,
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(NovelPrimary)
                                .clickable { showAddDialog = true }
                                .padding(horizontal = 18.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = NovelOnPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Create Collection",
                                color = NovelOnPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            items(categories, key = { it.id }) { category ->
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
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectCategory(category) }
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NovelPrimary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = NovelPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = category.name,
                                    color = textColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(NovelPrimary.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "${category.termCount} term${if (category.termCount != 1) "s" else ""}",
                                            color = if (theme.isDark) NovelPrimary else textColor,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = category.lastUpdatedChapter,
                                        color = secondaryColor.copy(alpha = 0.8f),
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        NovelNestIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "Delete collection",
                            onClick = { pendingDelete = category.id },
                            tint = secondaryColor.copy(alpha = 0.65f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (canSend) NovelPrimary else NovelPrimary.copy(alpha = 0.35f))
                                .clickable(enabled = canSend) { onSendExtract(category.id) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = null,
                                tint = NovelOnPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Send",
                                color = NovelOnPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Subpage within Quick Settings to view extracted terms from current chapter.
 */
@Composable
internal fun ReaderExtractDetailSubPage(
    category: ExtractionCategoryUi,
    currentChapter: Chapter?,
    textColor: Color,
    secondaryColor: Color,
    theme: ReaderTheme,
    aiState: ReaderAiState?,
    onCancelAi: () -> Unit,
    onBack: () -> Unit,
    onSendExtract: (String) -> Unit,
    canSend: Boolean,
    onDismissSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember(category.id) { mutableStateOf(emptySet<String>()) }
    val extractionState = aiState?.takeIf { state ->
        state.action == AiAction.EXTRACT && state.extractionCollectionId == category.id &&
            currentChapter?.let(state::matches) == true
    }
    val isExtracting = extractionState?.busy == true
    val extractFailure = extractionState?.failure
    val failureColor = NovelError

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NovelNestIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back to collections",
                onClick = onBack
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${category.termCount} terms · ${category.lastUpdatedChapter}",
                    color = secondaryColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (canSend) NovelPrimary else NovelPrimary.copy(alpha = 0.35f))
                    .clickable(enabled = canSend) { onSendExtract(category.id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null,
                    tint = NovelOnPrimary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Send",
                    color = NovelOnPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider(
            color = secondaryColor.copy(alpha = 0.12f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )

        // Contextual AI Progress inside Detail Page
        if (isExtracting) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = NovelPrimary.copy(alpha = 0.12f)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(NovelPrimary.copy(alpha = 0.3f))
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = NovelPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = extractionState.progress.message.ifBlank { "Extracting terms..." },
                            color = textColor,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = onCancelAi,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = failureColor),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("Cancel", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = failureColor)
                    }
                }
            }
        }

        if (extractFailure != null && !isExtracting) {
            ReaderExtractionFailureCard(
                failure = extractFailure,
                canRetry = canSend,
                onRetry = { onSendExtract(category.id) }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 360.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Sending from chapters extracts and appends new terms. Earlier meanings and references are preserved.",
                    color = secondaryColor,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            if (category.terms.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (category.termCount > 0) "Loading saved terms…" else "No terms discovered yet.",
                            color = textColor,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'Send' to analyze the current chapter and extract discoveries.",
                            color = secondaryColor,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
            category.terms.forEach { term ->
                item(key = term.id) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surfaceColor),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(secondaryColor.copy(alpha = 0.18f))
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = term.term,
                                    color = textColor,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (term.category.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(NovelPrimary.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = term.category,
                                            color = if (theme.isDark) NovelPrimary else textColor,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = term.description,
                                color = secondaryColor,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = term.chapterFound,
                                color = secondaryColor.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                            if (term.references.size > 1) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            expanded = if (term.id in expanded) expanded - term.id else expanded + term.id
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (term.id in expanded) "Hide earlier references" else "Show all ${term.references.size} references",
                                        color = if (theme.isDark) NovelPrimary else textColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
                if (term.id in expanded) {
                    items(term.references.dropLast(1), key = { "reference:" + it.id }) { reference ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.surfaceColor),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(secondaryColor.copy(alpha = 0.12f))
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = reference.chapterFound,
                                    color = textColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = reference.description,
                                    color = secondaryColor,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReaderExtractionFailureCard(failure: AiFailure, canRetry: Boolean, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NovelError.copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, NovelError.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = NovelError, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                aiErrorHelpText(failure, AiAction.EXTRACT),
                color = NovelError, fontSize = 11.5.sp, lineHeight = 15.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(enabled = canRetry, onClick = onRetry) { Text("Retry") }
        }
    }
}

