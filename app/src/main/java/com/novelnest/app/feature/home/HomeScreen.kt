package com.novelnest.app.feature.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.novelnest.app.core.designsystem.NovelBackground
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.HeroCarousel
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.NovelSectionRow
import com.novelnest.app.core.ui.SearchBarTrigger
import com.novelnest.app.core.ui.SourceTabRow
import com.novelnest.app.core.ui.animation.rememberAreAnimationsDisabled
import com.novelnest.app.core.ui.animation.shimmerPlaceholder
import com.novelnest.app.core.ui.footer.rememberFooterBottomPadding
import com.novelnest.app.core.ui.header.NovelNestHeader
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.model.SectionType

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSearch: (NovelSourceType) -> Unit,
    onNovelClick: (Novel) -> Unit,
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val areAnimationsDisabled = rememberAreAnimationsDisabled()

    // Independent scroll position per source so state is preserved when switching back
    val listStates = remember {
        NovelSourceType.entries.associateWith { LazyListState() }
    }

    // Active scroll state for current source
    val currentSource = uiState.selectedSource
    val currentListState = listStates[currentSource] ?: rememberLazyListState()

    // Dynamic scroll collapse detection
    val isScrolled by remember(currentSource, currentListState) {
        derivedStateOf {
            currentListState.firstVisibleItemIndex > 0 || currentListState.firstVisibleItemScrollOffset > 30
        }
    }

    // Retain sections per source during animated tab switching
    var cachedSectionsBySource by remember {
        mutableStateOf(mapOf<NovelSourceType, List<NovelSection>>())
    }

    LaunchedEffect(uiState.selectedSource, uiState.sections) {
        if (uiState.sections.isNotEmpty()) {
            cachedSectionsBySource = cachedSectionsBySource + (uiState.selectedSource to uiState.sections)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NovelBackground)
    ) {
        // App Header: Title + Collapsed Search Icon + Notification Icon + Profile Icon
        NovelNestHeader(
            title = "NovelNest",
            icon = Icons.Rounded.AutoStories,
            actions = {
                // Collapsed search icon appears smoothly in top row when scrolled
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

                // Notification Icon -> navigates to notifications page
                NovelNestIconButton(
                    icon = Icons.Rounded.NotificationsNone,
                    contentDescription = "Notifications",
                    onClick = onNavigateToNotifications
                )

                // Profile Icon -> navigates to settings/profile page
                NovelNestIconButton(
                    icon = Icons.Rounded.PersonOutline,
                    contentDescription = "Profile & Settings",
                    onClick = onNavigateToSettings
                )
            }
        )

        // Source Tabs + Search Field: animates out on scroll, transitioning into the header's search icon
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

        Spacer(modifier = Modifier.height(4.dp))

        // Content Area with animated source transitions and preserved list states
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
                label = "home_source_content_transition"
            ) { source ->
                val sourceSections = cachedSectionsBySource[source]
                    ?: if (source == uiState.selectedSource) uiState.sections else emptyList()
                val isCurrentSource = source == uiState.selectedSource
                val sourceListState = listStates[source] ?: rememberLazyListState()

                when {
                    isCurrentSource && uiState.isLoading && sourceSections.isEmpty() -> {
                        HomeShimmerFeed(sourceName = source.displayName)
                    }

                    isCurrentSource && uiState.errorMessage != null && sourceSections.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Unable to connect to ${source.displayName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = NovelTextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.errorMessage ?: "Network error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NovelTextSecondary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.refresh() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NovelPrimary,
                                    contentColor = NovelOnPrimary
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Retry")
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            state = sourceListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = rememberFooterBottomPadding()),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            items(sourceSections, key = { it.id }) { section ->
                                if (section.type == SectionType.HERO_SLIDER) {
                                    HeroCarousel(
                                        novels = section.novels,
                                        onNovelClick = onNovelClick
                                    )
                                } else {
                                    NovelSectionRow(
                                        section = section,
                                        onNovelClick = onNovelClick
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

@Composable
private fun HomeShimmerFeed(sourceName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Hero placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .padding(horizontal = 16.dp)
                .shimmerPlaceholder(visible = true, shape = RoundedCornerShape(16.dp))
        )

        // Section row placeholder
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(20.dp)
                    .shimmerPlaceholder(visible = true, shape = RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(3) {
                    Column(modifier = Modifier.width(135.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .shimmerPlaceholder(visible = true, shape = RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(14.dp)
                                .shimmerPlaceholder(visible = true, shape = RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}
