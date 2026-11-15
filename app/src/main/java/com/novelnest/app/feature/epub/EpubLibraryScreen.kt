package com.novelnest.app.feature.epub

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.footer.rememberPageBottomPadding
import com.novelnest.app.data.local.epub.EpubImportState
import com.novelnest.app.feature.home.HomeSectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubLibraryScreen(viewModel: EpubLibraryViewModel, onBack: () -> Unit,
    onOpenBook: (String) -> Unit, onOpenSeries: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val selectionSet = remember(selection) { selection.toSet() }
    val itemsByKey = remember(state.items) { state.items.associateBy { it.key } }
    val selectedItems = remember(itemsByKey, selection) { selection.mapNotNull(itemsByKey::get) }
    val selectedBooksOnly = selectedItems.isNotEmpty() && selectedItems.all { it is EpubShelfItem.Book }
    val selectionMode = selection.isNotEmpty()
    var selectionMenu by remember { mutableStateOf(false) }
    var batchKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    BackHandler(enabled = selectionMode) { viewModel.clearSelection() }
    val importState by viewModel.repository.importState.collectAsStateWithLifecycle()
    val libraryError by viewModel.repository.libraryError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val gridState = rememberLazyGridState()
    val searchFocusRequester = remember { FocusRequester() }
    var isSearchFocused by remember { mutableStateOf(false) }
    var searchRevealedByHeader by rememberSaveable { mutableStateOf(false) }
    var searchCollapsed by rememberSaveable { mutableStateOf(false) }
    val isSearchBarVisible = !searchCollapsed || searchRevealedByHeader || isSearchFocused || query.isNotEmpty()
    val onSearchScroll by rememberUpdatedState<(Boolean) -> Unit> { collapse ->
        if (query.isEmpty()) {
            if (collapse) {
                searchRevealedByHeader = false
                if (isSearchFocused) {
                    focus.clearFocus()
                    keyboardController?.hide()
                }
            }
            searchCollapsed = collapse
        }
    }
    val scrollThresholdPx = with(LocalDensity.current) { 24.dp.toPx() }
    val searchScrollConnection = remember(gridState, scrollThresholdPx) {
        EpubSearchScrollConnection(scrollThresholdPx, atStart = { !gridState.canScrollBackward },
            onCollapseChange = { onSearchScroll(it) })
    }

    LaunchedEffect(searchRevealedByHeader) {
        if (searchRevealedByHeader) {
            runCatching { searchFocusRequester.requestFocus() }
            keyboardController?.show()
        }
    }

    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerDestination by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerError by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(EpubImportContract()) { uris ->
        if (uris.isNotEmpty() && !viewModel.repository.importBooks(uris, pickerDestination))
            pickerError = "An import is already running, or the selection exceeded the batch limit."
        pickerDestination = null
    }
    fun importBooks() {
        focus.clearFocus()
        pickerDestination = viewModel.collectionId
        pickerError = null
        try {
            Toast.makeText(context.applicationContext, "Long-press a file in the picker to select multiple EPUBs.", Toast.LENGTH_LONG).show()
            picker.launch(Unit)
        }
        catch (_: ActivityNotFoundException) { pickerError = "No document picker is available on this device." }
        catch (_: SecurityException) { pickerError = "The document picker could not grant file access. Select the EPUBs again." }
    }
    var handledRevision by rememberSaveable { mutableLongStateOf(0L) }
    val selected = itemsByKey[selectedKey]
    LaunchedEffect(selectedKey, selected, state.loaded, operation.busy) {
        if (state.loaded && selectedKey != null && selected == null && !operation.busy && operation.revision <= handledRevision) {
            dialog = null; selectedKey = null
            Toast.makeText(context.applicationContext, "This item is no longer in this shelf.", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(operation.revision, operation.message) {
        if (operation.revision > handledRevision) {
            handledRevision = operation.revision
            val msg = operation.message
            if (!msg.isNullOrBlank()) {
                Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
            if (dialog == "delete-series" && viewModel.collectionId != null) onBack()
            dialog = null; selectedKey = null
        } else if (operation.message != null && (!operation.failed || dialog == null)) {
            Toast.makeText(context.applicationContext, operation.message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(pickerError) {
        val err = pickerError
        if (!err.isNullOrBlank()) {
            Toast.makeText(context.applicationContext, err, Toast.LENGTH_SHORT).show()
            pickerError = null
        }
    }
    LaunchedEffect(importState) {
        when (val currentImport = importState) {
            is EpubImportState.Complete -> {
                val summary = if (currentImport.importedCount + currentImport.duplicateCount + currentImport.failedCount > 1) {
                    "${currentImport.importedCount} imported, ${currentImport.duplicateCount} already saved, ${currentImport.failedCount} failed" +
                        (currentImport.message?.let { " • $it" } ?: "")
                } else if (currentImport.duplicate) {
                    currentImport.message ?: "Already in your library. Reading progress was kept."
                } else {
                    currentImport.message ?: "Imported \"${currentImport.book.title}\""
                }
                Toast.makeText(context.applicationContext, summary, Toast.LENGTH_SHORT).show()
                viewModel.repository.clearImportMessage()
            }
            is EpubImportState.Failed -> {
                Toast.makeText(context.applicationContext, currentImport.message, Toast.LENGTH_SHORT).show()
                viewModel.repository.clearImportMessage()
            }
            else -> Unit
        }
    }
    var previousQuery by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(query) {
        // Filtering starts at the top; entering/restoring the page must keep its saved position.
        if (query != previousQuery) {
            previousQuery = query
            searchCollapsed = false
            gridState.scrollToItem(0)
        }
    }
    fun select(item: EpubShelfItem) {
        if (viewModel.operation.value.busy) return
        // A long-press only selects, including when fast input arrives before recomposition.
        dialog = null; selectedKey = null; batchKeys = emptyList(); selectionMenu = false
        focus.clearFocus(); keyboardController?.hide()
        viewModel.selectItem(item.key)
    }
    fun open(item: EpubShelfItem) {
        focus.clearFocus()
        if (viewModel.operation.value.busy) return
        if (viewModel.selection.value.isNotEmpty()) { viewModel.toggleSelection(item.key); return }
        when (item) { is EpubShelfItem.Book -> onOpenBook(item.entry.book.id); is EpubShelfItem.Series -> onOpenSeries(item.collection.id) }
    }
    val searchPlaceholder = if (viewModel.collectionId == null) "Search EPUBs and series..." else "Search volumes..."
    Column(Modifier.fillMaxSize().background(NovelBackground)) {
        TopAppBar(
            title = {
                Text(if (selectionMode) selection.size.toString() + " selected" else
                    state.collection?.name ?: if (viewModel.collectionId == null) "EPUB Library" else "Series",
                    color = NovelTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = NovelBackground),
            navigationIcon = {
                NovelNestIconButton(if (selectionMode) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                    if (selectionMode) "Clear selection" else "Back", enabled = !operation.busy,
                    onClick = { if (selectionMode) viewModel.clearSelection() else onBack() })
            },
            actions = {
                if (selectionMode) {
                    if (selectedBooksOnly) NovelNestIconButton(Icons.AutoMirrored.Rounded.DriveFileMove,
                        "Move selected EPUBs", tint = NovelPrimary, enabled = !operation.busy, onClick = {
                            selectionMenu = false; batchKeys = selection.toList()
                            viewModel.clearMessage(); dialog = "batch-move"
                        })
                    NovelNestIconButton(Icons.Rounded.SelectAll, "Select all visible items", tint = NovelPrimary, enabled = !operation.busy,
                        onClick = viewModel::selectVisible)
                    Box {
                        NovelNestIconButton(Icons.Rounded.MoreVert, "Selected item actions", tint = NovelPrimary, enabled = !operation.busy,
                            onClick = { selectionMenu = true })
                        DropdownMenu(expanded = selectionMenu, onDismissRequest = { selectionMenu = false }) {
                            if (selectedBooksOnly) {
                                DropdownMenuItem(text = { Text("Move selected EPUBs") }, onClick = {
                                    selectionMenu = false; batchKeys = selection.toList()
                                    viewModel.clearMessage(); dialog = "batch-move"
                                })
                            }
                            if (viewModel.collectionId != null && selectedBooksOnly)
                                DropdownMenuItem(text = { Text("Remove from series") }, onClick = {
                                    selectionMenu = false; batchKeys = selection.toList()
                                    viewModel.clearMessage(); dialog = "batch-detach"
                                })
                            if (selectedItems.size == 1) DropdownMenuItem(text = { Text("Edit selected item") }, onClick = {
                                selectionMenu = false; selectedKey = selectedItems.single().key
                                viewModel.clearMessage(); dialog = "item-options"
                            })
                            DropdownMenuItem(text = { Text(if (selectedBooksOnly) "Delete selected EPUBs from device" else "Remove selected items", color = NovelError) }, onClick = {
                                selectionMenu = false; batchKeys = selection.toList()
                                viewModel.clearMessage(); dialog = "batch-remove"
                            })
                        }
                    }
                } else {
                    AnimatedVisibility(visible = !isSearchBarVisible,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut() + scaleOut(targetScale = 0.8f)) {
                        NovelNestIconButton(Icons.Rounded.Search, searchPlaceholder, tint = NovelPrimary,
                            onClick = { searchCollapsed = false; searchRevealedByHeader = true })
                    }
                    NovelNestIconButton(Icons.Rounded.UploadFile, "Import EPUBs", tint = NovelPrimary,
                        enabled = !operation.busy && importState !is EpubImportState.Working && !state.collectionMissing, onClick = ::importBooks)
                    if (viewModel.collectionId == null) NovelNestIconButton(Icons.Rounded.LibraryAdd, "Create series", tint = NovelPrimary,
                        enabled = !operation.busy, onClick = { viewModel.clearMessage(); dialog = "create" })
                    else if (state.collection != null) NovelNestIconButton(Icons.Rounded.MoreVert, "Series options", tint = NovelPrimary,
                        enabled = !operation.busy, onClick = { viewModel.clearMessage(); dialog = "series-options" })
                }
            })
        AnimatedVisibility(
            visible = isSearchBarVisible,
            enter = expandVertically(animationSpec = tween(240)) + fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(animationSpec = tween(240)) + fadeOut(animationSpec = tween(160))
        ) {
            EpubLibrarySearchBar(
                query = query,
                onQueryChange = viewModel::setQuery,
                placeholder = searchPlaceholder,
                isFocused = isSearchFocused,
                onFocusChange = { isSearchFocused = it },
                focusRequester = searchFocusRequester,
                onSearch = {
                    focus.clearFocus()
                    keyboardController?.hide()
                },
                onClear = {
                    viewModel.setQuery("")
                    focus.clearFocus()
                }
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cardWidth = (maxWidth - 56.dp) / 3
            val bottom = rememberPageBottomPadding()
            LazyVerticalGrid(columns = GridCells.Fixed(3), state = gridState, modifier = Modifier.fillMaxSize().nestedScroll(searchScrollConnection).testTag("EpubLibraryGrid"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (importState is EpubImportState.Working) item(key = "import", span = { GridItemSpan(maxLineSpan) }) {
                    EpubImportStatus(importState, viewModel.repository::cancelImport, viewModel.repository::clearImportMessage)
                }
                if (libraryError != null) item(key = "message", span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        color = NovelError.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, NovelError.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = libraryError.orEmpty(),
                                color = NovelError,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = viewModel.repository::reloadLibrary) {
                                Text("Retry library", color = NovelPrimary)
                            }
                        }
                    }
                }
                if (!state.loaded) item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = NovelPrimary) }
                }
                if (state.collectionMissing) item(key = "missing", span = { GridItemSpan(maxLineSpan) }) {
                    Text("This series no longer exists. Its EPUBs remain in My Library.", color = NovelTextSecondary)
                }
                if (query.isBlank() && !state.collectionMissing) {
                    state.continueBook?.let { entry -> item(key = "continue", span = { GridItemSpan(maxLineSpan) }) {
                        EpubContinueReadingCard(entry, viewModel.repository, seriesName = state.continueSeriesName, enabled = !selectionMode && !operation.busy) { onOpenBook(entry.book.id) }
                    } }
                    if (state.recentlyRead.isNotEmpty()) item(key = "recent", span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HomeSectionTitle("Recently Read", state.recentlyRead.size.toString() + if (state.recentlyRead.size == 1) " recent read" else " recent reads")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.recentlyRead, key = { it.key }) { item ->
                                    EpubShelfCard(item, viewModel.repository, Modifier.width(cardWidth),
                                        selected = item.key in selectionSet, selectionMode = selectionMode,
                                        onClick = { if (viewModel.operation.value.busy) Unit else if (viewModel.selection.value.isNotEmpty())
                                            viewModel.toggleSelection(item.key) else item.representative?.let { onOpenBook(it.book.id) } },
                                        onSelect = { select(item) })
                                }
                            }
                        }
                    }
                }
                item(key = "heading", span = { GridItemSpan(maxLineSpan) }) {
                    HomeSectionTitle(if (query.isNotBlank()) "Search Results" else if (viewModel.collectionId == null) "My Library" else "Volumes",
                        if (viewModel.collectionId == null) state.bookCount.toString() + " EPUBs - Series and standalone books" else "Reading order")
                }
                if (state.loaded && state.visibleItems.isEmpty() && libraryError == null && !state.collectionMissing) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.AutoStories, null, tint = NovelPrimary, modifier = Modifier.size(36.dp))
                        Text(if (query.isNotBlank()) "No matching EPUBs or series" else "Your offline shelf starts here", color = NovelTextPrimary)
                        if (query.isBlank()) {
                            Text("Import EPUBs, then organize separate volumes into a series.", color = NovelTextSecondary, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = ::importBooks, enabled = importState !is EpubImportState.Working,
                                colors = ButtonDefaults.buttonColors(containerColor = NovelPrimary, contentColor = NovelOnPrimary)) {
                                Text("Import EPUBs", color = NovelOnPrimary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
                items(state.visibleItems, key = { it.key }, contentType = { if (it is EpubShelfItem.Series) "series" else "book" }) { item ->
                    val number = if (viewModel.collectionId != null) state.volumeNumbers[item.key] else null
                    EpubShelfCard(item, viewModel.repository, volumeNumber = number,
                        selected = item.key in selectionSet, selectionMode = selectionMode, onClick = { open(item) },
                        onSelect = { select(item) })
                }
            }
        }
    }
    EpubBatchDialog(dialog, batchKeys, state, operation, viewModel,
        onDismiss = { if (!operation.busy) { dialog = null; batchKeys = emptyList() } })
    EpubLibraryDialogs(dialog, selected, state, operation, onDismiss = { if (!operation.busy) { dialog = null; selectedKey = null } },
        onChooseAction = { viewModel.clearMessage(); dialog = it }, viewModel = viewModel)
}

@Composable
private fun EpubLibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    val activeBorder = isFocused || query.isNotEmpty()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NovelSurfaceVariant)
            .border(
                width = 1.dp,
                color = if (activeBorder) NovelPrimary.copy(alpha = 0.65f) else NovelSurfaceBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = NovelPrimary,
                modifier = Modifier.size(18.dp)
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = NovelTextPrimary,
                    fontSize = 13.sp
                ),
                cursorBrush = SolidColor(NovelPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChange(it.isFocused) }
                    .testTag("EpubLibrarySearch"),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NovelTextTertiary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(NovelSurface.copy(alpha = 0.75f))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Clear EPUB search",
                            onClick = onClear
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear EPUB search",
                        tint = NovelTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
