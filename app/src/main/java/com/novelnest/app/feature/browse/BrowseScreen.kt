package com.novelnest.app.feature.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.NovelCard
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.SearchBarTrigger
import com.novelnest.app.core.ui.sourceSwipeNavigation
import com.novelnest.app.core.ui.SourceTabRow
import com.novelnest.app.core.ui.header.NovelNestHeader
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType

@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    onBack: () -> Unit,
    onNavigateToSearch: (NovelSourceType) -> Unit,
    onNovelClick: (Novel) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val gridStates = remember { NovelSourceType.entries.associateWith { LazyGridState() } }
    LaunchedEffect(state.selectedSource, state.selectedGenre) {
        gridStates.getValue(state.selectedSource).scrollToItem(0)
    }

    val currentGridState = gridStates.getValue(state.selectedSource)
    val isScrolled by remember(currentGridState) {
        derivedStateOf {
            currentGridState.firstVisibleItemIndex > 0 || currentGridState.firstVisibleItemScrollOffset > 24
        }
    }

    Column(modifier.fillMaxSize().background(NovelBackground)) {
        NovelNestHeader(
            title = "Browse",
            subtitle = state.selectedSource.displayName,
            navigationIcon = {
                NovelNestIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to Discover", onClick = onBack)
            },
            actions = {
                // Search icon appears in header when search bar is collapsed on scroll
                AnimatedVisibility(
                    visible = isScrolled,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    NovelNestIconButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = "Search ${state.selectedSource.displayName}",
                        tint = NovelPrimary,
                        onClick = { onNavigateToSearch(state.selectedSource) }
                    )
                }
            }
        )

        // Collapsible Source Tabs and Search Bar: hide and blend away when scrolled
        AnimatedVisibility(
            visible = !isScrolled,
            enter = expandVertically(animationSpec = tween(240)) + fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(animationSpec = tween(240)) + fadeOut(animationSpec = tween(160))
        ) {
            Column {
                SourceTabRow(selectedSource = state.selectedSource, onSourceSelected = viewModel::selectSource)
                SearchBarTrigger(currentSource = state.selectedSource, onClick = { onNavigateToSearch(state.selectedSource) })
            }
        }

        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = currentGridState,
            modifier = Modifier.weight(1f).fillMaxWidth()
                .sourceSwipeNavigation(state.selectedSource, viewModel::selectSource),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 14.dp, bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "categories", span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Category filter pill carousel
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.genres, key = { it }) { genre ->
                            val isSelected = state.selectedGenre == genre
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) NovelPrimary else NovelSurfaceVariant,
                                border = if (isSelected) null else BorderStroke(1.dp, NovelSurfaceBorder.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { viewModel.selectGenre(genre) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(NovelOnPrimary)
                                        )
                                    }
                                    Text(
                                        text = genre,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) NovelOnPrimary else NovelTextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // Category header and count indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(NovelPrimary)
                            )
                            Text(
                                text = if (state.selectedGenre == "All") "All Novels" else state.selectedGenre,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = NovelTextPrimary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NovelBadgeBackground,
                            border = BorderStroke(1.dp, NovelPrimary.copy(alpha = 0.2f))
                        ) {
                            Text(
                                text = "${state.filteredNovels.size} novels",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = NovelPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            when {
                state.isLoading && state.novels.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp,
                                color = NovelPrimary
                            )
                            Text(
                                "Exploring ${state.selectedSource.displayName}...",
                                style = MaterialTheme.typography.bodySmall,
                                color = NovelTextSecondary
                            )
                        }
                    }
                }
                state.errorMessage != null && state.novels.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = NovelSurface,
                        border = BorderStroke(1.dp, NovelSurfaceBorder),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(54.dp).clip(CircleShape).background(NovelSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = NovelTertiary, modifier = Modifier.size(28.dp))
                            }
                            Text(
                                "Failed to Load Novels",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = NovelTextPrimary
                            )
                            Text(
                                state.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = NovelTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { viewModel.load(forceRefresh = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = NovelPrimary, contentColor = NovelOnPrimary),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                            ) {
                                Text("Try Again", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                else -> {
                    if (state.errorMessage != null) item(span = { GridItemSpan(maxLineSpan) }) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = NovelSurfaceVariant,
                            border = BorderStroke(1.dp, NovelSurfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = NovelTertiary, modifier = Modifier.size(20.dp))
                                Text(
                                    text = state.errorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NovelTextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { if (state.paginationFailed) viewModel.loadMore() else viewModel.load(forceRefresh = true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NovelPrimary, contentColor = NovelOnPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Retry", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                    if (state.filteredNovels.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = NovelSurface,
                            border = BorderStroke(1.dp, NovelSurfaceBorder),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(54.dp).clip(CircleShape).background(NovelSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = NovelPrimary, modifier = Modifier.size(28.dp))
                                }
                                Text(
                                    "No Novels in this Category",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = NovelTextPrimary
                                )
                                Text(
                                    "No novels matched the current selection. Try switching category or source.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NovelTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                if (state.selectedGenre != "All") {
                                    Button(
                                        onClick = { viewModel.selectGenre("All") },
                                        colors = ButtonDefaults.buttonColors(containerColor = NovelPrimary, contentColor = NovelOnPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                                    ) {
                                        Text("Show All Novels", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    items(state.filteredNovels, key = { it.id }, contentType = { "novel" }) { novel ->
                        NovelCard(novel, Modifier.fillMaxWidth(), isCompact = true, onClick = { onNovelClick(novel) })
                    }
                    if (state.nextPageUrl != null) item(span = { GridItemSpan(maxLineSpan) }) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = NovelSurface,
                            border = BorderStroke(1.dp, NovelSurfaceBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = !state.isLoadingMore) { viewModel.loadMore() }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 14.dp, horizontal = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.isLoadingMore) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = NovelPrimary
                                        )
                                        Text(
                                            "Loading more novels...",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = NovelTextSecondary
                                        )
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.ExpandMore,
                                            contentDescription = null,
                                            tint = NovelPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Load More Novels",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = NovelTextPrimary
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
