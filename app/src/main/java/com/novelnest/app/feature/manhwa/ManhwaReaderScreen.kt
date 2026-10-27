package com.novelnest.app.feature.manhwa

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.LocalNarrationBottomPadding
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.animation.AnimatedBookmarkButton
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic
import com.novelnest.app.feature.manhwa.components.ManhwaTiledPageView
import com.novelnest.app.feature.manhwa.components.ManhwaSystemBars
import com.novelnest.app.feature.reader.Chapter
import com.novelnest.app.feature.reader.components.ReaderChaptersDrawer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ManhwaReaderScreen(
    viewModel: ManhwaReaderViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as NovelNestApplication
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val narrationBottomPadding = LocalNarrationBottomPadding.current
    val playHaptic = rememberNovelNestHaptic()
    val coroutineScope = rememberCoroutineScope()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var areControlsVisible by remember { mutableStateOf(true) }
    ManhwaSystemBars(
        controlsVisible = areControlsVisible || uiState.content == null ||
            drawerState.currentValue == DrawerValue.Open || drawerState.targetValue == DrawerValue.Open
    )

    // Convert ManhwaChapter list to Chapter adapter list for the shared ReaderChaptersDrawer
    val chaptersAdapter = remember(uiState.details?.chapters) {
        uiState.details?.chapters?.map { mc ->
            Chapter(
                id = mc.id,
                title = mc.title,
                url = mc.url,
                paragraphs = emptyList()
            )
        } ?: emptyList()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen || areControlsVisible,
        drawerContent = {
            ReaderChaptersDrawer(
                chapters = chaptersAdapter,
                currentChapterIndex = uiState.currentChapterIndex,
                readChapterIds = uiState.readChapterIds,
                onSelectChapter = { index ->
                    playHaptic(NovelNestHapticType.LIGHT_TICK)
                    viewModel.selectChapter(index)
                    coroutineScope.launch { drawerState.close() }
                },
                onCloseDrawer = {
                    coroutineScope.launch { drawerState.close() }
                },
                isOpen = drawerState.targetValue == DrawerValue.Open
            )
        }
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(NovelBackground)
        ) {
            when {
                // 1. Initial full-catalog loading
                uiState.isLoading && uiState.details == null -> {
                    ManhwaLoadingView(
                        title = "Loading manhwa...",
                        onBack = onBack
                    )
                }

                // 2. Error state when no content is present
                uiState.error != null && uiState.content == null -> {
                    ManhwaErrorView(
                        failure = uiState.error!!,
                        onRetry = { viewModel.retry() },
                        onRefreshCatalog = { viewModel.refreshCatalog(forceRefresh = true) },
                        onBack = onBack
                    )
                }

                // 3. Empty catalog
                uiState.isEmpty -> {
                    ManhwaEmptyView(
                        onRefresh = { viewModel.refreshCatalog(forceRefresh = true) },
                        onBack = onBack
                    )
                }

                // 4. Chapter content or loading chapter
                else -> {
                    val content = uiState.content
                    if (content != null) {
                        ManhwaContentView(
                            content = content,
                            imageStore = app.manhwaImageStore,
                            initialPageIndex = uiState.currentPageIndex,
                            initialOffsetFraction = uiState.pageOffsetFraction,
                            onSavePagePosition = { chapterId, page, fraction ->
                                viewModel.savePagePosition(chapterId, page, fraction)
                            },
                            onMarkChapterFinished = { chapterId ->
                                viewModel.markChapterFinished(chapterId)
                            },
                            onToggleControls = {
                                areControlsVisible = !areControlsVisible
                            },
                            canNext = uiState.canNext,
                            canPrevious = uiState.canPrevious,
                            onNextChapter = {
                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                viewModel.nextChapter()
                            },
                            onPreviousChapter = {
                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                viewModel.previousChapter()
                            },
                            onOpenChapters = {
                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                coroutineScope.launch { drawerState.open() }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Chapter transition loading overlay
                    if (uiState.isChapterLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(NovelBackground.copy(alpha = 0.75f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = NovelSurface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = NovelPrimary,
                                        strokeWidth = 2.5.dp
                                    )
                                    Text(
                                        text = "Loading chapter...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NovelTextPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Floating Top Bar
                    AnimatedVisibility(
                        visible = areControlsVisible,
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut() + slideOutVertically { -it },
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        ManhwaTopBar(
                            novelTitle = uiState.details?.novel?.title.orEmpty(),
                            chapterTitle = content?.chapter?.title
                                ?: uiState.details?.chapters?.getOrNull(uiState.currentChapterIndex)?.title.orEmpty(),
                            chapterNumber = content?.chapter?.number
                                ?: uiState.details?.chapters?.getOrNull(uiState.currentChapterIndex)?.number.orEmpty(),
                            isBookmarked = uiState.isBookmarked,
                            onToggleBookmark = {
                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                viewModel.toggleBookmark()
                            },
                            onBack = onBack
                        )
                    }

                    // Floating Bottom Navigation Bar
                    AnimatedVisibility(
                        visible = areControlsVisible,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        ManhwaBottomBar(
                            currentChapterNumber = content?.chapter?.number
                                ?: uiState.details?.chapters?.getOrNull(uiState.currentChapterIndex)?.number.orEmpty(),
                            currentPage = uiState.currentPageIndex + 1,
                            totalPageCount = content?.pages?.size ?: 0,
                            canPrevious = uiState.canPrevious,
                            canNext = uiState.canNext,
                            onPrevious = {
                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                viewModel.previousChapter()
                            },
                            onNext = {
                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                viewModel.nextChapter()
                            },
                            onOpenChapters = {
                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                coroutineScope.launch { drawerState.open() }
                            },
                            extraBottomPadding = narrationBottomPadding
                        )
                    }
                }
            }
        }
    }
}

/**
 * Vertical webtoon reader canvas:
 * - LazyColumn with stable keys.
 * - Restores scroll position on chapter layout ready.
 * - Debounces scroll position reporting on idle and disposal.
 * - Marks chapter finished ONLY when the final image is fully loaded and scrolled to the bottom.
 */
@Composable
private fun ManhwaContentView(
    content: com.novelnest.app.data.model.ManhwaChapterContent,
    imageStore: com.novelnest.app.data.manhwa.ManhwaImageStore,
    initialPageIndex: Int,
    initialOffsetFraction: Float,
    onSavePagePosition: (String, Int, Float) -> Unit,
    onMarkChapterFinished: (String, ) -> Unit,
    onToggleControls: () -> Unit,
    canNext: Boolean,
    canPrevious: Boolean,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onOpenChapters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var restoredChapterId by remember { mutableStateOf<String?>(null) }
    val loadedPages = remember(content.chapter.id) { mutableStateMapOf<Int, Boolean>() }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun savePosition() {
        if (restoredChapterId != content.chapter.id) return
        val index = listState.firstVisibleItemIndex
        val page = index.coerceAtMost(content.pages.lastIndex)
        if (loadedPages[page] != true) return
        val size = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
        val position = manhwaPagePosition(index, listState.firstVisibleItemScrollOffset, size, content.pages.size) ?: return
        onSavePagePosition(content.chapter.id, position.pageIndex, position.offsetFraction)
    }

    // 1. Restore scroll position keyed strictly to chapter identity
    LaunchedEffect(content.chapter.id) {
        if (restoredChapterId != content.chapter.id) {
            val targetPage = initialPageIndex.coerceIn(0, content.pages.lastIndex.coerceAtLeast(0))
            listState.scrollToItem(targetPage, 0)
            // A tall image's placeholder may have a different height. Wait for real layout,
            // or let a deliberate user scroll take over without jumping back afterward.
            val ready = snapshotFlow {
                val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetPage }
                val size = info?.size?.takeIf { loadedPages[targetPage] == true && it > 0 }
                size to (listState.isScrollInProgress || listState.firstVisibleItemIndex != targetPage)
            }.first { (size, userMoved) -> size != null || userMoved }
            if (!ready.second) {
                val fraction = initialOffsetFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
                listState.scrollToItem(targetPage, ((ready.first ?: 0) * fraction).roundToInt())
            }
            restoredChapterId = content.chapter.id
        }
    }

    // 2. Debounced savePagePosition reporting on scroll idle
    LaunchedEffect(listState, content.chapter.id) {
        val chapterId = content.chapter.id
        snapshotFlow {
            // Observe only the idle transition; no position stream or disk write per frame.
            !listState.isScrollInProgress && restoredChapterId == content.chapter.id
        }
        .distinctUntilChanged()
        .collect { idle ->
            if (idle && content.chapter.id == chapterId) savePosition()
        }
    }

    // 3. Home/lock does not dispose the composition. Flush before the process can be reclaimed.
    DisposableEffect(content.chapter.id, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) savePosition()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            savePosition()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 4. Mark chapter finished only after final image is loaded and its bottom is actually reached
    LaunchedEffect(listState, content.chapter.id) {
        val chapterId = content.chapter.id
        val lastPageIndex = content.pages.lastIndex
        if (lastPageIndex < 0) return@LaunchedEffect

        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val last = layoutInfo.visibleItemsInfo.firstOrNull { it.index == lastPageIndex }
            val footer = layoutInfo.visibleItemsInfo.firstOrNull { it.index == content.pages.size }
            loadedPages[lastPageIndex] == true && restoredChapterId == chapterId &&
                manhwaChapterEndReached(content.pages.size, listState.firstVisibleItemIndex,
                    last?.let { it.offset + it.size }, layoutInfo.viewportEndOffset,
                    footer != null && footer.offset < layoutInfo.viewportEndOffset)
        }
        .distinctUntilChanged()
        .collect { isBottomReached ->
            if (isBottomReached && content.chapter.id == chapterId) {
                onMarkChapterFinished(chapterId)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleControls
            ),
        contentPadding = PaddingValues(top = 0.dp, bottom = 120.dp)
    ) {
        items(
            items = content.pages,
            key = { page -> "${content.chapter.id}_${page.index}" }
        ) { page ->
            ManhwaTiledPageView(
                page = page,
                chapterUrl = content.chapter.url,
                imageStore = imageStore,
                onPageLoaded = { success ->
                    loadedPages[page.index] = success
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // End of Chapter Footer Card
        item(key = "footer_${content.chapter.id}") {
            ManhwaChapterEndFooter(
                chapterTitle = content.chapter.title,
                chapterNumber = content.chapter.number,
                canNext = canNext,
                canPrevious = canPrevious,
                onNext = onNextChapter,
                onPrevious = onPreviousChapter,
                onOpenChapters = onOpenChapters,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 32.dp)
            )
        }
    }
}

@Composable
private fun ManhwaChapterEndFooter(
    chapterTitle: String,
    chapterNumber: String,
    canNext: Boolean,
    canPrevious: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenChapters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NovelSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(NovelGold.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = NovelGold,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Finished Chapter $chapterNumber",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NovelTextPrimary
                )
                if (chapterTitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = chapterTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = NovelTextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }

            HorizontalDivider(color = NovelSurfaceBorder.copy(alpha = 0.5f))

            if (canNext) {
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NovelPrimary,
                        contentColor = NovelOnPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Next Chapter",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = NovelOnPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = NovelOnPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Text(
                    text = "You are all caught up to the latest chapter!",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = NovelGold,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (canPrevious) {
                    OutlinedButton(
                        onClick = onPrevious,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NovelTextPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Previous", fontSize = 13.sp)
                    }
                }
                OutlinedButton(
                    onClick = onOpenChapters,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NovelTextPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "All Chapters", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ManhwaTopBar(
    novelTitle: String,
    chapterTitle: String,
    chapterNumber: String,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        color = NovelSurface.copy(alpha = 0.95f),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = NovelSurfaceBorder.copy(alpha = 0.6f),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
            )
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Keep the same header height through system-bar and header exit animations.
                // Padding inside Surface paints its background all the way to the screen edge.
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NovelNestIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = onBack
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                if (novelTitle.isNotBlank()) {
                    Text(
                        text = novelTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = NovelTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (chapterNumber.isNotBlank()) "Chapter $chapterNumber ${if (chapterTitle.isNotBlank()) "• $chapterTitle" else ""}" else chapterTitle.ifBlank { "Manhwa Reader" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NovelTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedBookmarkButton(
                isBookmarked = isBookmarked,
                onToggle = { onToggleBookmark() }
            )
        }
    }
}

@Composable
private fun ManhwaBottomBar(
    currentChapterNumber: String,
    currentPage: Int,
    totalPageCount: Int,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenChapters: () -> Unit,
    extraBottomPadding: androidx.compose.ui.unit.Dp
) {
    Surface(
        color = NovelSurface.copy(alpha = 0.95f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = extraBottomPadding)
            .navigationBarsPadding()
            .border(
                width = 1.dp,
                color = NovelSurfaceBorder.copy(alpha = 0.6f),
                shape = RoundedCornerShape(24.dp)
            )
            .clip(RoundedCornerShape(24.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous chapter button
            IconButton(
                onClick = onPrevious,
                enabled = canPrevious
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Previous Chapter",
                    tint = if (canPrevious) NovelTextPrimary else NovelTextTertiary.copy(alpha = 0.4f)
                )
            }

            // Chapter list & page counter
            Surface(
                onClick = onOpenChapters,
                shape = RoundedCornerShape(12.dp),
                color = NovelSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = null,
                        tint = NovelGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (totalPageCount > 0) "Ch. $currentChapterNumber • $currentPage/$totalPageCount" else "Ch. $currentChapterNumber",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NovelTextPrimary
                    )
                }
            }

            // Next chapter button
            IconButton(
                onClick = onNext,
                enabled = canNext
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Next Chapter",
                    tint = if (canNext) NovelTextPrimary else NovelTextTertiary.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun ManhwaLoadingView(
    title: String,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NovelBackground)
    ) {
        NovelNestIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                color = NovelPrimary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = NovelTextSecondary
            )
        }
    }
}

@Composable
private fun ManhwaEmptyView(
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NovelBackground)
    ) {
        NovelNestIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoStories,
                contentDescription = null,
                tint = NovelTextTertiary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "No chapters found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NovelTextPrimary
            )
            Text(
                text = "This manhwa catalog is currently empty or unavailable.",
                style = MaterialTheme.typography.bodySmall,
                color = NovelTextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NovelPrimary,
                    contentColor = NovelOnPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Refresh Catalog", fontWeight = FontWeight.Bold, color = NovelOnPrimary)
            }
        }
    }
}

@Composable
private fun ManhwaErrorView(
    failure: ManhwaReaderFailure,
    onRetry: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onBack: () -> Unit
) {
    val (icon, title) = remember(failure.kind) {
        when (failure.kind) {
            ManhwaFailureKind.LOCKED -> Pair(Icons.Rounded.Lock, "Chapter is Locked")
            ManhwaFailureKind.ACCESS_REQUIRED -> Pair(Icons.Rounded.VpnKey, "Access Required")
            ManhwaFailureKind.RATE_LIMITED -> Pair(Icons.Rounded.HourglassTop, "Rate Limited")
            ManhwaFailureKind.BLOCKED -> Pair(Icons.Rounded.Shield, "Access Blocked")
            ManhwaFailureKind.NETWORK -> Pair(Icons.Rounded.WifiOff, "Connection Error")
            ManhwaFailureKind.NOT_FOUND -> Pair(Icons.Rounded.SearchOff, "Chapter Not Found")
            ManhwaFailureKind.FORMAT -> Pair(Icons.Rounded.ErrorOutline, "Format Error")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NovelBackground)
    ) {
        NovelNestIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = NovelSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            if (failure.kind == ManhwaFailureKind.LOCKED) NovelGold.copy(alpha = 0.15f)
                            else NovelPrimary.copy(alpha = 0.15f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (failure.kind == ManhwaFailureKind.LOCKED) NovelGold else NovelPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NovelTextPrimary
                )

                Text(
                    text = failure.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = NovelTextSecondary,
                    textAlign = TextAlign.Center
                )

                if (failure.unlockTime != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NovelSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "Unlocks: ${failure.unlockTime}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NovelGold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRefreshCatalog,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NovelSurfaceBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text(text = "Catalog", fontSize = 13.sp, color = NovelTextPrimary)
                    }

                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NovelPrimary,
                            contentColor = NovelOnPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text(text = "Retry", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NovelOnPrimary)
                    }
                }
            }
        }
    }
}
