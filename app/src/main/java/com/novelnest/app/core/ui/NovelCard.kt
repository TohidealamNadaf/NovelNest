package com.novelnest.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.novelnest.app.data.model.NovelSourceType

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
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val playHaptic = rememberNovelNestHaptic()

    Column(
        modifier = modifier
            .bounceClick(
                scaleDown = BounceScale.Large,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open ${novel.title}",
                onClick = {
                    playHaptic(NovelNestHapticType.LIGHT_TICK)
                    onClick()
                }
            )
    ) {
        // Poster with cover image, badges, and gradient
        // Ripple and touch indication is strictly contained here and clipped to 12.dp corners
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(12.dp))
                .background(NovelSurfaceVariant)
                .indication(interactionSource, LocalIndication.current)
                .border(1.dp, NovelSurfaceBorder, RoundedCornerShape(12.dp))
        ) {
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

            // Top rating badge
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC161B22))
                    .border(0.5.dp, NovelSurfaceBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = "Rating",
                        tint = NovelGold,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = String.format("%.1f", novel.rating),
                        color = NovelTextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bottom chapter badge
            if (novel.latestChapter.isNotBlank()) {
                Text(
                    text = novel.latestChapter,
                    color = NovelPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title and category captions container with comfortable internal padding
        // Placed outside the ripple zone and free from rounded-corner clipping
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .padding(bottom = 4.dp)
        ) {
            Text(
                text = novel.title,
                style = MaterialTheme.typography.titleMedium,
                color = NovelTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            if (novel.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = novel.genres.take(2).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = NovelTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
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
