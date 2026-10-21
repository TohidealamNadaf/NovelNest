package com.novelnest.app.feature.discover

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelBackground
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.NovelCard
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.SearchBarTrigger
import com.novelnest.app.core.ui.SourceTabRow
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.core.ui.animation.rememberAreAnimationsDisabled
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic
import com.novelnest.app.core.ui.animation.shimmerPlaceholder
import com.novelnest.app.core.ui.footer.rememberFooterBottomPadding
import com.novelnest.app.core.ui.header.NovelNestHeader
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType

@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    onNavigateToSearch: (NovelSourceType) -> Unit,
    onNovelClick: (Novel) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val areAnimationsDisabled = rememberAreAnimationsDisabled()
    val playHaptic = rememberNovelNestHaptic()

    // Independent scroll position per source for discovering novels
    val gridStates = remember {
        NovelSourceType.entries.associateWith { LazyGridState() }
    }

    // Active scroll state for current source
    val currentSource = uiState.selectedSource
    val currentGridState = gridStates[currentSource] ?: rememberLazyGridState()

    // Dynamic scroll collapse detection
    val isScrolled by remember(currentSource, currentGridState) {
        derivedStateOf {
            currentGridState.firstVisibleItemIndex > 0 || currentGridState.firstVisibleItemScrollOffset > 30
        }
    }

    // Cache novels per source to preserve state and avoid jumps when switching back
    var cachedNovelsBySource by remember {
        mutableStateOf(mapOf<NovelSourceType, List<Novel>>())
    }

    LaunchedEffect(uiState.selectedSource, uiState.novels) {
        if (uiState.novels.isNotEmpty()) {
            cachedNovelsBySource = cachedNovelsBySource + (uiState.selectedSource to uiState.novels)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NovelBackground)
    ) {
        // Top Header with animated Search icon appearing on scroll
        NovelNestHeader(
            title = "Discover",
            icon = Icons.Rounded.Explore,
            actions = {
                AnimatedVisibility(
                    visible = isScrolled,
                    enter = fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.8f),
                    exit = fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.8f)
                ) {
                    NovelNestIconButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = "Search Novels",
                        onClick = { onNavigateToSearch(uiState.selectedSource) }
                    )
                }
            }
        )

        // Source Tabs + Search Field: animates out on scroll into the header search icon
        AnimatedVisibility(
            visible = !isScrolled,
            enter = expandVertically(animationSpec = tween(260)) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(260)) + fadeOut(animationSpec = tween(200))
        ) {
            Column {
                SourceTabRow(
                    selectedSource = uiState.selectedSource,
                    onSourceSelected = { viewModel.selectSource(it) }
                )

                SearchBarTrigger(
                    currentSource = uiState.selectedSource,
                    onClick = { onNavigateToSearch(uiState.selectedSource) }
                )
            }
        }

        // Pinned Solid Categories Container (will NOT blend with scrolling grid content)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NovelBackground)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(uiState.genres) { genre ->
                    val isSelected = genre == uiState.selectedGenre
                    Box(
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) NovelPrimary else NovelSurface)
                            .border(
                                1.dp,
                                if (isSelected) NovelPrimary else NovelSurfaceBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .bounceClick(
                                scaleDown = BounceScale.Small,
                                onClickLabel = "Filter by $genre",
                                role = Role.Tab
                            ) {
                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                viewModel.selectGenre(genre)
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = genre,
                            color = if (isSelected) NovelOnPrimary else NovelTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Divider establishing clear boundary between category chips and scrolling novels
            HorizontalDivider(
                color = NovelSurfaceBorder.copy(alpha = 0.5f),
                thickness = 1.dp
            )
        }

        // Grid Content Area with animated transitions
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            AnimatedContent(
                targetState = uiState.selectedSource,
                transitionSpec = {
                    if (areAnimationsDisabled) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        val isForward = targetState.ordinal > initialState.ordinal
                        val slideOffset = { width: Int -> (width * 0.15f).toInt() }
                        (slideInHorizontally(
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            initialOffsetX = { if (isForward) slideOffset(it) else -slideOffset(it) }
                        ) + fadeIn(animationSpec = tween(220))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            targetOffsetX = { if (isForward) -slideOffset(it) else slideOffset(it) }
                        ) + fadeOut(animationSpec = tween(220)))
                    }
                },
                label = "discover_source_grid_transition"
            ) { source ->
                val sourceNovels = cachedNovelsBySource[source]
                    ?: if (source == uiState.selectedSource) uiState.novels else emptyList()
                val isCurrentSource = source == uiState.selectedSource
                val sourceGridState = gridStates[source] ?: rememberLazyGridState()

                val filteredNovels = if (uiState.selectedGenre == "All") {
                    sourceNovels
                } else {
                    sourceNovels.filter { novel ->
                        novel.genres.any { it.equals(uiState.selectedGenre, ignoreCase = true) }
                    }
                }

                if (isCurrentSource && uiState.isLoading && sourceNovels.isEmpty()) {
                    DiscoverShimmerGrid()
                } else {
                    LazyVerticalGrid(
                        state = sourceGridState,
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = rememberFooterBottomPadding()
                        ),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredNovels, key = { it.id }) { novel ->
                            NovelCard(
                                novel = novel,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    playHaptic(NovelNestHapticType.ACTION_CONFIRM)
                                    onNovelClick(novel)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverShimmerGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = rememberFooterBottomPadding()
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(6) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .shimmerPlaceholder(visible = true, shape = RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(16.dp)
                        .shimmerPlaceholder(visible = true, shape = RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .shimmerPlaceholder(visible = true, shape = RoundedCornerShape(4.dp))
                )
            }
        }
    }
}
