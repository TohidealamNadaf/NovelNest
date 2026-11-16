package com.novelnest.app.core.ui.footer

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import com.novelnest.app.core.ui.animation.rememberAreAnimationsDisabled
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic
import kotlinx.coroutines.launch

private val ActiveFooterContentColor = Color(0xFF0F1216)

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
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
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
                .padding(horizontal = 4.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val count = destinations.size.coerceAtLeast(1)
            val targetSlot = selectedIndex.toFloat()

            // Animate in normalized slot coordinates (0f..count-1f) so layout measurement never
            // requires BoxWithConstraints/SubcomposeLayout or recreates Animatable instances.
            val leftSlotAnim = remember { Animatable(targetSlot, visibilityThreshold = 0.0005f) }
            val rightSlotAnim = remember { Animatable(targetSlot, visibilityThreshold = 0.0005f) }

            LaunchedEffect(targetSlot, areAnimationsDisabled) {
                if (areAnimationsDisabled) {
                    leftSlotAnim.snapTo(targetSlot)
                    rightSlotAnim.snapTo(targetSlot)
                    return@LaunchedEffect
                }
                if (leftSlotAnim.value == targetSlot && rightSlotAnim.value == targetSlot) {
                    return@LaunchedEffect
                }

                // Critically damped leading/trailing springs produce an organic capsule stretch
                // without underdamped overshoot kinks when settling into the target slot.
                val currentMid = (leftSlotAnim.value + rightSlotAnim.value) * 0.5f
                val movingLeft = targetSlot < currentMid
                val movingRight = targetSlot > currentMid

                launch {
                    leftSlotAnim.animateTo(
                        targetValue = targetSlot,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = if (movingLeft) 680f else 440f,
                            visibilityThreshold = 0.0005f
                        )
                    )
                }
                launch {
                    rightSlotAnim.animateTo(
                        targetValue = targetSlot,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = if (movingRight) 680f else 440f,
                            visibilityThreshold = 0.0005f
                        )
                    )
                }
            }

            // Read animated edges only during drawing: zero per-frame recomposition or
            // remeasurement of navigation items, labels, or click targets.
            Canvas(Modifier.fillMaxSize()) {
                val slotWidthPx = size.width / count
                val sideInsetPx = 3.dp.toPx()
                val pillRestWidthPx = (slotWidthPx - sideInsetPx * 2f).coerceAtLeast(0f)
                val rawLeft = minOf(leftSlotAnim.value, rightSlotAnim.value) * slotWidthPx + sideInsetPx
                val rawRight = (maxOf(leftSlotAnim.value, rightSlotAnim.value) + 1f) * slotWidthPx - sideInsetPx
                val width = maxOf(rawRight - rawLeft, pillRestWidthPx)
                val x = if (layoutDirection == LayoutDirection.Ltr) rawLeft else size.width - rawLeft - width
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

    // Single float state read inside graphicsLayer during draw phase: avoids 60/120fps
    // recompositions of FooterNavItem, Icon, and Text while NavHost transitions pages.
    val selectionProgress = animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = if (areAnimationsDisabled) {
            snap()
        } else {
            tween(durationMillis = 190, easing = FastOutSlowInEasing)
        },
        label = "nav_item_selection_progress"
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
            selectionProgress = selectionProgress,
            areAnimationsDisabled = areAnimationsDisabled
        )

        Spacer(modifier = Modifier.height(1.dp))

        Text(
            text = destination.label,
            color = if (isSelected) ActiveFooterContentColor else NovelTextSecondary,
            fontSize = 10.5.sp,
            lineHeight = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                alpha = if (isSelected) {
                    0.75f + 0.25f * selectionProgress.value
                } else {
                    1f - 0.15f * selectionProgress.value
                }
            }
        )
    }
}

/**
 * Animated navigation icon preserving all signature interactive motions purely in the draw layer:
 * - Home: Subtle vertical hop that returns smoothly to center + controlled scale pulse + filled transition
 * - Discover: 360-degree compass rotation on selection + filled transition (without backward spin on deselect)
 * - Settings: Controlled 60-degree mechanical rotation on selection + filled transition
 */
@Composable
private fun AnimatedNavIcon(
    destination: FooterDestination,
    isSelected: Boolean,
    selectionProgress: State<Float>,
    areAnimationsDisabled: Boolean,
    modifier: Modifier = Modifier
) {
    val isHome = remember(destination.route) { destination.route.contains("home", ignoreCase = true) }
    val isDiscover = remember(destination.route) { destination.route.contains("discover", ignoreCase = true) }
    val isSettings = remember(destination.route) { destination.route.contains("settings", ignoreCase = true) }

    // 1. Home transient hop & Discover/Settings forward-only rotation on selection
    val homeHopAnim = remember { Animatable(0f) }
    val rotationAnim = remember { Animatable(0f) }

    LaunchedEffect(isSelected, areAnimationsDisabled) {
        if (!isSelected || areAnimationsDisabled) {
            homeHopAnim.snapTo(0f)
            if (!isDiscover) rotationAnim.snapTo(0f)
            return@LaunchedEffect
        }
        when {
            isHome -> {
                homeHopAnim.snapTo(0f)
                homeHopAnim.animateTo(
                    targetValue = -2.8f,
                    animationSpec = tween(durationMillis = 95, easing = FastOutSlowInEasing)
                )
                homeHopAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.88f,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            isDiscover -> {
                rotationAnim.snapTo(0f)
                rotationAnim.animateTo(
                    targetValue = 360f,
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                rotationAnim.snapTo(0f)
            }
            isSettings -> {
                rotationAnim.snapTo(0f)
                rotationAnim.animateTo(
                    targetValue = 60f,
                    animationSpec = spring(
                        dampingRatio = 0.88f,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }

    // 2. Subtle, refined scale pulse on selection (read only inside graphicsLayer)
    val iconScaleState = animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1.0f,
        animationSpec = if (areAnimationsDisabled) {
            snap()
        } else {
            spring(
                dampingRatio = 0.88f,
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
                val scale = iconScaleState.value
                scaleX = scale
                scaleY = scale
                rotationZ = rotationAnim.value
                translationY = homeHopAnim.value.dp.toPx()
            }
            .clearAndSetSemantics { }
    ) {
        // Pre-mounted unselected & selected icons crossfaded on the GPU via graphicsLayer alpha:
        // zero Crossfade subtree allocations and zero recompositions during tab transitions.
        Icon(
            imageVector = destination.unselectedIcon,
            contentDescription = null,
            tint = NovelTextSecondary,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { alpha = 1f - selectionProgress.value }
        )
        Icon(
            imageVector = destination.selectedIcon,
            contentDescription = null,
            tint = ActiveFooterContentColor,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { alpha = selectionProgress.value }
        )
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
