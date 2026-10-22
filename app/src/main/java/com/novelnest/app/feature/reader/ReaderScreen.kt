package com.novelnest.app.feature.reader

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.readerSettings.collectAsState()
    val playHaptic = rememberNovelNestHaptic()
    val areAnimationsDisabled = rememberAreAnimationsDisabled()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

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

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            com.novelnest.app.feature.reader.components.ReaderChaptersDrawer(
                chapters = uiState.chapters,
                currentChapterIndex = uiState.currentChapterIndex,
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
                val restoredScroll = remember(chapterIndex) { viewModel.scrollPosition(chapterIndex) }
                val listState = rememberLazyListState()
                var scrollRestored by remember(chapterIndex) { mutableStateOf(false) }
                LaunchedEffect(chapterIndex, chapter.paragraphs.isNotEmpty()) {
                    if (!scrollRestored && chapter.paragraphs.isNotEmpty()) {
                        listState.scrollToItem(restoredScroll.first, restoredScroll.second)
                        scrollRestored = true
                    }
                }
                LaunchedEffect(listState, chapterIndex, scrollRestored) {
                    androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                        .collect { (item, offset) ->
                            if (scrollRestored) viewModel.saveScrollPosition(chapterIndex, item, offset)
                        }
                }

                // Interactive pull offset for swipe-up (bottom) and swipe-down (top) overscroll
                var pullOffset by remember(chapterIndex) { mutableFloatStateOf(0f) }
                var releasingPull by remember(chapterIndex) { mutableStateOf(false) }
                val swipeEnabled by rememberUpdatedState(uiState.canNavigateFrom(chapterIndex))
                val thresholdPx = with(density) { 75.dp.toPx() }

                // NestedScrollConnection detects overscroll at boundaries
                val pullScrollConnection = remember(listState, hasPrevChapter, hasNextChapter, thresholdPx) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (source == NestedScrollSource.UserInput && swipeEnabled && !releasingPull) {
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
                            if (source == NestedScrollSource.UserInput && swipeEnabled && !releasingPull) {
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
                            if (!swipeEnabled || releasingPull || pullOffset == 0f) return Velocity.Zero
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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pullScrollConnection)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationY = pullOffset },
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = topPadding,
                            bottom = 48.dp
                        )
                    ) {
                        // Chapter Title Header with generous breathing space below ReaderTopBar
                        item {
                            Text(
                                text = chapter.title,
                                fontFamily = settings.fontFamily.fontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = (settings.fontSizeSp + 4f).sp,
                                lineHeight = ((settings.fontSizeSp + 4f) * settings.lineHeightMultiplier).sp,
                                color = textColor,
                                modifier = Modifier.padding(top = 18.dp, bottom = 22.dp)
                            )
                        }

                        // Chapter Paragraphs
                        itemsIndexed(
                            items = chapter.paragraphs,
                            key = { index, _ -> readerParagraphKey(chapter.id, index) }
                        ) { _, paragraph ->
                            Text(
                                text = paragraph,
                                fontFamily = settings.fontFamily.fontFamily,
                                fontSize = settings.fontSizeSp.sp,
                                lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
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
            onOpenChapterList = { coroutineScope.launch { drawerState.open() } },
            onToggleBookmark = { viewModel.toggleBookmark() },
            onResyncChapter = { viewModel.resyncChapter() },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Keep scrolling text behind an opaque surface in the system navigation inset.
        ReaderNavigationBar(currentTheme, Modifier.align(Alignment.BottomCenter))

        // Quick Reader Typography & Theme Customization Sheet
        if (uiState.showQuickSettingsSheet) {
            ReaderQuickSettingsSheet(
                settings = settings,
                textColor = textColor,
                secondaryColor = secondaryColor,
                onDismiss = { viewModel.showQuickSettings(false) },
                onSetTheme = { viewModel.setTheme(it) },
                onSetFontSize = { viewModel.setFontSize(it) },
                onSetLineHeight = { viewModel.setLineHeight(it) },
                onSetFontFamily = { viewModel.setFontFamily(it) },
                onSetParagraphSpacing = { viewModel.setParagraphSpacing(it) }
            )
        }
    }
}
}
