package com.novelnest.app.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.HeroCarousel
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.NovelSectionRow
import com.novelnest.app.core.ui.SearchBarTrigger
import com.novelnest.app.core.ui.sourceSwipeNavigation
import com.novelnest.app.core.ui.SourceTabRow
import com.novelnest.app.core.ui.footer.rememberFooterBottomPadding
import com.novelnest.app.core.ui.header.NovelNestHeader
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.model.SectionType

@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    onNavigateToSearch: (NovelSourceType) -> Unit,
    onNovelClick: (Novel) -> Unit,
    onBrowse: (NovelSourceType) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.setRatingActive(true)
        onStopOrDispose { viewModel.setRatingActive(false) }
    }
    val listStates = remember { NovelSourceType.entries.associateWith { LazyListState() } }
    val currentListState = listStates.getValue(state.selectedSource)
    val visibleSections by remember(currentListState) {
        derivedStateOf { currentListState.layoutInfo.visibleItemsInfo.map { it.key }.toSet() }
    }
    val isScrolled by remember(currentListState) {
        derivedStateOf {
            currentListState.firstVisibleItemIndex > 0 || currentListState.firstVisibleItemScrollOffset > 24
        }
    }

    val refreshRotation = remember { Animatable(0f) }
    var refreshSpinTrigger by remember { mutableIntStateOf(0) }
    var hasPendingSpin by remember { mutableStateOf(false) }

    LaunchedEffect(state.isRefreshing, refreshSpinTrigger) {
        if (state.isRefreshing) {
            hasPendingSpin = true
            while (isActive) {
                refreshRotation.animateTo(
                    targetValue = refreshRotation.value + 360f,
                    animationSpec = tween(durationMillis = 650, easing = LinearEasing)
                )
            }
        } else if (hasPendingSpin || refreshRotation.value > 0f) {
            val currentMod = refreshRotation.value % 360f
            val remaining = when {
                refreshRotation.value < 15f -> 360f - currentMod
                currentMod > 0.5f -> 360f - currentMod
                else -> 0f
            }
            if (remaining > 0f) {
                val duration = ((remaining / 360f) * 520f).toInt().coerceIn(220, 520)
                refreshRotation.animateTo(
                    targetValue = refreshRotation.value + remaining,
                    animationSpec = tween(
                        durationMillis = duration,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            refreshRotation.snapTo(0f)
            hasPendingSpin = false
        }
    }

    Column(modifier.fillMaxSize().background(NovelBackground)) {
        NovelNestHeader(
            title = "Novel Nest",
            subtitle = "Discover",
            icon = Icons.Rounded.AutoStories,
            actions = {
                // Search icon appears in header when search bar is collapsed on scroll
                androidx.compose.animation.AnimatedVisibility(
                    visible = isScrolled,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.8f)
                ) {
                    com.novelnest.app.core.ui.NovelNestIconButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = "Search ${state.selectedSource.displayName}",
                        tint = NovelPrimary,
                        onClick = { onNavigateToSearch(state.selectedSource) }
                    )
                }
                NovelNestIconButton(
                    icon = Icons.Rounded.Explore,
                    contentDescription = "Browse all novels",
                    tint = NovelPrimary,
                    onClick = { onBrowse(state.selectedSource) }
                )
                NovelNestIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Refresh discoveries",
                    tint = NovelPrimary,
                    enabled = !state.isRefreshing && !hasPendingSpin,
                    iconModifier = Modifier.graphicsLayer {
                        rotationZ = refreshRotation.value
                    },
                    onClick = {
                        hasPendingSpin = true
                        refreshSpinTrigger++
                        viewModel.refresh()
                    }
                )
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
        Box(Modifier.weight(1f).fillMaxWidth()
            .sourceSwipeNavigation(state.selectedSource, viewModel::selectSource)) {
            when {
                state.isLoading && state.sections.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NovelPrimary)
                }
                state.sections.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.errorMessage ?: "No novels available yet.", color = NovelTextSecondary)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::refresh) { Text("Retry") }
                }
                else -> {
                    val bottomPadding = rememberFooterBottomPadding()
                    LazyColumn(state = listStates.getValue(state.selectedSource),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        if (state.errorMessage != null) item {
                            Text(state.errorMessage.orEmpty(), color = NovelTextSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp))
                        }
                        items(state.sections, key = { it.id }) { section ->
                            if (section.type == SectionType.HERO_SLIDER) {
                                HeroCarousel(novels = section.novels, onNovelClick = onNovelClick,
                                    onNovelVisibility = viewModel::ratingVisibility,
                                    isVisible = section.id in visibleSections,
                                    isParentScrolling = currentListState.isScrollInProgress)
                            } else {
                                NovelSectionRow(section = section, onNovelClick = onNovelClick,
                                    onSeeAllClick = { onBrowse(state.selectedSource) },
                                    onNovelVisibility = viewModel::ratingVisibility,
                                    isSectionVisible = section.id in visibleSections)
                            }
                        }
                    }
                }
            }
        }
    }
}
