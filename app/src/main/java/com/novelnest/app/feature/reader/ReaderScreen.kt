package com.novelnest.app.feature.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.rememberAreAnimationsDisabled
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic
import com.novelnest.app.feature.reader.components.ChapterCutSection
import com.novelnest.app.feature.reader.components.PullChapterIndicator
import com.novelnest.app.feature.reader.components.ReaderQuickSettingsSheet
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

    fun navigateToNextChapter() {
        if (uiState.currentChapterIndex < uiState.chapters.size - 1) {
            playHaptic(NovelNestHapticType.LIGHT_TICK)
            navDirection = 1
            viewModel.nextChapter()
        }
    }

    fun navigateToPrevChapter() {
        if (uiState.currentChapterIndex > 0) {
            playHaptic(NovelNestHapticType.LIGHT_TICK)
            navDirection = -1
            viewModel.prevChapter()
        }
    }

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
                val chapter = uiState.chapters[chapterIndex]
                val listState = rememberLazyListState()

                // Scroll to top on chapter change
                LaunchedEffect(chapterIndex) {
                    listState.scrollToItem(0)
                }

                // Interactive pull offset for swipe-up (bottom) and swipe-down (top) overscroll
                val pullOffset = remember { Animatable(0f) }
                val thresholdPx = with(density) { 75.dp.toPx() }

                // NestedScrollConnection detects overscroll at boundaries
                val pullScrollConnection = remember(listState, hasPrevChapter, hasNextChapter, thresholdPx) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (source == NestedScrollSource.UserInput) {
                                val current = pullOffset.value
                                // Pulling back up from downward pull
                                if (current > 0f && available.y < 0f) {
                                    val consumed = available.y.coerceAtLeast(-current)
                                    coroutineScope.launch { pullOffset.snapTo(current + consumed) }
                                    return Offset(0f, consumed)
                                }
                                // Pulling back down from upward pull
                                if (current < 0f && available.y > 0f) {
                                    val consumed = available.y.coerceAtMost(-current)
                                    coroutineScope.launch { pullOffset.snapTo(current + consumed) }
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
                            if (source == NestedScrollSource.UserInput) {
                                // 1. Swipe down at top of chapter -> pull previous chapter from above
                                if (available.y > 0f && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 && hasPrevChapter) {
                                    val delta = available.y * 0.45f
                                    val newTarget = (pullOffset.value + delta).coerceIn(-thresholdPx * 1.5f, thresholdPx * 1.5f)
                                    coroutineScope.launch { pullOffset.snapTo(newTarget) }
                                    return Offset(0f, available.y)
                                }

                                // 2. Swipe up at end of chapter -> pull next chapter from below
                                if (available.y < 0f && !listState.canScrollForward && hasNextChapter) {
                                    val delta = available.y * 0.45f
                                    val newTarget = (pullOffset.value + delta).coerceIn(-thresholdPx * 1.5f, thresholdPx * 1.5f)
                                    coroutineScope.launch { pullOffset.snapTo(newTarget) }
                                    return Offset(0f, available.y)
                                }
                            }
                            return Offset.Zero
                        }

                        override suspend fun onPreFling(available: Velocity): Velocity {
                            val current = pullOffset.value
                            if (current >= thresholdPx && hasPrevChapter) {
                                navigateToPrevChapter()
                            } else if (current <= -thresholdPx && hasNextChapter) {
                                navigateToNextChapter()
                            }
                            pullOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            return Velocity.Zero
                        }
                    }
                }

                // Fire haptic tick when crossing pull threshold
                var hasFiredThresholdHaptic by remember { mutableStateOf(false) }
                LaunchedEffect(pullOffset.value) {
                    val currentAbs = kotlin.math.abs(pullOffset.value)
                    if (currentAbs >= thresholdPx && !hasFiredThresholdHaptic) {
                        playHaptic(NovelNestHapticType.LIGHT_TICK)
                        hasFiredThresholdHaptic = true
                    } else if (currentAbs < thresholdPx * 0.7f) {
                        hasFiredThresholdHaptic = false
                    }
                }

                val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 62.dp

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pullScrollConnection)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationY = pullOffset.value }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { viewModel.toggleChrome() }
                            ),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = topPadding,
                            bottom = 48.dp
                        )
                    ) {
                        // Chapter Title Header
                        item {
                            Text(
                                text = chapter.title,
                                fontFamily = settings.fontFamily.fontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = (settings.fontSizeSp + 4f).sp,
                                lineHeight = ((settings.fontSizeSp + 4f) * settings.lineHeightMultiplier).sp,
                                color = textColor,
                                modifier = Modifier.padding(bottom = 18.dp)
                            )
                        }

                        // Chapter Paragraphs
                        items(chapter.paragraphs) { paragraph ->
                            Text(
                                text = paragraph,
                                fontFamily = settings.fontFamily.fontFamily,
                                fontSize = settings.fontSizeSp.sp,
                                lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
                                color = textColor,
                                modifier = Modifier.padding(bottom = settings.paragraphSpacingDp.dp)
                            )
                        }

                        // End-of-chapter cut section with '---' border space and Tap/Swipe Up caption
                        item {
                            ChapterCutSection(
                                hasNextChapter = hasNextChapter,
                                textColor = textColor,
                                secondaryColor = secondaryColor,
                                onNextChapter = { navigateToNextChapter() }
                            )
                        }
                    }

                    // Pull Down Indicator (Previous Chapter Preview & Release state)
                    PullChapterIndicator(
                        isPullingDown = true,
                        pullDistancePx = pullOffset.value.coerceAtLeast(0f),
                        thresholdPx = thresholdPx,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topPadding + 8.dp)
                    )

                    // Pull Up Indicator (Next Chapter Preview & Release state)
                    PullChapterIndicator(
                        isPullingDown = false,
                        pullDistancePx = (-pullOffset.value).coerceAtLeast(0f),
                        thresholdPx = thresholdPx,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 28.dp)
                    )
                }
            }
        }

        // Properly placed Top Reading Control Header (Footer removed completely)
        ReaderTopBar(
            novelTitle = uiState.novel?.title ?: "NovelNest",
            chapterTitle = if (uiState.chapters.isNotEmpty()) uiState.chapters[uiState.currentChapterIndex].title else "",
            currentTheme = currentTheme,
            textColor = textColor,
            secondaryColor = secondaryColor,
            isVisible = uiState.isChromeVisible,
            isBookmarked = uiState.isBookmarked,
            isSyncing = uiState.isChapterLoading,
            onBack = onBack,
            onOpenSettings = { viewModel.showQuickSettings(true) },
            onToggleBookmark = { viewModel.toggleBookmark() },
            onResyncChapter = { viewModel.resyncChapter() },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Quick Reader Typography & Theme Customization Sheet
        if (uiState.showQuickSettingsSheet) {
            ReaderQuickSettingsSheet(
                settings = settings,
                textColor = textColor,
                secondaryColor = secondaryColor,
                onDismiss = { viewModel.showQuickSettings(false) },
                onSetTheme = { viewModel.setTheme(it) },
                onSetFontSize = { viewModel.setFontSize(it) },
                onSetLineHeight = { viewModel.setLineHeight(it) }
            )
        }
    }
}
