package com.novelnest.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.novelnest.app.core.designsystem.NovelBackground
import com.novelnest.app.core.ui.footer.FooterDestination
import com.novelnest.app.core.ui.footer.NovelNestFooter
import com.novelnest.app.data.repository.NovelRepository
import com.novelnest.app.navigation.NovelNestDestination
import com.novelnest.app.navigation.NovelNestNavHost

@Composable
fun NovelNestApp(
    repository: NovelRepository
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        NovelNestDestination.Home.route,
        NovelNestDestination.Discover.route,
        NovelNestDestination.Settings.route
    )

    val footerDestinations = remember {
        listOf(
            FooterDestination(
                route = NovelNestDestination.Home.route,
                label = "Home",
                unselectedIcon = Icons.Outlined.Home,
                selectedIcon = Icons.Filled.Home
            ),
            FooterDestination(
                route = NovelNestDestination.Discover.route,
                label = "Discover",
                unselectedIcon = Icons.Outlined.Explore,
                selectedIcon = Icons.Filled.Explore
            ),
            FooterDestination(
                route = NovelNestDestination.Settings.route,
                label = "Settings",
                unselectedIcon = Icons.Outlined.Settings,
                selectedIcon = Icons.Filled.Settings
            )
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = NovelBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            NovelNestNavHost(
                navController = navController,
                repository = repository,
                onNovelClick = { novel ->
                    // Open full Novel Detail & Chapter List screen directly (bottom sheet removed)
                    navController.navigate(NovelNestDestination.Detail.createRoute(novel.id))
                },
                modifier = Modifier.fillMaxSize()
            )

            // Floating Capsule Footer Dock (shown on Home, Discover, Settings)
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                NovelNestFooter(
                    destinations = footerDestinations,
                    selectedRoute = currentRoute,
                    onNavigate = { route ->
                        if (currentRoute != route) {
                            navController.navigate(route) {
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
    }
}
