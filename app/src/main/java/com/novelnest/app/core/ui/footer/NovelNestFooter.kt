package com.novelnest.app.core.ui.footer

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.rememberAreAnimationsDisabled
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic

/**
 * Navigation destination specification for the footer component.
 * Supports dual-state icons (unselected outlined & selected filled) for rich animated morphs.
 */
data class FooterDestination(
    val route: String,
    val label: String,
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector = unselectedIcon
) {
    /** Backward compatibility constructor accepting a single icon */
    constructor(route: String, label: String, icon: ImageVector) : this(
        route = route,
        label = label,
        unselectedIcon = icon,
        selectedIcon = icon
    )

    val icon: ImageVector get() = unselectedIcon
}

/**
 * Premium docked navigation footer matching NovelNest's core card and surface design language.
 * Features:
 * - Solid 100% opaque NovelSurface (no transparency bleed from background cards or text)
 * - Full-width docked layout with clean top boundary divider
 * - M3-inspired active pill indicator behind icons (no cramped card borders, no clipping)
 * - Removed permanent circular ripple artifact behind Home icon
 * - Animated icon transitions:
 *   - Discover: 360-degree compass needle rotation
 *   - Settings: 90-degree smooth gear turn
 *   - Clean spring scale pulse on selection
 * - Safe navigation bar insets
 */
@Composable
fun NovelNestFooter(
    destinations: List<FooterDestination>,
    selectedRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val playHaptic = rememberNovelNestHaptic()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NovelSurface)
            .navigationBarsPadding()
    ) {
        // Crisp boundary divider matching settings & card border system
        HorizontalDivider(
            thickness = 1.dp,
            color = NovelSurfaceBorder.copy(alpha = 0.85f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FooterTokens.BarHeight)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { destination ->
                val isSelected = destination.route == selectedRoute
                FooterNavItem(
                    destination = destination,
                    isSelected = isSelected,
                    onClick = {
                        playHaptic(NovelNestHapticType.LIGHT_TICK)
                        onNavigate(destination.route)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FooterNavItem(
    destination: FooterDestination,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val areAnimationsDisabled = rememberAreAnimationsDisabled()

    // Smooth color transitions
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) NovelPrimary else NovelTextSecondary,
        animationSpec = if (areAnimationsDisabled) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_item_content_color"
    )

    val pillColor by animateColorAsState(
        targetValue = if (isSelected) NovelPrimary.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = if (areAnimationsDisabled) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_item_pill_color"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = NovelPrimary),
                role = Role.Tab,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Active indicator pill behind the icon: ample width & height prevents any cutoff
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(pillColor),
            contentAlignment = Alignment.Center
        ) {
            AnimatedNavIcon(
                destination = destination,
                isSelected = isSelected,
                contentColor = contentColor,
                areAnimationsDisabled = areAnimationsDisabled
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = destination.label,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/**
 * Animated navigation icon with interactive motions:
 * - Discover: 360-degree compass rotation + filled morph
 * - Settings: 90-degree gear rotation + filled morph
 * - Spring scale transition without clipping
 */
@Composable
private fun AnimatedNavIcon(
    destination: FooterDestination,
    isSelected: Boolean,
    contentColor: Color,
    areAnimationsDisabled: Boolean,
    modifier: Modifier = Modifier
) {
    // Rotation animation
    val targetRotation = when {
        !isSelected -> 0f
        destination.route.contains("discover", ignoreCase = true) -> 360f
        destination.route.contains("settings", ignoreCase = true) -> 90f
        else -> 0f
    }

    val iconRotation by animateFloatAsState(
        targetValue = if (areAnimationsDisabled) 0f else targetRotation,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "nav_icon_rotation"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = if (areAnimationsDisabled) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "nav_icon_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(24.dp)
            .graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
                rotationZ = iconRotation
            }
            .clearAndSetSemantics { }
    ) {
        // Crossfade between unselected (outline) and selected (filled)
        Crossfade(
            targetState = isSelected,
            animationSpec = tween(durationMillis = 180),
            label = "nav_icon_morph"
        ) { selected ->
            val iconVector = if (selected) destination.selectedIcon else destination.unselectedIcon
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1216)
@Composable
private fun NovelNestFooterPreview() {
    val sampleDestinations = listOf(
        FooterDestination(
            route = "home",
            label = "Home",
            unselectedIcon = Icons.Outlined.Home,
            selectedIcon = Icons.Filled.Home
        ),
        FooterDestination(
            route = "discover",
            label = "Discover",
            unselectedIcon = Icons.Outlined.Explore,
            selectedIcon = Icons.Filled.Explore
        ),
        FooterDestination(
            route = "settings",
            label = "Settings",
            unselectedIcon = Icons.Outlined.Settings,
            selectedIcon = Icons.Filled.Settings
        )
    )
    NovelNestFooter(
        destinations = sampleDestinations,
        selectedRoute = "home",
        onNavigate = {}
    )
}
