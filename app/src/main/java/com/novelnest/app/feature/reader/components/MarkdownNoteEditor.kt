package com.novelnest.app.feature.reader.components

import android.content.Context
import android.graphics.Rect as AndroidRect
import android.text.Editable
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.EditText
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.data.preferences.ReaderTheme
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext

/**
 * Rich Markdown Note Reader & Editor.
 *
 * For existing notes (note != null): opens in formatted Preview Mode first, with a
 * prominent [Edit] button to switch to editor mode.
 * For new notes (note == null): opens directly in Editor Mode.
 */
@Composable
internal fun MarkdownNoteEditor(
    note: ChapterNoteUi?,
    draft: ReaderNoteDraft,
    textColor: Color,
    secondaryColor: Color,
    theme: ReaderTheme,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit,
    onDismissSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val title = draft.title
    val content = draft.content
    // Existing notes open in Preview Mode first; new notes open in Edit Mode
    var preview by rememberSaveable(note?.id) { mutableStateOf(note != null) }
    var editor by remember { mutableStateOf<ScrollableNoteEditText?>(null) }
    val bodyScrollState = rememberScrollState()
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val safeBottom = WindowInsets.safeDrawing.getBottom(density)
    var bodyHeightPx by remember { mutableIntStateOf(0) }
    val bottomClearancePx = with(density) { 16.dp.toPx() }
    val editorMaxHeight = if (bodyHeightPx > 0) {
        (with(density) { bodyHeightPx.toDp() } - 20.dp).coerceIn(170.dp, 280.dp)
    } else {
        280.dp
    }
    val cursorRequester = remember { BringIntoViewRequester() }
    val cursorRequests = remember { Channel<Pair<ScrollableNoteEditText, Rect>>(Channel.CONFLATED) }
    DisposableEffect(cursorRequests) {
        onDispose { cursorRequests.close() }
    }
    LaunchedEffect(cursorRequests, preview) {
        cursorRequests.receiveAsFlow().collectLatest { (view, rectangle) ->
            if (preview) return@collectLatest
            // Keyboard/viewport measurement must finish before asking the form to scroll.
            withFrameNanos { }
            if (editor === view && view.isAttachedToWindow && view.isFocused) {
                cursorRequester.bringIntoView(view.targetViewportRect() ?: rectangle)
            }
        }
    }
    LaunchedEffect(imeBottom, safeBottom, bodyHeightPx, preview) {
        if (preview) return@LaunchedEffect
        val view = editor ?: return@LaunchedEffect
        view.viewportHeightPx = bodyHeightPx
        view.bottomClearancePx = bottomClearancePx
        if (view.isAttachedToWindow && view.isFocused) {
            view.requestCursorVisibility()
            withFrameNanos { }
            if (editor === view && view.isAttachedToWindow && view.isFocused) {
                view.targetViewportRect()?.let { cursorRequester.bringIntoView(it) }
            }
        }
    }

    val accentColor = if (theme.isDark) NovelPrimary else textColor

    Column(modifier = modifier.fillMaxWidth()) {
        // ─── TOP HEADER BAR ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NovelNestIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back to notes",
                onClick = onBack
            )
            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (preview) {
                        title.ifBlank { "Untitled Note" }
                    } else if (note == null) {
                        "New Note"
                    } else {
                        "Edit Note"
                    },
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (preview) {
                        note?.updatedAt ?: "Note Preview"
                    } else {
                        "Markdown Supported"
                    },
                    color = secondaryColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (preview) {
                // [ Edit ] action button in Preview Mode
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NovelPrimary)
                        .clickable { preview = false }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = NovelOnPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Edit",
                        color = NovelOnPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // In Edit Mode: Preview toggle + Save button
                if (note != null || content.isNotBlank()) {
                    TextButton(
                        onClick = { preview = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Preview",
                            color = accentColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                val canSave = !saving && title.trim().isNotBlank() && title.length <= 160 && content.length <= 64_000
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (canSave) NovelPrimary else NovelPrimary.copy(alpha = 0.35f))
                        .clickable(enabled = canSave) { onSave(title.trim(), content) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            color = NovelOnPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = NovelOnPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (saving) "Saving…" else "Save",
                        color = NovelOnPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        HorizontalDivider(
            color = secondaryColor.copy(alpha = 0.12f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )

        // ─── BODY CONTAINER ───
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 380.dp)
                .onSizeChanged { size ->
                    bodyHeightPx = size.height
                    editor?.let {
                        it.viewportHeightPx = size.height
                        it.bottomClearancePx = bottomClearancePx
                        it.requestCursorVisibility()
                    }
                }
                .verticalScroll(bodyScrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (preview) {
                // ─── PREVIEW VIEW ───
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surfaceColor),
                    border = BorderStroke(1.dp, secondaryColor.copy(alpha = 0.18f)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)
                ) {
                    if (content.isBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "This note has no written text yet.\nTap 'Edit' above to add your thoughts.",
                                color = secondaryColor,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        MarkdownNotePreview(
                            markdown = content,
                            color = textColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        )
                    }
                }
            } else {
                // ─── EDIT VIEW ───
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 160) draft.title = it },
                    singleLine = true,
                    enabled = !saving,
                    textStyle = TextStyle(
                        color = textColor,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    placeholder = {
                        Text(
                            text = "Note title (e.g. Cultivation Insights, Plot Revelation)",
                            color = secondaryColor.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NovelPrimary,
                        unfocusedBorderColor = secondaryColor.copy(alpha = 0.25f),
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        cursorColor = NovelPrimary,
                        focusedContainerColor = theme.surfaceColor,
                        unfocusedContainerColor = theme.surfaceColor
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("ReaderNoteTitle")
                )

                // Formatting Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(secondaryColor.copy(alpha = 0.08f))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val actions = listOf(
                            "B" to ("**" to "**"),
                            "I" to ("*" to "*"),
                            "H" to ("### " to ""),
                            "•" to ("- " to ""),
                            ">" to ("> " to ""),
                            "`" to ("`" to "`"),
                            "~~" to ("~~" to "~~")
                        )
                        actions.forEach { (label, syntax) ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(secondaryColor.copy(alpha = 0.12f))
                                    .clickable(enabled = !saving) {
                                        editor?.let { formatSelection(it, syntax.first, syntax.second) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = textColor,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NovelPrimary.copy(alpha = 0.15f))
                            .clickable { preview = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Preview",
                            color = accentColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Native Android Multi-line Editor
                val changed by rememberUpdatedState<(String) -> Unit>({ draft.content = it })
                AndroidView(
                    factory = { context ->
                        ScrollableNoteEditText(context).apply {
                            viewportHeightPx = bodyHeightPx
                            this.bottomClearancePx = bottomClearancePx
                            onCursorRectangleRequested = { cursorRequests.trySend(this to it) }
                            setText(content)
                            hint = "Write Markdown: **bold**, *italic*, headings, lists, quotes, tables…"
                            gravity = android.view.Gravity.TOP or android.view.Gravity.START
                            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                            setPadding(24, 20, 24, 20)
                            minLines = 6
                            textSize = 14.5f
                            filters = arrayOf(android.text.InputFilter.LengthFilter(64_000))
                            customSelectionActionModeCallback = object : ActionMode.Callback {
                                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                                    listOf("Bold", "Italic", "Strike", "Code", "Link").forEachIndexed { i, name ->
                                        menu.add(0, 10_001 + i, 20 + i, name)
                                    }
                                    return true
                                }
                                override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
                                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                                    val syntax = when (item.itemId) {
                                        10_001 -> "**" to "**"
                                        10_002 -> "*" to "*"
                                        10_003 -> "~~" to "~~"
                                        10_004 -> "`" to "`"
                                        10_005 -> "[" to "](https://)"
                                        else -> return false
                                    }
                                    formatSelection(this@apply, syntax.first, syntax.second)
                                    mode.finish()
                                    return true
                                }
                                override fun onDestroyActionMode(mode: ActionMode) = Unit
                            }
                            addTextChangedListener(object : TextWatcher {
                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                    changed(s?.toString().orEmpty())
                                    requestCursorVisibility()
                                }
                                override fun afterTextChanged(s: Editable?) = Unit
                            })
                            editor = this
                        }
                    },
                    onReset = null,
                    onRelease = { view ->
                        view.onCursorRectangleRequested = null
                        if (editor === view) editor = null
                    },
                    update = { view ->
                        view.viewportHeightPx = bodyHeightPx
                        view.bottomClearancePx = bottomClearancePx
                        if (view.text.toString() != content) {
                            val position = view.selectionStart.coerceIn(0, content.length)
                            view.setText(content)
                            view.setSelection(position)
                        }
                        view.isEnabled = !saving
                        view.setTextColor(textColor.toArgb())
                        view.setHintTextColor(secondaryColor.copy(alpha = 0.5f).toArgb())
                        view.background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(theme.surfaceColor.toArgb())
                            cornerRadius = 24f
                            setStroke(2, secondaryColor.copy(alpha = 0.25f).toArgb())
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 170.dp, max = editorMaxHeight)
                        .bringIntoViewRequester(cursorRequester)
                )

                Text(
                    text = "Select text or tap toolbar to format. Supports CommonMark, tables, tasks, and strikethrough.",
                    color = secondaryColor.copy(alpha = 0.7f),
                    fontSize = 10.5.sp
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** Keep overflowing native text scrolls from being intercepted by the Compose form. */
private class ScrollableNoteEditText(context: Context) : EditText(context) {
    private var ownsScrollGesture = false
    private var lastTouchY = 0f
    var viewportHeightPx: Int = 0
    var bottomClearancePx: Float = 0f
    var onCursorRectangleRequested: ((Rect) -> Unit)? = null
    private val revealCursor = Runnable {
        if (isAttachedToWindow && isFocused) {
            if (selectionEnd >= 0) bringPointIntoView(selectionEnd)
            snapCursorLineIntoInternalViewport()
            targetViewportRect()?.let { onCursorRectangleRequested?.invoke(it) }
        }
    }

    fun requestCursorVisibility() {
        if (isAttachedToWindow && isFocused) {
            removeCallbacks(revealCursor)
            postOnAnimation(revealCursor)
        }
    }

    private fun snapCursorLineIntoInternalViewport() {
        val currentLayout = layout ?: return
        val cursorOffset = selectionEnd.takeIf { it >= 0 } ?: return
        val clampedOffset = cursorOffset.coerceIn(0, text?.length ?: 0)
        val line = currentLayout.getLineForOffset(clampedOffset)
        val lineTop = currentLayout.getLineTop(line)
        val lineBottom = currentLayout.getLineBottom(line)
        val availableHeight = (height - compoundPaddingTop - compoundPaddingBottom).coerceAtLeast(1)
        val maxScroll = (currentLayout.height - availableHeight).coerceAtLeast(0)
        val targetScrollY = when {
            lineBottom > scrollY + availableHeight -> (lineBottom - availableHeight).coerceIn(0, maxScroll)
            lineTop < scrollY -> lineTop.coerceIn(0, maxScroll)
            else -> scrollY.coerceIn(0, maxScroll)
        }
        if (targetScrollY != scrollY) {
            scrollTo(scrollX, targetScrollY)
        }
    }

    fun targetViewportRect(requested: AndroidRect? = null): Rect? {
        if (width <= 0 || height <= 0) return null
        val w = width.toFloat()
        val h = height.toFloat()
        // When the editor fits within the outer scroll viewport, bring the entire editor
        // (including its bottom padding and clearance) into view so the last line is never hidden.
        if (viewportHeightPx <= 0 || h + bottomClearancePx <= viewportHeightPx.toFloat()) {
            return Rect(0f, 0f, w, h + bottomClearancePx)
        }
        val currentLayout = layout
        if (currentLayout != null && selectionEnd >= 0) {
            val clampedOffset = selectionEnd.coerceIn(0, text?.length ?: 0)
            val line = currentLayout.getLineForOffset(clampedOffset)
            val lineHeight = (currentLayout.getLineBottom(line) - currentLayout.getLineTop(line)).toFloat().coerceAtLeast(1f)
            val rawTop = (currentLayout.getLineTop(line) + compoundPaddingTop - scrollY).toFloat()
            val rawBottom = (currentLayout.getLineBottom(line) + compoundPaddingTop + compoundPaddingBottom - scrollY).toFloat()
            val topInView = when {
                rawBottom > h -> (h - lineHeight - compoundPaddingBottom).coerceAtLeast(0f)
                rawTop < 0f -> 0f
                else -> rawTop.coerceIn(0f, h)
            }
            val bottomInView = when {
                rawBottom > h -> h + bottomClearancePx
                rawBottom >= h - compoundPaddingBottom -> h + bottomClearancePx
                else -> rawBottom.coerceIn(topInView + 1f, h)
            }
            return Rect(0f, topInView, w, bottomInView)
        }
        if (requested != null) {
            val reqHeight = (requested.bottom - requested.top).toFloat().coerceAtLeast(1f)
            val rawTop = (requested.top - scrollY).toFloat()
            val rawBottom = (requested.bottom - scrollY).toFloat()
            val topInView = if (rawBottom > h) (h - reqHeight).coerceAtLeast(0f) else rawTop.coerceIn(0f, h)
            val bottomInView = if (rawBottom >= h - compoundPaddingBottom) h + bottomClearancePx else rawBottom.coerceIn(topInView + 1f, h)
            return Rect(0f, topInView, w, bottomInView)
        }
        return Rect(0f, 0f, w, h + bottomClearancePx)
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: AndroidRect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (focused) {
            requestCursorVisibility()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (isFocused) {
            snapCursorLineIntoInternalViewport()
            targetViewportRect()?.let { onCursorRectangleRequested?.invoke(it) }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h != oldh && isFocused) {
            requestCursorVisibility()
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (isFocused) {
            requestCursorVisibility()
        }
    }

    override fun onScrollChanged(horiz: Int, vert: Int, oldHoriz: Int, oldVert: Int) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert)
        if (isFocused && vert != oldVert) {
            targetViewportRect()?.let { onCursorRectangleRequested?.invoke(it) }
        }
    }

    override fun requestRectangleOnScreen(rectangle: AndroidRect, immediate: Boolean): Boolean {
        val requestedCopy = AndroidRect(rectangle)
        val scrolled = super.requestRectangleOnScreen(rectangle, immediate)
        if (isFocused && onCursorRectangleRequested != null) {
            snapCursorLineIntoInternalViewport()
            targetViewportRect(requestedCopy)?.let { onCursorRectangleRequested?.invoke(it) }
        }
        return scrolled
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            lastTouchY = event.y
            ownsScrollGesture = isEnabled && (canScrollVertically(-1) || canScrollVertically(1))
            if (ownsScrollGesture) parent?.requestDisallowInterceptTouchEvent(true)
            if (isEnabled) requestCursorVisibility()
        } else if (event.actionMasked == MotionEvent.ACTION_MOVE && ownsScrollGesture) {
            val dy = event.y - lastTouchY
            lastTouchY = event.y
            if ((dy < -1f && !canScrollVertically(1)) || (dy > 1f && !canScrollVertically(-1))) {
                releaseScrollGesture()
            }
        }
        return try {
            // Preserve native cursor placement, long press, selection handles and scrolling.
            super.dispatchTouchEvent(event)
        } finally {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                releaseScrollGesture()
                if (isFocused) requestCursorVisibility()
            }
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(revealCursor)
        releaseScrollGesture()
        super.onDetachedFromWindow()
    }

    private fun releaseScrollGesture() {
        if (ownsScrollGesture) {
            parent?.requestDisallowInterceptTouchEvent(false)
            ownsScrollGesture = false
        }
    }
}


internal fun markdownWrap(text: String, start: Int, end: Int, prefix: String, suffix: String): Triple<String, Int, Int> {
    val left = minOf(start, end).coerceIn(0, text.length)
    val right = maxOf(start, end).coerceIn(left, text.length)
    return Triple(text.substring(0, left) + prefix + text.substring(left, right) + suffix + text.substring(right),
        left + prefix.length, right + prefix.length)
}

private fun formatSelection(editor: EditText, prefix: String, suffix: String) {
    val text = editor.text.toString()
    val result = markdownWrap(text, editor.selectionStart.coerceAtLeast(0), editor.selectionEnd.coerceAtLeast(0), prefix, suffix)
    if (result.first.length > 64_000) return
    val left = result.second - prefix.length
    val right = result.third - prefix.length
    editor.beginBatchEdit()
    try {
        editor.text.replace(left, right, prefix + text.substring(left, right) + suffix)
        editor.requestFocus()
        editor.setSelection(result.second, result.third)
    } finally { editor.endBatchEdit() }
}

@Composable
private fun MarkdownNotePreview(markdown: String, color: Color, modifier: Modifier) {
    val context = LocalContext.current
    val renderer = remember(context) { Markwon.builder(context)
        .usePlugin(TablePlugin.create(context)).usePlugin(TaskListPlugin.create(context))
        .usePlugin(StrikethroughPlugin.create()).build() }
    val rendered by produceState<android.text.Spanned>(android.text.SpannableString(""), markdown, renderer) {
        value = withContext(Dispatchers.Default) {
            try { renderer.toMarkdown(markdown) } catch (_: Exception) { android.text.SpannableString(markdown) }
        }
    }
    AndroidView(factory = { TextView(it).apply { textSize = 15f; setTextIsSelectable(true); setPadding(12, 8, 12, 8) } },
        update = {
            it.setTextColor(color.toArgb())
            if (it.tag !== rendered) { renderer.setParsedMarkdown(it, rendered); it.tag = rendered }
        }, modifier = modifier)
}
