package com.novelnest.app.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelBackground
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
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.bounceClick
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

@OptIn(ExperimentalLayoutApi::class)
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
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NovelNestIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Navigate back",
                tint = NovelTextPrimary,
                onClick = onBack
            )

            // Search Bar Input Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NovelSurfaceVariant)
                    .border(1.dp, NovelSurfaceBorder, RoundedCornerShape(12.dp))
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
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    BasicTextField(
                        value = uiState.query,
                        onValueChange = { viewModel.onQueryChange(it) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = NovelTextPrimary),
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
                                    style = MaterialTheme.typography.bodyLarge,
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
                            iconSize = 18.dp,
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

        // Active source tab switcher inside search
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
                            text = "No results found for \"${uiState.query}\"",
                            style = MaterialTheme.typography.titleMedium,
                            color = NovelTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Try searching with different keywords or switch source tab above",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NovelTextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                viewModel.clearQuery()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NovelPrimary,
                                contentColor = NovelOnPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Clear Search")
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
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navBarBottom + 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.results, key = { it.id }) { novel ->
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

                else -> {
                    // Landing state: Popular/Recent searches & Genre shortcuts
                    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = navBarBottom + 16.dp)
                    ) {
                        if (uiState.recentSearches.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = NovelPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Popular & Recent Searches",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = NovelTextPrimary
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        playHaptic(NovelNestHapticType.LIGHT_TICK)
                                        viewModel.clearRecentSearches()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Clear all recent searches",
                                        tint = NovelTextTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Clear",
                                        color = NovelTextTertiary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.recentSearches.forEach { keyword ->
                                    Box(
                                        modifier = Modifier
                                            .heightIn(min = 36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NovelSurfaceVariant)
                                            .border(1.dp, NovelSurfaceBorder, RoundedCornerShape(8.dp))
                                            .bounceClick(
                                                scaleDown = BounceScale.Small,
                                                onClickLabel = "Search $keyword",
                                                role = Role.Button
                                            ) {
                                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                                keyboardController?.hide()
                                                viewModel.onQueryChange(keyword)
                                                viewModel.performSearch(keyword)
                                            }
                                            .padding(horizontal = 14.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = keyword,
                                            color = NovelTextPrimary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // Genre Discovery Shortcuts
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Category,
                                contentDescription = null,
                                tint = NovelPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Browse by Genre",
                                style = MaterialTheme.typography.titleMedium,
                                color = NovelTextPrimary
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            POPULAR_GENRES.forEach { genre ->
                                Box(
                                    modifier = Modifier
                                        .heightIn(min = 36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NovelSurfaceVariant.copy(alpha = 0.7f))
                                        .border(1.dp, NovelSurfaceBorder, RoundedCornerShape(8.dp))
                                        .bounceClick(
                                            scaleDown = BounceScale.Small,
                                            onClickLabel = "Search genre $genre",
                                            role = Role.Button
                                        ) {
                                            playHaptic(NovelNestHapticType.LIGHT_TICK)
                                            keyboardController?.hide()
                                            viewModel.onQueryChange(genre)
                                            viewModel.performSearch(genre)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = genre,
                                        color = NovelTextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
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
private fun SearchShimmerGrid(sourceName: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Searching $sourceName...",
            color = NovelTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(4) {
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
