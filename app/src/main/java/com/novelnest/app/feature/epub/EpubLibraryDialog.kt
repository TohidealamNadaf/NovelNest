package com.novelnest.app.feature.epub

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.data.local.epub.*
import kotlinx.coroutines.launch

@Composable
fun EpubLibraryDialog(repository: EpubRepository, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val library by repository.library.collectAsStateWithLifecycle()
    val importing by repository.importState.collectAsStateWithLifecycle()
    val libraryError by repository.libraryError.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var removing by remember { mutableStateOf<LocalBookEntry?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(repository::import)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 620.dp),
            shape = RoundedCornerShape(24.dp), color = NovelSurface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Local Books", style = MaterialTheme.typography.titleLarge, color = NovelTextPrimary)
                        Text("EPUB library - Available offline", style = MaterialTheme.typography.bodySmall, color = NovelTextSecondary)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close library", tint = NovelTextSecondary) }
                }
                Button(onClick = {
                    try { picker.launch(arrayOf("application/epub+zip", "application/zip", "application/octet-stream")) }
                    catch (_: android.content.ActivityNotFoundException) { error = "No document picker is available on this device." }
                },
                    enabled = importing !is EpubImportState.Working,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NovelPrimary, contentColor = NovelOnPrimary)) {
                    Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Import EPUB", color = LocalContentColor.current)
                }
                when (val state = importing) {
                    is EpubImportState.Working -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = NovelPrimary)
                        Text(state.message, color = NovelTextSecondary)
                        TextButton(onClick = repository::cancelImport) { Text("Cancel import") }
                    }
                    is EpubImportState.Failed -> Text(state.message, color = NovelError)
                    is EpubImportState.Complete -> Text(if (state.duplicate) "Already in your library. Reading progress was kept." else "Imported: " + state.book.title,
                        color = NovelSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    EpubImportState.Idle -> Unit
                }
                (error ?: libraryError)?.let { Text(it, color = NovelError) }
                if (libraryError != null) TextButton(onClick = repository::reloadLibrary) { Text("Retry library") }
                if (library.isEmpty() && libraryError == null) {
                    Text("Import a DRM-free, reflowable EPUB to start reading. Your original file stays unchanged.",
                        modifier = Modifier.padding(vertical = 20.dp), color = NovelTextSecondary)
                }
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(library, key = { it.book.id }) { entry ->
                        Card(onClick = { onOpen(entry.book.id) }, colors = CardDefaults.cardColors(containerColor = NovelSurfaceVariant)) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                EpubCover(repository, entry.book.id, Modifier.width(44.dp).height(64.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(entry.book.title, color = NovelTextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(entry.book.author.ifBlank { "Unknown author" }, color = NovelTextSecondary,
                                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(entry.position?.let { (it.progression * 100).toInt().toString() + "% read" } ?: "Not started",
                                        color = NovelPrimary, style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = { removing = entry }) {
                                    Icon(Icons.Rounded.DeleteOutline, "Remove " + entry.book.title, tint = NovelTextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    removing?.let { entry ->
        AlertDialog(onDismissRequest = { if (!deleting) removing = null },
            title = { Text("Remove imported book?") },
            text = { Text("This removes the app's copy of " + entry.book.title + " and its reading position. The original EPUB is kept.") },
            confirmButton = { TextButton(enabled = !deleting, onClick = {
                deleting = true
                scope.launch {
                    try { repository.delete(entry.book.id); removing = null }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { error = "Could not remove the book. Please try again."; removing = null }
                    finally { deleting = false }
                }
            }) { Text(if (deleting) "Removing..." else "Remove") } },
            dismissButton = { TextButton(enabled = !deleting, onClick = { removing = null }) { Text("Cancel") } })
    }
}

@Composable
internal fun EpubCover(repository: EpubRepository, id: String, modifier: Modifier = Modifier) {
    val cover = remember(id, repository) { repository.coverFile(id) }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(NovelSurfaceVariant), contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = NovelPrimary)
        AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun EpubContinueReadingCard(entry: LocalBookEntry, repository: EpubRepository, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NovelSurface)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            EpubCover(repository, entry.book.id, Modifier.width(88.dp).height(132.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.book.title, color = NovelTextPrimary, style = MaterialTheme.typography.titleLarge,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(entry.position?.chapterTitle?.takeIf { it.isNotBlank() } ?: "Local EPUB", color = NovelTextSecondary,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(progress = { entry.position?.progression?.toFloat() ?: 0f }, modifier = Modifier.fillMaxWidth(), color = NovelPrimary)
                Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = NovelPrimary, contentColor = NovelOnPrimary)) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Continue", color = LocalContentColor.current)
                }
            }
        }
    }
}
