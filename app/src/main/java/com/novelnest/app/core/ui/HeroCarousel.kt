package com.novelnest.app.core.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.Locale
import kotlin.math.absoluteValue
import com.novelnest.app.core.designsystem.NovelGold
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSecondary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.NovelNestPagerIndicator
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.core.ui.animation.rememberAreAnimationsDisabled
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.formattedViews
import com.novelnest.app.data.model.ratingLabel
import com.novelnest.app.data.model.hasRating
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun HeroCarousel(
    novels: List<Novel>,
    modifier: Modifier = Modifier,
    onNovelClick: (Novel) -> Unit = {},
    onNovelVisibility: (Novel, Boolean) -> Unit = { _, _ -> },
    isVisible: Boolean = true,
    isParentScrolling: Boolean = false
) {
    if (novels.isEmpty()) return

    val fontScale = LocalDensity.current.fontScale
    val slideHeight = 424.dp + 110.dp * (fontScale - 1f).coerceAtLeast(0f)
    val actualCount = novels.size
    val isLooping = actualCount > 1
    val virtualPageCount = if (isLooping) actualCount * 1000 else actualCount
    val initialVirtualPage = if (isLooping) {
        val mid = virtualPageCount / 2
        mid - (mid % actualCount)
    } else 0
    val pagerState = rememberPagerState(
        initialPage = initialVirtualPage,
        pageCount = { virtualPageCount }
    )
    // Rating enrichment must not restart the timer or replace a page's image request.
    val pageKeys = remember(novels) {
        val seen = HashSet<String>(novels.size)
        novels.mapIndexed { index, novel ->
            val base = "${novel.source.name}:${novel.id}"
            if (seen.add(base)) base else "$base#$index"
        }
    }
    var activePressCount by remember { mutableIntStateOf(0) }
    val isPressed = activePressCount > 0
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val parentScrolling by rememberUpdatedState(isParentScrolling)
    val animationsDisabled = rememberAreAnimationsDisabled()

    val visibleNovel = novels.getOrNull(Math.floorMod(pagerState.settledPage, actualCount))
    DisposableEffect(visibleNovel?.source, visibleNovel?.id, isVisible) {
        if (isVisible) visibleNovel?.let { onNovelVisibility(it, true) }
        onDispose { if (isVisible) visibleNovel?.let { onNovelVisibility(it, false) } }
    }

    // Wait for a full idle interval after any gesture or dot navigation. Each slide owns
    // a child job: pager mutation cancellation must not terminate the auto-advance loop.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(pagerState, pageKeys, isVisible, lifecycle, animationsDisabled) {
        if (!isVisible || pageKeys.size < 2 || animationsDisabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            fun isIdle() = !pagerState.isScrollInProgress && !isDragged && !isPressed && !parentScrolling
            while (isActive) {
                snapshotFlow { isIdle() }.first { it }
                val interrupted = withTimeoutOrNull(5000L) {
                    snapshotFlow { isIdle() }.first { !it }
                }
                if (interrupted != null) continue
                launch {
                    if (isIdle() && pagerState.pageCount > 1) {
                        val nextPage = (pagerState.settledPage + 1) % pagerState.pageCount
                        pagerState.animateScrollToPage(nextPage, animationSpec = tween(600))
                    }
                }.join()
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            key = { page ->
                val actualIdx = Math.floorMod(page, actualCount)
                "${pageKeys.getOrElse(actualIdx) { actualIdx }}@$page"
            },
            contentPadding = PaddingValues(horizontal = if (isLooping) 32.dp else 18.dp),
            pageSpacing = 4.dp,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxWidth()
                .height(slideHeight)
        ) { page ->
            val actualIndex = Math.floorMod(page, actualCount)
            val novel = novels.getOrNull(actualIndex) ?: return@HorizontalPager
            val pageZIndex by remember(pagerState, page) {
                androidx.compose.runtime.derivedStateOf {
                    val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        .absoluteValue
                        .coerceIn(0f, 2f)
                    2f - distance
                }
            }
            Box(modifier = Modifier.zIndex(pageZIndex)) {
                HeroSlideCard(
                    novel = novel,
                    pageIndex = page,
                    displayRankIndex = actualIndex,
                    pagerState = pagerState,
                    onPressChanged = { pressed ->
                        activePressCount = (activePressCount + if (pressed) 1 else -1).coerceAtLeast(0)
                    },
                    onClick = { onNovelClick(novel) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Animated Pager Indicator Dots (Smooth real-time morphing & tap navigation)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            NovelNestPagerIndicator(
                pagerState = pagerState,
                pageCount = novels.size,
                dotHeight = 5.dp,
                activeDotWidth = 22.dp,
                inactiveDotWidth = 5.dp,
                spacing = 6.dp
            )
        }
    }
}

@Composable
private fun HeroSlideCard(
    novel: Novel,
    pageIndex: Int,
    displayRankIndex: Int,
    pagerState: PagerState,
    onPressChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(context, novel.coverUrl) {
        ImageRequest.Builder(context).data(novel.coverUrl).crossfade(true).build()
    }
    val cardInteractionSource = remember { MutableInteractionSource() }
    val readInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()
    val isReadPressed by readInteractionSource.collectIsPressedAsState()
    val isAnyPressed = isCardPressed || isReadPressed
    val currentOnPressChanged by rememberUpdatedState(onPressChanged)
    DisposableEffect(isAnyPressed) {
        if (isAnyPressed) currentOnPressChanged(true)
        onDispose { if (isAnyPressed) currentOnPressChanged(false) }
    }

    val outerCardShape = RoundedCornerShape(22.dp)
    val innerDockShape = RoundedCornerShape(16.dp)
    val rankLabel = remember(displayRankIndex, novel.rank) {
        val rankNum = if (novel.rank > 0) novel.rank else displayRankIndex + 1
        String.format(Locale.ROOT, "#%02d Trending", rankNum)
    }
    val formattedReads = remember(novel.views) { novel.formattedViews() }
    val subtitleMeta = remember(novel.genres, novel.latestChapter, novel.synopsis) {
        when {
            novel.genres.isNotEmpty() -> novel.genres.take(3).joinToString(" • ")
            novel.latestChapter.isNotBlank() -> novel.latestChapter
            else -> novel.synopsis
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // relativeOffset: -1f = left neighbor, 0f = active center card, +1f = right neighbor
                val relativeOffset = ((pageIndex - pagerState.currentPage) - pagerState.currentPageOffsetFraction)
                    .coerceIn(-1f, 1f)
                val pageOffset = relativeOffset.absoluteValue

                // Pivot from the lower-inner spine edge so adjacent cards fan outward and tuck
                // underneath the center card like books stacked on top of one another
                transformOrigin = TransformOrigin(
                    pivotFractionX = (0.5f - relativeOffset * 0.35f).coerceIn(0.15f, 0.85f),
                    pivotFractionY = 0.82f
                )
                cameraDistance = 14f * density
                rotationZ = relativeOffset * 6.5f
                rotationY = -relativeOffset * 12f
                translationX = -relativeOffset * 22.dp.toPx()
                translationY = pageOffset * 10.dp.toPx()
                scaleX = lerp(1f, 0.91f, pageOffset)
                scaleY = lerp(1f, 0.87f, pageOffset)
                alpha = lerp(1f, 0.62f, pageOffset)
                shadowElevation = lerp(20.dp.toPx(), 6.dp.toPx(), pageOffset)
                shape = outerCardShape
                clip = true
            }
            .background(NovelSurfaceVariant)
            .border(1.dp, NovelSurfaceBorder.copy(alpha = 0.85f), outerCardShape)
            .bounceClick(
                scaleDown = BounceScale.ExtraLarge,
                interactionSource = cardInteractionSource,
                onClick = onClick
            )
    ) {
        // 1. Tall vertical cover artwork filling the card stage
        AsyncImage(
            model = imageRequest,
            contentDescription = novel.title,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(0f, -0.35f),
            modifier = Modifier.fillMaxSize()
        )

        // 2. Subtle lower-half atmospheric darkening behind the floating dock
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.45f to Color(0x1A0F1216),
                        0.72f to Color(0xB30F1216),
                        1.0f to Color(0xEB0F1216)
                    )
                )
        )

        // 3. Floating Inset Rounded Editorial Dock with Ambient Cover Glow
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .clip(innerDockShape)
                .background(NovelSurface)
                .border(1.dp, Color(0xFF2B323E), innerDockShape)
        ) {
            if (novel.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .blur(26.dp)
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                NovelSurface.copy(alpha = 0.64f),
                                NovelSurface.copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                // Top Pill Row inside Dock: Solid Amber Gold [#01 Trending] + Source Label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(NovelPrimary)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = NovelOnPrimary,
                            modifier = Modifier.size(11.5.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = rankLabel,
                            color = NovelOnPrimary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Text(
                        text = novel.source.displayName,
                        color = NovelPrimary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Main Dock Content Row: Taller Mini 2:3 Poster on Left + Title, Genres, Stats & Full-Width CTA on Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Miniature uncropped 2:3 portrait cover (increased height)
                    Box(
                        modifier = Modifier
                            .width(74.dp)
                            .height(110.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NovelSurfaceVariant)
                            .border(0.75.dp, NovelSurfaceBorder, RoundedCornerShape(10.dp))
                    ) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Title
                        Text(
                            text = novel.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = NovelTextPrimary,
                            fontSize = 15.5.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Genres / Chapter line (e.g. "Action • Fantasy • Adventure" or "Chapter 112")
                        if (subtitleMeta.isNotBlank()) {
                            Text(
                                text = subtitleMeta,
                                color = NovelTextSecondary,
                                fontSize = 11.5.sp,
                                lineHeight = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Rating & Reads line: ★ 4.7 (38.4M reads)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = if (novel.hasRating()) "Rating" else "Not rated",
                                tint = NovelGold,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = novel.ratingLabel(),
                                color = NovelTextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (formattedReads.isNotBlank()) {
                                Text(
                                    text = "($formattedReads reads)",
                                    color = Color(0xFFB1BAC4),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else if (novel.genres.isNotEmpty() && novel.latestChapter.isNotBlank()) {
                                Text(
                                    text = "• ${novel.latestChapter}",
                                    color = NovelSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        // Full-Width Warm Amber Gold "Read Now" Pill Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(NovelPrimary)
                                .bounceClick(
                                    scaleDown = BounceScale.Medium,
                                    interactionSource = readInteractionSource,
                                    onClick = onClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Read Now",
                                color = NovelOnPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
