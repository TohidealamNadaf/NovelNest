package com.novelnest.app.core.ui.footer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.animation.bounceClick

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
 */
@Composable
fun NovelNestFooter(
    destinations: List<FooterDestination>,
    selectedRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating pill container
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(29.dp),
                    ambientColor = Color.Black,
                    spotColor = NovelPrimary.copy(alpha = 0.25f)
                )
                .clip(RoundedCornerShape(29.dp))
                .background(NovelSurface.copy(alpha = 0.96f))
                .border(
                    width = 1.dp,
                    color = NovelSurfaceBorder.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(29.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { destination ->
                FooterNavItem(
                    icon = destination.icon,
                    label = destination.label,
                    isSelected = destination.route == selectedRoute,
                    onClick = { onNavigate(destination.route) },
                    modifier = Modifier.weight(1f)
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
    modifier: Modifier = Modifier
) {
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "footer_icon_scale"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) NovelPrimary else NovelTextSecondary,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "footer_color"
    )

    val bgPillColor by animateColorAsState(
        targetValue = if (isSelected) NovelSurfaceVariant.copy(alpha = 0.7f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "footer_pill_bg"
    )

    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(bgPillColor)
            .bounceClick(scaleDown = 0.92f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier
                    .size(22.dp)
                    .scale(iconScale)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )

            // Glowing indicator dot when active
            if (isSelected) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(NovelPrimary)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1216)
@Composable
private fun NovelNestFooterHomeSelectedPreview() {
    val sampleDestinations = listOf(
        FooterDestination(route = "home", label = "Home", icon = Icons.Rounded.Home),
        FooterDestination(route = "discover", label = "Discover", icon = Icons.Rounded.Explore)
    )
    NovelNestFooter(
        destinations = sampleDestinations,
        selectedRoute = "home",
        onNavigate = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1216)
@Composable
private fun NovelNestFooterDiscoverSelectedPreview() {
    val sampleDestinations = listOf(
        FooterDestination(route = "home", label = "Home", icon = Icons.Rounded.Home),
        FooterDestination(route = "discover", label = "Discover", icon = Icons.Rounded.Explore)
    )
    NovelNestFooter(
        destinations = sampleDestinations,
        selectedRoute = "discover",
        onNavigate = {}
    )
}
