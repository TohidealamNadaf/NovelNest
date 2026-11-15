package com.novelnest.app.feature.epub

import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import com.novelnest.app.R
import com.novelnest.app.feature.dictionary.DictionaryLookupActivity
import org.json.JSONTokener

/** Adds a local action without replacing Chromium's Copy/Share/selection callbacks. */
internal class EpubDictionarySelectionCallback(
    private val original: ActionMode.Callback,
    private val source: WebView,
    private val define: (WebView, ActionMode, String?) -> Unit,
) : ActionMode.Callback2() {
    private var active = false
    private var readingSelection = false

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        active = original.onCreateActionMode(mode, menu)
        if (active) addDefine(menu)
        return active
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val changed = original.onPrepareActionMode(mode, menu)
        return addDefine(menu) || changed
    }

    private fun addDefine(menu: Menu): Boolean {
        var changed = false
        // Some WebViews discover our exported processor too. Keep just the local action.
        for (index in menu.size() - 1 downTo 0) {
            val item = menu.getItem(index)
            val component = item.intent?.component
            if (component?.packageName == source.context.packageName &&
                component.className == DictionaryLookupActivity::class.java.name) {
                // Chromium processors may share Menu.NONE IDs with unrelated actions.
                // Hide by item instance, never remove another action with the same ID.
                if (item.isVisible) {
                    item.isVisible = false
                    changed = true
                }
            }
        }
        if (menu.findItem(R.id.epub_define_selection) == null) {
            menu.add(Menu.NONE, R.id.epub_define_selection, Menu.CATEGORY_SECONDARY,
                source.context.getString(R.string.dictionary_action))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            changed = true
        }
        return changed
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId != R.id.epub_define_selection) return original.onActionItemClicked(mode, item)
        if (!active || readingSelection || !source.isAttachedToWindow) return true
        readingSelection = true
        val documentUrl = source.url
        try {
            // Read only after an explicit tap. Bound text before crossing the JS bridge.
            source.evaluateJavascript(SELECTION) { raw ->
                readingSelection = false
                if (active && source.isAttachedToWindow && source.url == documentUrl) {
                    val word = if (raw != null && raw.length <= 4096) {
                        try { JSONTokener(raw).nextValue() as? String }
                        catch (_: Exception) { null }
                    } else null
                    define(source, mode, word)
                }
            }
        } catch (_: IllegalStateException) {
            readingSelection = false // WebView can be detached/destroyed during reader teardown.
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        active = false
        readingSelection = false
        original.onDestroyActionMode(mode)
    }

    override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
        if (original is ActionMode.Callback2) original.onGetContentRect(mode, view, outRect)
        else super.onGetContentRect(mode, view, outRect)
    }

    private companion object {
        const val SELECTION = "(function(){var s=window.getSelection();if(!s||s.isCollapsed)return null;var t=s.toString();return t.length>0&&t.length<=512?t:null;})()"
    }
}
