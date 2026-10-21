package com.novelnest.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection

@Composable
fun NovelSectionRow(
    section: NovelSection,
    modifier: Modifier = Modifier,
    onNovelClick: (Novel) -> Unit = {},
    onSeeAllClick: () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val sectionIcon = when {
                        section.id.contains("trending", ignoreCase = true) ||
                            section.title.contains("Trending", ignoreCase = true) -> Icons.Rounded.Whatshot
                        section.id.contains("latest", ignoreCase = true) ||
                            section.title.contains("Recently", ignoreCase = true) ||
                            section.title.contains("Fresh", ignoreCase = true) -> Icons.Rounded.Update
                        section.id.contains("ranking", ignoreCase = true) ||
                            section.title.contains("Rankings", ignoreCase = true) ||
                            section.title.contains("Hot", ignoreCase = true) -> Icons.Rounded.Leaderboard
                        section.id.contains("manhwa", ignoreCase = true) ||
                            section.title.contains("Manhwa", ignoreCase = true) -> Icons.Rounded.Bolt
                        section.id.contains("hero", ignoreCase = true) ||
                            section.title.contains("Featured", ignoreCase = true) ||
                            section.title.contains("Highlights", ignoreCase = true) -> Icons.Rounded.AutoAwesome
                        else -> Icons.Rounded.AutoStories
                    }

                    Icon(
                        imageVector = sectionIcon,
                        contentDescription = null,
                        tint = NovelPrimary,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = NovelTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!section.subtitle.isNullOrBlank()) {
                    Text(
                        text = section.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = NovelTextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 28.dp, top = 2.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.bounceClick(
                    scaleDown = BounceScale.Small,
                    onClickLabel = "See all ${section.title}",
                    role = Role.Button,
                    onClick = onSeeAllClick
                )
            ) {
                Text(
                    text = "See all",
                    color = NovelPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "See all",
                    tint = NovelPrimary,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal list of novel cards
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(section.novels, key = { it.id }) { novel ->
                NovelCard(
                    novel = novel,
                    onClick = { onNovelClick(novel) }
                )
            }
        }
    }
}
