package com.novelnest.app.feature.reader.components

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
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
    var editor by remember { mutableStateOf<EditText?>(null) }

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
                .verticalScroll(rememberScrollState())
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
                            setText(content)
                            hint = "Write Markdown: **bold**, *italic*, headings, lists, quotes, tables…"
                            gravity = android.view.Gravity.TOP or android.view.Gravity.START
                            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                            setPadding(24, 20, 24, 20)
                            minLines = 6
                            maxLines = 12
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
                                }
                                override fun afterTextChanged(s: Editable?) = Unit
                            })
                            editor = this
                        }
                    },
                    update = { view ->
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 170.dp, max = 280.dp)
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

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            ownsScrollGesture = isEnabled && (canScrollVertically(-1) || canScrollVertically(1))
            if (ownsScrollGesture) parent?.requestDisallowInterceptTouchEvent(true)
        }
        return try {
            // Preserve native cursor placement, long press, selection handles and scrolling.
            super.dispatchTouchEvent(event)
        } finally {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                releaseScrollGesture()
            }
        }
    }

    override fun onDetachedFromWindow() {
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
