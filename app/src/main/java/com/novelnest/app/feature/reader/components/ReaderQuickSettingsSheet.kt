package com.novelnest.app.feature.reader.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.core.ui.animation.bounceSelectable
import com.novelnest.app.data.ai.AiAction
import com.novelnest.app.data.ai.AiPhase
import com.novelnest.app.data.ai.AiProgress
import com.novelnest.app.data.narration.NarrationService
import com.novelnest.app.data.narration.NarrationStatus
import com.novelnest.app.data.narration.NarrationStore
import com.novelnest.app.data.preferences.ReaderFont
import com.novelnest.app.data.preferences.ReaderSettings
import com.novelnest.app.data.preferences.ReaderTheme
import com.novelnest.app.feature.reader.Chapter
import com.novelnest.app.feature.reader.ReaderAiState
import com.novelnest.app.feature.reader.aiErrorHelpText
import com.novelnest.app.feature.reader.retryAiFailure
import com.novelnest.app.feature.reader.ReaderContentVersion
import com.novelnest.app.feature.reader.ReaderUiState
import kotlin.math.roundToInt

private enum class QuickSettingsSheetPage {
    MAIN,
    SUMMARY,
    NOTES_LIST,
    NOTE_EDIT,
    EXTRACT_LIST,
    EXTRACT_DETAIL
}

data class ChapterNoteUi(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val updatedAt: String = "Just now"
)

data class ExtractionCategoryUi(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val promptTarget: String,
    val terms: List<ExtractedTermItem> = emptyList(),
    val lastUpdatedChapter: String = "",
    val termCount: Int = terms.size
)

/**
 * Nested scroll connection that absorbs downward scroll/fling deltas when the user reaches the top
 * of a subpage's scrollable content, preventing the parent [ModalBottomSheet] from closing accidentally.
 */
@Composable
internal fun rememberSubpageScrollConnection(): NestedScrollConnection = remember {
    object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            return if (available.y > 0f) {
                Offset(0f, available.y)
            } else {
                Offset.Zero
            }
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            return if (available.y > 0f) {
                Velocity(0f, available.y)
            } else {
                Velocity.Zero
            }
        }
    }
}

/**
 * Allows the user to swipe down specifically from the header area or drag handle to dismiss the sheet.
 */
internal fun Modifier.swipeDownToDismiss(
    onDismiss: () -> Unit,
    threshold: Float = 36f
): Modifier = this.pointerInput(onDismiss, threshold) {
    var totalDragY = 0f
    var dismissed = false
    detectVerticalDragGestures(
        onDragStart = {
            totalDragY = 0f
            dismissed = false
        },
        onDragEnd = {
            if (!dismissed && totalDragY > threshold) {
                onDismiss()
            }
            totalDragY = 0f
            dismissed = false
        },
        onDragCancel = {
            totalDragY = 0f
            dismissed = false
        },
        onVerticalDrag = { change, dragAmount ->
            if (dragAmount > 0f) {
                totalDragY += dragAmount
                if (!dismissed && totalDragY > threshold) {
                    dismissed = true
                    change.consume()
                    onDismiss()
                }
            }
        }
    )
}

