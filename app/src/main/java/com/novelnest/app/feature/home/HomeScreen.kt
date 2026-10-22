package com.novelnest.app.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import com.novelnest.app.core.ui.footer.rememberFooterBottomPadding
import com.novelnest.app.core.ui.header.NovelNestHeader
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.progress.readingCompletion

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSearch: (NovelSourceType) -> Unit,
    onNovelClick: (Novel) -> Unit,
    onNavigateToDiscover: (NovelSourceType) -> Unit,
    onContinueReading: (Novel, Int) -> Unit = { novel, _ -> onNovelClick(novel) },
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val gridStates = remember { NovelSourceType.entries.associateWith { LazyGridState() } }
    var selected by remember { mutableStateOf<Novel?>(null) }
    val shownHistory = remember { mutableSetOf<NovelSourceType>() }
    LaunchedEffect(state.selectedSource, state.currentReading != null) {
        if (state.currentReading != null && shownHistory.add(state.selectedSource)) {
            gridStates.getValue(state.selectedSource).scrollToItem(0)
        }
    }
    LaunchedEffect(state.selectedSource, state.bookmarkedNovels) {
        if (state.visibleBookmarks.none { it.id == selected?.id }) selected = null
    }
    BackHandler(enabled = selected != null) { selected = null }
    Column(modifier.fillMaxSize().background(NovelBackground)) {
        NovelNestHeader(title = if (selected != null) "Remove Bookmark" else "My Library",
            icon = Icons.Rounded.Bookmark,
            actions = {
                if (selected != null) {
                    NovelNestIconButton(Icons.Rounded.DeleteOutline, "Remove bookmark", onClick = {
                        selected?.let { viewModel.removeBookmark(it.id) }
                        selected = null
                    })
                    NovelNestIconButton(Icons.Rounded.Close, "Cancel selection", onClick = { selected = null })
                } else {
                    NovelNestIconButton(Icons.Rounded.Search, "Search novels", onClick = { onNavigateToSearch(state.selectedSource) })
                    NovelNestIconButton(Icons.Rounded.NotificationsNone, "Notifications", onClick = onNavigateToNotifications)
                    NovelNestIconButton(Icons.Rounded.PersonOutline, "Profile and settings", onClick = onNavigateToSettings)
                }
            })
        SourceTabRow(selectedSource = state.selectedSource, onSourceSelected = viewModel::selectSource)
        SearchBarTrigger(currentSource = state.selectedSource, onClick = { onNavigateToSearch(state.selectedSource) })
        val bottomPadding = rememberFooterBottomPadding()
        LazyVerticalGrid(columns = GridCells.Adaptive(128.dp),
            state = gridStates.getValue(state.selectedSource),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.currentReading?.let { current ->
                item(key = "current_reading", span = { GridItemSpan(maxLineSpan) }, contentType = "current") {
                    HomeContinueReadingCard(
                        entry = current,
                        onOpenDetails = { onNovelClick(current.novel) },
                        onContinue = { onContinueReading(current.novel, current.progress.lastReadChapterIndex) }
                    )
                }
            }
            if (state.recentlyRead.isNotEmpty()) {
                item(key = "recent_reading", span = { GridItemSpan(maxLineSpan) }, contentType = "recent") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeSectionTitle("Recently Read", "${state.recentlyRead.size} novels")
                        key(state.selectedSource) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(state.recentlyRead, key = { it.novel.id }) { entry ->
                                    val completion = remember(entry) { entry.novel.readingCompletion(entry.progress) }
                                    NovelCard(entry.novel, readingCompletion = completion,
                                        onClick = { onNovelClick(entry.novel) })
                                }
                            }
                        }
                    }
                }
            }
            item(key = "bookmarks_heading", span = { GridItemSpan(maxLineSpan) }, contentType = "heading") {
                HomeSectionTitle("Bookmarks", "${state.visibleBookmarks.size} saved · Recent reads first")
            }
            if (state.visibleBookmarks.isEmpty()) {
                item(key = "bookmarks_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Bookmark, null, tint = NovelPrimary, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No saved novels from ${state.selectedSource.shortName}", color = NovelTextPrimary,
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Bookmark a novel to keep it here.", color = NovelTextSecondary)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { onNavigateToDiscover(state.selectedSource) }) { Text("Discover novels") }
                    }
                }
            }
            items(state.visibleBookmarks, key = { "bookmark:${it.id}" }, contentType = { "bookmark" }) { novel ->
                val progress = state.progressFor(novel)
                val completion = remember(novel, progress) { novel.readingCompletion(progress) }
                NovelCard(novel, readingCompletion = completion, modifier = Modifier.fillMaxWidth(),
                    isSelected = selected?.id == novel.id,
                    onLongClick = { selected = if (selected?.id == novel.id) null else novel },
                    onClick = { if (selected != null) selected = null else onNovelClick(novel) })
            }
        }
    }
}

@Composable
internal fun HomeSectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = NovelTextPrimary)
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = NovelTextSecondary)
    }
}
