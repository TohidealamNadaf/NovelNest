package com.novelnest.app.feature.discover

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.NovelCard
import com.novelnest.app.core.ui.SearchBarTrigger
import com.novelnest.app.core.ui.SourceTabRow
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.core.ui.animation.rememberAreAnimationsDisabled
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

    // Independent scroll position per source for discovering novels
    val gridStates = remember {
        NovelSourceType.entries.associateWith { LazyGridState() }
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
        // Reusable Header
        NovelNestHeader(
            title = "Discover",
            icon = Icons.Rounded.Explore
        )

        // Source Tab Row (NovelPhoenix, FWN, Asura Scans)
        SourceTabRow(
            selectedSource = uiState.selectedSource,
            onSourceSelected = { viewModel.selectSource(it) }
        )

        // Search Trigger
        SearchBarTrigger(
            currentSource = uiState.selectedSource,
            onClick = { onNavigateToSearch(uiState.selectedSource) }
        )

        // Genre Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.genres) { genre ->
                val isSelected = genre == uiState.selectedGenre
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) NovelPrimary else NovelSurfaceVariant)
                        .border(
                            1.dp,
                            if (isSelected) NovelPrimary else NovelSurfaceBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .bounceClick(
                            scaleDown = BounceScale.Small,
                            onClickLabel = "Filter by $genre",
                            role = Role.Tab
                        ) { viewModel.selectGenre(genre) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = genre,
                        color = if (isSelected) NovelOnPrimary else NovelTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
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
                    }
                } else {
                    LazyVerticalGrid(
                        state = sourceGridState,
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = com.novelnest.app.core.ui.footer.rememberFooterBottomPadding()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredNovels, key = { it.id }) { novel ->
                            NovelCard(
                                novel = novel,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onNovelClick(novel) }
                            )
                        }
                    }
                }
            }
        }
    }
}
