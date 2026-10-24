package com.novelnest.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withStarted
import com.novelnest.app.data.narration.NarrationStatus
import com.novelnest.app.core.ui.NarrationMiniPlayer
import com.novelnest.app.core.ui.LocalNarrationBottomPadding
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    repository: NovelRepository,
    navigationPreferences: com.novelnest.app.data.preferences.NavigationPreferences? = null,
    notificationRequest: com.novelnest.app.data.notification.NotificationOpen? = null,
    onNotificationHandled: (com.novelnest.app.data.notification.NotificationOpen) -> Unit = {}
) {
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val hasNarration by remember {
        NarrationStatus.state.map { it.novelId.isNotBlank() }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    var playerHeightPx by remember { mutableIntStateOf(0) }
    val playerPadding = if (hasNarration) with(LocalDensity.current) { playerHeightPx.toDp() } else 0.dp
    LaunchedEffect(notificationRequest, lifecycleOwner) {
        notificationRequest?.let { request ->
            // Cold launches and restored tasks must install their graph before
            // consuming an external action. Wait once; no polling or fixed delay.
            navController.currentBackStackEntryFlow.first()
            lifecycleOwner.lifecycle.withStarted {
                if (request.novelId != null) {
                    navController.navigate(NovelNestDestination.Detail.createRoute(request.novelId)) { launchSingleTop = true }
                } else {
                    val home = NovelNestDestination.Home.route
                    // A stale notification/reminder opens Home rather than restoring
                    // an unrelated reader stack saved by the bottom navigation.
                    if (navController.currentDestination?.route != home &&
                        !navController.popBackStack(home, inclusive = false)) {
                        navController.navigate(home) { launchSingleTop = true }
                    }
                }
                onNotificationHandled(request)
            }
        }
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NovelBackground)
    ) {
        CompositionLocalProvider(LocalNarrationBottomPadding provides playerPadding) {
            NovelNestNavHost(
                navController = navController,
                repository = repository,
                onNovelClick = { novel ->
                    navigationPreferences?.selectSource(novel.source)
                    repository.rememberNovel(novel)
                    // Open full Novel Detail & Chapter List screen directly (bottom sheet removed)
                    navController.navigate(NovelNestDestination.Detail.createRoute(novel.id))
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
        if (hasNarration) {
            // Measure the player body separately from navigation-bar padding so
            // page content, which already handles those insets, counts them once.
            Box(if (!showBottomBar) Modifier.navigationBarsPadding() else Modifier) {
                NarrationMiniPlayer(Modifier.onSizeChanged { playerHeightPx = it.height })
            }
        }
        // Floating Capsule Footer Dock (shown on Home, Discover, Settings)
        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
        ) {
            NovelNestFooter(
                destinations = footerDestinations,
                selectedRoute = currentRoute,
                onNavigate = { route ->
                    if (navController.currentDestination?.route != route) {
                        // Home already anchors the stack. Restoring a saved stack here can
                        // bring back another screen instead of the requested Home screen.
                        if (route == NovelNestDestination.Home.route) {
                            val returnedHome = navController.popBackStack(
                                NovelNestDestination.Home.route, inclusive = false, saveState = true)
                            if (!returnedHome) navController.navigate(route) { launchSingleTop = true }
                        } else navController.navigate(route) {
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
