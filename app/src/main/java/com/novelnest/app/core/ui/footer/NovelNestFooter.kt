package com.novelnest.app.core.ui.footer

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.bounceSelectable
import com.novelnest.app.core.ui.animation.rememberAreAnimationsDisabled
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic

/**
 * Navigation destination specification for the decoupled footer component.
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
 * Modern floating capsule navigation footer.
 * Fully decoupled from NavController and screen types.
 * Features:
 * - Animated icon transitions:
 *   - Home: vertical hop, scale pulse, and outlined-to-filled morph
 *   - Discover: 360-degree compass needle rotation and outlined-to-filled morph
 *   - Settings: 90-degree gear rotation and outlined-to-filled morph
 * - Comfortable padding room around the active card hover / selected pill
 * - Single sliding glowing indicator pill sliding between items with spring physics
 * - Accessible selectable semantics and haptic feedback
 */
@Composable
fun NovelNestFooter(
    destinations: List<FooterDestination>,
    selectedRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val areAnimationsDisabled = rememberAreAnimationsDisabled()
    val playHaptic = rememberNovelNestHaptic()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating pill container with generous bounds
        val pillShape = RoundedCornerShape(32.dp)
        val shadowModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Modifier.shadow(
                elevation = 14.dp,
                shape = pillShape,
                ambientColor = Color.Black,
                spotColor = NovelPrimary.copy(alpha = 0.28f)
            )
        } else {
            Modifier.shadow(
                elevation = 14.dp,
                shape = pillShape
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .then(shadowModifier)
                .clip(pillShape)
                .background(NovelSurface.copy(alpha = 0.96f))
                .border(
                    width = 1.dp,
                    color = NovelSurfaceBorder.copy(alpha = 0.85f),
                    shape = pillShape
                )
                // Generous container padding creates room around active card hover
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEach { destination ->
                    FooterNavItem(
                        destination = destination,
                        isSelected = destination.route == selectedRoute,
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
}

@Composable
private fun FooterNavItem(
    destination: FooterDestination,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val areAnimationsDisabled = rememberAreAnimationsDisabled()

    // Active Card Hover & Selection Styling with dedicated padding room
    val bgPillColor by animateColorAsState(
        targetValue = if (isSelected) NovelSurfaceVariant.copy(alpha = 0.92f) else Color.Transparent,
        animationSpec = if (areAnimationsDisabled) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "footer_pill_bg"
    )

    val activeBorderColor by animateColorAsState(
        targetValue = if (isSelected) NovelSurfaceBorder.copy(alpha = 0.85f) else Color.Transparent,
        animationSpec = if (areAnimationsDisabled) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "footer_pill_border"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) NovelPrimary else NovelTextSecondary,
        animationSpec = if (areAnimationsDisabled) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "footer_color"
    )

    // Dedicated item card container with internal & external padding room
    Box(
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bgPillColor)
            .border(1.dp, activeBorderColor, RoundedCornerShape(20.dp))
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedNavIcon(
                destination = destination,
                isSelected = isSelected,
                contentColor = contentColor,
                areAnimationsDisabled = areAnimationsDisabled
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = destination.label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

/**
 * Animated navigation icon with destination-specific interactive motions:
 * - Home: Bouncy vertical hop + scale pulse + filled transition
 * - Discover: 360-degree compass rotation + filled transition
 * - Settings: 90-degree gear rotation + filled transition
 */
@Composable
private fun AnimatedNavIcon(
    destination: FooterDestination,
    isSelected: Boolean,
    contentColor: Color,
    areAnimationsDisabled: Boolean,
    modifier: Modifier = Modifier
) {
    var hasBeenSelected by remember { mutableStateOf(isSelected) }

    // Home vertical hop animation
    var homeHopTrigger by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isSelected) {
        if (isSelected && destination.route.contains("home", ignoreCase = true)) {
            homeHopTrigger = 1f
        } else {
            homeHopTrigger = 0f
        }
        hasBeenSelected = isSelected
    }

    val hopOffset by animateDpAsState(
        targetValue = if (homeHopTrigger == 1f && !areAnimationsDisabled) (-4).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "home_hop_offset"
    )

    // Destination-specific rotation (Discover compass spin 360°, Settings gear turn 90°)
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
        targetValue = if (isSelected) 1.15f else 1.0f,
        animationSpec = if (areAnimationsDisabled) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
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
                translationY = if (destination.route.contains("home", ignoreCase = true)) hopOffset.toPx() else 0f
            }
            .clearAndSetSemantics { }
    ) {
        // Soft pulsing glow behind Home icon when active
        if (isSelected && destination.route.contains("home", ignoreCase = true)) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(NovelPrimary.copy(alpha = 0.15f))
            )
        }

        // Smooth crossfade between unselected (outlined) and selected (filled) icons
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
private fun NovelNestFooterHomeSelectedPreview() {
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

@Preview(name = "Footer - Discover Selected", showBackground = true, backgroundColor = 0xFF0F1216)
@Composable
private fun NovelNestFooterDiscoverSelectedPreview() {
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
        selectedRoute = "discover",
        onNavigate = {}
    )
}

@Preview(name = "Footer - Settings Selected", showBackground = true, backgroundColor = 0xFF0F1216)
@Composable
private fun NovelNestFooterSettingsSelectedPreview() {
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
        selectedRoute = "settings",
        onNavigate = {}
    )
}