/**
 * Compact Half-Page Quick Menu bottom sheet for the Novel Reader screen.
 * Firmly anchored to the bottom of the screen with a streamlined action bar:
 * [ ❐ Copy ] [ ≪ Prev ] ( ▶ Play ) [ Next ≫ ]
 * followed by a segmented 3-tab switcher for Theme/Typography, Sizing/Spacing, and AI Tools.
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
    aiState: ReaderAiState? = null,
    studyState: ReaderStudyState = ReaderStudyState(),
    onSaveNote: (String?, String, String) -> Unit = { _, _, _ -> },
    onDeleteNote: (String) -> Unit = {},
    onSelectNote: (String?) -> Unit = {},
    onAddCollection: (String) -> Unit = {},
    onSelectCollection: (String?) -> Unit = {},
    onDeleteCollection: (String) -> Unit = {},
    onExtractTerms: (String) -> Unit = {},
    onDismissStudyMessage: () -> Unit = {},
    onRequestSummary: () -> Unit = {},
    onRequestRewrite: () -> Unit = {},
    onCancelAi: () -> Unit = {},
    onShowVersion: (ReaderContentVersion) -> Unit = {},
    narrationContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Theme & Font, 1: Sizing & Spacing
    var currentSheetPage by rememberSaveable(currentChapterKey(reader)) { mutableStateOf(QuickSettingsSheetPage.MAIN) }

    // System back button returns hierarchically
    BackHandler(enabled = currentSheetPage != QuickSettingsSheetPage.MAIN) {
        currentSheetPage = when (currentSheetPage) {
            QuickSettingsSheetPage.NOTE_EDIT -> QuickSettingsSheetPage.NOTES_LIST
            QuickSettingsSheetPage.EXTRACT_DETAIL -> QuickSettingsSheetPage.EXTRACT_LIST
            else -> QuickSettingsSheetPage.MAIN
        }
    }

    val currentChapter = reader?.chapters?.getOrNull(reader.currentChapterIndex)


    val notesList = remember(studyState.notes) {
        val format = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
        studyState.notes.map { ChapterNoteUi(it.id, it.title, it.preview,
            format.format(java.util.Date(it.updatedAt))) }
    }
    val activeNote = studyState.editorNote?.let { ChapterNoteUi(it.id, it.title, it.markdown) }
    val termResult by produceState<Pair<String?, List<ExtractedTermItem>>>(null to emptyList(), studyState.selectedCollectionId, studyState.discoveries) {
        value = studyState.selectedCollectionId to withContext(Dispatchers.Default) {
            buildTermItems(studyState.discoveries)
        }
    }
    val termItems = if (termResult.first == studyState.selectedCollectionId) termResult.second else emptyList()
    val extractionCategories = remember(studyState.collections, studyState.selectedCollectionId, termItems) {
        studyState.collections.map { row ->
            val c = row.collection
            ExtractionCategoryUi(c.id, c.name, c.target,
                if (c.id == studyState.selectedCollectionId) termItems else emptyList(),
                c.lastChapter.ifBlank { "Not extracted yet" }, row.termCount)
        }
    }
    val activeExtractCategory = extractionCategories.firstOrNull { it.id == studyState.selectedCollectionId }
    var savingNote by rememberSaveable(currentChapterKey(reader)) { mutableStateOf(false) }
    val savedRevision = studyState.noteSavedRevision
    LaunchedEffect(savedRevision) {
        if (savingNote) {
            savingNote = false
            currentSheetPage = QuickSettingsSheetPage.NOTES_LIST
        }
    }
    LaunchedEffect(studyState.saving, studyState.message) {
        if (!studyState.saving && studyState.message != null && studyState.message != "Note saved") savingNote = false
    }
    LaunchedEffect(currentSheetPage) {
        if (currentSheetPage == QuickSettingsSheetPage.MAIN && studyState.message != null) {
            onDismissStudyMessage()
        }
    }
    val extractionEnabled = reader?.let { !it.isLoading && !it.isChapterLoading } == true &&
        currentChapter?.paragraphs?.any { it.isNotBlank() } == true && aiState?.busy != true && !studyState.saving

    // Narration state handling
    val store = remember { NarrationStore(context.applicationContext) }
    val narrationState by NarrationStatus.state.collectAsStateWithLifecycle()
    val configuredNarration = store.endpoint.isNotBlank()
    val isMatchingChapter = currentChapter != null && aiState?.matches(currentChapter) == true
    val currentSummary = if (isMatchingChapter) aiState?.summary else null
    val currentNovel = reader?.novel
    val isCurrentAudioSession = narrationState.novelId == currentNovel?.id && narrationState.chapterId == currentChapter?.id
    val isAudioPlaying = isCurrentAudioSession && narrationState.playing
    val isAudioLoading = isCurrentAudioSession && narrationState.loading
    val hasPlayableAudio = currentNovel != null && currentChapter?.paragraphs?.any { it.isNotBlank() } == true

    val coroutineScope = rememberCoroutineScope()
    val dismissSheet: () -> Unit = {
        coroutineScope.launch {
            try {
                bottomSheetState.hide()
            } finally {
                onDismiss()
            }
        }
    }

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
                    .swipeDownToDismiss(dismissSheet)
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


            // Subpage Navigation Animated Transition (MAIN <-> SUMMARY)
            AnimatedContent(
                targetState = currentSheetPage,
                transitionSpec = {
                    if (targetState != QuickSettingsSheetPage.MAIN && initialState == QuickSettingsSheetPage.MAIN ||
                        targetState == QuickSettingsSheetPage.NOTE_EDIT ||
                        targetState == QuickSettingsSheetPage.EXTRACT_DETAIL
                    ) {
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
                label = "QuickSettingsPageTransition"
            ) { page ->
                when (page) {
                    QuickSettingsSheetPage.SUMMARY -> {
                        ReaderSummarySubPage(
                            currentChapter = currentChapter,
                            aiState = aiState,
                            theme = settings.theme,
                            canGenerate = reader?.let { !it.isLoading && !it.isChapterLoading } == true,
                            textColor = textColor,
                            secondaryColor = secondaryColor,
                            onBack = { currentSheetPage = QuickSettingsSheetPage.MAIN },
                            onRequestSummary = onRequestSummary,
                            onCancelAi = onCancelAi,
                            onDismissSheet = dismissSheet
                        )
                    }

                    QuickSettingsSheetPage.NOTES_LIST -> {
                        ReaderNotesListSubPage(
                            currentChapter = currentChapter,
                            notes = notesList,
                            textColor = textColor,
                            secondaryColor = secondaryColor,
                            theme = settings.theme,
                            studyMessage = studyState.message,
                            onDismissStudyMessage = onDismissStudyMessage,
                            onBack = { currentSheetPage = QuickSettingsSheetPage.MAIN },
                            onNewNote = { if (studyState.key != null) { onSelectNote(null); currentSheetPage = QuickSettingsSheetPage.NOTE_EDIT } },
                            onOpenNote = { onSelectNote(it.id); currentSheetPage = QuickSettingsSheetPage.NOTE_EDIT },
                            onDeleteNote = onDeleteNote,
                            onDismissSheet = dismissSheet
                        )
                    }
                    QuickSettingsSheetPage.NOTE_EDIT -> {
                        if (studyState.selectedNoteId == null || activeNote != null) ReaderNoteEditSubPage(
                            note = activeNote, textColor = textColor, secondaryColor = secondaryColor,
                            theme = settings.theme, saving = studyState.saving || studyState.key == null,
                            onBack = { currentSheetPage = QuickSettingsSheetPage.NOTES_LIST },
                            onSave = { title, content ->
                                savingNote = true
                                onSaveNote(activeNote?.id, title, content)
                            },
                            onDismissSheet = dismissSheet
                        ) else TextButton(onClick = { currentSheetPage = QuickSettingsSheetPage.NOTES_LIST }) {
                            Text("Loading note… Back to notes", color = textColor)
                        }
                    }
                    QuickSettingsSheetPage.EXTRACT_LIST -> {
                        ReaderExtractListSubPage(
                            currentChapter = currentChapter,
                            categories = extractionCategories,
                            textColor = textColor,
                            secondaryColor = secondaryColor,
                            theme = settings.theme,
                            aiState = aiState,
                            onCancelAi = onCancelAi,
                            studyMessage = studyState.message,
                            onDismissStudyMessage = onDismissStudyMessage,
                            onBack = { currentSheetPage = QuickSettingsSheetPage.MAIN },
                            onSelectCategory = {
                                onSelectCollection(it.id)
                                currentSheetPage = QuickSettingsSheetPage.EXTRACT_DETAIL
                            },
                            onAddCategory = { name, _ -> onAddCollection(name) },
                            onSendExtract = onExtractTerms,
                            canSend = extractionEnabled, saving = studyState.saving || studyState.key == null,
                            savedRevision = studyState.collectionSavedRevision,
                            onDeleteCategory = onDeleteCollection,
                            onDismissSheet = dismissSheet
                        )
                    }
                    QuickSettingsSheetPage.EXTRACT_DETAIL -> {
                        val category = activeExtractCategory
                        if (category != null) {
                            ReaderExtractDetailSubPage(
                                category = category,
                                currentChapter = currentChapter,
                                textColor = textColor,
                                secondaryColor = secondaryColor,
                                theme = settings.theme,
                                aiState = aiState,
                                onCancelAi = onCancelAi,
                                onBack = {
                                    onSelectCollection(null)
                                    currentSheetPage = QuickSettingsSheetPage.EXTRACT_LIST
                                },
                                onSendExtract = onExtractTerms,
                                canSend = extractionEnabled,
                                onDismissSheet = dismissSheet
                            )
                        } else {
                            TextButton(onClick = { currentSheetPage = QuickSettingsSheetPage.EXTRACT_LIST }) {
                                Text("Back to extraction collections", color = textColor)
                            }
                        }
                    }
                    QuickSettingsSheetPage.MAIN -> {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
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
                                                if (narrationState.playing || narrationState.loading) NarrationService.PAUSE else NarrationService.PLAY,
                                                narrationState.sessionId
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


            Spacer(modifier = Modifier.height(10.dp))

            // ─── AI READING ACTIONS (Above Tabs) ───
            AiReadingActionSection(
                aiState = aiState,
                currentChapter = currentChapter,
                theme = settings.theme,
                canGenerate = reader?.let { !it.isLoading && !it.isChapterLoading } == true,
                textColor = textColor,
                secondaryColor = secondaryColor,
                onRequestSummary = onRequestSummary,
                onRequestRewrite = onRequestRewrite,
                onCancelAi = onCancelAi,
                onShowVersion = onShowVersion,
                onOpenSummary = {
                    if (aiState?.busy != true && currentSummary == null) {
                        onRequestSummary()
                    }
                    currentSheetPage = QuickSettingsSheetPage.SUMMARY
                },
                onOpenNotes = {
                    currentSheetPage = QuickSettingsSheetPage.NOTES_LIST
                },
                onOpenExtract = {
                    currentSheetPage = QuickSettingsSheetPage.EXTRACT_LIST
                },
                onRetryExtract = {
                    currentSheetPage = QuickSettingsSheetPage.EXTRACT_LIST
                    val collectionId = aiState?.extractionCollectionId
                    if (extractionEnabled && collectionId != null && extractionCategories.any { it.id == collectionId }) {
                        onExtractTerms(collectionId)
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Segmented Tab Selector with Sliding Pill Indicator (2 Tabs)
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
                    targetValue = tabWidth * selectedTab,
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
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Theme & Font",
                                fontSize = 12.sp,
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
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Size & Spacing",
                                fontSize = 12.sp,
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
                    } else if (tab == 1) {
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
        }
    }
}
}

/**
 * Dedicated Subpage within Quick Settings to view or trigger AI Chapter Summary.
 * Features a top navigation bar with [Back] to quick menu, [Re-summarize] action icon,
 * [Copy] action icon, live generation progress with cancel, and rich formatted summary display.
 */
