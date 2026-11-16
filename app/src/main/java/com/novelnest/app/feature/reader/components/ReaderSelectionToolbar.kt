package com.novelnest.app.feature.reader.components

import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.KeyEvent
import android.view.View
import com.novelnest.app.R
import kotlin.math.ceil
import kotlin.math.floor

/** A private reader ActionMode; no exported PROCESS_TEXT registration or clipboard lookup. */
internal class ReaderSelectionToolbar(
    private val view: View,
    private val selection: ReaderChapterSelection,
    private val canUse: () -> Boolean,
    private val onCopy: () -> Unit,
    private val onDefine: (String) -> Unit
) : ActionMode.Callback2() {
    private var mode: ActionMode? = null
    private var lastRange: ReaderSelectionRange? = null

    fun update() {
        if (!selection.active || selection.dragging || !canUse() || selection.selectionRect() == null) { close(); return }
        if (mode == null && view.isAttachedToWindow) {
            mode = view.startActionMode(this, ActionMode.TYPE_FLOATING)
            lastRange = selection.range
        } else {
            if (lastRange != selection.range) { lastRange = selection.range; mode?.invalidate() }
            mode?.invalidateContentRect()
        }
    }
    fun close() {
        val old = mode
        mode = null
        lastRange = null
        old?.finish() // Its destroy callback must not clear a newer selection or a handle drag.
    }
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        if (!selection.active || !canUse()) return false
        menu.add(Menu.NONE, android.R.id.copy, 0, android.R.string.copy)
            .setAlphabeticShortcut('c', KeyEvent.META_CTRL_ON).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(Menu.NONE, R.id.reader_define_selection, 1, R.string.dictionary_action).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(Menu.NONE, android.R.id.selectAll, 2, android.R.string.selectAll)
            .setAlphabeticShortcut('a', KeyEvent.META_CTRL_ON).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }
    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.findItem(R.id.reader_define_selection)?.isVisible = selection.word() != null
        menu.findItem(android.R.id.copy)?.isEnabled = selection.range?.let { it.start != it.end } == true
        return true
    }
    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (mode !== this.mode || !canUse() || !selection.active || selection.dragging) return true
        return when (item.itemId) {
            android.R.id.copy -> { onCopy(); true }
            android.R.id.selectAll -> { selection.selectAll(); true }
            R.id.reader_define_selection -> { selection.word()?.let(onDefine); true }
            else -> false
        }
    }
    override fun onDestroyActionMode(mode: ActionMode) {
        if (this.mode === mode) { this.mode = null; selection.clear() }
    }
    override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
        val bounds = selection.selectionRect()
        if (bounds == null) { outRect.set(0, 0, 1, 1); return }
        val origin = IntArray(2)
        view.getLocationInWindow(origin)
        outRect.set(floor(bounds.left - origin[0]).toInt(), floor(bounds.top - origin[1]).toInt(),
            ceil(bounds.right - origin[0]).toInt(), ceil(bounds.bottom - origin[1]).toInt())
    }
}
