package com.novelnest.app.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.ReadingProgressBar
import com.novelnest.app.data.progress.readingCompletion

@Composable
internal fun HomeContinueReadingCard(
    entry: HomeReadingEntry,
    onOpenDetails: () -> Unit,
    onContinue: () -> Unit
) {
    val completion = remember(entry) { entry.novel.readingCompletion(entry.progress) }
    val context = LocalContext.current
    // Both images share one bounded Coil request and its memory/disk caches.
    val coverRequest = remember(entry.novel.coverUrl, context) {
        ImageRequest.Builder(context).data(entry.novel.coverUrl).size(360).build()
    }
    Column {
        Card(onClick = onOpenDetails, shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = NovelSurface),
            border = BorderStroke(1.dp, NovelSurfaceBorder),
            modifier = Modifier.fillMaxWidth()) {
            Box {
                if (entry.novel.coverUrl.isNotBlank()) {
                    AsyncImage(model = coverRequest, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().blur(26.dp))
                }
                Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(
                    NovelSurface.copy(alpha = 0.62f), NovelSurface.copy(alpha = 0.94f)))))
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.width(104.dp).height(154.dp).clip(RoundedCornerShape(10.dp))
                        .background(NovelSurfaceVariant), contentAlignment = Alignment.Center) {
                        if (entry.novel.coverUrl.isBlank()) {
                            Icon(Icons.Rounded.AutoStories, null, tint = NovelPrimary, modifier = Modifier.size(32.dp))
                        } else {
                            AsyncImage(model = coverRequest, contentDescription = entry.novel.title,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(entry.novel.title, style = MaterialTheme.typography.titleLarge, color = NovelTextPrimary,
                            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (entry.novel.author.isNotBlank() && entry.novel.author != "Unknown") {
                            Text(entry.novel.author, style = MaterialTheme.typography.bodySmall, color = NovelTextSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(entry.progress.lastReadChapterTitle.ifBlank { "Chapter ${entry.progress.lastReadChapterIndex + 1}" },
                            style = MaterialTheme.typography.bodySmall, color = NovelTextSecondary,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        ReadingProgressBar(completion)
                        Button(
                            onClick = onContinue,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NovelPrimary,
                                contentColor = NovelOnPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = NovelOnPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Continue",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = NovelOnPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
