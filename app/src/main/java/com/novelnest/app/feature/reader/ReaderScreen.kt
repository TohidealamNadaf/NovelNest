package com.novelnest.app.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.rememberAreAnimationsDisabled
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic
import com.novelnest.app.feature.reader.components.ChapterCutSection
import com.novelnest.app.feature.reader.components.PullChapterIndicator
import com.novelnest.app.feature.reader.components.ReaderQuickSettingsSheet
import com.novelnest.app.feature.reader.components.ReaderNavigationBar
import com.novelnest.app.feature.reader.components.ReaderTopBar
import com.novelnest.app.feature.reader.components.ReaderFastScroller
import com.novelnest.app.core.ui.LocalNarrationBottomPadding
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val aiState by viewModel.aiState.collectAsState()
    val studyState by viewModel.studyState.collectAsStateWithLifecycle()
    val settings by viewModel.readerSettings.collectAsState()
    val autoScroll by viewModel.autoScrollState.collectAsStateWithLifecycle()
    val playHaptic = rememberNovelNestHaptic()
    val narrationBottomPadding = LocalNarrationBottomPadding.current
    val areAnimationsDisabled = rememberAreAnimationsDisabled()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    val activity = context.findReaderActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val readerWindowFocused = LocalWindowInfo.current.isWindowFocused
    var readerResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var autoScrollTouchInterrupted by remember { mutableStateOf(false) }
    LaunchedEffect(readerWindowFocused) {
        if (!readerWindowFocused) viewModel.pauseAutoScroll()
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, _ ->
            readerResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!readerResumed) viewModel.pauseAutoScroll()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.pauseAutoScroll()
        }
    }
    ReaderKeepScreenOn(settings.keepScreenOn && readerResumed)
    // The ViewModel survives rotation; leaving its destination cancels generation immediately.
    DisposableEffect(viewModel) {
        onDispose { if (activity?.isChangingConfigurations != true) viewModel.cancelAiRequest() }
    }

    val currentTheme = settings.theme
    val textColor = currentTheme.textColor.copy(alpha = settings.textBrightness)
    val secondaryColor = currentTheme.secondaryTextColor.copy(alpha = settings.textBrightness)

    // Navigation direction tracking: 1 for next chapter (slides up), -1 for previous chapter (slides down)
    var navDirection by remember { mutableIntStateOf(1) }

    fun navigateToNextChapter(expectedIndex: Int) {
        if (uiState.canNavigateFrom(expectedIndex) && uiState.currentChapterIndex < uiState.chapters.size - 1) {
            playHaptic(NovelNestHapticType.LIGHT_TICK)
            navDirection = 1
            viewModel.nextChapter(expectedIndex)
        }
    }

    fun navigateToPrevChapter(expectedIndex: Int) {
        if (uiState.canNavigateFrom(expectedIndex) && uiState.currentChapterIndex > 0) {
            playHaptic(NovelNestHapticType.LIGHT_TICK)
            navDirection = -1
            viewModel.prevChapter(expectedIndex)
        }
    }

    val drawerState = androidx.compose.material3.rememberDrawerState(
        initialValue = androidx.compose.material3.DrawerValue.Closed
    )
    LaunchedEffect(drawerState.currentValue, drawerState.targetValue) {
        if (drawerState.currentValue != androidx.compose.material3.DrawerValue.Closed ||
            drawerState.targetValue != androidx.compose.material3.DrawerValue.Closed) viewModel.pauseAutoScroll()
    }

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            com.novelnest.app.feature.reader.components.ReaderChaptersDrawer(
                chapters = uiState.chapters,
                currentChapterIndex = uiState.currentChapterIndex,
                isOpen = drawerState.targetValue == androidx.compose.material3.DrawerValue.Open,
                readChapterIds = uiState.readChapterIds,
                onSelectChapter = { index ->
                    viewModel.selectChapter(index)
                },
                onCloseDrawer = {
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(currentTheme.backgroundColor)
                .pointerInput(viewModel) {
                    awaitEachGesture {
                        // Observe before children, without consuming the touch or blocking their gestures.
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        autoScrollTouchInterrupted = viewModel.autoScrollState.value.phase == ReaderAutoScrollPhase.RUNNING
                        if (autoScrollTouchInterrupted) viewModel.pauseAutoScroll()
                    }
                }
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
            val hasNextChapter = uiState.currentChapterIndex < uiState.chapters.size - 1
            val hasPrevChapter = uiState.currentChapterIndex > 0

            // Animated chapter transition: next chapter pulls up from below; previous pulls down from above
            AnimatedContent(
                targetState = uiState.currentChapterIndex,
                transitionSpec = {
                    if (areAnimationsDisabled) {
                        fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                    } else if (navDirection >= 0) {
                        // Next chapter pulls up from bottom
                        (slideInVertically(animationSpec = tween(340, easing = FastOutSlowInEasing)) { fullHeight -> fullHeight } +
                            fadeIn(animationSpec = tween(300)))
                            .togetherWith(
                                slideOutVertically(animationSpec = tween(340, easing = FastOutSlowInEasing)) { fullHeight -> -fullHeight } +
                                    fadeOut(animationSpec = tween(240))
                            )
                    } else {
                        // Previous chapter pulls down from above
                        (slideInVertically(animationSpec = tween(340, easing = FastOutSlowInEasing)) { fullHeight -> -fullHeight } +
                            fadeIn(animationSpec = tween(300)))
                            .togetherWith(
                                slideOutVertically(animationSpec = tween(340, easing = FastOutSlowInEasing)) { fullHeight -> fullHeight } +
                                    fadeOut(animationSpec = tween(240))
                            )
                    }
                },
                label = "chapter_reading_transition"
            ) { chapterIndex ->
                val chapter = uiState.chapters.getOrNull(chapterIndex) ?: return@AnimatedContent
                val displayedParagraphs = aiState.paragraphs(chapter)
                val contentKey = if (aiState.matches(chapter)) aiState.contentKey else "original"
                val restoredScroll = remember(chapterIndex, contentKey) { viewModel.scrollPosition(chapterIndex, contentKey) }
                val listState = key(contentKey) { rememberLazyListState() }
                var scrollRestored by remember(chapterIndex, contentKey) { mutableStateOf(false) }
                LaunchedEffect(chapterIndex, contentKey, chapter.paragraphs.isNotEmpty()) {
                    if (!scrollRestored && chapter.paragraphs.isNotEmpty()) {
                        listState.scrollToItem(restoredScroll.first.coerceIn(0, displayedParagraphs.size), restoredScroll.second.coerceAtLeast(0))
                        scrollRestored = true
                    }
                }
                LaunchedEffect(listState, chapterIndex, contentKey, scrollRestored) {
                    try {
                        androidx.compose.runtime.snapshotFlow {
                            Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, listState.isScrollInProgress)
                        }.collect { (item, offset, moving) ->
                            if (scrollRestored) {
                                viewModel.saveScrollPosition(chapterIndex, item, offset, contentKey)
                                if (!moving) viewModel.flushScrollPosition()
                            }
                        }
                    } finally { viewModel.flushScrollPosition() }
                }

                // Interactive pull offset for swipe-up (bottom) and swipe-down (top) overscroll
                var pullOffset by remember(chapterIndex) { mutableFloatStateOf(0f) }
                var releasingPull by remember(chapterIndex) { mutableStateOf(false) }
                var fastScrolling by remember(chapterIndex) { mutableStateOf(false) }
                val swipeEnabled by rememberUpdatedState(uiState.canNavigateFrom(chapterIndex))
                val thresholdPx = with(density) { 75.dp.toPx() }

                // NestedScrollConnection detects overscroll at boundaries
                val pullScrollConnection = remember(listState, hasPrevChapter, hasNextChapter, thresholdPx) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (source == NestedScrollSource.UserInput && swipeEnabled && !releasingPull && !fastScrolling) {
                                val current = pullOffset
                                // Pulling back up from downward pull
                                if (current > 0f && available.y < 0f) {
                                    val consumed = available.y.coerceAtLeast(-current)
                                    pullOffset = current + consumed
                                    return Offset(0f, consumed)
                                }
                                // Pulling back down from upward pull
                                if (current < 0f && available.y > 0f) {
                                    val consumed = available.y.coerceAtMost(-current)
                                    pullOffset = current + consumed
                                    return Offset(0f, consumed)
                                }
                            }
                            return Offset.Zero
                        }

                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            if (source == NestedScrollSource.UserInput && swipeEnabled && !releasingPull && !fastScrolling) {
                                // 1. Swipe down at top of chapter -> pull previous chapter from above
                                if (available.y > 0f && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 && hasPrevChapter) {
                                    val delta = available.y * 0.45f
                                    val newTarget = (pullOffset + delta).coerceIn(-thresholdPx * 1.5f, thresholdPx * 1.5f)
                                    pullOffset = newTarget
                                    return Offset(0f, available.y)
                                }

                                // 2. Swipe up at end of chapter -> pull next chapter from below
                                if (available.y < 0f && !listState.canScrollForward && hasNextChapter) {
                                    val delta = available.y * 0.45f
                                    val newTarget = (pullOffset + delta).coerceIn(-thresholdPx * 1.5f, thresholdPx * 1.5f)
                                    pullOffset = newTarget
                                    return Offset(0f, available.y)
                                }
                            }
                            return Offset.Zero
                        }

                        override suspend fun onPreFling(available: Velocity): Velocity {
                            if (!swipeEnabled || releasingPull || fastScrolling || pullOffset == 0f) return Velocity.Zero
                            val current = pullOffset
                            releasingPull = true
                            try {
                                if (current >= thresholdPx && hasPrevChapter) {
                                    pullOffset = 0f
                                    navigateToPrevChapter(chapterIndex)
                                } else if (current <= -thresholdPx && hasNextChapter) {
                                    pullOffset = 0f
                                    navigateToNextChapter(chapterIndex)
                                } else {
                                    animate(current, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                                        pullOffset = value
                                    }
                                }
                            } finally {
                                pullOffset = 0f
                                releasingPull = false
                            }
                            return Velocity(0f, available.y)
                        }
                    }
                }

                // Fire haptic tick when crossing pull threshold
                var hasFiredThresholdHaptic by remember { mutableStateOf(false) }
                LaunchedEffect(pullOffset) {
                    val currentAbs = kotlin.math.abs(pullOffset)
                    if (currentAbs >= thresholdPx && !hasFiredThresholdHaptic) {
                        playHaptic(NovelNestHapticType.LIGHT_TICK)
                        hasFiredThresholdHaptic = true
                    } else if (currentAbs < thresholdPx * 0.7f) {
                        hasFiredThresholdHaptic = false
                    }
                }

                val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 68.dp

                ReaderAutoScrollEffect(
                    listState = listState,
                    target = if (chapterIndex == uiState.currentChapterIndex) readerAutoScrollTarget(uiState, aiState) else null,
                    session = autoScroll,
                    eligible = readerResumed && readerWindowFocused && scrollRestored && swipeEnabled && !fastScrolling &&
                        pullOffset == 0f && !releasingPull && !uiState.showQuickSettingsSheet &&
                        drawerState.currentValue == androidx.compose.material3.DrawerValue.Closed &&
                        drawerState.targetValue == androidx.compose.material3.DrawerValue.Closed,
                    speed = settings.autoScrollSpeed,
                    onInterrupted = { viewModel.pauseAutoScroll(it) },
                    onFinished = viewModel::finishAutoScroll
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pullScrollConnection)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(chapter.id) {
                                awaitEachGesture {
                                    // Do not consume side touches, scrolling, long presses, or child controls.
                                    val down = awaitFirstDown(requireUnconsumed = true)
                                    if (down.position.x !in (size.width * 0.2f)..(size.width * 0.8f) ||
                                        !swipeEnabled || (listState.isScrollInProgress && !autoScrollTouchInterrupted) ||
                                        pullOffset != 0f) return@awaitEachGesture
                                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                                    if (up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis &&
                                        (up.position - down.position).getDistance() <= viewConfiguration.touchSlop) {
                                        up.consume()
                                        viewModel.showQuickSettings(true)
                                    }
                                }
                            }
                            .graphicsLayer { translationY = pullOffset },
                        contentPadding = PaddingValues(
                            start = 22.dp, // Subtly pushed by 2dp ("two salt grains") from 20.dp
                            end = 20.dp,
                            top = topPadding,
                            bottom = 48.dp + narrationBottomPadding
                        )
                    ) {
                        // Keep title and rewrite controls in one item so paragraph indexes stay stable.
                        item {
                            Column {
                                Text(
                                    text = chapter.title,
                                    fontFamily = settings.fontFamily.fontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = ((settings.fontSizeSp + 4f) * 0.975f).sp,
                                    lineHeight = (((settings.fontSizeSp + 4f) * 0.975f) * settings.lineHeightMultiplier).sp,
                                    letterSpacing = 0.15.sp,
                                    color = textColor,
                                    modifier = Modifier.padding(top = 18.dp, bottom = 22.dp)
                                )

                                if (aiState.matches(chapter) && aiState.rewrite != null) {
                                    val isRewrite = aiState.version == ReaderContentVersion.REWRITE
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = currentTheme.surfaceColor
                                        ),
                                        border = CardDefaults.outlinedCardBorder().copy(
                                            brush = androidx.compose.ui.graphics.SolidColor(
                                                if (isRewrite) NovelPrimary.copy(alpha = 0.5f) else secondaryColor.copy(alpha = 0.25f)
                                            )
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.AutoAwesome,
                                                        contentDescription = null,
                                                        tint = if (isRewrite) NovelPrimary else secondaryColor,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (isRewrite) "AI Rewrite (Active)" else "AI Rewrite (Available)",
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isRewrite) NovelPrimary else textColor
                                                    )
                                                }

                                                Text(
                                                    text = "Narration reads original text",
                                                    fontSize = 10.5.sp,
                                                    color = secondaryColor
                                                )
                                            }

                                            // Show Original / Show Rewrite control
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(secondaryColor.copy(alpha = 0.10f))
                                                    .padding(2.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(32.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (!isRewrite) NovelPrimary else androidx.compose.ui.graphics.Color.Transparent)
                                                        .clickable { viewModel.showAiContentVersion(ReaderContentVersion.ORIGINAL) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "Show Original",
                                                        color = if (!isRewrite) NovelOnPrimary else textColor,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = if (!isRewrite) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(32.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isRewrite) NovelPrimary else androidx.compose.ui.graphics.Color.Transparent)
                                                        .clickable { viewModel.showAiContentVersion(ReaderContentVersion.REWRITE) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "Show Rewrite",
                                                        color = if (isRewrite) NovelOnPrimary else textColor,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = if (isRewrite) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Chapter Paragraphs calibrated to match Capacitor web typography
                        itemsIndexed(
                            items = displayedParagraphs,
                            key = { index, _ -> readerParagraphKey(if (contentKey == "original") chapter.id else "${chapter.id}:$contentKey", index) }
                        ) { _, paragraph ->
                            Text(
                                text = paragraph,
                                fontFamily = settings.fontFamily.fontFamily,
                                fontSize = (settings.fontSizeSp * 0.975f).sp,
                                lineHeight = ((settings.fontSizeSp * 0.975f) * settings.lineHeightMultiplier).sp,
                                letterSpacing = 0.2.sp,
                                color = textColor,
                                modifier = Modifier.padding(bottom = settings.paragraphSpacingDp.dp)
                            )
                        }

                        // Chapter Loading Skeleton/Spinner when paragraphs are fetching
                        if (chapter.paragraphs.isEmpty() && uiState.isChapterLoading) {
                            item(key = "${chapter.id}_loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = NovelPrimary,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }

                        // Chapter Failure State with Retry button
                        if (chapter.paragraphs.isEmpty() && !uiState.isChapterLoading && uiState.errorMessage != null) {
                            item(key = "${chapter.id}_retry") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 36.dp, horizontal = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = uiState.errorMessage ?: "Failed to load chapter content.",
                                        color = secondaryColor,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    androidx.compose.material3.Button(
                                        onClick = { viewModel.retryCurrentChapter() },
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = NovelPrimary)
                                    ) {
                                        Text("Retry Chapter", color = NovelOnPrimary)
                                    }
                                }
                            }
                        }

                        // End-of-chapter cut section with '---' border space and Tap/Swipe Up caption
                        // Only show when chapter content is actually present or loaded to prevent anchor jumps
                        if (chapter.paragraphs.isNotEmpty()) {
                            item(key = "${chapter.id}_cut_section") {
                                ChapterCutSection(
                                    hasNextChapter = uiState.chapters.getOrNull(chapterIndex + 1) != null,
                                    nextChapterTitle = uiState.chapters.getOrNull(chapterIndex + 1)?.title,
                                    textColor = textColor,
                                    secondaryColor = secondaryColor,
                                    onNextChapter = { navigateToNextChapter(chapterIndex) }
                                )
                            }
                        }
                    }

                    ReaderFastScroller(
                        listState = listState,
                        enabled = scrollRestored && swipeEnabled && chapter.paragraphs.isNotEmpty() &&
                            !uiState.showQuickSettingsSheet &&
                            drawerState.currentValue == androidx.compose.material3.DrawerValue.Closed &&
                            drawerState.targetValue == androidx.compose.material3.DrawerValue.Closed,
                        color = textColor,
                        onDraggingChanged = { dragging ->
                            fastScrolling = dragging
                            if (dragging) { viewModel.pauseAutoScroll(); pullOffset = 0f }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                            .padding(top = topPadding + 8.dp, bottom = 48.dp + narrationBottomPadding)
                    )

                    // Pull Down Indicator (Previous Chapter Preview & Release state)
                    PullChapterIndicator(
                        isPullingDown = true,
                        pullDistancePx = pullOffset.coerceAtLeast(0f),
                        thresholdPx = thresholdPx,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topPadding + 8.dp)
                    )

                    // Pull Up Indicator (Next Chapter Preview & Release state)
                    PullChapterIndicator(
                        isPullingDown = false,
                        pullDistancePx = (-pullOffset).coerceAtLeast(0f),
                        thresholdPx = thresholdPx,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 28.dp)
                    )
                }
            }
        }

        // Fixed Top Reading Control Header (pinned, does not collapse or slide up on tap)
        ReaderTopBar(
            novelTitle = uiState.novel?.title ?: "NovelNest",
            chapterTitle = uiState.chapters.getOrNull(uiState.currentChapterIndex)?.title.orEmpty(),
            currentTheme = currentTheme,
            textColor = textColor,
            secondaryColor = secondaryColor,
            isVisible = true,
            isBookmarked = uiState.isBookmarked,
            isSyncing = uiState.isChapterLoading,
            onBack = onBack,
            onOpenSettings = { viewModel.showQuickSettings(true) },
            onOpenChapterList = { viewModel.pauseAutoScroll(); coroutineScope.launch { drawerState.open() } },
            onToggleBookmark = { viewModel.toggleBookmark() },
            onResyncChapter = { viewModel.resyncChapter() },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Keep scrolling text behind an opaque surface in the system navigation inset.
        ReaderNavigationBar(currentTheme, Modifier.align(Alignment.BottomCenter))

        // Quick Reader Typography & Theme Customization Sheet
        if (uiState.showQuickSettingsSheet) {
            val chapter = uiState.chapters.getOrNull(uiState.currentChapterIndex)
            val expectedStudyKey = uiState.novel?.let { novel -> chapter?.let {
                com.novelnest.app.data.study.StudyChapterKey(novel.source.name, novel.id, it.id)
            } }
            val sheetStudy = if (studyState.key == expectedStudyKey) studyState else ReaderStudyState()
            ReaderQuickSettingsSheet(
                settings = settings,
                textColor = textColor,
                secondaryColor = secondaryColor,
                reader = uiState,
                hasPrevChapter = uiState.currentChapterIndex > 0,
                hasNextChapter = uiState.currentChapterIndex < uiState.chapters.size - 1,
                onPrevChapter = { navigateToPrevChapter(uiState.currentChapterIndex) },
                onNextChapter = { navigateToNextChapter(uiState.currentChapterIndex) },
                onDismiss = { viewModel.showQuickSettings(false) },
                onSetTheme = { viewModel.setTheme(it) },
                onSetFontSize = { viewModel.setFontSize(it) },
                onSetLineHeight = { viewModel.setLineHeight(it) },
                onSetFontFamily = { viewModel.setFontFamily(it) },
                onSetParagraphSpacing = { viewModel.setParagraphSpacing(it) },
                autoScrollState = autoScroll,
                onStartAutoScroll = viewModel::startAutoScroll,
                onPauseAutoScroll = { viewModel.pauseAutoScroll() },
                onStopAutoScroll = viewModel::stopAutoScroll,
                onSetAutoScrollSpeed = viewModel::setAutoScrollSpeed,
                aiState = aiState,
                studyState = sheetStudy,
                onSaveNote = { id, title, text ->
                    if (expectedStudyKey != null && viewModel.studyState.value.key == expectedStudyKey)
                        viewModel.saveChapterNote(id, title, text)
                },
                onDeleteNote = { if (viewModel.studyState.value.key == expectedStudyKey) viewModel.deleteChapterNote(it) },
                onSelectNote = { if (viewModel.studyState.value.key == expectedStudyKey) viewModel.selectChapterNote(it) },
                onAddCollection = { if (viewModel.studyState.value.key == expectedStudyKey) viewModel.addTermCollection(it) },
                onSelectCollection = { if (viewModel.studyState.value.key == expectedStudyKey) viewModel.selectTermCollection(it) },
                onDeleteCollection = { if (viewModel.studyState.value.key == expectedStudyKey) viewModel.deleteTermCollection(it) },
                onClearCollection = { if (viewModel.studyState.value.key == expectedStudyKey) viewModel.clearTermCollection(it) },
                onExtractTerms = viewModel::extractTerms,
                onDismissStudyMessage = viewModel::dismissStudyMessage,
                onRequestSummary = { viewModel.requestAiSummary() },
                onRequestRewrite = { viewModel.requestAiRewrite() },
                onCancelAi = { viewModel.cancelAiRequest() },
                onShowVersion = { viewModel.showAiContentVersion(it) },
                canCopyChapter = !uiState.isLoading && uiState.chapters.getOrNull(uiState.currentChapterIndex)
                    ?.paragraphs?.any { it.isNotBlank() } == true,
                onCopyChapter = {
                    // Resolve on tap so the clipboard always receives the active chapter, not an outgoing animation frame.
                    val state = viewModel.uiState.value
                    val chapter = state.chapters.getOrNull(state.currentChapterIndex)
                    if (!state.isLoading && chapter != null && chapter.paragraphs.any { it.isNotBlank() }) {
                        val chapterText = (listOf(chapter.title) + viewModel.displayedChapterParagraphs(chapter))
                            .filter { it.isNotBlank() }.joinToString("\n\n")
                        try {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            checkNotNull(clipboard).setPrimaryClip(ClipData.newPlainText(chapter.title, chapterText))
                            // Android 13+ supplies its own clipboard confirmation.
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                Toast.makeText(context, "Chapter copied", Toast.LENGTH_SHORT).show()
                            }
                        } catch (_: RuntimeException) {
                            Toast.makeText(context, "Could not copy this chapter. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

}

private fun android.content.Context.findReaderActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.takeIf { it !== this }?.findReaderActivity()
    else -> null
}
