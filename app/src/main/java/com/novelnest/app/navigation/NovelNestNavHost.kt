package com.novelnest.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.novelnest.app.feature.browse.BrowseScreen
import com.novelnest.app.feature.browse.BrowseViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
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

    var requestedDiscoverSource by rememberSaveable { mutableStateOf<Int?>(null) }

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
            val homeViewModel: HomeViewModel = viewModel(factory = screenFactory {
                HomeViewModel(app.bookmarkManager, app.readingProgressManager, repository, app.navigationPreferences)
            })
            HomeScreen(
                viewModel = homeViewModel,
                onContinueReading = { novel, chapterIndex ->
                    app.navigationPreferences.selectSource(novel.source)
                    repository.rememberNovel(novel)
                    navController.navigate(NovelNestDestination.Reader.createRoute(novel.id, chapterIndex.coerceAtLeast(0)))
                },
                onNavigateToSearch = { source ->
                    navController.navigate(NovelNestDestination.Search.createRoute(source.ordinal))
                },
                onNavigateToDiscover = { source ->
                    requestedDiscoverSource = source.ordinal
                    navController.navigate(NovelNestDestination.Discover.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNovelClick = onNovelClick,
                onNavigateToNotifications = {
                    navController.navigate(NovelNestDestination.Notifications.route)
                },
                onNavigateToProfile = {
                    navController.navigate(NovelNestDestination.Profile.route)
                }
            )
        }

        // DISCOVER SCREEN
        composable(
            route = NovelNestDestination.Discover.route,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            val discoverViewModel: DiscoverViewModel = viewModel(factory = screenFactory {
                DiscoverViewModel(repository, NovelSourceType.fromOrdinal(requestedDiscoverSource ?: 0), app.navigationPreferences)
            })
            LaunchedEffect(requestedDiscoverSource) {
                requestedDiscoverSource?.let { discoverViewModel.selectSource(NovelSourceType.fromOrdinal(it)) }
                requestedDiscoverSource = null
            }
            DiscoverScreen(
                viewModel = discoverViewModel,
                onBrowse = { source -> navController.navigate(NovelNestDestination.Browse.createRoute(source.ordinal)) },
                onNavigateToSearch = { source ->
                    navController.navigate(NovelNestDestination.Search.createRoute(source.ordinal))
                },
                onNovelClick = onNovelClick
            )
        }

        composable(
            route = NovelNestDestination.Browse.route,
            arguments = listOf(navArgument("sourceOrdinal") { type = NavType.IntType; defaultValue = 0 })
        ) { entry ->
            val source = NovelSourceType.fromOrdinal(entry.arguments?.getInt("sourceOrdinal") ?: 0)
            val browseViewModel: BrowseViewModel = viewModel(factory = screenFactory {
                BrowseViewModel(repository, source, app.navigationPreferences)
            })
            BrowseScreen(
                viewModel = browseViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToSearch = { navController.navigate(NovelNestDestination.Search.createRoute(it.ordinal)) },
                onNovelClick = onNovelClick
            )
        }

        composable(NovelNestDestination.Profile.route) {
            com.novelnest.app.feature.profile.ProfileScreen(
                onBack = { navController.popBackStack() },
                onSettings = { navController.navigate(NovelNestDestination.Settings.route) },
                onNotifications = { navController.navigate(NovelNestDestination.Notifications.route) }
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
            val aiViewModel: com.novelnest.app.feature.settings.AiReadingToolsViewModel = viewModel(factory = screenFactory {
                com.novelnest.app.feature.settings.AiReadingToolsViewModel(app.aiConfiguration, app.aiModelDirectory, app.aiResultCache)
            })
            SettingsScreen(
                viewModel = settingsViewModel,
                aiViewModel = aiViewModel
            )
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
                SearchViewModel(repository, initialSource = source, historyManager = app.searchHistoryManager,
                    navigationPreferences = app.navigationPreferences)
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
            val detailViewModel: NovelDetailViewModel = viewModel(
                key = "novel_detail_$novelId",
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return NovelDetailViewModel(
                            novelId = novelId,
                            repository = repository,
                            bookmarkManager = app.bookmarkManager,
                            readingProgressManager = app.readingProgressManager
                        ) as T
                    }
                }
            )

            NovelDetailScreen(
                viewModel = detailViewModel,
                onBack = { navController.popBackStack() },
                onStartReading = { id, chapterIndex ->
                    detailViewModel.uiState.value.novel?.source?.let(app.navigationPreferences::selectSource)
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
            val readerViewModel: ReaderViewModel = viewModel(
                key = "reader_${novelId}_$chapterIndex",
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
                        return ReaderViewModel(
                            novelId = novelId,
                            repository = repository,
                            preferencesManager = app.preferencesManager,
                            bookmarkManager = app.bookmarkManager,
                            readingProgressManager = app.readingProgressManager,
                            initialChapterIndex = chapterIndex,
                            savedStateHandle = extras.createSavedStateHandle(),
                            aiRepository = app.chapterAiRepository,
                            aiConfigurationRevision = app.aiConfiguration.revision,
                            aiForeground = app.aiForeground.foreground,
                            studyRepository = app.studyRepository
                        ) as T
                    }
                }
            )

            val readerState by readerViewModel.uiState.collectAsState()
            LaunchedEffect(readerState.novel?.source) {
                // Save the actual source even for restored/deep-linked reader entries.
                // An outgoing reader composition must not overwrite a newer source choice.
                if (navController.currentBackStackEntry == backStackEntry) {
                    readerState.novel?.source?.let(app.navigationPreferences::selectSource)
                }
            }
            ReaderScreen(
                viewModel = readerViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private fun <VM : ViewModel> screenFactory(create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
