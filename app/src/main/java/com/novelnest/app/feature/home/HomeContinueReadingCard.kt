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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionTitle("Continue Reading", "Pick up where you left off")
        Card(onClick = onOpenDetails, shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = NovelSurface),
            border = BorderStroke(1.dp, NovelSurfaceBorder),
            modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.width(104.dp).height(154.dp).clip(RoundedCornerShape(10.dp))
                    .background(NovelSurfaceVariant), contentAlignment = Alignment.Center) {
                    if (entry.novel.coverUrl.isBlank()) {
                        Icon(Icons.Rounded.AutoStories, null, tint = NovelPrimary, modifier = Modifier.size(32.dp))
                    } else {
                        AsyncImage(model = entry.novel.coverUrl, contentDescription = entry.novel.title,
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
                    Button(onClick = onContinue, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Continue")
                    }
                }
            }
        }
    }
}
