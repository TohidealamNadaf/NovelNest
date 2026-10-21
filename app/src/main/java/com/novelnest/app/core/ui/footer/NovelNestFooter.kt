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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.bounceClick
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
 * Polished, modern, premium pill-shaped bottom navigation footer.
 * Features:
 * - Solid 100% opaque NovelSurface fill with fully rounded pill shape (32dp)
 * - Soft elevation/shadow distinguishing it from screen content without transparency bleed
 * - Subtle, refined edge definition (no harsh double borders)
 * - Cohesive pill container respecting system navigation insets and balanced margins
 * - Preserved and improved interactive icon animations:
 *   - Home: vertical spring hop, scale pulse, and outlined-to-filled morph
 *   - Discover: 360-degree compass needle rotation and outlined-to-filled morph
 *   - Settings: 90-degree smooth gear rotation and outlined-to-filled morph
 * - Integrated subtle active indicator pill with bounded ripple feedback
 * - Restrained touch scale and light haptic feedback
 */
@Composable
fun NovelNestFooter(
    destinations: List<FooterDestination>,
    selectedRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val playHaptic = rememberNovelNestHaptic()
    val pillShape = RoundedCornerShape(32.dp)

    val shadowModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Modifier.shadow(
            elevation = 12.dp,
            shape = pillShape,
            ambientColor = Color.Black.copy(alpha = 0.5f),
            spotColor = NovelPrimary.copy(alpha = 0.22f)
        )
    } else {
        Modifier.shadow(
            elevation = 10.dp,
            shape = pillShape
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = FooterTokens.VerticalMargin),
        contentAlignment = Alignment.Center
    ) {
        // Pill-shaped container: Solid background, smooth rounded corners, subtle border and shadow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FooterTokens.PillHeight)
                .then(shadowModifier)
                .clip(pillShape)
                .background(NovelSurface)
                .border(
                    width = 1.dp,
                    color = NovelSurfaceBorder.copy(alpha = 0.65f),
                    shape = pillShape
                )
                .padding(horizontal = 8.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
}

@Composable
private fun FooterNavItem(
    destination: FooterDestination,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val areAnimationsDisabled = rememberAreAnimationsDisabled()
    val itemShape = RoundedCornerShape(26.dp)

    // Smooth active indicator color transition integrated into the pill
    val activeHighlightColor by animateColorAsState(
        targetValue = if (isSelected) NovelPrimary.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = if (areAnimationsDisabled) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_item_pill_highlight"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) NovelPrimary else NovelTextSecondary,
        animationSpec = if (areAnimationsDisabled) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_item_content_color"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 2.dp)
            .clip(itemShape)
            .background(activeHighlightColor)
            .bounceClick(
                scaleDown = BounceScale.Small,
                enabled = true,
                onClickLabel = destination.label,
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

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = destination.label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/**
 * Animated navigation icon preserving all signature interactive motions:
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
    // 1. Home vertical hop animation
    var homeHopTrigger by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isSelected) {
        if (isSelected && destination.route.contains("home", ignoreCase = true)) {
            homeHopTrigger = 1f
        } else {
            homeHopTrigger = 0f
        }
    }

    val hopOffset by animateDpAsState(
        targetValue = if (homeHopTrigger == 1f && !areAnimationsDisabled) (-3).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "home_hop_offset"
    )

    // 2. Discover & Settings rotation animation
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

    // 3. Subtle scale pulse on selection
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.10f else 1.0f,
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
                translationY = if (destination.route.contains("home", ignoreCase = true)) hopOffset.toPx() else 0f
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
private fun NovelNestFooterPillPreview() {
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
