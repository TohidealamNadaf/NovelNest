package com.novelnest.app.core.ui.footer

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import com.novelnest.app.core.ui.animation.bounceSelectable
import com.novelnest.app.core.ui.animation.rememberAreAnimationsDisabled

/**
 * Navigation destination specification for the decoupled footer component.
 */
data class FooterDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Modern floating capsule navigation footer.
 * Fully decoupled from NavController and screen types.
 * Features a single glowing indicator dot sliding between items with spring physics,
 * spring icon bouncing on selection, and accessible selectable semantics.
 */
@Composable
fun NovelNestFooter(
    destinations: List<FooterDestination>,
    selectedRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var itemCenters by remember { mutableStateOf(mapOf<String, Dp>()) }
    val areAnimationsDisabled = rememberAreAnimationsDisabled()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating pill container
        val pillShape = RoundedCornerShape(29.dp)
        val shadowModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Modifier.shadow(
                elevation = 12.dp,
                shape = pillShape,
                ambientColor = Color.Black,
                spotColor = NovelPrimary.copy(alpha = 0.25f)
            )
        } else {
            Modifier.shadow(
                elevation = 12.dp,
                shape = pillShape
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .then(shadowModifier)
                .clip(pillShape)
                .background(NovelSurface.copy(alpha = 0.96f))
                .border(
                    width = 1.dp,
                    color = NovelSurfaceBorder.copy(alpha = 0.8f),
                    shape = pillShape
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEach { destination ->
                    FooterNavItem(
                        icon = destination.icon,
                        label = destination.label,
                        isSelected = destination.route == selectedRoute,
                        onClick = { onNavigate(destination.route) },
                        onPositioned = { centerX ->
                            if (itemCenters[destination.route] != centerX) {
                                itemCenters = itemCenters + (destination.route to centerX)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Single sliding glowing indicator dot
            val targetCenterX = itemCenters[selectedRoute]
            if (targetCenterX != null) {
                val animatedDotCenterX by animateDpAsState(
                    targetValue = targetCenterX,
                    animationSpec = if (areAnimationsDisabled) {
                        snap()
                    } else {
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    },
                    label = "footer_sliding_dot"
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 5.dp)
                        .offset(x = animatedDotCenterX - 2.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(NovelPrimary)
                )
            }
        }
    }
}

@Composable
private fun FooterNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPositioned: (Dp) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val areAnimationsDisabled = rememberAreAnimationsDisabled()

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
        label = "footer_icon_scale"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) NovelPrimary else NovelTextSecondary,
        animationSpec = if (areAnimationsDisabled) {
            snap()
        } else {
            spring(stiffness = Spring.StiffnessMediumLow)
        },
        label = "footer_color"
    )

    val bgPillColor by animateColorAsState(
        targetValue = if (isSelected) NovelSurfaceVariant.copy(alpha = 0.7f) else Color.Transparent,
        animationSpec = if (areAnimationsDisabled) {
            snap()
        } else {
            spring(stiffness = Spring.StiffnessMediumLow)
        },
        label = "footer_pill_bg"
    )

    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(bgPillColor)
            .onGloballyPositioned { coordinates ->
                val centerX = with(density) {
                    coordinates.positionInParent().x.toDp() + (coordinates.size.width.toDp() / 2f)
                }
                onPositioned(centerX)
            }
            .bounceSelectable(
                selected = isSelected,
                scaleDown = BounceScale.Small,
                role = Role.Tab,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(22.dp)
                    .scale(iconScale)
                    .clearAndSetSemantics { }
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )

            // Space reserved for sliding indicator dot
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1216)
@Composable
private fun NovelNestFooterHomeSelectedPreview() {
    val sampleDestinations = listOf(
        FooterDestination(route = "home", label = "Home", icon = Icons.Rounded.Home),
        FooterDestination(route = "discover", label = "Discover", icon = Icons.Rounded.Explore),
        FooterDestination(route = "settings", label = "Settings", icon = Icons.Rounded.Settings)
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
        FooterDestination(route = "home", label = "Home", icon = Icons.Rounded.Home),
        FooterDestination(route = "discover", label = "Discover", icon = Icons.Rounded.Explore),
        FooterDestination(route = "settings", label = "Settings", icon = Icons.Rounded.Settings)
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
        FooterDestination(route = "home", label = "Home", icon = Icons.Rounded.Home),
        FooterDestination(route = "discover", label = "Discover", icon = Icons.Rounded.Explore),
        FooterDestination(route = "settings", label = "Settings", icon = Icons.Rounded.Settings)
    )
    NovelNestFooter(
        destinations = sampleDestinations,
        selectedRoute = "settings",
        onNavigate = {}
    )
}

@Preview(name = "Footer - Font Scale 1.5x", fontScale = 1.5f, showBackground = true, backgroundColor = 0xFF0F1216)
@Composable
private fun NovelNestFooterLargeFontPreview() {
    val sampleDestinations = listOf(
        FooterDestination(route = "home", label = "Home", icon = Icons.Rounded.Home),
        FooterDestination(route = "discover", label = "Discover", icon = Icons.Rounded.Explore),
        FooterDestination(route = "settings", label = "Settings", icon = Icons.Rounded.Settings)
    )
    NovelNestFooter(
        destinations = sampleDestinations,
        selectedRoute = "home",
        onNavigate = {}
    )
}