@Composable
private fun ReaderSummarySubPage(
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .swipeDownToDismiss(onDismissSheet)
            ) {
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
                icon = Icons.Rounded.ContentCopy,
                contentDescription = "Copy summary text",
                onClick = {
                    val text = currentSummary?.summary?.text
                    if (!text.isNullOrBlank()) {
                        try {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(
                                android.content.ClipData.newPlainText("Chapter Summary", text)
                            )
                            Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {}
                    }
                },
                enabled = currentSummary?.summary?.text?.isNotBlank() == true,
                tint = if (currentSummary?.summary?.text?.isNotBlank() == true) textColor else secondaryColor.copy(alpha = 0.35f)
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
                .nestedScroll(rememberSubpageScrollConnection())
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

/**
 * Dedicated Subpage within Quick Settings to view and manage chapter notes.
 */
@Composable
private fun ReaderNotesListSubPage(
    currentChapter: Chapter?,
    notes: List<ChapterNoteUi>,
    textColor: Color,
    secondaryColor: Color,
    theme: ReaderTheme,
    studyMessage: String?,
    onDismissStudyMessage: () -> Unit,
    onBack: () -> Unit,
    onNewNote: () -> Unit,
    onOpenNote: (ChapterNoteUi) -> Unit,
    onDeleteNote: (String) -> Unit,
    onDismissSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete note?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this note? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDeleteNote(id); pendingDelete = null }) {
                    Text("Delete", color = NovelError, fontWeight = FontWeight.Bold)
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .swipeDownToDismiss(onDismissSheet)
            ) {
                Text(
                    text = "Chapter Notes",
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                val subtitle = "${notes.size} note${if (notes.size != 1) "s" else ""} • ${currentChapter?.title ?: "Current Chapter"}"
                Text(
                    text = subtitle,
                    color = secondaryColor,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            NovelNestIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Create new note",
                onClick = onNewNote,
                tint = accentColor
            )
        }

        HorizontalDivider(
            color = secondaryColor.copy(alpha = 0.12f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )

        // Contextual Feedback Banner inside Notes Page
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
                .nestedScroll(rememberSubpageScrollConnection())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (notes.isEmpty()) {
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
                                imageVector = Icons.AutoMirrored.Rounded.StickyNote2,
                                contentDescription = null,
                                tint = NovelPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No notes recorded yet",
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Take notes, jot down plot clues, or capture theories.",
                            color = secondaryColor,
                            fontSize = 11.5.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(NovelPrimary)
                                .clickable(onClick = onNewNote)
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
                                text = "Create Note",
                                color = NovelOnPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                items(notes, key = { it.id }) { note ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surfaceColor),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(secondaryColor.copy(alpha = 0.2f))
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick(scaleDown = BounceScale.Small, onClick = { onOpenNote(note) })
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NovelPrimary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.StickyNote2,
                                        contentDescription = null,
                                        tint = NovelPrimary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = note.title.ifBlank { "Untitled Note" },
                                        color = textColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = note.updatedAt,
                                        color = secondaryColor.copy(alpha = 0.85f),
                                        fontSize = 10.5.sp
                                    )
                                }
                                NovelNestIconButton(
                                    icon = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete note",
                                    onClick = { pendingDelete = note.id },
                                    tint = secondaryColor.copy(alpha = 0.65f)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = note.content.ifBlank { "Empty note content" },
                                color = secondaryColor,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Subpage within Quick Settings to create/edit markdown chapter notes.
 */
@Composable
private fun ReaderNoteEditSubPage(
    note: ChapterNoteUi?, textColor: Color, secondaryColor: Color, theme: ReaderTheme,
    saving: Boolean, onBack: () -> Unit, onSave: (String, String) -> Unit,
    onDismissSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) = MarkdownNoteEditor(note, textColor, secondaryColor, theme, saving, onBack, onSave, onDismissSheet, modifier)

/**
 * Dedicated Subpage within Quick Settings to view and trigger term extraction categories.
 */
@Composable
private fun ReaderExtractListSubPage(
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
    onDismissSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this collection?", fontWeight = FontWeight.Bold) },
            text = { Text("Its saved terms will also be deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDeleteCategory(id); pendingDelete = null }) {
                    Text("Delete", color = NovelError, fontWeight = FontWeight.Bold)
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

    val isExtracting = aiState?.action == AiAction.EXTRACT && aiState.busy
    val extractFailure = if (aiState?.action == AiAction.EXTRACT) aiState.failure else null

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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .swipeDownToDismiss(onDismissSheet)
            ) {
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
        if (isExtracting && aiState != null) {
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
                                text = aiPhaseTitle(aiState.progress.phase, AiAction.EXTRACT),
                                color = textColor,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val detail = aiState.progress.message.ifBlank {
                                if (aiState.progress.totalChunks > 1) {
                                    "Analyzing terms (Chunk ${aiState.progress.chunk}/${aiState.progress.totalChunks})..."
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

        // 2. Contextual Failure Card
        if (extractFailure != null && !isExtracting) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = failureColor.copy(alpha = 0.12f)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(failureColor.copy(alpha = 0.35f))
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
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = failureColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = aiErrorHelpText(extractFailure, AiAction.EXTRACT),
                        color = failureColor,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
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
                .nestedScroll(rememberSubpageScrollConnection())
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
private fun ReaderExtractDetailSubPage(
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
    val isExtracting = aiState?.action == AiAction.EXTRACT && aiState.busy
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .swipeDownToDismiss(onDismissSheet)
            ) {
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
        if (isExtracting && aiState != null) {
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
                            text = aiState.progress.message.ifBlank { "Extracting terms..." },
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

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 360.dp)
                .nestedScroll(rememberSubpageScrollConnection())
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
private fun AiReadingActionSection(
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

private fun aiPhaseTitle(phase: AiPhase, action: AiAction?): String {
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

private fun currentChapterKey(reader: ReaderUiState?): String =
    reader?.novel?.source.toString() + ":" + reader?.novel?.id + ":" + reader?.chapters?.getOrNull(reader.currentChapterIndex)?.id
