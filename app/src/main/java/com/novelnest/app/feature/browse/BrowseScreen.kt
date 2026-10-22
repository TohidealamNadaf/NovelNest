package com.novelnest.app.feature.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.NovelCard
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.SearchBarTrigger
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
    Column(modifier.fillMaxSize().background(NovelBackground)) {
        NovelNestHeader(title = "Browse", navigationIcon = {
            NovelNestIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to Discover", onClick = onBack)
        })
        SourceTabRow(selectedSource = state.selectedSource, onSourceSelected = viewModel::selectSource)
        SearchBarTrigger(currentSource = state.selectedSource, onClick = { onNavigateToSearch(state.selectedSource) })
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.genres, key = { it }) { genre ->
                FilterChip(selected = state.selectedGenre == genre, onClick = { viewModel.selectGenre(genre) },
                    label = { Text(genre) }, modifier = Modifier.heightIn(min = 48.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NovelPrimary, selectedLabelColor = NovelOnPrimary))
            }
        }
        HorizontalDivider(color = NovelSurfaceBorder)
        when {
            state.isLoading && state.novels.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NovelPrimary)
            }
            state.errorMessage != null && state.novels.isEmpty() -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.errorMessage.orEmpty(), color = NovelTextSecondary)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.load(forceRefresh = true) }) { Text("Retry") }
            }
            state.filteredNovels.isEmpty() -> Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No novels available in this category.", color = NovelTextSecondary)
                if (state.selectedGenre != "All") TextButton(onClick = { viewModel.selectGenre("All") }) { Text("Show all") }
            }
            else -> {
                val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                LazyVerticalGrid(columns = GridCells.Adaptive(128.dp), state = gridStates.getValue(state.selectedSource),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (state.errorMessage != null) item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text(state.errorMessage.orEmpty(), color = NovelTextSecondary)
                            TextButton(onClick = { if (state.paginationFailed) viewModel.loadMore() else viewModel.load(true) }) { Text("Retry") }
                        }
                    }
                    items(state.filteredNovels, key = { it.id }) { novel ->
                        NovelCard(novel, Modifier.fillMaxWidth(), onClick = { onNovelClick(novel) })
                    }
                    if (state.nextPageUrl != null) item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (state.isLoadingMore) CircularProgressIndicator(color = NovelPrimary)
                            else TextButton(onClick = viewModel::loadMore) { Text("Load more") }
                        }
                    }
                }
            }
        }
    }
}
