package com.novelnest.app.feature.epub

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.data.local.epub.*
import kotlinx.coroutines.launch

@Composable
fun EpubLibraryDialog(repository: EpubRepository, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val library by repository.library.collectAsStateWithLifecycle()
    val importing by repository.importState.collectAsStateWithLifecycle()
    val libraryError by repository.libraryError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var removing by remember { mutableStateOf<LocalBookEntry?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(EpubImportContract()) { uris ->
        if (uris.isNotEmpty() && !repository.importBooks(uris))
            error = "Another import is running, or the selection exceeds the batch limit."
    }

    LaunchedEffect(importing) {
        when (val state = importing) {
            is EpubImportState.Complete -> {
                val msg = if (state.importedCount + state.duplicateCount + state.failedCount > 1) {
                    "${state.importedCount} imported, ${state.duplicateCount} already saved, ${state.failedCount} failed. " + state.message.orEmpty()
                } else if (state.duplicate) {
                    "Already in your library. Reading progress was kept."
                } else {
                    "Imported \"${state.book.title}\""
                }
                Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show()
                repository.clearImportMessage()
            }
            is EpubImportState.Failed -> {
                Toast.makeText(context.applicationContext, state.message, Toast.LENGTH_SHORT).show()
                repository.clearImportMessage()
            }
            else -> Unit
        }
    }

    LaunchedEffect(error) {
        val err = error
        if (!err.isNullOrBlank()) {
            Toast.makeText(context.applicationContext, err, Toast.LENGTH_SHORT).show()
            error = null
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .widthIn(max = 420.dp)
                .heightIn(max = 620.dp),
            shape = RoundedCornerShape(24.dp),
            color = NovelSurface,
            border = BorderStroke(1.dp, NovelSurfaceBorder),
            shadowElevation = 18.dp
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Local Books",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = NovelTextPrimary
                    )
                    NovelNestIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Close library",
                        tint = NovelTextSecondary,
                        onClick = onDismiss
                    )
                }

                Button(
                    onClick = {
                        try {
                            Toast.makeText(context, "Long-press a file in the picker to select multiple EPUBs.", Toast.LENGTH_LONG).show()
                            picker.launch(Unit)
                        } catch (_: android.content.ActivityNotFoundException) {
                            error = "No document picker is available on this device."
                        } catch (_: SecurityException) {
                            error = "The document picker could not grant access. Select the EPUBs again."
                        }
                    },
                    enabled = importing !is EpubImportState.Working,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NovelPrimary, contentColor = NovelOnPrimary)
                ) {
                    Icon(Icons.Rounded.Add, null, tint = NovelOnPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import EPUBs", color = NovelOnPrimary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }

                if (importing is EpubImportState.Working) {
                    val workingState = importing as EpubImportState.Working
                    Surface(
                        color = NovelSurfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, NovelSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = NovelPrimary,
                                trackColor = NovelSurfaceBorder
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = workingState.message,
                                    color = NovelTextSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = repository::cancelImport) {
                                    Text("Cancel import", color = NovelPrimary)
                                }
                            }
                        }
                    }
                }

                if (libraryError != null) {
                    Surface(
                        color = NovelError.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NovelError.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = libraryError.orEmpty(),
                                color = NovelError,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = repository::reloadLibrary) {
                                Text("Retry library", color = NovelPrimary)
                            }
                        }
                    }
                }

                if (library.isEmpty() && libraryError == null) {
                    Surface(
                        color = NovelSurfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, NovelSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Import a DRM-free, reflowable EPUB to start reading. Your original file stays unchanged.",
                            modifier = Modifier.padding(16.dp),
                            color = NovelTextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(library, key = { it.book.id }) { entry ->
                        Card(
                            onClick = { onOpen(entry.book.id) },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, NovelSurfaceBorder),
                            colors = CardDefaults.cardColors(containerColor = NovelSurfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                EpubCover(
                                    repository = repository,
                                    id = entry.book.id,
                                    modifier = Modifier.width(44.dp).height(64.dp),
                                    title = entry.book.title,
                                    author = entry.book.author,
                                    volumeLabel = entry.book.volumeLabel,
                                    seriesId = entry.book.collectionId
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = entry.book.title,
                                        color = NovelTextPrimary,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = entry.book.author.ifBlank { "Unknown author" },
                                        color = NovelTextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = entry.position?.let { (it.progression * 100).toInt().toString() + "% read" } ?: "Not started",
                                        color = NovelPrimary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                NovelNestIconButton(
                                    icon = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Remove " + entry.book.title,
                                    tint = NovelTextSecondary,
                                    onClick = { removing = entry }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    removing?.let { entry ->
        Dialog(
            onDismissRequest = { if (!deleting) removing = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 400.dp),
                shape = RoundedCornerShape(16.dp),
                color = NovelSurface,
                border = BorderStroke(1.dp, NovelSurfaceBorder),
                shadowElevation = 12.dp
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Remove imported book?",
                        color = NovelTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "This removes the app's copy of " + entry.book.title + " and its reading position. The original EPUB is kept.",
                        color = NovelTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { removing = null },
                            enabled = !deleting
                        ) {
                            Text(
                                text = "Cancel",
                                color = NovelTextSecondary,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = !deleting,
                            onClick = {
                                deleting = true
                                scope.launch {
                                    try {
                                        repository.delete(entry.book.id)
                                        Toast.makeText(
                                            context.applicationContext,
                                            "EPUB removed from this device",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        removing = null
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        error = "Could not remove the book. Please try again."
                                        removing = null
                                    } finally {
                                        deleting = false
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NovelError,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = if (deleting) "Removing..." else "Remove",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberEpubCoverRequest(repository: EpubRepository, id: String, size: Int? = null): ImageRequest? {
    val context = LocalContext.current
    val snapshot by remember(repository, id) { repository.observeCover(id) }.collectAsStateWithLifecycle(initialValue = null)
    return remember(snapshot, context, size) {
        snapshot?.let { cover ->
            ImageRequest.Builder(context).data(cover.file)
                .memoryCacheKey("epub-cover2|" + id + "|" + cover.modifiedAt + "|" + cover.byteCount)
                .apply { if (size != null) size(size) }.build()
        }
    }
}

@Composable
internal fun EpubCover(
    repository: EpubRepository,
    id: String,
    modifier: Modifier = Modifier,
    title: String = "",
    author: String = "",
    volumeLabel: String? = null,
    seriesId: String? = null
) {
    val cover = rememberEpubCoverRequest(repository, id)
    var coverLoaded by remember(id, cover) { mutableStateOf(false) }
    val palette = remember(id, seriesId) { epubCoverPalette(seriesId ?: id) }
    val background = remember(palette) { Brush.linearGradient(listOf(palette.top, palette.middle, palette.bottom)) }
    val glow = remember(palette) { Brush.radialGradient(listOf(palette.accent.copy(alpha = 0.16f), Color.Transparent)) }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        val isMini = maxWidth < 60.dp
        val isCompact = maxWidth < 96.dp

        if (!coverLoaded) {
            if (title.isBlank()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(if (isMini) 18.dp else 26.dp)
                )
            } else {
                // Ambient top-right warm glow
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            glow
                        )
                )

                // Book spine crease along left edge
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(if (isMini) 3.dp else 5.dp)
                        .background(palette.accent.copy(alpha = 0.28f))
                )
                if (!isMini) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 6.dp)
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(NovelSurfaceBorder.copy(alpha = 0.7f))
                    )
                }

                // Inner ornamental frame
                if (!isMini) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                            .border(
                                width = 0.8.dp,
                                color = palette.accent.copy(alpha = 0.24f),
                                shape = RoundedCornerShape(5.dp)
                            )
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = if (isMini) 6.dp else 14.dp,
                            end = if (isMini) 4.dp else 10.dp,
                            top = if (isMini) 5.dp else 10.dp,
                            bottom = if (isMini) 5.dp else 12.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    if (!isMini) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(if (isCompact) 10.dp else 12.dp)
                                )
                                Text(
                                    text = (volumeLabel?.takeIf { it.isNotBlank() } ?: "EPUB").uppercase(),
                                    color = palette.accent,
                                    fontSize = if (isCompact) 7.5.sp else 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(if (isCompact) 20.dp else 28.dp)
                                    .height(1.dp)
                                    .background(palette.accent.copy(alpha = 0.45f))
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(1.dp))
                    }

                    Text(
                        text = title,
                        color = NovelTextPrimary,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = when {
                            isMini -> 8.sp
                            isCompact -> 10.sp
                            else -> 11.5.sp
                        },
                        lineHeight = when {
                            isMini -> 10.sp
                            isCompact -> 13.sp
                            else -> 15.sp
                        },
                        textAlign = TextAlign.Center,
                        maxLines = if (isMini) 3 else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!isMini && author.isNotBlank()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(if (isCompact) 16.dp else 22.dp)
                                    .height(0.8.dp)
                                    .background(NovelSurfaceBorder)
                            )
                            Text(
                                text = author,
                                color = palette.accent.copy(alpha = 0.85f),
                                fontSize = if (isCompact) 7.5.sp else 8.5.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }
            }
        }

        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onLoading = { coverLoaded = false },
            onSuccess = { coverLoaded = true },
            onError = { coverLoaded = false },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun EpubContinueReadingCard(entry: LocalBookEntry, repository: EpubRepository, seriesName: String? = null, enabled: Boolean = true, onOpen: () -> Unit) {
    val coverRequest = rememberEpubCoverRequest(repository, entry.book.id, size = 360)
    Card(
        onClick = onOpen,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, NovelSurfaceBorder),
        colors = CardDefaults.cardColors(containerColor = NovelSurface)
    ) {
        Box {
            // Warm ambient fallback glow for EPUBs without an extracted cover image
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                NovelPrimary.copy(alpha = 0.20f),
                                NovelSurfaceVariant.copy(alpha = 0.65f),
                                NovelSurface
                            )
                        )
                    )
            )
            AsyncImage(
                model = coverRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(26.dp)
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                NovelSurface.copy(alpha = 0.62f),
                                NovelSurface.copy(alpha = 0.94f)
                            )
                        )
                    )
            )
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                EpubCover(
                    repository = repository,
                    id = entry.book.id,
                    modifier = Modifier.width(88.dp).height(132.dp),
                    title = entry.book.title,
                    author = entry.book.author,
                    volumeLabel = entry.book.volumeLabel,
                    seriesId = entry.book.collectionId
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.book.title, color = NovelTextPrimary, style = MaterialTheme.typography.titleLarge,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    seriesName?.let {
                        Text(it + entry.book.volumeLabel?.let { label -> " - " + label }.orEmpty(), color = NovelPrimary,
                            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(entry.position?.chapterTitle?.takeIf { it.isNotBlank() } ?: "Local EPUB", color = NovelTextSecondary,
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Reading progress uses a continuous track without Material's trailing stop dot.
                    LinearProgressIndicator(
                        progress = { epubProgress(entry) },
                        modifier = Modifier.fillMaxWidth(),
                        color = NovelPrimary,
                        trackColor = NovelSurfaceBorder,
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )
                    Button(
                        onClick = onOpen,
                        enabled = enabled,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NovelPrimary, contentColor = NovelOnPrimary)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = NovelOnPrimary)
                        Spacer(Modifier.width(6.dp))
                        Text("Continue", color = NovelOnPrimary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}
