package com.novelnest.app.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.R
import com.novelnest.app.core.designsystem.NovelBackground
import com.novelnest.app.core.designsystem.NovelGold
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Premium app launch reveal showcasing the attractive NovelNest emblem,
 * choreographed ambient radiance, typography entry, and a seamless dissolve into the main UI.
 * Fully accessible, non-blocking, and immediately skippable on tap or back press.
 */
@Composable
fun NovelNestLaunchReveal(onFinished: () -> Unit) {
    val logoScale = remember { Animatable(0.72f) }
    val logoAlpha = remember { Animatable(0f) }
    val logoTranslationY = remember { Animatable(22f) }

    val sunrayRotation = remember { Animatable(0f) }
    val sunrayScale = remember { Animatable(0.6f) }
    val sunrayAlpha = remember { Animatable(0f) }

    val glowScale = remember { Animatable(0.45f) }
    val glowAlpha = remember { Animatable(0f) }

    val textAlpha = remember { Animatable(0f) }
    val textTranslationY = remember { Animatable(18f) }

    val dividerScale = remember { Animatable(0f) }
    val taglineAlpha = remember { Animatable(0f) }

    val exitAlpha = remember { Animatable(1f) }
    val exitScale = remember { Animatable(1f) }

    val finish by rememberUpdatedState(onFinished)
    val continueLabel = stringResource(R.string.skip_launch_reveal)

    LaunchedEffect(Unit) {
        withFrameNanos { }

        // Phase 1: Logo emergence & warm radial atmosphere with shining sunrays (0ms - 320ms)
        launch {
            logoAlpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        }
        launch {
            sunrayAlpha.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        }
        launch {
            sunrayScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            sunrayRotation.animateTo(
                targetValue = 28f,
                animationSpec = tween(1400, easing = FastOutSlowInEasing)
            )
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            logoTranslationY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            glowAlpha.animateTo(0.85f, tween(400, easing = FastOutSlowInEasing))
        }
        launch {
            glowScale.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }

        // Phase 2: Brand title & diamond divider reveal (220ms - 520ms)
        delay(220)
        launch {
            textAlpha.animateTo(1f, tween(260))
        }
        launch {
            textTranslationY.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
        }
        launch {
            dividerScale.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        }

        // Phase 3: Brand tagline reveal (380ms - 680ms)
        delay(160)
        launch {
            taglineAlpha.animateTo(1f, tween(300))
        }

        // Hold beat for brand recognition and visual polish
        delay(520)

        // Phase 4: Seamless dissolve revealing the main application (1160ms - 1440ms)
        launch {
            exitScale.animateTo(1.04f, tween(280, easing = FastOutSlowInEasing))
        }
        exitAlpha.animateTo(0f, tween(280, easing = FastOutSlowInEasing))

        finish()
    }

    BackHandler { finish() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = exitAlpha.value
                scaleX = exitScale.value
                scaleY = exitScale.value
            }
            .background(NovelBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = continueLabel,
                onClick = { finish() }
            ),
        contentAlignment = Alignment.Center
    ) {
        // 1. Warm Ambient Radial Glow
        Box(
            modifier = Modifier
                .size(380.dp)
                .graphicsLayer {
                    scaleX = glowScale.value
                    scaleY = glowScale.value
                    alpha = glowAlpha.value
                }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NovelPrimary.copy(alpha = 0.28f),
                            NovelPrimary.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // 2. Central Logo & Typography Composition
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Circular Logo Mark with Shining Sunrays and Amber-Gold Bezel
            Box(
                modifier = Modifier.size(210.dp),
                contentAlignment = Alignment.Center
            ) {
                // Layer A: Radiant Golden Sunrays
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = logoScale.value * sunrayScale.value
                            scaleY = logoScale.value * sunrayScale.value
                            alpha = logoAlpha.value * sunrayAlpha.value
                            rotationZ = sunrayRotation.value
                            translationY = logoTranslationY.value
                        }
                ) {
                    val center = this.center
                    val rayCount = 20
                    val innerR = 70.dp.toPx()
                    val outerR = 100.dp.toPx()
                    for (i in 0 until rayCount) {
                        val angleDeg = (i * (360f / rayCount))
                        val rad = Math.toRadians(angleDeg.toDouble())
                        val isMajorRay = i % 2 == 0
                        val curOuterR = if (isMajorRay) outerR else outerR * 0.88f
                        val startX = center.x + innerR * cos(rad).toFloat()
                        val startY = center.y + innerR * sin(rad).toFloat()
                        val endX = center.x + curOuterR * cos(rad).toFloat()
                        val endY = center.y + curOuterR * sin(rad).toFloat()

                        drawLine(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFD54F).copy(alpha = if (isMajorRay) 0.85f else 0.5f),
                                    Color(0xFFE2A765).copy(alpha = 0.4f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = curOuterR
                            ),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = (if (isMajorRay) 3.5.dp else 2.2.dp).toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Layer B: Round Logo Emblem with Amber-Gold Border
                Box(
                    modifier = Modifier
                        .size(142.dp)
                        .graphicsLayer {
                            scaleX = logoScale.value
                            scaleY = logoScale.value
                            alpha = logoAlpha.value
                            translationY = logoTranslationY.value
                        }
                        .clip(CircleShape)
                        .background(NovelBackground)
                        .border(
                            width = 3.5.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFFFFD54F),
                                    NovelPrimary,
                                    Color(0xFFFFF9C4),
                                    NovelGold,
                                    Color(0xFFC78C4B),
                                    Color(0xFFFFD54F)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_novelnest_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Brand Title
            Text(
                text = stringResource(R.string.launch_brand_name),
                color = NovelTextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.5.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha.value
                    translationY = textTranslationY.value
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Decorative Diamond Divider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .width(130.dp)
                    .graphicsLayer {
                        scaleX = dividerScale.value
                        alpha = dividerScale.value
                    }
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = NovelPrimary.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(6.dp)
                        .graphicsLayer { rotationZ = 45f }
                        .background(NovelPrimary, RoundedCornerShape(1.dp))
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = NovelPrimary.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Brand Tagline
            Text(
                text = stringResource(R.string.launch_brand_tagline),
                color = NovelTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                letterSpacing = 1.2.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = taglineAlpha.value
                }
            )
        }
    }
}
