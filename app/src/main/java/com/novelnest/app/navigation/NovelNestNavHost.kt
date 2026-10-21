package com.novelnest.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.repository.NovelRepository
import com.novelnest.app.feature.discover.DiscoverScreen
import com.novelnest.app.feature.discover.DiscoverViewModel
import com.novelnest.app.feature.home.HomeScreen
import com.novelnest.app.feature.home.HomeViewModel
import com.novelnest.app.feature.search.SearchScreen
import com.novelnest.app.feature.search.SearchViewModel

@Composable
fun NovelNestNavHost(
    navController: NavHostController,
    repository: NovelRepository,
    onNovelClick: (Novel) -> Unit,
    modifier: Modifier = Modifier
) {
    val homeViewModel = remember { HomeViewModel(repository) }
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
                onNovelClick = onNovelClick
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
    }
}
