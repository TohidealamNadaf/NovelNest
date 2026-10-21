package com.novelnest.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.novelnest.app.core.designsystem.NovelBackground
import com.novelnest.app.core.designsystem.NovelGold
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSecondary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.data.model.Novel
import com.novelnest.app.core.ui.footer.NovelNestFooter
import com.novelnest.app.data.repository.NovelRepository
import com.novelnest.app.navigation.NovelNestDestination
import com.novelnest.app.navigation.NovelNestNavHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelNestApp(
    repository: NovelRepository
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var selectedNovelForPreview by remember { mutableStateOf<Novel?>(null) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val showBottomBar = currentRoute in listOf(
        NovelNestDestination.Home.route,
        NovelNestDestination.Discover.route
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = NovelBackground,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                NovelNestFooter(
                    currentRoute = currentRoute,
                    onNavigateToHome = {
                        if (currentRoute != NovelNestDestination.Home.route) {
                            navController.navigate(NovelNestDestination.Home.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onNavigateToDiscover = {
                        if (currentRoute != NovelNestDestination.Discover.route) {
                            navController.navigate(NovelNestDestination.Discover.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NovelNestNavHost(
            navController = navController,
            repository = repository,
            onNovelClick = { novel ->
                selectedNovelForPreview = novel
            },
            modifier = Modifier.padding(innerPadding)
        )

        // Novel Quick Preview Bottom Sheet
        selectedNovelForPreview?.let { novel ->
            ModalBottomSheet(
                onDismissRequest = { selectedNovelForPreview = null },
                sheetState = bottomSheetState,
                containerColor = NovelSurface,
                dragHandle = null
            ) {
                NovelPreviewSheetContent(
                    novel = novel,
                    onDismiss = { selectedNovelForPreview = null }
                )
            }
        }
    }
}

@Composable
private fun NovelPreviewSheetContent(
    novel: Novel,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(modifier = Modifier.weight(1f)) {
                // Cover
                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 115.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NovelSurfaceVariant)
                        .border(1.dp, NovelSurfaceBorder, RoundedCornerShape(10.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(novel.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = novel.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    // Source tag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NovelSurfaceVariant)
                            .border(0.5.dp, NovelSurfaceBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = novel.source.displayName,
                            color = NovelPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = novel.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = NovelTextPrimary,
                        maxLines = 2,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Author: ${novel.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NovelTextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = NovelGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format("%.1f", novel.rating),
                            color = NovelTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = novel.latestChapter,
                            color = NovelSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = NovelTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (novel.genres.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                novel.genres.take(4).forEach { genre ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NovelSurfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = genre,
                            color = NovelTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = "Synopsis",
            style = MaterialTheme.typography.labelLarge,
            color = NovelTextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = novel.synopsis.ifBlank { "An extraordinary story waiting to be read." },
            style = MaterialTheme.typography.bodyMedium,
            color = NovelTextSecondary,
            lineHeight = 20.sp,
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Read Now Action
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NovelPrimary,
                contentColor = NovelOnPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Rounded.AutoStories, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Start Reading",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}
