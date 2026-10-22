package com.novelnest.app.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novelnest.app.core.designsystem.NovelBackground
import com.novelnest.app.core.designsystem.NovelBadgeBackground
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.designsystem.NovelTextTertiary
import com.novelnest.app.core.ui.NovelCard
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.SourceTabRow
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic
import com.novelnest.app.core.ui.animation.shimmerPlaceholder
import com.novelnest.app.data.model.Novel
import kotlinx.coroutines.delay

private val POPULAR_GENRES = listOf(
    "Action",
    "Fantasy",
    "Cultivation",
    "Adventure",
    "Romance",
    "Mystery",
    "Sci-Fi",
    "Comedy"
)

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onNovelClick: (Novel) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val playHaptic = rememberNovelNestHaptic()

    // Smooth Back Handling: first back dismisses query/keyboard, second back exits screen
    BackHandler {
        if (uiState.query.isNotEmpty()) {
            playHaptic(NovelNestHapticType.LIGHT_TICK)
            keyboardController?.hide()
            viewModel.clearQuery()
        } else {
            onBack()
        }
    }

    // Gracefully request focus after enter transition
    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NovelBackground)
    ) {
        // Search Input Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NovelNestIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Navigate back",
                tint = NovelTextPrimary,
                onClick = onBack
            )

            // Search Bar Input Field (compact 44dp height)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NovelSurfaceVariant)
                    .border(1.dp, NovelSurfaceBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = NovelPrimary,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    BasicTextField(
                        value = uiState.query,
                        onValueChange = { viewModel.onQueryChange(it) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = NovelTextPrimary),
                        cursorBrush = SolidColor(NovelPrimary),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                keyboardController?.hide()
                                viewModel.performSearch(uiState.query)
                            }
                        ),
                        decorationBox = { innerTextField ->
                            if (uiState.query.isEmpty()) {
                                Text(
                                    text = "Search in ${uiState.selectedSource.displayName}...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NovelTextTertiary
                                )
                            }
                            innerTextField()
                        }
                    )

                    AnimatedVisibility(
                        visible = uiState.query.isNotEmpty(),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        NovelNestIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "Clear search query",
                            tint = NovelTextSecondary,
                            iconSize = 16.dp,
                            onClick = {
                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                viewModel.clearQuery()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
        }

        // Active source tab switcher (kept consistent with reusable SourceTabRow)
        SourceTabRow(
            selectedSource = uiState.selectedSource,
            onSourceSelected = {
                playHaptic(NovelNestHapticType.LIGHT_TICK)
                viewModel.selectSource(it)
            }
        )

        // Results / Loading / Empty / Recent searches
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when {
                uiState.isSearching -> {
                    SearchShimmerGrid(sourceName = uiState.selectedSource.displayName)
                }

                uiState.hasSearched && uiState.results.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SearchOff,
                            contentDescription = null,
                            tint = NovelTextTertiary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.errorMessage ?: "No results found for \"${uiState.query}\"",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = NovelTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (uiState.errorMessage != null) "Check your connection and retry." else "Try searching with different keywords or switch source tab above",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NovelTextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                if (uiState.errorMessage != null) viewModel.performSearch(uiState.query) else viewModel.clearQuery()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NovelPrimary,
                                contentColor = NovelOnPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (uiState.errorMessage != null) "Retry Search" else "Clear Search", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                uiState.results.isNotEmpty() -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "${uiState.results.size} results in ${uiState.selectedSource.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NovelTextSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = navBarBottom + 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.results, key = { it.id }) { novel ->
                                NovelCard(
                                    novel = novel,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        playHaptic(NovelNestHapticType.ACTION_CONFIRM)
                                        viewModel.onNovelOpened(novel)
                                        onNovelClick(novel)
                                    }
                                )
                            }
                        }
                    }
                }

                else -> {
                    // Landing state: Redesigned Recent Searches, Popular Shelf, and Genre Shortcuts
                    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = navBarBottom + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        // 1. Redesigned Recent Searches (Compact pills, no bloated padding)
                        if (uiState.recentSearches.isNotEmpty()) {
                            RecentSearchesSection(
                                keywords = uiState.recentSearches,
                                onSearch = { keyword ->
                                    playHaptic(NovelNestHapticType.LIGHT_TICK)
                                    keyboardController?.hide()
                                    viewModel.performSearch(keyword)
                                },
                                onRemove = viewModel::removeRecentSearch,
                                onClear = viewModel::clearRecentSearches
                            )
                        }

                        // 2. Redesigned Popular Section (Visual carousel + compact search pills)
                        when {
                            uiState.isPopularLoading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Discovering popular novels...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NovelTextSecondary
                                    )
                                }
                            }
                            uiState.popularErrorMessage != null -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = uiState.popularErrorMessage.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NovelTextSecondary
                                    )
                                    TextButton(onClick = { viewModel.loadPopular(forceRefresh = true) }) {
                                        Text("Retry popular novels", color = NovelPrimary)
                                    }
                                }
                            }
                            uiState.popularNovels.isNotEmpty() -> {
                                PopularNovelsSection(
                                    sourceName = uiState.selectedSource.displayName,
                                    novels = uiState.popularNovels,
                                    onNovelClick = { novel ->
                                        playHaptic(NovelNestHapticType.ACTION_CONFIRM)
                                        viewModel.onNovelOpened(novel)
                                        onNovelClick(novel)
                                    },
                                    onSearchKeyword = { keyword ->
                                        playHaptic(NovelNestHapticType.LIGHT_TICK)
                                        keyboardController?.hide()
                                        viewModel.performSearch(keyword)
                                    }
                                )
                            }
                        }

                        // 3. Genre Discovery Shortcuts (Compact pills)
                        GenreShortcutsSection(
                            genres = POPULAR_GENRES,
                            onGenreClick = { genre ->
                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                keyboardController?.hide()
                                viewModel.onQueryChange(genre)
                                viewModel.performSearch(genre)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact Recent Searches Section.
 * Redesigned with sleek 32dp capsule pills, eliminating oversized cards and bloated padding.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSearchesSection(
    keywords: List<String>,
    onSearch: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = NovelPrimary,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = "Recent Searches",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = NovelTextPrimary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = NovelTextTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Clear All",
                    style = MaterialTheme.typography.labelSmall,
                    color = NovelTextTertiary
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            keywords.forEach { keyword ->
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NovelSurfaceVariant)
                        .border(1.dp, NovelSurfaceBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(start = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = NovelTextTertiary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = keyword,
                        color = NovelTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 160.dp)
                            .clickable { onSearch(keyword) }
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { onRemove(keyword) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Remove $keyword",
                            tint = NovelTextTertiary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Redesigned Popular Novels Section.
 * Replaces bloated cards with a sleek visual Top Trending carousel + compact search chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PopularNovelsSection(
    sourceName: String,
    novels: List<Novel>,
    onNovelClick: (Novel) -> Unit,
    onSearchKeyword: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = NovelPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Trending on $sourceName",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = NovelTextPrimary
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = NovelBadgeBackground,
                border = BorderStroke(1.dp, NovelPrimary.copy(alpha = 0.2f))
            ) {
                Text(
                    text = "Top Picks",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = NovelPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // Horizontal Carousel of popular novels
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            itemsIndexed(novels.take(10), key = { index, novel -> "${novel.id}:$index" }) { index, novel ->
                Column(
                    modifier = Modifier
                        .width(104.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onNovelClick(novel) },
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(104.dp)
                            .height(148.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NovelSurfaceVariant)
                            .border(1.dp, NovelSurfaceBorder.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    ) {
                        if (novel.coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = novel.coverUrl,
                                contentDescription = novel.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Top-left Rank Pill Badge
                        Surface(
                            shape = RoundedCornerShape(bottomEnd = 8.dp, topStart = 10.dp),
                            color = if (index < 3) NovelPrimary else Color(0xCC161B22),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Text(
                                text = "#${index + 1}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (index < 3) NovelOnPrimary else NovelTextPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = novel.title,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = NovelTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Compact trending search pills
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 2.dp)
        ) {
            novels.take(6).forEachIndexed { index, novel ->
                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(NovelSurfaceVariant)
                        .border(1.dp, NovelSurfaceBorder.copy(alpha = 0.5f), RoundedCornerShape(15.dp))
                        .clickable { onSearchKeyword(novel.title) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = NovelPrimary
                    )
                    Text(
                        text = novel.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = NovelTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact Genre Shortcuts Section.
 * Redesigned with sleek 32dp capsule pills.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreShortcutsSection(
    genres: List<String>,
    onGenreClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Category,
                contentDescription = null,
                tint = NovelPrimary,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = "Browse by Genre",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = NovelTextPrimary
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            genres.forEach { genre ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = NovelSurfaceVariant,
                    border = BorderStroke(1.dp, NovelSurfaceBorder.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onGenreClick(genre) }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = genre,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = NovelTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchShimmerGrid(sourceName: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Searching $sourceName...",
            color = NovelTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
}
