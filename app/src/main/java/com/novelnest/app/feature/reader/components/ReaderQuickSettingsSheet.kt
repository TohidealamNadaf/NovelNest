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

private enum class QuickSettingsSheetPage {
    MAIN,
    AUTO_SCROLL,
    SUMMARY,
    NOTES_LIST,
    NOTE_EDIT,
    EXTRACT_LIST,
    EXTRACT_DETAIL
}

private enum class NoteExit { NOTES_LIST, DISMISS }

/** Apply to the sheet's own window, never the Activity window behind it. */
@Composable
private fun ReaderSheetSystemBars(theme: ReaderTheme) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    // Material3 1.4 initializes these flags when creating its dialog, but does not
    // update them on theme changes. Keep an already-open sheet in sync as well.
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !theme.isDark
            isAppearanceLightNavigationBars = !theme.isDark
        }
    }
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
    onClearCollection: (String) -> Unit = {},
    onExtractTerms: (String) -> Unit = {},
    onDismissStudyMessage: () -> Unit = {},
    onRequestSummary: () -> Unit = {},
    onRequestRewrite: () -> Unit = {},
    onCancelAi: () -> Unit = {},
    onShowVersion: (ReaderContentVersion) -> Unit = {},
    narrationContent: (@Composable () -> Unit)? = null,
    autoScrollState: ReaderAutoScrollState = ReaderAutoScrollState(),
    onStartAutoScroll: () -> Unit = {},
    onPauseAutoScroll: () -> Unit = {},
    onStopAutoScroll: () -> Unit = {},
    onSetAutoScrollSpeed: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentSheetPage by rememberSaveable(currentChapterKey(reader)) { mutableStateOf(QuickSettingsSheetPage.MAIN) }
    var dismissing by remember { mutableStateOf(false) }
    val pageTransition = updateTransition(currentSheetPage, label = "QuickSettingsPageTransition")
    // Keep gestures disabled until nested content has completely left, including its exit animation.
    val sheetGesturesEnabled = !dismissing && currentSheetPage == QuickSettingsSheetPage.MAIN &&
        pageTransition.currentState == QuickSettingsSheetPage.MAIN && !pageTransition.isRunning
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Theme & Font, 1: Sizing & Spacing

    val currentChapter = reader?.chapters?.getOrNull(reader.currentChapterIndex)
    val playHaptic = rememberNovelNestHaptic()
    var chapterCopyTrigger by remember(currentChapterKey(reader)) { mutableLongStateOf(0L) }
    val isChapterCopied = chapterCopyTrigger > 0L
    LaunchedEffect(chapterCopyTrigger) {
        if (chapterCopyTrigger > 0L) {
            delay(2000L)
            chapterCopyTrigger = 0L
        }
    }
    val canStartAutoScroll = reader?.let { !it.isLoading && !it.isChapterLoading } == true &&
        currentChapter?.paragraphs?.any { it.isNotBlank() } == true &&
        settings.scrollMode == ReaderScrollMode.CONTINUOUS
    val autoScrollRequestKey = if (canStartAutoScroll) currentChapterKey(reader) to
        (if (aiState?.matches(currentChapter) == true) aiState.contentKey else "original")
        else null
    val latestAutoScrollRequestKey by rememberUpdatedState(autoScrollRequestKey)
    val startAutoScroll by rememberUpdatedState(onStartAutoScroll)


    val notesList = remember(studyState.notes) {
        val format = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
        studyState.notes.map { ChapterNoteUi(it.id, it.title, it.preview,
            format.format(java.util.Date(it.updatedAt))) }
    }
    val activeNote = studyState.editorNote?.let { ChapterNoteUi(it.id, it.title, it.markdown) }
    val noteDraft = rememberSaveable(currentChapterKey(reader), activeNote?.id, saver = ReaderNoteDraft.Saver) {
        ReaderNoteDraft(activeNote?.title.orEmpty(), activeNote?.content.orEmpty())
    }
    var pendingNoteExit by rememberSaveable(currentChapterKey(reader)) { mutableStateOf<NoteExit?>(null) }
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
    // Keep this callback identity stable; gestures are controlled separately at the sheet.
    // Veto only an unsaved/in-flight note exit; clean subpages still dismiss on scrim taps.
    val canHide by rememberUpdatedState<() -> Boolean>({
        when {
            currentSheetPage != QuickSettingsSheetPage.NOTE_EDIT -> true
            savingNote || studyState.saving -> false
            noteDraft.changedFrom(activeNote) -> {
                pendingNoteExit = NoteExit.DISMISS
                false
            }
            else -> true
        }
    })
    val confirmSheetChange = remember { { value: SheetValue -> value != SheetValue.Hidden || canHide() } }
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = confirmSheetChange
    )
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
    var pendingAutoScrollKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    val onSheetDismissed by rememberUpdatedState(onDismiss)
    var dismissalDispatched by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        // A cancelled animation from a disposed instance must not close a newly opened sheet.
        onDispose {
            dismissalDispatched = true
            pendingAutoScrollKey = null
        }
    }
    val notifyDismissed: () -> Unit = {
        if (!dismissalDispatched) {
            dismissalDispatched = true
            pendingAutoScrollKey = null
            onSheetDismissed()
        }
    }
    val dismissSheet: () -> Unit = {
        if (!dismissing && !dismissalDispatched && canHide()) {
            dismissing = true
            coroutineScope.launch {
                try {
                    bottomSheetState.hide()
                    if (!bottomSheetState.isVisible && !dismissalDispatched) {
                        // Do not change the card's phase/layout while its sheet is animating.
                        // Only a completed explicit hide may hand control to the chapter runner.
                        val shouldStart = pendingAutoScrollKey != null &&
                            pendingAutoScrollKey == latestAutoScrollRequestKey
                        notifyDismissed()
                        if (shouldStart) startAutoScroll()
                    }
                } finally {
                    // Cancellation, disposal or a changed chapter/content drops the request.
                    pendingAutoScrollKey = null
                    dismissing = false
                }
            }
        }
    }
    val requestAutoScrollStart: () -> Unit = {
        if (!dismissing && !dismissalDispatched && latestAutoScrollRequestKey != null) {
            pendingAutoScrollKey = latestAutoScrollRequestKey
            dismissSheet()
        }
    }
    val leaveNote: (NoteExit) -> Unit = { exit ->
        if (exit == NoteExit.DISMISS) dismissSheet() else {
            noteDraft.reset(activeNote)
            currentSheetPage = QuickSettingsSheetPage.NOTES_LIST
        }
    }
    val requestNoteExit: (NoteExit) -> Unit = { exit ->
        if (!savingNote && !studyState.saving) {
            if (noteDraft.changedFrom(activeNote)) pendingNoteExit = exit else leaveNote(exit)
        }
    }

    ModalBottomSheet(
        onDismissRequest = notifyDismissed,
        sheetState = bottomSheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        containerColor = settings.theme.surfaceColor,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null,
        // Own bottom insets inside; reserve stable top space outside Material's moving anchors.
        contentWindowInsets = { WindowInsets(0) },
        properties = remember(settings.theme.isDark) {
            ModalBottomSheetProperties(
                isAppearanceLightStatusBars = !settings.theme.isDark,
                isAppearanceLightNavigationBars = !settings.theme.isDark,
                shouldDismissOnBackPress = false
            )
        },
        modifier = modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
    ) {
        ReaderSheetSystemBars(settings.theme)
        ReaderSheetBackHandler {
            when (currentSheetPage) {
                QuickSettingsSheetPage.MAIN -> dismissSheet()
                QuickSettingsSheetPage.NOTE_EDIT -> requestNoteExit(NoteExit.NOTES_LIST)
                QuickSettingsSheetPage.EXTRACT_DETAIL -> {
                    onSelectCollection(null)
                    currentSheetPage = QuickSettingsSheetPage.EXTRACT_LIST
                }
                else -> currentSheetPage = QuickSettingsSheetPage.MAIN
            }
        }
        pendingNoteExit?.let { exit ->
            AlertDialog(
                onDismissRequest = { pendingNoteExit = null },
                title = { Text("Discard note changes?") },
                text = { Text("Your changes have not been saved. Keep editing or discard them to leave.") },
                confirmButton = {
                    TextButton(enabled = !studyState.saving && !savingNote, onClick = {
                        noteDraft.reset(activeNote)
                        pendingNoteExit = null
                        leaveNote(exit)
                    }) { Text("Discard changes", color = NovelError) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingNoteExit = null }) { Text("Keep editing") }
                }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .testTag("ReaderQuickSettingsContent")
                // Do not read Material's offset-dependent top inset in measured content.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
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


            // Subpage Navigation Animated Transition (MAIN <-> SUMMARY)
            pageTransition.AnimatedContent(
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
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    when (page) {
                    QuickSettingsSheetPage.AUTO_SCROLL -> {
                        ReaderAutoScrollSubPage(
                            state = autoScrollState,
                            speed = settings.autoScrollSpeed,
                            canStart = canStartAutoScroll,
                            continuousMode = settings.scrollMode == ReaderScrollMode.CONTINUOUS,
                            textColor = textColor,
                            secondaryColor = secondaryColor,
                            theme = settings.theme,
                            onBack = { currentSheetPage = QuickSettingsSheetPage.MAIN },
                            onStart = requestAutoScrollStart,
                            onPause = onPauseAutoScroll,
                            onStop = onStopAutoScroll,
                            onSetSpeed = onSetAutoScrollSpeed
                        )
                    }
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
                            note = activeNote, draft = noteDraft, textColor = textColor, secondaryColor = secondaryColor,
                            theme = settings.theme, saving = studyState.saving || studyState.key == null,
                            onBack = { requestNoteExit(NoteExit.NOTES_LIST) },
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
                            onClearCategory = onClearCollection,
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
                val copySuccessColor = if (settings.theme.isDark) NovelSecondary else Color(0xFF2E6B4F)
                val copyBgColor by animateColorAsState(
                    targetValue = if (isChapterCopied) copySuccessColor.copy(alpha = 0.18f)
                    else secondaryColor.copy(alpha = if (canCopyChapter) 0.12f else 0.05f),
                    animationSpec = tween(durationMillis = 200),
                    label = "copyBgColor"
                )
                val copyBorderColor by animateColorAsState(
                    targetValue = if (isChapterCopied) copySuccessColor.copy(alpha = 0.45f)
                    else secondaryColor.copy(alpha = if (canCopyChapter) 0.18f else 0.08f),
                    animationSpec = tween(durationMillis = 200),
                    label = "copyBorderColor"
                )
                val copyIconTint by animateColorAsState(
                    targetValue = if (isChapterCopied) copySuccessColor
                    else if (canCopyChapter) textColor else secondaryColor.copy(alpha = 0.35f),
                    animationSpec = tween(durationMillis = 200),
                    label = "copyIconTint"
                )

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(copyBgColor)
                        .border(
                            1.dp,
                            copyBorderColor,
                            RoundedCornerShape(14.dp)
                        )
                        .bounceClick(
                            enabled = canCopyChapter,
                            scaleDown = BounceScale.Small,
                            onClick = {
                                playHaptic(NovelNestHapticType.SUCCESS)
                                chapterCopyTrigger = System.currentTimeMillis()
                                onCopyChapter()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isChapterCopied,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.65f, animationSpec = tween(180)))
                                .togetherWith(fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.65f, animationSpec = tween(140)))
                        },
                        label = "copyChapterIconTransition"
                    ) { copied ->
                        if (copied) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Chapter copied",
                                tint = copyIconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy chapter",
                                tint = copyIconTint,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
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
                                        notifyDismissed()
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
                        ReaderAutoScrollEntry(
                            state = autoScrollState,
                            speed = settings.autoScrollSpeed,
                            canStart = canStartAutoScroll,
                            textColor = textColor,
                            secondaryColor = secondaryColor,
                            theme = settings.theme,
                            onStart = requestAutoScrollStart,
                            onPause = onPauseAutoScroll,
                            onStop = onStopAutoScroll,
                            onClick = { currentSheetPage = QuickSettingsSheetPage.AUTO_SCROLL }
                        )
                        Spacer(modifier = Modifier.height(10.dp))

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
}

private fun currentChapterKey(reader: ReaderUiState?): String =
    reader?.novel?.source.toString() + ":" + reader?.novel?.id + ":" + reader?.chapters?.getOrNull(reader.currentChapterIndex)?.id
