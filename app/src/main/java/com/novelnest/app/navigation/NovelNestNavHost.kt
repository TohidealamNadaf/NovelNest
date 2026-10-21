package com.novelnest.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.repository.NovelRepository
import com.novelnest.app.feature.detail.NovelDetailScreen
import com.novelnest.app.feature.detail.NovelDetailViewModel
import com.novelnest.app.feature.discover.DiscoverScreen
import com.novelnest.app.feature.discover.DiscoverViewModel
import com.novelnest.app.feature.home.HomeScreen
import com.novelnest.app.feature.home.HomeViewModel
import com.novelnest.app.feature.notification.NotificationScreen
import com.novelnest.app.feature.reader.ReaderScreen
import com.novelnest.app.feature.reader.ReaderViewModel
import com.novelnest.app.feature.search.SearchScreen
import com.novelnest.app.feature.search.SearchViewModel
import com.novelnest.app.feature.settings.SettingsScreen
import com.novelnest.app.feature.settings.SettingsViewModel

@Composable
fun NovelNestNavHost(
    navController: NavHostController,
    repository: NovelRepository,
    onNovelClick: (Novel) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as NovelNestApplication

    val homeViewModel = remember { HomeViewModel(repository, app.bookmarkManager) }
    val discoverViewModel = remember { DiscoverViewModel(repository) }

    NavHost(
        navController = navController,
        startDestination = NovelNestDestination.Home.route,
        modifier = modifier
    ) {
        // HOME SCREEN
        composable(
            route = NovelNestDestination.Home.route,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToSearch = { source ->
                    navController.navigate(NovelNestDestination.Search.createRoute(source.ordinal))
                },
                onNovelClick = onNovelClick,
                onNavigateToNotifications = {
                    navController.navigate(NovelNestDestination.Notifications.route)
                },
                onNavigateToSettings = {
                    navController.navigate(NovelNestDestination.Settings.route)
                }
            )
        }

        // DISCOVER SCREEN
        composable(
            route = NovelNestDestination.Discover.route,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            DiscoverScreen(
                viewModel = discoverViewModel,
                onNavigateToSearch = { source ->
                    navController.navigate(NovelNestDestination.Search.createRoute(source.ordinal))
                },
                onNovelClick = onNovelClick
            )
        }

        // SETTINGS SCREEN
        composable(
            route = NovelNestDestination.Settings.route,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            val settingsViewModel = remember {
                SettingsViewModel(app.preferencesManager, context)
            }
            SettingsScreen(viewModel = settingsViewModel)
        }

        // SEARCH SCREEN (Fluid animated entry)
        composable(
            route = NovelNestDestination.Search.route,
            arguments = listOf(
                navArgument("sourceOrdinal") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            ),
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight / 4 },
                    animationSpec = tween(350)
                ) + fadeIn(animationSpec = tween(350))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight / 4 },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(250))
            }
        ) { backStackEntry ->
            val sourceOrdinal = backStackEntry.arguments?.getInt("sourceOrdinal") ?: 0
            val source = NovelSourceType.fromOrdinal(sourceOrdinal)
            val searchViewModel = remember(source) {
                SearchViewModel(repository, initialSource = source)
            }

            SearchScreen(
                viewModel = searchViewModel,
                onBack = { navController.popBackStack() },
                onNovelClick = onNovelClick
            )
        }

        // FULL NOVEL DETAIL & CHAPTER LIST SCREEN
        composable(
            route = NovelNestDestination.Detail.route,
            arguments = listOf(
                navArgument("novelId") {
                    type = NavType.StringType
                }
            ),
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight / 4 },
                    animationSpec = tween(320)
                ) + fadeIn(animationSpec = tween(320))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight / 4 },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(250))
            }
        ) { backStackEntry ->
            val novelId = backStackEntry.arguments?.getString("novelId") ?: ""
            val detailViewModel = remember(novelId) {
                NovelDetailViewModel(
                    novelId = novelId,
                    repository = repository,
                    bookmarkManager = app.bookmarkManager,
                    readingProgressManager = app.readingProgressManager
                )
            }

            NovelDetailScreen(
                viewModel = detailViewModel,
                onBack = { navController.popBackStack() },
                onStartReading = { id, chapterIndex ->
                    navController.navigate(NovelNestDestination.Reader.createRoute(id, chapterIndex))
                }
            )
        }

        // NOTIFICATIONS SCREEN
        composable(
            route = NovelNestDestination.Notifications.route,
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight / 4 },
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight / 4 },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(250))
            }
        ) {
            NotificationScreen(
                onBack = { navController.popBackStack() },
                onNovelClick = { novelId ->
                    navController.navigate(NovelNestDestination.Detail.createRoute(novelId))
                }
            )
        }

        // READER SCREEN (Immersive reading canvas)
        composable(
            route = NovelNestDestination.Reader.route,
            arguments = listOf(
                navArgument("novelId") {
                    type = NavType.StringType
                },
                navArgument("chapterIndex") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            ),
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight / 3 },
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight / 3 },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(250))
            }
        ) { backStackEntry ->
            val novelId = backStackEntry.arguments?.getString("novelId") ?: ""
            val chapterIndex = backStackEntry.arguments?.getInt("chapterIndex") ?: -1
            val readerViewModel = remember(novelId, chapterIndex) {
                ReaderViewModel(
                    novelId = novelId,
                    repository = repository,
                    preferencesManager = app.preferencesManager,
                    bookmarkManager = app.bookmarkManager,
                    readingProgressManager = app.readingProgressManager,
                    initialChapterIndex = chapterIndex
                )
            }

            ReaderScreen(
                viewModel = readerViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
