package com.novelnest.app.core.ui.footer

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import androidx.compose.foundation.selection.selectable
import com.novelnest.app.core.ui.animation.rememberAreAnimationsDisabled
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic
import kotlinx.coroutines.launch

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
    val pillShape = RoundedCornerShape(29.dp)

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

    val selectedIndex = destinations.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    val areAnimationsDisabled = rememberAreAnimationsDisabled()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = FooterTokens.VerticalMargin),
        contentAlignment = Alignment.Center
    ) {
        // Pill-shaped container: Solid background, smooth rounded corners, subtle border and shadow
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 290.dp).fillMaxWidth()
                .height(FooterTokens.PillHeight)
                .then(shadowModifier)
                .clip(pillShape)
                .background(NovelSurface)
                .border(
                    width = 1.dp,
                    color = NovelSurfaceBorder.copy(alpha = 0.65f),
                    shape = pillShape
                )
                .padding(horizontal = 4.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val density = LocalDensity.current
            val totalWidthPx = with(density) { maxWidth.toPx() }
            val count = destinations.size.coerceAtLeast(1)
            val slotWidthPx = totalWidthPx / count
            val pillRestWidthPx = (slotWidthPx - with(density) { 6.dp.toPx() }).coerceAtLeast(0f)
            val halfPillWidthPx = pillRestWidthPx / 2f
            val targetLeft = slotWidthPx * (selectedIndex + 0.5f) - halfPillWidthPx
            val targetRight = slotWidthPx * (selectedIndex + 0.5f) + halfPillWidthPx

            // Geometry changes create one correctly positioned pair. Selection changes
            // retarget that pair instead of competing with an initialization snap effect.
            val leftAnim = remember(totalWidthPx, pillRestWidthPx) { Animatable(targetLeft) }
            val rightAnim = remember(totalWidthPx, pillRestWidthPx) { Animatable(targetRight) }
            var previousIndex by remember(leftAnim) { mutableIntStateOf(selectedIndex) }

            LaunchedEffect(selectedIndex, leftAnim, rightAnim, areAnimationsDisabled) {
                val previous = previousIndex
                previousIndex = selectedIndex
                if (areAnimationsDisabled) {
                    leftAnim.snapTo(targetLeft)
                    rightAnim.snapTo(targetRight)
                    return@LaunchedEffect
                }
                if (leftAnim.value == targetLeft && rightAnim.value == targetRight) return@LaunchedEffect

                // A new selection cancels both old edge jobs together, then continues
                // from their current positions. Rapid taps cannot leave a stale target.
                val adjacent = kotlin.math.abs(selectedIndex - previous) == 1
                val leadingLeft = adjacent && selectedIndex < previous
                val leadingRight = adjacent && selectedIndex > previous
                launch {
                    leftAnim.animateTo(targetLeft, spring(
                        dampingRatio = if (leadingLeft) 0.88f else if (adjacent) 0.85f else 0.88f,
                        stiffness = if (leadingLeft) Spring.StiffnessMedium else Spring.StiffnessMediumLow
                    ))
                }
                launch {
                    rightAnim.animateTo(targetRight, spring(
                        dampingRatio = if (leadingRight) 0.88f else if (adjacent) 0.85f else 0.88f,
                        stiffness = if (leadingRight) Spring.StiffnessMedium else Spring.StiffnessMediumLow
                    ))
                }
            }

            // Read animated edges only during drawing: no per-frame recomposition or
            // remeasurement of navigation items, labels, or click targets.
            Canvas(Modifier.fillMaxSize()) {
                val left = minOf(leftAnim.value, rightAnim.value)
                val width = maxOf(kotlin.math.abs(rightAnim.value - leftAnim.value), pillRestWidthPx)
                val x = if (layoutDirection == LayoutDirection.Ltr) left else size.width - left - width
                drawRoundRect(
                    color = NovelPrimary,
                    topLeft = Offset(x, 0f),
                    size = Size(width, size.height),
                    cornerRadius = CornerRadius(25.dp.toPx())
                )
            }

            // 2. Interactive Navigation Items Row
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEach { destination ->
                    key(destination.route) {
                        val isSelected = destination.route == selectedRoute
                        FooterNavItem(
                            destination = destination,
                            isSelected = isSelected,
                            areAnimationsDisabled = areAnimationsDisabled,
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
}

@Composable
private fun FooterNavItem(
    destination: FooterDestination,
    isSelected: Boolean,
    areAnimationsDisabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val itemShape = RoundedCornerShape(25.dp)

    // Contrast-aware coloring:
    // Active icon & label on solid NovelPrimary indicator pill has crisp dark #0F1216 tint.
    // Inactive icon & label uses NovelTextSecondary.
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF0F1216) else NovelTextSecondary,
        animationSpec = if (areAnimationsDisabled) snap() else tween(durationMillis = 180),
        label = "nav_item_content_color"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(itemShape)
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedNavIcon(
            destination = destination,
            isSelected = isSelected,
            contentColor = contentColor,
            areAnimationsDisabled = areAnimationsDisabled
        )

        Spacer(modifier = Modifier.height(1.dp))

        Text(
            text = destination.label,
            color = contentColor,
            fontSize = 10.5.sp,
            lineHeight = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * Animated navigation icon preserving all signature interactive motions:
 * - Home: Subtle vertical hop that returns smoothly to center + controlled scale pulse + filled transition
 * - Discover: 360-degree compass rotation + filled transition
 * - Settings: Controlled 60-degree mechanical rotation + filled transition
 */
@Composable
private fun AnimatedNavIcon(
    destination: FooterDestination,
    isSelected: Boolean,
    contentColor: Color,
    areAnimationsDisabled: Boolean,
    modifier: Modifier = Modifier
) {
    // 1. Home transient hop: springs up on tap and settles back to 0dp so it stays centered
    val homeHopAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(isSelected, areAnimationsDisabled) {
        if (isSelected && destination.route.contains("home", ignoreCase = true) && !areAnimationsDisabled) {
            homeHopAnim.snapTo(0f)
            homeHopAnim.animateTo(
                targetValue = -3f,
                animationSpec = tween(durationMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            )
            homeHopAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMedium
                )
            )
        } else {
            homeHopAnim.snapTo(0f)
        }
    }

    // 2. Discover & Settings rotation animation (controlled, refined, no wild overshoot)
    val targetRotation = when {
        !isSelected -> 0f
        destination.route.contains("discover", ignoreCase = true) -> 360f
        destination.route.contains("settings", ignoreCase = true) -> 60f
        else -> 0f
    }

    val iconRotation by animateFloatAsState(
        targetValue = if (areAnimationsDisabled) 0f else targetRotation,
        animationSpec = if (areAnimationsDisabled) snap() else spring(
            dampingRatio = 0.86f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nav_icon_rotation"
    )

    // 3. Subtle, refined scale pulse on selection (restrained to 1.06f)
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1.0f,
        animationSpec = if (areAnimationsDisabled) {
            snap()
        } else {
            spring(
                dampingRatio = 0.85f,
                stiffness = Spring.StiffnessMedium
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
                translationY = homeHopAnim.value.dp.toPx()
            }
            .clearAndSetSemantics { }
    ) {
        // Crossfade between unselected (outline) and selected (filled)
        Crossfade(
            targetState = isSelected,
            animationSpec = if (areAnimationsDisabled) snap() else tween(durationMillis = 180),
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
