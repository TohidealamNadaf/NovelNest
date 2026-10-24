package com.novelnest.app.feature.detail

import com.novelnest.app.core.ui.LocalNarrationBottomPadding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import com.novelnest.app.data.progress.NovelProgress
import com.novelnest.app.data.progress.ReadingCompletion
import com.novelnest.app.data.progress.readingCompletion
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.novelnest.app.core.designsystem.NovelBackground
import com.novelnest.app.core.designsystem.NovelGold
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSecondary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.designsystem.NovelTextTertiary
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.animation.AnimatedBookmarkButton
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic
import com.novelnest.app.data.model.Novel

@Composable
fun NovelDetailScreen(
    viewModel: NovelDetailViewModel,
    onBack: () -> Unit,
    onStartReading: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val narrationBottomPadding = LocalNarrationBottomPadding.current
    val novel = uiState.novel
    val playHaptic = rememberNovelNestHaptic()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }
    val isScrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 250 }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NovelBackground)
    ) {
        if (uiState.isLoading && novel == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NovelPrimary)
            }
        } else if (novel == null && uiState.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Unable to load novel details",
                        style = MaterialTheme.typography.titleMedium,
                        color = NovelTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NovelTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.retryLoadDetails() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NovelPrimary,
                            contentColor = NovelOnPrimary
                        )
                    ) {
                        Text(
                            text = "Retry",
                            color = NovelOnPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else if (novel != null) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 60.dp + narrationBottomPadding)
            ) {
                // 1. HERO POSTER & METADATA SECTION
                item(key = "detail:hero", contentType = "hero") {
                    NovelDetailHeroHeader(
                        novel = novel,
                        uiState = uiState,
                        onBack = onBack,
                        onToggleBookmark = {
                            playHaptic(NovelNestHapticType.LIGHT_TICK)
                            viewModel.toggleBookmark()
                        },
                        onStartReading = { targetIndex -> onStartReading(novel.id, targetIndex) }
                    )
                }

                item(key = "detail:progress", contentType = "progress") {
                    val completion = remember(novel, uiState.readingProgress) { novel.readingCompletion(uiState.readingProgress) }
                    val targetIndex = remember(uiState.readingProgress, uiState.chapters) {
                        val lastReadIndex = uiState.readingProgress?.lastReadChapterIndex ?: 0
                        if (uiState.chapters.isNotEmpty()) {
                            lastReadIndex.coerceIn(0, uiState.chapters.lastIndex)
                        } else {
                            lastReadIndex.coerceAtLeast(0)
                        }
                    }
                    NovelReadingProgressCard(
                        completion = completion,
                        progress = uiState.readingProgress,
                        onContinueReading = { onStartReading(novel.id, targetIndex) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // 2. SYNOPSIS / SUMMARY SECTION
                item(key = "detail:synopsis", contentType = "synopsis") {
                    NovelSynopsisCard(
                        synopsis = novel.synopsis,
                        isExpanded = uiState.isSynopsisExpanded,
                        onToggleExpand = { viewModel.toggleSynopsis() }
                    )
                }

                // 3. CHAPTERS FILTER & SORT CONTROLS
                item(key = "detail:chapter_controls", contentType = "chapter_controls") {
                    ChapterListHeader(
                        totalChapters = uiState.chapters.size,
                        filteredCount = uiState.filteredChapters.size,
                        searchQuery = uiState.chapterQuery,
                        isAscending = uiState.isAscending,
                        isSyncing = uiState.isChaptersLoading,
                        onSearchChange = { viewModel.onChapterSearch(it) },
                        onToggleSort = {
                            playHaptic(NovelNestHapticType.LIGHT_TICK)
                            viewModel.toggleSortOrder()
                        },
                        onResync = {
                            playHaptic(NovelNestHapticType.LIGHT_TICK)
                            viewModel.resyncChapters()
                        }
                    )
                }

                // 3b. FALLBACK / ERROR NOTIFICATION BANNER
                if (uiState.chaptersErrorMessage != null || uiState.errorMessage != null) {
                    item(key = "detail:chapter_error", contentType = "chapter_error") {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = NovelSurfaceVariant),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder),
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Offline / Cached Chapters",
                                        color = NovelTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = (uiState.chaptersErrorMessage ?: uiState.errorMessage).orEmpty(),
                                        color = NovelTextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { if (uiState.errorMessage != null) viewModel.retryLoadDetails() else viewModel.resyncChapters() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NovelPrimary,
                                        contentColor = NovelOnPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        tint = NovelOnPrimary,
                                        modifier = Modifier.size(14.dp)
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
                }

                // 4. CHAPTER LIST ITEMS
                itemsIndexed(
                    items = uiState.filteredChapters,
                    key = { _, ch -> ch.id },
                    contentType = { _, _ -> "chapter" }
                ) { index, chapter ->
                    val isRead = uiState.readChapterIds.contains(chapter.id)
                    val actualIndex = viewModel.readerChapterIndex(chapter.id)
                    val isCurrent = uiState.readingProgress != null && actualIndex == uiState.readingProgress?.lastReadChapterIndex
                    ChapterListItem(
                        chapterTitle = chapter.title,
                        chapterNumber = index + 1,
                        isRead = isRead,
                        isCurrent = isCurrent,
                        onClick = {
                            playHaptic(NovelNestHapticType.LIGHT_TICK)
                            onStartReading(novel.id, actualIndex)
                        }
                    )
                }
            }

            // 1. Initial state: Back button over the hero.
            AnimatedVisibility(
                visible = !isScrolled,
                enter = fadeIn(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(180))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NovelNestIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        tint = NovelTextPrimary,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC0F1216))
                    )

                }
            }

            // 2. Scrolled Down State: Sticky Slide-in Top Header with Novel Title & Attached Search Field
            AnimatedVisibility(
                visible = isScrolled,
                enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(280)) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(220)) + fadeOut(animationSpec = tween(180))
            ) {
                androidx.compose.material3.Surface(
                    color = com.novelnest.app.core.designsystem.NovelSurface,
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, com.novelnest.app.core.designsystem.NovelSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NovelNestIconButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = NovelTextPrimary,
                            onClick = onBack
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Attached Search Field with Novel Title context
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(com.novelnest.app.core.designsystem.NovelSurfaceVariant)
                                .border(1.dp, com.novelnest.app.core.designsystem.NovelSurfaceBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = NovelPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                BasicTextField(
                                    value = uiState.chapterQuery,
                                    onValueChange = { viewModel.onChapterSearch(it) },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = NovelTextPrimary,
                                        fontSize = 12.sp
                                    ),
                                    singleLine = true,
                                    cursorBrush = SolidColor(NovelPrimary),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { innerTextField ->
                                        if (uiState.chapterQuery.isEmpty()) {
                                            Text(
                                                text = "${novel.title}...",
                                                color = com.novelnest.app.core.designsystem.NovelTextSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                if (uiState.chapterQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear chapter search",
                                        tint = com.novelnest.app.core.designsystem.NovelTextSecondary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { viewModel.onChapterSearch("") }
                                    )
                                }
                            }
                        }


                    }
                }
            }

            // Floating Instant Scroll-to-Top Button (Moved to bottom left of page)
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn() + androidx.compose.animation.scaleIn(),
                exit = fadeOut() + androidx.compose.animation.scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, bottom = 24.dp + narrationBottomPadding)
            ) {
                NovelNestIconButton(
                    icon = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Scroll to top instantly",
                    onClick = {
                        playHaptic(NovelNestHapticType.LIGHT_TICK)
                        // A focused search field must not request visibility again after the jump.
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        coroutineScope.launch {
                            // Skip measuring/animating through the variable-height header sections.
                            listState.scrollToItem(index = 0, scrollOffset = 0)
                        }
                    },
                    tint = NovelOnPrimary,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(NovelPrimary)
                )
            }

            // Floating Pill Read / Continue Button (Positioned in bottom right of page)
            AnimatedVisibility(
                visible = isScrolled,
                enter = fadeIn() + androidx.compose.animation.scaleIn(),
                exit = fadeOut() + androidx.compose.animation.scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 24.dp + narrationBottomPadding)
            ) {
                val lastReadIdx = uiState.readingProgress?.lastReadChapterIndex?.coerceIn(0, (uiState.chapters.size - 1).coerceAtLeast(0)) ?: 0
                Button(
                    onClick = {
                        playHaptic(NovelNestHapticType.LIGHT_TICK)
                        onStartReading(novel.id, lastReadIdx)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NovelPrimary,
                        contentColor = NovelOnPrimary
                    ),
                    shape = CircleShape,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    modifier = Modifier
                        .height(46.dp)
                        .bounceClick(scaleDown = BounceScale.Small, onClick = {
                            playHaptic(NovelNestHapticType.LIGHT_TICK)
                            onStartReading(novel.id, lastReadIdx)
                        })
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoStories,
                        contentDescription = null,
                        tint = NovelOnPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = if (uiState.readingProgress != null) "Continue" else "Read",
                        color = NovelOnPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Top Hero Poster with blurred backdrop gradient, novel details, and key actions.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NovelDetailHeroHeader(
    novel: Novel,
    uiState: NovelDetailUiState,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onStartReading: (Int) -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NovelBackground)
    ) {
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        // Backdrop cover with deep dark gradient overlay covering status bar
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(novel.coverUrl.ifBlank { "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600&q=80" })
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        )

        // Gradient fading smoothly into NovelBackground
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x550F1216),
                            Color(0x990F1216),
                            NovelBackground
                        )
                    )
                )
        )

        // Main Novel Info Layout (properly positioned below status bar & top back bar)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarHeight + 66.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                // High-resolution Poster Card with Rank Badge
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = NovelSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    modifier = Modifier
                        .width(115.dp)
                        .aspectRatio(2f / 3f)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(novel.coverUrl.ifBlank { "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600&q=80" })
                                .crossfade(true)
                                .build(),
                            contentDescription = novel.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Rank Badge on Poster
                        if (novel.rank > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xE60F1216))
                                    .border(1.dp, NovelGold.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "#${novel.rank}",
                                    color = NovelGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Title, Author, Source & Stats
                Column(modifier = Modifier.weight(1f)) {
                    // Source Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NovelPrimary.copy(alpha = 0.15f))
                            .border(0.5.dp, NovelPrimary.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = novel.source.displayName,
                            color = NovelPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = novel.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = NovelTextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = NovelTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = novel.author,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NovelTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Ranking, Rating, Status, and Views Badges (FlowRow ensures badges wrap cleanly without squishing or line breaks)
                    FlowRow(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Rank Badge in details
                        if (novel.rank > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NovelGold.copy(alpha = 0.15f))
                                    .border(0.5.dp, NovelGold.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Rank #${novel.rank}",
                                    color = NovelGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Rating Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x99161B22))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = NovelGold,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = String.format("%.1f", novel.rating),
                                color = NovelTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x99161B22))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = novel.status,
                                color = NovelSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // Views Badge - Always kept on a single line with icon
                        if (novel.views.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x99161B22))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Visibility,
                                    contentDescription = null,
                                    tint = NovelTextSecondary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = novel.views,
                                    color = NovelTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Genres Chips Row
            if (novel.genres.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(novel.genres) { genre ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(NovelSurfaceVariant)
                                .border(1.dp, NovelSurfaceBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = genre,
                                color = NovelTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Primary Read Action Button (Start Reading vs Continue Reading)
            val lastReadIndex = uiState.readingProgress?.lastReadChapterIndex?.coerceIn(0, (uiState.chapters.size - 1).coerceAtLeast(0)) ?: 0
            val hasStartedReading = uiState.readingProgress != null
            val continueLabel = if (hasStartedReading) {
                val chTitle = uiState.readingProgress?.lastReadChapterTitle.orEmpty()
                if (chTitle.isNotBlank()) "Continue: $chTitle" else "Continue Chapter ${lastReadIndex + 1}"
            } else {
                "Start Reading"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onStartReading(lastReadIndex) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NovelPrimary,
                        contentColor = NovelOnPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .bounceClick(scaleDown = BounceScale.Medium, onClick = { onStartReading(lastReadIndex) })
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoStories,
                        contentDescription = null,
                        tint = NovelOnPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = continueLabel,
                        color = NovelOnPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Bookmark toggle in hero header: visible when NOT bookmarked, hidden when bookmarked
                if (!uiState.isBookmarked) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NovelSurfaceVariant)
                            .border(1.dp, NovelSurfaceBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedBookmarkButton(
                            isBookmarked = false,
                            onToggle = { onToggleBookmark() }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern Reading Progress Card reflecting application and detail page theme.
 */
@Composable
private fun NovelReadingProgressCard(
    completion: ReadingCompletion,
    progress: NovelProgress?,
    onContinueReading: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val surfaceColor by animateColorAsState(
        targetValue = if (pressed || focused) NovelSurfaceVariant else NovelSurface,
        label = "progress_card_press"
    )

    val animatedFraction by animateFloatAsState(
        targetValue = completion.fraction,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "progress_fraction"
    )

    val percent = (completion.fraction * 100).toInt()
    val hasStarted = completion.read > 0 || (progress != null && progress.lastReadChapterIndex >= 0)

    Card(
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactions,
                indication = null,
                role = Role.Button,
                onClickLabel = if (hasStarted) "Continue reading" else "Start reading",
                onClick = onContinueReading
            )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Header: Icon + Title/Subtitle + Percentage Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Book Icon in theme accent container
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NovelPrimary.copy(alpha = 0.14f))
                        .border(1.dp, NovelPrimary.copy(alpha = 0.28f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoStories,
                        contentDescription = null,
                        tint = NovelPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reading Progress",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NovelTextPrimary
                    )

                    val subtitleText = when {
                        progress != null && progress.lastReadChapterTitle.isNotBlank() ->
                            "Last: ${progress.lastReadChapterTitle}"
                        hasStarted ->
                            "${completion.read} chapters completed"
                        else ->
                            "Not started yet"
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = NovelTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Percentage Badge Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (percent > 0) NovelPrimary.copy(alpha = 0.15f)
                            else NovelSurfaceVariant
                        )
                        .border(
                            1.dp,
                            if (percent > 0) NovelPrimary.copy(alpha = 0.45f)
                            else NovelSurfaceBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$percent%",
                        color = if (percent > 0) NovelPrimary else NovelTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sleek Gradient Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(NovelSurfaceVariant)
            ) {
                if (animatedFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(NovelPrimary, NovelGold)
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Chapter count & Action prompt
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = completion.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = NovelTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (hasStarted) "Continue" else "Start Reading",
                        color = NovelPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = NovelPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

/**
 * Expandable Synopsis card.
 */
@Composable
private fun NovelSynopsisCard(
    synopsis: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val surfaceColor by animateColorAsState(
        if (pressed || focused) NovelSurfaceVariant else NovelSurface, label = "synopsis_press")
    Card(
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactions, indication = null,
                role = androidx.compose.ui.semantics.Role.Button,
                onClickLabel = if (isExpanded) "Collapse synopsis" else "Expand synopsis",
                onClick = onToggleExpand)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Synopsis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NovelTextPrimary
                )

                Text(
                    text = if (isExpanded) "Show Less" else "Read More",
                    color = NovelPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = synopsis.ifBlank { "No synopsis available for this title." },
                style = MaterialTheme.typography.bodyMedium,
                color = NovelTextSecondary,
                lineHeight = 20.sp,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Header above chapter list with search filter & order toggle.
 */
@Composable
private fun ChapterListHeader(
    totalChapters: Int,
    filteredCount: Int,
    searchQuery: String,
    isAscending: Boolean,
    isSyncing: Boolean = false,
    onSearchChange: (String) -> Unit,
    onToggleSort: () -> Unit,
    onResync: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Chapters",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NovelTextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NovelSurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$totalChapters",
                        color = NovelPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Resync / Refresh button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NovelSurfaceVariant)
                        .border(1.dp, NovelSurfaceBorder, RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onResync)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            color = NovelPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Resync Chapters",
                                tint = NovelPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sync",
                                color = NovelTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Ascending / Descending order toggle button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NovelSurfaceVariant)
                        .border(1.dp, NovelSurfaceBorder, RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onToggleSort)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapVert,
                        contentDescription = "Sort order",
                        tint = NovelPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isAscending) "1 - $totalChapters" else "$totalChapters - 1",
                        color = NovelTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search in chapter list
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NovelSurfaceVariant)
                .border(1.dp, NovelSurfaceBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = NovelTextTertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = NovelTextPrimary),
                    cursorBrush = SolidColor(NovelPrimary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search chapters...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NovelTextTertiary
                            )
                        }
                        innerField()
                    }
                )

                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear",
                        tint = NovelTextSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onSearchChange("") }
                    )
                }
            }
        }
    }
}

