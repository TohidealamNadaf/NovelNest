package com.novelnest.app.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val areAnimationsDisabled = rememberAreAnimationsDisabled()

    // Independent scroll position per source so state is preserved when switching back
    val listStates = remember {
        NovelSourceType.entries.associateWith { LazyListState() }
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
        // Reusable App Header
        NovelNestHeader(
            title = "NovelNest",
            icon = Icons.Rounded.AutoStories,
            actions = {
                NovelNestIconButton(
                    icon = Icons.Rounded.BookmarkBorder,
                    contentDescription = "Bookmarks",
                    tint = NovelTextSecondary,
                    onClick = {}
                )
            }
        )

        // Source Tabs (NovelPhoenix, FWN, Asura Scans)
        SourceTabRow(
            selectedSource = uiState.selectedSource,
            onSourceSelected = { viewModel.selectSource(it) }
        )

        // Animated Search Trigger (transitions to Search Screen)
        SearchBarTrigger(
            currentSource = uiState.selectedSource,
            onClick = { onNavigateToSearch(uiState.selectedSource) }
        )

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
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = NovelPrimary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading ${source.displayName}...",
                                color = NovelTextSecondary,
                                fontSize = 14.sp
                            )
                        }
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
                            contentPadding = PaddingValues(bottom = 80.dp),
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
