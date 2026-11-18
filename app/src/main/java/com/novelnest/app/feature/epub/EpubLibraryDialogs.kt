package com.novelnest.app.feature.epub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.NovelNestIconButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
internal fun EpubLibraryDialogs(
    dialog: String?,
    selected: EpubShelfItem?,
    state: EpubLibraryUiState,
    operation: EpubLibraryOperation,
    onDismiss: () -> Unit,
    onChooseAction: (String) -> Unit,
    viewModel: EpubLibraryViewModel
) {
    if (dialog == null) return
    val group = selected as? EpubShelfItem.Series ?: state.collection?.let {
        EpubShelfItem.Series(it, state.items.mapNotNull { item -> (item as? EpubShelfItem.Book)?.entry })
    }
    val book = selected as? EpubShelfItem.Book

    when (dialog) {
        "item-options", "series-options" -> {
            val headerTitle = selected?.title ?: group?.title ?: "Library options"
            EpubModalContainer(
                title = headerTitle,
                onDismiss = onDismiss
            ) {
                if (group != null && book == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        EpubMenuOptionRow(
                            icon = Icons.Rounded.Edit,
                            title = "Rename series",
                            onClick = { onChooseAction("rename-series") }
                        )
                        EpubMenuOptionRow(
                            icon = Icons.Rounded.SwapVert,
                            title = "Reorder volumes",
                            enabled = group.volumes.size > 1,
                            onClick = { onChooseAction("reorder") }
                        )
                        EpubMenuOptionRow(
                            icon = Icons.Rounded.DeleteOutline,
                            title = "Remove series",
                            isDestructive = true,
                            onClick = { onChooseAction("delete-series") }
                        )
                    }
                } else if (book != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        EpubMenuOptionRow(
                            icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                            title = "Move to series",
                            onClick = { onChooseAction("move") }
                        )
                        EpubMenuOptionRow(
                            icon = Icons.Rounded.BookmarkBorder,
                            title = "Edit volume label",
                            onClick = { onChooseAction("label") }
                        )
                        EpubMenuOptionRow(
                            icon = Icons.Rounded.DeleteOutline,
                            title = "Remove EPUB from device",
                            isDestructive = true,
                            onClick = { onChooseAction("delete-book") }
                        )
                    }
                } else {
                    Text(
                        text = "This item is no longer in the library.",
                        color = NovelTextSecondary,
                        fontSize = 13.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    EpubModalDismissButton(
                        text = "Close",
                        onClick = onDismiss
                    )
                }
            }
        }

        "create", "rename-series", "label" -> {
            val initial = when (dialog) {
                "label" -> book?.entry?.book?.volumeLabel.orEmpty()
                "rename-series" -> group?.title.orEmpty()
                else -> ""
            }
            val key = dialog + (book?.key ?: group?.key).orEmpty()
            var text by rememberSaveable(key) { mutableStateOf(initial) }
            val max = if (dialog == "label") 128 else 160
            val canSave = !operation.busy && (dialog == "label" || text.isNotBlank())

            fun save() {
                when (dialog) {
                    "create" -> viewModel.createSeries(text)
                    "rename-series" -> group?.let { viewModel.renameSeries(it.collection.id, text) }
                    "label" -> book?.let { viewModel.labelVolume(it.entry.book.id, text) }
                }
            }

            val modalTitle = when (dialog) {
                "create" -> "Create series"
                "label" -> "Volume label"
                else -> "Rename series"
            }

            EpubModalContainer(
                title = modalTitle,
                onDismiss = onDismiss
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(max) },
                        singleLine = true,
                        enabled = !operation.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("EpubLibraryNameInput"),
                        shape = RoundedCornerShape(12.dp),
                        label = {
                            Text(
                                text = if (dialog == "label") "For example, Volume 2" else "Series name",
                                color = NovelTextSecondary
                            )
                        },
                        supportingText = if (dialog == "label") {
                            {
                                Text(
                                    text = "Leave blank to use its position in the series.",
                                    color = NovelTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        } else null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = NovelSurfaceVariant,
                            unfocusedContainerColor = NovelSurfaceVariant,
                            disabledContainerColor = NovelSurfaceVariant.copy(alpha = 0.6f),
                            focusedTextColor = NovelTextPrimary,
                            unfocusedTextColor = NovelTextPrimary,
                            focusedBorderColor = NovelPrimary,
                            unfocusedBorderColor = NovelSurfaceBorder,
                            focusedLabelColor = NovelPrimary,
                            unfocusedLabelColor = NovelTextSecondary,
                            cursorColor = NovelPrimary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (canSave) save() })
                    )

                    if (operation.failed && !operation.message.isNullOrBlank()) {
                        Text(
                            text = operation.message,
                            color = NovelError,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EpubModalDismissButton(
                        text = "Cancel",
                        enabled = !operation.busy,
                        onClick = onDismiss
                    )
                    Spacer(Modifier.width(8.dp))
                    EpubModalPrimaryButton(
                        text = if (operation.busy) "Saving..." else "Save",
                        enabled = canSave,
                        onClick = ::save
                    )
                }
            }
        }

        "move" -> if (book != null) {
            var target by rememberSaveable(book.key) { mutableStateOf(book.entry.book.collectionId) }
            EpubModalContainer(
                title = "Move EPUB",
                onDismiss = onDismiss
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        item {
                            DestinationRow(
                                label = "My Library (standalone)",
                                selected = target == null,
                                onClick = { target = null }
                            )
                        }
                        itemsIndexed(state.collections, key = { _, it -> it.id }) { _, collection ->
                            DestinationRow(
                                label = collection.name,
                                selected = target == collection.id,
                                onClick = { target = collection.id }
                            )
                        }
                    }

                    if (state.collections.isEmpty()) {
                        Text(
                            text = "Create a series from the library header to group volumes.",
                            color = NovelTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    if (operation.failed && !operation.message.isNullOrBlank()) {
                        Text(
                            text = operation.message,
                            color = NovelError,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EpubModalDismissButton(
                        text = "Cancel",
                        enabled = !operation.busy,
                        onClick = onDismiss
                    )
                    Spacer(Modifier.width(8.dp))
                    EpubModalPrimaryButton(
                        text = "Move",
                        enabled = !operation.busy,
                        onClick = { viewModel.moveBook(book.entry.book.id, target) }
                    )
                }
            }
        }

        "reorder" -> if (group != null) {
            EpubReorderVolumesEditor(
                group = group,
                operation = operation,
                viewModel = viewModel,
                onDismiss = onDismiss
            )
        }

        "delete-series", "delete-book" -> {
            val isDeleteSeries = dialog == "delete-series"
            EpubModalContainer(
                title = if (isDeleteSeries) "Remove series?" else "Remove imported EPUB?",
                onDismiss = onDismiss
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isDeleteSeries) {
                            "The folder will be removed. Every volume and its reading position will stay in My Library."
                        } else {
                            "This removes the app's copy and its reading position. Your original EPUB file is kept."
                        },
                        color = NovelTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )

                    if (operation.failed && !operation.message.isNullOrBlank()) {
                        Text(
                            text = operation.message,
                            color = NovelError,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EpubModalDismissButton(
                        text = "Cancel",
                        enabled = !operation.busy,
                        onClick = onDismiss
                    )
                    Spacer(Modifier.width(8.dp))
                    EpubModalPrimaryButton(
                        text = if (operation.busy) "Removing..." else "Remove",
                        enabled = !operation.busy,
                        isDestructive = true,
                        onClick = {
                            if (isDeleteSeries) group?.let { viewModel.removeSeries(it.collection.id) }
                            else book?.let { viewModel.removeBook(it.entry.book.id) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EpubModalContainer(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
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
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = title,
                    color = NovelTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                content()
            }
        }
    }
}

@Composable
private fun EpubMenuOptionRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val itemColor = if (isDestructive) NovelError else NovelTextPrimary
    val iconColor = if (isDestructive) NovelError else NovelPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            color = itemColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DestinationRow(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) NovelPrimary.copy(alpha = 0.10f) else Color.Transparent)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = NovelPrimary,
                unselectedColor = NovelTextSecondary
            )
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = if (selected) NovelPrimary else NovelTextPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EpubModalPrimaryButton(
    text: String,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = if (isDestructive) NovelError else NovelPrimary
    val textColor = if (isDestructive) Color.White else NovelOnPrimary
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = textColor,
            disabledContainerColor = containerColor.copy(alpha = 0.35f),
            disabledContentColor = textColor.copy(alpha = 0.6f)
        )
    ) {
        Text(
            text = text,
            color = if (enabled) textColor else textColor.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun EpubModalDismissButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled
    ) {
        Text(
            text = text,
            color = if (enabled) NovelTextSecondary else NovelTextTertiary,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
        )
    }
}


/** Confirmation captures IDs, not a list of publications, cover bitmaps or reader locators. */
@Composable
internal fun EpubBatchDialog(dialog: String?, keys: List<String>, state: EpubLibraryUiState,
    operation: EpubLibraryOperation, viewModel: EpubLibraryViewModel, onDismiss: () -> Unit) {
    if (dialog !in setOf("batch-move", "batch-remove", "batch-detach")) return
    val keySet = remember(keys) { keys.toHashSet() }
    val selected = remember(state.items, keySet) { state.items.filter { it.key in keySet } }
    val books = selected.filterIsInstance<EpubShelfItem.Book>().map { it.entry.book.id }
    val collections = selected.filterIsInstance<EpubShelfItem.Series>().map { it.collection.id }
    val stale = selected.size != keys.size || selected.isEmpty()
    var target by rememberSaveable(keys) { mutableStateOf<String?>(null) }
    val moving = dialog == "batch-move"
    val detaching = dialog == "batch-detach"
    val validDetach = !detaching || (viewModel.collectionId != null && collections.isEmpty() &&
        selected.filterIsInstance<EpubShelfItem.Book>().all { it.entry.book.collectionId == viewModel.collectionId })
    val validTarget = target == null || state.collections.any { it.id == target }
    EpubModalContainer(title = if (moving) "Move selected EPUBs" else if (detaching) "Remove from series?" else "Remove selected items?", onDismiss = onDismiss) {
        if (moving) {
            LazyColumn(Modifier.heightIn(max = 280.dp)) {
                item { DestinationRow("My Library (standalone)", target == null, enabled = !operation.busy) { target = null } }
                itemsIndexed(state.collections, key = { _, it -> it.id }) { _, collection ->
                    DestinationRow(collection.name, target == collection.id, enabled = !operation.busy) { target = collection.id }
                }
            }
            Text("Reading positions and volume labels are kept. Volumes are appended in shelf order.",
                color = NovelTextSecondary, style = MaterialTheme.typography.bodySmall)
        } else if (detaching) {
            Text(books.size.toString() + " selected EPUBs will move to My Library as standalone books. Their files, volume labels and reading positions are kept.",
                color = NovelTextSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            if (books.isNotEmpty()) Text(books.size.toString() + " selected EPUB copies and their reading positions will be removed. Original files are kept.",
                color = NovelTextSecondary, style = MaterialTheme.typography.bodySmall)
            if (collections.isNotEmpty()) Text(collections.size.toString() + " series folders will be removed. Their unselected volumes and reading positions stay in My Library.",
                color = NovelTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        if (!operation.busy && (stale || !validTarget || !validDetach)) Text("The library changed. Close this window and select the items again.", color = NovelError,
            style = MaterialTheme.typography.bodySmall)
        if (operation.failed) Text(operation.message.orEmpty(), color = NovelError, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            EpubModalDismissButton("Cancel", !operation.busy, onDismiss)
            Spacer(Modifier.width(8.dp))
            EpubModalPrimaryButton(text = if (operation.busy) "Saving..." else if (moving) "Move" else if (detaching) "Remove from series" else "Remove",
                enabled = !operation.busy && !stale && validDetach && (!moving || (collections.isEmpty() && validTarget)),
                isDestructive = !moving && !detaching, onClick = {
                    if (moving) viewModel.moveSelection(books, target)
                    else if (detaching) viewModel.removeSelectionFromSeries(books)
                    else viewModel.removeSelection(books, collections)
                })
        }
    }
}

/**
 * Full-height dedicated "Reorder volumes" editor with:
 * - Visible 48x48dp drag handles (lift, follow finger, live slot swap, and edge auto-scroll)
 * - Tappable 48x48dp position number badge -> "Move to position" dialog with "Move to first" / "Move to last"
 * - Accessible 48x48dp Up/Down buttons with interaction state bound to permanent book ID
 * - Undo for the last move + LiveRegionMode.Polite position announcement + scroll-into-view
 */
@Composable
private fun EpubReorderVolumesEditor(
    group: EpubShelfItem.Series,
    operation: EpubLibraryOperation,
    viewModel: EpubLibraryViewModel,
    onDismiss: () -> Unit
) {
    var orderedIds by rememberSaveable(group.key) { mutableStateOf(group.volumes.map { it.book.id }) }
    var undoOrder by rememberSaveable(group.key) { mutableStateOf<List<String>?>(null) }
    var lastMoveMessage by rememberSaveable(group.key) { mutableStateOf<String?>(null) }
    var moveDialogBookId by rememberSaveable(group.key) { mutableStateOf<String?>(null) }

    val entries = remember(group.volumes) { group.volumes.associateBy { it.book.id } }
    val changed = remember(orderedIds, entries) { orderedIds.toSet() != entries.keys }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Drag-and-drop state tracked by permanent book ID
    var draggedBookId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragStartOrder by remember { mutableStateOf<List<String>?>(null) }
    // Read the mutable draft directly: pointer events may precede the next recomposition.

    fun displayTitleFor(bookId: String): String {
        val book = entries[bookId]?.book ?: return "Volume"
        return book.volumeLabel?.takeIf { it.isNotBlank() } ?: book.title
    }

    fun moveBookToIndex(bookId: String, targetIndex: Int, scrollIntoView: Boolean = true) {
        val currentList = orderedIds
        val fromIndex = currentList.indexOf(bookId)
        val clampedTarget = targetIndex.coerceIn(0, currentList.lastIndex.coerceAtLeast(0))
        if (fromIndex == -1 || fromIndex == clampedTarget) return
        undoOrder = currentList
        val updated = currentList.toMutableList().apply {
            add(clampedTarget, removeAt(fromIndex))
        }
        orderedIds = updated
        lastMoveMessage = "Moved \"${displayTitleFor(bookId)}\" to position ${clampedTarget + 1}"
        if (scrollIntoView) {
            scope.launch {
                val visibleIndices = listState.layoutInfo.visibleItemsInfo.map { it.index }
                if (clampedTarget !in visibleIndices) {
                    listState.animateScrollToItem(clampedTarget)
                }
            }
        }
    }

    // Automatic edge scrolling when dragging a volume near the top or bottom of the viewport
    val edgeThresholdPx = with(density) { 72.dp.toPx() }
    val maxAutoScrollPx = with(density) { 14.dp.toPx() }
    LaunchedEffect(draggedBookId) {
        val activeId = draggedBookId ?: return@LaunchedEffect
        while (isActive && draggedBookId == activeId) {
            val layoutInfo = listState.layoutInfo
            val draggedItem = layoutInfo.visibleItemsInfo.firstOrNull { it.key == activeId }
            if (draggedItem != null) {
                val itemTop = draggedItem.offset + dragOffsetY
                val itemBottom = itemTop + draggedItem.size
                val viewportTop = layoutInfo.viewportStartOffset.toFloat()
                val viewportBottom = layoutInfo.viewportEndOffset.toFloat()

                val scrollDelta = when {
                    itemTop < viewportTop + edgeThresholdPx && listState.canScrollBackward -> {
                        val fraction = ((viewportTop + edgeThresholdPx - itemTop) / edgeThresholdPx).coerceIn(0.15f, 1f)
                        -maxAutoScrollPx * fraction
                    }
                    itemBottom > viewportBottom - edgeThresholdPx && listState.canScrollForward -> {
                        val fraction = ((itemBottom - (viewportBottom - edgeThresholdPx)) / edgeThresholdPx).coerceIn(0.15f, 1f)
                        maxAutoScrollPx * fraction
                    }
                    else -> 0f
                }

                if (scrollDelta != 0f) {
                    val consumed = listState.scrollBy(scrollDelta)
                    if (consumed != 0f) {
                        dragOffsetY += consumed
                        // Check if scrolling brought a new neighbor under the dragged center
                        val updatedInfo = listState.layoutInfo
                        val currentItem = updatedInfo.visibleItemsInfo.firstOrNull { it.key == activeId }
                        if (currentItem != null) {
                            val centerY = currentItem.offset + (currentItem.size / 2f) + dragOffsetY
                            val targetItem = updatedInfo.visibleItemsInfo.firstOrNull { item ->
                                item.key != activeId && centerY.toInt() in item.offset..(item.offset + item.size)
                            }
                            if (targetItem != null) {
                                val targetId = targetItem.key as? String
                                val list = orderedIds
                                val fromIdx = list.indexOf(activeId)
                                val toIdx = if (targetId != null) list.indexOf(targetId) else -1
                                if (fromIdx != -1 && toIdx != -1 && fromIdx != toIdx) {
                                    dragOffsetY += (currentItem.offset - targetItem.offset)
                                    orderedIds = list.toMutableList().apply {
                                        add(toIdx, removeAt(fromIdx))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            delay(16L)
        }
    }

    Dialog(
        onDismissRequest = { if (!operation.busy) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = NovelBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                // ─── FULL-HEIGHT EDITOR TOP BAR (Cancel + Title + Save order) ───
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NovelNestIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Cancel",
                        tint = NovelTextSecondary,
                        enabled = !operation.busy,
                        onClick = onDismiss
                    )

                    Spacer(Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reorder volumes",
                            color = NovelTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "${group.title} • ${orderedIds.size} volumes",
                            color = NovelTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    TextButton(
                        onClick = onDismiss,
                        enabled = !operation.busy,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            color = if (!operation.busy) NovelTextSecondary else NovelTextTertiary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    EpubModalPrimaryButton(
                        text = if (operation.busy) "Saving..." else "Save order",
                        enabled = !operation.busy && !changed,
                        onClick = { viewModel.reorder(group.collection.id, orderedIds) }
                    )
                }

                HorizontalDivider(color = NovelSurfaceBorder.copy(alpha = 0.65f))

                // ─── HELPER / UNDO & LIVE-REGION ANNOUNCEMENT BAR ───
                Surface(
                    color = NovelSurface,
                    border = BorderStroke(0.5.dp, NovelSurfaceBorder.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (lastMoveMessage != null) {
                            Text(
                                text = lastMoveMessage.orEmpty(),
                                color = NovelTextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { liveRegion = LiveRegionMode.Polite }
                            )
                        } else {
                            Text(
                                text = "Drag the handle to move nearby, or tap a position number to jump.",
                                color = NovelTextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (undoOrder != null) {
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    val restored = undoOrder ?: return@TextButton
                                    orderedIds = restored
                                    undoOrder = null
                                    lastMoveMessage = "Undid last volume move"
                                },
                                enabled = !operation.busy,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                                    contentDescription = null,
                                    tint = NovelPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "Undo",
                                    color = NovelPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }

                // ─── STALE / ERROR BANNERS ───
                if (changed) {
                    Surface(
                        color = NovelPrimary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, NovelPrimary.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Volumes changed while this editor was open.",
                                color = NovelPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    orderedIds = group.volumes.map { it.book.id }
                                    undoOrder = null
                                    lastMoveMessage = null
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Reload volumes", color = NovelPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                if (operation.failed && !operation.message.isNullOrBlank()) {
                    Text(
                        text = operation.message,
                        color = NovelError,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // ─── REORDERABLE VOLUME LIST ───
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(orderedIds, key = { _, id -> id }) { index, id ->
                        val entry = entries[id]
                        val isDragged = draggedBookId == id
                        val currentIndexState by rememberUpdatedState(index)

                        // Key interaction sources by permanent book ID so press state never sticks to row index
                        val positionInteraction = remember(id) { MutableInteractionSource() }
                        val upInteraction = remember(id) { MutableInteractionSource() }
                        val downInteraction = remember(id) { MutableInteractionSource() }

                        val rowModifier = if (isDragged) {
                            Modifier
                                .zIndex(10f)
                                .graphicsLayer {
                                    translationY = dragOffsetY
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                    shadowElevation = 18.dp.toPx()
                                }
                        } else {
                            Modifier
                                .zIndex(0f)
                                .animateItem()
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDragged) NovelSurfaceVariant else NovelSurface,
                            border = BorderStroke(
                                width = if (isDragged) 1.5.dp else 1.dp,
                                color = if (isDragged) NovelPrimary else NovelSurfaceBorder
                            ),
                            modifier = rowModifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Visible 48x48dp Drag Handle (exclusively owns vertical drag gesture)
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .semantics {
                                            contentDescription = "Drag handle for ${displayTitleFor(id)}"
                                        }
                                        .pointerInput(id, operation.busy) {
                                            if (operation.busy) return@pointerInput
                                            detectDragGestures(
                                                onDragStart = {
                                                    dragStartOrder = orderedIds
                                                    draggedBookId = id
                                                    dragOffsetY = 0f
                                                },
                                                onDragEnd = {
                                                    val startOrder = dragStartOrder
                                                    if (startOrder != null && startOrder != orderedIds) {
                                                        undoOrder = startOrder
                                                        val newPos = orderedIds.indexOf(id) + 1
                                                        lastMoveMessage = "Moved \"${displayTitleFor(id)}\" to position $newPos"
                                                    }
                                                    draggedBookId = null
                                                    dragOffsetY = 0f
                                                    dragStartOrder = null
                                                },
                                                onDragCancel = {
                                                    dragStartOrder?.let { orderedIds = it }
                                                    draggedBookId = null
                                                    dragOffsetY = 0f
                                                    dragStartOrder = null
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetY += dragAmount.y
                                                    val visibleItems = listState.layoutInfo.visibleItemsInfo
                                                    val currentItem = visibleItems.firstOrNull { it.key == id }
                                                    if (currentItem != null) {
                                                        val draggedCenterY = currentItem.offset + (currentItem.size / 2f) + dragOffsetY
                                                        val targetItem = visibleItems.firstOrNull { item ->
                                                            item.key != id && draggedCenterY.toInt() in item.offset..(item.offset + item.size)
                                                        }
                                                        if (targetItem != null) {
                                                            val targetId = targetItem.key as? String
                                                            val list = orderedIds
                                                            val fromIdx = list.indexOf(id)
                                                            val toIdx = if (targetId != null) list.indexOf(targetId) else -1
                                                            if (fromIdx != -1 && toIdx != -1 && fromIdx != toIdx) {
                                                                dragOffsetY += (currentItem.offset - targetItem.offset)
                                                                orderedIds = list.toMutableList().apply {
                                                                    add(toIdx, removeAt(fromIdx))
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragIndicator,
                                        contentDescription = null,
                                        tint = if (isDragged) NovelPrimary else NovelTextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // 2. Tappable 48x48dp Position Number Badge -> opens "Move to position"
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(
                                            interactionSource = positionInteraction,
                                            indication = ripple(),
                                            enabled = !operation.busy,
                                            role = Role.Button,
                                            onClickLabel = "Move to position",
                                            onClick = { moveDialogBookId = id }
                                        )
                                        .semantics {
                                            contentDescription = "Position ${index + 1}. Tap to move to position"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = NovelPrimary.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, NovelPrimary.copy(alpha = 0.45f))
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            color = NovelPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(6.dp))

                                // 3. Book Cover + Title & Volume Label
                                if (entry != null) {
                                    EpubCover(
                                        repository = viewModel.repository,
                                        id = entry.book.id,
                                        modifier = Modifier
                                            .width(38.dp)
                                            .height(54.dp),
                                        title = entry.book.title,
                                        author = entry.book.author,
                                        volumeLabel = entry.book.volumeLabel,
                                        seriesId = entry.book.collectionId
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = entry?.book?.title ?: "Removed EPUB",
                                        color = NovelTextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val volLabel = entry?.book?.volumeLabel?.takeIf { it.isNotBlank() }
                                        ?: "Volume ${index + 1}"
                                    Text(
                                        text = "$volLabel • Tap #${index + 1} to jump",
                                        color = NovelTextSecondary,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // 4. Non-overlapping 48x48dp Up/Down Controls bound to permanent book ID
                                IconButton(
                                    onClick = { moveBookToIndex(id, currentIndexState - 1, scrollIntoView = false) },
                                    enabled = index > 0 && !operation.busy,
                                    interactionSource = upInteraction,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowUpward,
                                        contentDescription = "Move volume up",
                                        tint = if (index > 0 && !operation.busy) NovelPrimary else NovelTextTertiary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { moveBookToIndex(id, currentIndexState + 1, scrollIntoView = false) },
                                    enabled = index < orderedIds.lastIndex && !operation.busy,
                                    interactionSource = downInteraction,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDownward,
                                        contentDescription = "Move volume down",
                                        tint = if (index < orderedIds.lastIndex && !operation.busy) NovelPrimary else NovelTextTertiary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ─── "MOVE TO POSITION" DIALOG (Move to first, Move to last, or Enter position 1..N) ───
    val targetBookId = moveDialogBookId
    if (targetBookId != null) {
        val currentIdx = orderedIds.indexOf(targetBookId)
        if (currentIdx == -1) {
            moveDialogBookId = null
        } else {
            MoveToPositionDialog(
                bookTitle = displayTitleFor(targetBookId),
                currentPosition = currentIdx + 1,
                totalCount = orderedIds.size,
                onMoveTo = { newOneBasedPos ->
                    moveDialogBookId = null
                    moveBookToIndex(targetBookId, newOneBasedPos - 1, scrollIntoView = true)
                },
                onDismiss = { moveDialogBookId = null }
            )
        }
    }
}

@Composable
private fun MoveToPositionDialog(
    bookTitle: String,
    currentPosition: Int,
    totalCount: Int,
    onMoveTo: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var positionInput by rememberSaveable(bookTitle, currentPosition) {
        mutableStateOf(currentPosition.toString())
    }
    val parsedPosition = positionInput.toIntOrNull()
    val isValidPosition = parsedPosition != null && parsedPosition in 1..totalCount && parsedPosition != currentPosition

    EpubModalContainer(
        title = "Move to position",
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "\"$bookTitle\" is currently at position $currentPosition of $totalCount.",
                color = NovelTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            // Quick Placement Actions: Move to first / Move to last
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onMoveTo(1) },
                    enabled = currentPosition > 1,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NovelSurfaceBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = NovelSurfaceVariant,
                        contentColor = NovelPrimary,
                        disabledContentColor = NovelTextTertiary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerticalAlignTop,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Move to first", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { onMoveTo(totalCount) },
                    enabled = currentPosition < totalCount,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NovelSurfaceBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = NovelSurfaceVariant,
                        contentColor = NovelPrimary,
                        disabledContentColor = NovelTextTertiary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerticalAlignBottom,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Move to last", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            OutlinedTextField(
                value = positionInput,
                onValueChange = { input ->
                    positionInput = input.filter { it in '0'..'9' }.take(4)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                label = {
                    Text(
                        text = "Enter position (1–$totalCount)",
                        color = NovelTextSecondary
                    )
                },
                supportingText = {
                    if (positionInput.isNotEmpty() && (parsedPosition == null || parsedPosition !in 1..totalCount)) {
                        Text(
                            text = "Enter a number between 1 and $totalCount.",
                            color = NovelError,
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            text = "Jump directly without repeated arrow taps.",
                            color = NovelTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = NovelSurfaceVariant,
                    unfocusedContainerColor = NovelSurfaceVariant,
                    focusedTextColor = NovelTextPrimary,
                    unfocusedTextColor = NovelTextPrimary,
                    focusedBorderColor = NovelPrimary,
                    unfocusedBorderColor = NovelSurfaceBorder,
                    focusedLabelColor = NovelPrimary,
                    unfocusedLabelColor = NovelTextSecondary,
                    cursorColor = NovelPrimary
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (isValidPosition) onMoveTo(parsedPosition!!) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("EpubMoveToPositionInput")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EpubModalDismissButton(
                text = "Cancel",
                onClick = onDismiss
            )
            Spacer(Modifier.width(8.dp))
            EpubModalPrimaryButton(
                text = "Move",
                enabled = isValidPosition,
                onClick = { if (parsedPosition != null) onMoveTo(parsedPosition) }
            )
        }
    }
}