/**
 * Individual Chapter List Item row.
 * - Wraps long chapter titles naturally across lines without truncation.
 * - Dims read chapter titles to indicate read progression.
 */
@Composable
private fun ChapterListItem(
    chapterTitle: String,
    chapterNumber: Int,
    isRead: Boolean = false,
    isCurrent: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCurrent -> NovelSurfaceVariant.copy(alpha = 0.65f)
                isRead -> NovelSurface.copy(alpha = 0.55f)
                else -> NovelSurface
            }
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                isCurrent -> NovelSurfaceBorder.copy(alpha = 0.8f)
                isRead -> NovelSurfaceBorder.copy(alpha = 0.35f)
                else -> NovelSurfaceBorder.copy(alpha = 0.6f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Redesigned Index Card:
            // - Read: dark emerald container with circular outline tick mark
            // - Current: background does NOT turn amber gold; title text indicates amber gold
            // - Unread: clean surface badge with chapter number
            val indexBg = when {
                isRead -> Color(0xFF0D251D)
                else -> NovelSurfaceVariant
            }
            val indexBorder = when {
                isRead -> Color(0x3310B981)
                isCurrent -> NovelSurfaceBorder.copy(alpha = 0.8f)
                else -> NovelSurfaceBorder.copy(alpha = 0.5f)
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(indexBg)
                    .border(1.dp, indexBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isRead) {
                    // Circular outline tick mark as in reference image
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .border(1.5.dp, Color(0xFF10B981), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Read",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                } else {
                    Text(
                        text = "$chapterNumber",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) NovelPrimary else NovelTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapterTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        isCurrent -> NovelPrimary // ONLY text color indicates amber gold
                        isRead -> NovelTextTertiary.copy(alpha = 0.65f)
                        else -> NovelTextPrimary
                    },
                    fontSize = 13.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else if (isRead) FontWeight.Normal else FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        isCurrent -> "Currently reading"
                        isRead -> "Read"
                        else -> "Ready to read"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isCurrent -> NovelPrimary.copy(alpha = 0.85f)
                        isRead -> Color(0xFF10B981).copy(alpha = 0.85f)
                        else -> NovelTextSecondary
                    },
                    fontSize = 11.sp
                )
            }
        }
    }
}
