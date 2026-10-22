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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelnest.app.NovelNestApplication
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
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val app = LocalContext.current.applicationContext as NovelNestApplication
    val profile by app.profileManager.profile.collectAsStateWithLifecycle()
    val gridStates = remember { NovelSourceType.entries.associateWith { LazyGridState() } }
    var selected by remember { mutableStateOf<Novel?>(null) }
    val shownHistory = remember { mutableSetOf<NovelSourceType>() }
    LaunchedEffect(state.selectedSource, state.currentReading != null) {
        if (state.currentReading != null && shownHistory.add(state.selectedSource)) {
            gridStates.getValue(state.selectedSource).scrollToItem(0)
        }
    }
    LaunchedEffect(state.selectedSource, state.bookmarkedNovels, state.recentlyRead) {
        if (state.visibleBookmarks.none { it.id == selected?.id } &&
            state.recentlyRead.none { it.novel.id == selected?.id }) selected = null
    }
    val onSelectNovel: (Novel) -> Unit = { novel ->
        selected = if (selected?.id == novel.id) null else novel
    }
    val onOpenNovel: (Novel) -> Unit = { novel ->
        if (selected != null) selected = null else onNovelClick(novel)
    }
    val currentGridState = gridStates.getValue(state.selectedSource)
    val isScrolled by remember(currentGridState) {
        derivedStateOf {
            currentGridState.firstVisibleItemIndex > 0 || currentGridState.firstVisibleItemScrollOffset > 24
        }
    }

    BackHandler(enabled = selected != null) { selected = null }
    Column(modifier.fillMaxSize().background(NovelBackground)) {
        NovelNestHeader(
            title = if (selected != null) "Remove Bookmark" else "Novel Nest",
            icon = Icons.Rounded.AutoStories,
            actions = {
                if (selected != null) {
                    NovelNestIconButton(Icons.Rounded.DeleteOutline, "Remove bookmark", onClick = {
                        selected?.let { viewModel.removeBookmark(it.id) }
                        selected = null
                    })
                    NovelNestIconButton(Icons.Rounded.Close, "Cancel selection", onClick = { selected = null })
                } else {
                    // Search icon appears in header when search bar is collapsed on scroll
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isScrolled,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f),
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.8f)
                    ) {
                        NovelNestIconButton(
                            icon = Icons.Rounded.Search,
                            contentDescription = "Search ${state.selectedSource.displayName}",
                            tint = NovelPrimary,
                            onClick = { onNavigateToSearch(state.selectedSource) }
                        )
                    }
                    NovelNestIconButton(Icons.Rounded.NotificationsNone, "Notifications", onClick = onNavigateToNotifications)
                    NovelNestIconButton(Icons.Rounded.PersonOutline, "Profile", onClick = onNavigateToProfile,
                        imageUri = profile.photoUri)
                }
            }
        )

        // Collapsible Source Tabs and Search Bar: hide and blend away when scrolled
        androidx.compose.animation.AnimatedVisibility(
            visible = !isScrolled,
            enter = androidx.compose.animation.expandVertically(animationSpec = androidx.compose.animation.core.tween(240)) +
                    androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(180)),
            exit = androidx.compose.animation.shrinkVertically(animationSpec = androidx.compose.animation.core.tween(240)) +
                    androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(160))
        ) {
            Column {
                SourceTabRow(selectedSource = state.selectedSource, onSourceSelected = viewModel::selectSource)
                SearchBarTrigger(currentSource = state.selectedSource, onClick = { onNavigateToSearch(state.selectedSource) })
            }
        }
        val bottomPadding = rememberFooterBottomPadding()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cardWidth = (maxWidth - 32.dp - 24.dp) / 3
            LazyVerticalGrid(columns = GridCells.Fixed(3),
                state = gridStates.getValue(state.selectedSource),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(state.recentlyRead, key = { it.novel.id }) { entry ->
                                        val completion = remember(entry) { entry.novel.readingCompletion(entry.progress) }
                                        NovelCard(entry.novel, modifier = Modifier.width(cardWidth), readingCompletion = completion,
                                            isSelected = selected?.id == entry.novel.id,
                                            onLongClick = { onSelectNovel(entry.novel) },
                                            onClick = { onOpenNovel(entry.novel) })
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
                        onLongClick = { onSelectNovel(novel) },
                        onClick = { onOpenNovel(novel) })
                }
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
