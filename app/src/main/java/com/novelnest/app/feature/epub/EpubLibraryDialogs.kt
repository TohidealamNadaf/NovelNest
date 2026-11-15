package com.novelnest.app.feature.epub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.NovelNestIconButton

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
            var orderedIds by rememberSaveable(group.key) { mutableStateOf(group.volumes.map { it.book.id }) }
            val entries = group.volumes.associateBy { it.book.id }
            val changed = orderedIds.toSet() != entries.keys

            EpubModalContainer(
                title = "Volume order",
                onDismiss = onDismiss
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp)
                    ) {
                        itemsIndexed(orderedIds, key = { _, id -> id }) { index, id ->
                            val entry = entries[id]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    color = NovelPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    text = entry?.book?.volumeLabel ?: entry?.book?.title ?: "Removed EPUB",
                                    color = NovelTextPrimary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                NovelNestIconButton(
                                    icon = Icons.Rounded.ArrowUpward,
                                    contentDescription = "Move volume up",
                                    enabled = index > 0 && !operation.busy,
                                    onClick = {
                                        orderedIds = orderedIds.toMutableList().apply {
                                            add(index - 1, removeAt(index))
                                        }
                                    }
                                )
                                NovelNestIconButton(
                                    icon = Icons.Rounded.ArrowDownward,
                                    contentDescription = "Move volume down",
                                    enabled = index < orderedIds.lastIndex && !operation.busy,
                                    onClick = {
                                        orderedIds = orderedIds.toMutableList().apply {
                                            add(index + 1, removeAt(index))
                                        }
                                    }
                                )
                            }
                            if (index < orderedIds.lastIndex) {
                                HorizontalDivider(color = NovelSurfaceBorder.copy(alpha = 0.4f))
                            }
                        }
                    }

                    if (changed) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Volumes changed while this window was open.",
                                color = NovelPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { orderedIds = group.volumes.map { it.book.id } }) {
                                Text("Reload volumes", color = NovelPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
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
                        text = "Save order",
                        enabled = !operation.busy && !changed,
                        onClick = { viewModel.reorder(group.collection.id, orderedIds) }
                    )
                }
            }
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
