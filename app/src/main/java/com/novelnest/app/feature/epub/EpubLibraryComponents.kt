package com.novelnest.app.feature.epub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.data.local.epub.*

@Composable
fun EpubLibraryButton(onClick: () -> Unit) {
    Box(Modifier.size(48.dp).testTag("HomeEpubLibrary")
        .semantics { contentDescription = "Open EPUB library" }
        .bounceClick(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text("EPUB", color = NovelPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(NovelPrimary.copy(alpha = 0.10f))
                .border(1.dp, NovelPrimary.copy(alpha = 0.35f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 6.dp))
    }
}

@Composable
internal fun EpubShelfCard(item: EpubShelfItem, repository: EpubRepository, modifier: Modifier = Modifier,
    volumeNumber: Int? = null, selected: Boolean = false, selectionMode: Boolean = false, onClick: () -> Unit, onSelect: () -> Unit) {
    val entry = item.representative
    val isSeries = item is EpubShelfItem.Series
    Column(modifier.testTag("EpubShelf:" + item.key)
        .semantics { if (selectionMode) this.selected = selected }
        .bounceClick(scaleDown = BounceScale.Large, role = Role.Button,
            onClickLabel = (if (selectionMode) "Toggle selection for " else "Open ") + item.title, onLongClickLabel = "Select " + item.title,
            onLongClick = onSelect, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(10.dp))
            .background(NovelSurfaceVariant).border(if (selected) 2.dp else 1.dp, if (selected) NovelPrimary else NovelSurfaceBorder, RoundedCornerShape(10.dp))) {
            if (entry != null) EpubCover(repository, entry.book.id, Modifier.fillMaxSize())
            else Icon(Icons.Rounded.Folder, null, tint = NovelPrimary, modifier = Modifier.size(36.dp).align(Alignment.Center))
            if (isSeries) {
                Surface(color = NovelBackground.copy(alpha = 0.86f), shape = RoundedCornerShape(5.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(5.dp)) {
                    Row(Modifier.padding(horizontal = 5.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Folder, null, tint = NovelPrimary, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(3.dp))
                        Text((item as EpubShelfItem.Series).volumes.size.toString(), color = NovelTextPrimary, fontSize = 10.sp)
                    }
                }
            }
            if (selected) {
                Icon(Icons.Rounded.CheckCircle, "Selected", tint = NovelPrimary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(24.dp)
                        .background(NovelBackground, RoundedCornerShape(12.dp)))
            }
            entry?.position?.let {
                Text((epubProgress(entry) * 100).toInt().toString() + "%", color = NovelPrimary, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 6.dp)
                        .background(NovelBackground.copy(alpha = 0.86f)).padding(horizontal = 5.dp, vertical = 2.dp))
                LinearProgressIndicator(progress = { epubProgress(entry) }, color = NovelPrimary,
                    trackColor = NovelSurfaceBorder, gapSize = 0.dp, drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter))
            }
        }
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = item.title,
                color = NovelTextPrimary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val caption = when (item) {
                is EpubShelfItem.Series -> item.representative?.takeIf { it.position != null }?.book?.let { book ->
                    book.volumeLabel ?: ("Volume " + (item.volumes.indexOfFirst { it.book.id == book.id } + 1))
                } ?: (item.volumes.size.toString() + if (item.volumes.size == 1) " volume" else " volumes")
                is EpubShelfItem.Book -> item.entry.book.volumeLabel ?: volumeNumber?.let { "Volume " + it }
                    ?: item.entry.book.author.ifBlank { "Local EPUB" }
            }
            Text(
                text = caption,
                color = NovelTextSecondary,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun EpubImportStatus(state: EpubImportState, onCancel: () -> Unit, @Suppress("UNUSED_PARAMETER") onDismiss: () -> Unit) {
    if (state !is EpubImportState.Working) return
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, NovelSurfaceBorder),
        colors = CardDefaults.cardColors(containerColor = NovelSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(NovelPrimary.copy(alpha = 0.14f))
                        .border(1.dp, NovelPrimary.copy(alpha = 0.3f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileUpload,
                        contentDescription = null,
                        tint = NovelPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = state.message,
                        color = NovelTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Completed imports are kept if you cancel.",
                        color = NovelTextSecondary,
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel import", color = NovelPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = NovelPrimary,
                trackColor = NovelSurfaceBorder
            )
        }
    }
}
