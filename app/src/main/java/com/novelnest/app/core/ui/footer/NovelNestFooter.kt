package com.novelnest.app.core.ui.footer

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.navigation.NovelNestDestination

/**
 * Reusable, modular Bottom Navigation Footer component.
 * Decoupled from screen-level orchestration for clean reuse across navigation layouts.
 */
@Composable
fun NovelNestFooter(
    currentRoute: String?,
    onNavigateToHome: () -> Unit,
    onNavigateToDiscover: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = NovelSurface,
        tonalElevation = 0.dp,
        modifier = modifier
            .navigationBarsPadding()
            .height(64.dp)
            .border(
                width = 0.5.dp,
                color = NovelSurfaceBorder,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
    ) {
        // Home Navigation Item
        NavigationBarItem(
            selected = currentRoute == NovelNestDestination.Home.route,
            onClick = onNavigateToHome,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = "Home"
                )
            },
            label = {
                Text(text = "Home", fontSize = 11.sp)
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NovelPrimary,
                selectedTextColor = NovelPrimary,
                unselectedIconColor = NovelTextSecondary,
                unselectedTextColor = NovelTextSecondary,
                indicatorColor = NovelSurfaceVariant
            )
        )

        // Discover Navigation Item
        NavigationBarItem(
            selected = currentRoute == NovelNestDestination.Discover.route,
            onClick = onNavigateToDiscover,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Explore,
                    contentDescription = "Discover"
                )
            },
            label = {
                Text(text = "Discover", fontSize = 11.sp)
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NovelPrimary,
                selectedTextColor = NovelPrimary,
                unselectedIconColor = NovelTextSecondary,
                unselectedTextColor = NovelTextSecondary,
                indicatorColor = NovelSurfaceVariant
            )
        )
    }
}
