package com.novelnest.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.LocalIndication
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.novelnest.app.core.designsystem.NovelBackground
import com.novelnest.app.core.designsystem.NovelGold
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.ratingLabel
import com.novelnest.app.data.model.hasRating
import com.novelnest.app.data.model.chapterCountLabel
import com.novelnest.app.data.progress.ReadingCompletion
import kotlin.math.ceil
import kotlin.math.sqrt
import com.novelnest.app.data.model.NovelSourceType

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Check
import com.novelnest.app.core.designsystem.NovelOnPrimary

/**
 * Modern novel poster card component.
 * - Restricts ripple / touch indication specifically to the rounded poster surface.
 * - Prevents touch feedback from washing over novel title and category captions.
 * - Eliminates rounded-corner text clipping with dedicated internal padding and spacing.
 * - Responds with tactile spring bounce across the entire card area on press.
 */
@Composable
fun NovelCard(
    novel: Novel,
    modifier: Modifier = Modifier.width(135.dp),
    isSelected: Boolean = false,
    readingCompletion: ReadingCompletion? = null,
    isCompact: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    val chapterBadge = readingCompletion?.let {
        if (it.total > 0) "${it.read} / ${it.total}" else "${it.read} read"
    } ?: novel.chapterCountLabel()
    val interactionSource = remember { MutableInteractionSource() }
    val playHaptic = rememberNovelNestHaptic()
    val cardCornerRadius = if (isCompact) 8.dp else 12.dp

    Column(
        modifier = modifier
            .bounceClick(
                scaleDown = BounceScale.Large,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open ${novel.title}",
                onLongClickLabel = if (onLongClick != null) "Select ${novel.title}" else null,
                onLongClick = onLongClick?.let { action ->
                    {
                        playHaptic(NovelNestHapticType.ACTION_CONFIRM)
                        action()
                    }
                },
                onClick = {
                    playHaptic(NovelNestHapticType.LIGHT_TICK)
                    onClick()
                }
            )
    ) {
        // Poster with cover image, badges, and gradient
        // Ripple and touch indication is strictly contained here and clipped to corners
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(cardCornerRadius))
                .background(NovelSurfaceVariant)
                .indication(interactionSource, LocalIndication.current)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) NovelPrimary else NovelSurfaceBorder,
                    shape = RoundedCornerShape(cardCornerRadius)
                )
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(NovelPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = NovelOnPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(novel.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = novel.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Bottom gradient overlay for chapter visibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x60000000),
                                Color(0xDD0F1216)
                            ),
                            startY = 100f
                        )
                    )
            )

            // Top rank badge
            if (novel.rank > 0) {
                Box(
                    modifier = Modifier
                        .padding(if (isCompact) 3.dp else 6.dp)
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(if (isCompact) 4.dp else 6.dp))
                        .background(Color(0xD90F1216))
                        .border(
                            0.5.dp,
                            if (novel.rank <= 3) NovelGold.copy(alpha = 0.8f) else NovelSurfaceBorder,
                            RoundedCornerShape(if (isCompact) 4.dp else 6.dp)
                        )
                        .padding(horizontal = if (isCompact) 4.dp else 5.dp, vertical = if (isCompact) 1.dp else 2.dp)
                ) {
                    Text(
                        text = "#${novel.rank}",
                        color = if (novel.rank <= 3) NovelGold else NovelTextPrimary,
                        fontSize = if (isCompact) 8.5.sp else 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Top rating badge
            Box(
                modifier = Modifier
                    .padding(if (isCompact) 3.dp else 6.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(if (isCompact) 4.dp else 6.dp))
                    .background(Color(0xCC161B22))
                    .border(0.5.dp, NovelSurfaceBorder, RoundedCornerShape(if (isCompact) 4.dp else 6.dp))
                    .padding(horizontal = if (isCompact) 4.dp else 5.dp, vertical = if (isCompact) 1.dp else 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = if (novel.hasRating()) "Rating" else "Not rated",
                        tint = NovelGold,
                        modifier = Modifier.size(if (isCompact) 8.dp else 10.dp)
                    )
                    Text(
                        text = novel.ratingLabel(),
                        color = NovelTextPrimary,
                        fontSize = if (isCompact) 8.5.sp else 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bottom chapter badge
            if (chapterBadge.isNotBlank()) {
                Text(
                    text = chapterBadge,
                    color = NovelPrimary,
                    fontSize = if (isCompact) 8.5.sp else 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            horizontal = if (isCompact) 4.dp else 6.dp,
                            vertical = if (isCompact) 4.dp else 7.dp
                        )
                )
            }
            if (readingCompletion != null) {
                PosterReadingProgress(
                    completion = readingCompletion,
                    cornerRadius = cardCornerRadius,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 6.dp))

        // Title and category captions container with comfortable internal padding
        // Placed outside the ripple zone and free from rounded-corner clipping
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isCompact) 2.dp else 4.dp)
                .padding(bottom = if (isCompact) 2.dp else 4.dp)
        ) {
            Text(
                text = novel.title,
                style = if (isCompact) {
                    MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = NovelTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = if (isCompact) 15.sp else 18.sp
            )

            if (novel.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = novel.genres.take(if (isCompact) 1 else 2).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (isCompact) 9.5.sp else 11.sp
                    ),
                    color = NovelTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Measure the fill against the visible track, rather than the part hidden by poster corners. */
@Composable
private fun PosterReadingProgress(
    completion: ReadingCompletion,
    cornerRadius: Dp,
    modifier: Modifier = Modifier
) {
    val fraction = completion.fraction
    Spacer(modifier.progressSemantics(fraction).drawWithCache {
        val radius = cornerRadius.toPx().coerceIn(0f, size.width / 2f)
        val fill = Path().apply {
            moveTo(0f, 0f)
            // The poster clips this strip into its two bottom corner arcs. At each
            // height, use the remaining visible width so small percentages survive.
            // Cache the path; scrolling does not rebuild it or scan chapter history.
            val steps = ceil(size.height.toDouble()).toInt().coerceAtLeast(1)
            for (step in 0..steps) {
                val y = size.height * step / steps
                val fromCenter = (radius - size.height + y).coerceIn(0f, radius)
                val inset = radius - sqrt((radius * radius - fromCenter * fromCenter).coerceAtLeast(0f))
                lineTo(inset + (size.width - 2f * inset) * fraction, y)
            }
            lineTo(0f, size.height)
            close()
        }
        onDrawBehind {
            drawRect(NovelSurfaceBorder)
            if (fraction >= 1f) drawRect(NovelPrimary)
            else if (fraction > 0f) drawPath(fill, NovelPrimary)
        }
    })
}

@Preview(name = "NovelCard - Standard", showBackground = true, backgroundColor = 0xFF0F1216)
@Composable
private fun NovelCardStandardPreview() {
    NovelCard(
        novel = Novel(
            id = "shadow-slave-preview",
            title = "Shadow Slave",
            sourceUrl = "https://example.com/shadow-slave",
            coverUrl = "",
            author = "Guiltythree",
            synopsis = "Growing up in poverty, Sunny was not expecting anything good from life.",
            rating = 4.8f,
            latestChapter = "Ch. 1890",
            genres = listOf("Action", "Fantasy", "Mystery"),
            source = NovelSourceType.NOVEL_PHOENIX
        )
    )
}

@Preview(name = "NovelCard - Long Title & Categories", showBackground = true, backgroundColor = 0xFF0F1216)
@Composable
private fun NovelCardLongTitlePreview() {
    NovelCard(
        novel = Novel(
            id = "long-title-preview",
            title = "The Legendary Moonlight Sculptor and the Dawn of the Virtual World",
            sourceUrl = "https://example.com/lms",
            coverUrl = "",
            author = "Nam Heesung",
            synopsis = "A legendary story of perseverance.",
            rating = 4.9f,
            latestChapter = "Ch. 580 (Final)",
            genres = listOf("Action", "Adventure", "Virtual Reality", "Comedy"),
            source = NovelSourceType.FREE_WEB_NOVEL
        )
    )
}

@Preview(name = "NovelCard - Font Scale 1.5x", fontScale = 1.5f, showBackground = true, backgroundColor = 0xFF0F1216)
@Composable
private fun NovelCardLargeFontPreview() {
    NovelCard(
        novel = Novel(
            id = "large-font-preview",
            title = "Reverend Insanity",
            sourceUrl = "https://example.com/reverend-insanity",
            coverUrl = "",
            author = "Gu Zhen Ren",
            synopsis = "Fang Yuan was reborn 500 years into the past.",
            rating = 4.9f,
            latestChapter = "Ch. 2334",
            genres = listOf("Xianxia", "Martial Arts"),
            source = NovelSourceType.ASURA_SCANS
        )
    )
}
