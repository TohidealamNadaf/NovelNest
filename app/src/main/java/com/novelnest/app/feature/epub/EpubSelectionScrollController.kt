package com.novelnest.app.feature.epub

import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import org.readium.r2.shared.util.Url
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Readium 3.0.3 ignores onOverScrolled while selecting, a paginated-column workaround.
 * In continuous mode, synchronize the native viewport to the renderer's selected-text scroll.
 * Keep Readium's selection state/handles intact and leave paginated horizontal offsets alone.
 * DOM scroll/selection events drive this; there is no polling or synthetic touch sequence.
 */
internal class EpubSelectionScrollController(
    private val root: () -> View?,
    private val continuous: () -> Boolean,
    private val resumed: () -> Boolean
) {
    private data class Update(val section: Url, val selected: Boolean, val y: Double, val width: Double)
    private val handler = Handler(Looper.getMainLooper())
    private val pending = AtomicReference<Update?>(null)
    private val scheduled = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    @Volatile private var section: Url? = null
    private var cachedView: WeakReference<WebView>? = null
    var hasSelection: Boolean = false
        private set

    // Called from Readium's locator collector on Main. Ignore messages from preloaded/old sections.
    fun setSection(href: Url) {
        val next = href.normalize().removeQuery().removeFragment()
        if (section?.isEquivalent(next) == true) return
        section = next
        hasSelection = false
        pending.set(null)
        cachedView = null
    }

    // JavascriptInterface calls arrive on WebView's bridge thread. Retain only the latest event.
    fun offer(resource: Url, selected: Boolean, y: Double, width: Double) {
        if (closed.get() || !y.isFinite() || !width.isFinite() ||
            section?.isEquivalent(resource.removeQuery().removeFragment()) != true) return
        pending.set(Update(resource, selected, y, width))
        schedule()
    }

    private fun schedule() {
        if (!closed.get() && scheduled.compareAndSet(false, true)) handler.post(drain)
    }

    private val drain = object : Runnable {
        override fun run() {
            try {
                pending.getAndSet(null)?.let { if (!closed.get() && resumed()) apply(it) }
            } finally {
                scheduled.set(false)
                if (pending.get() != null) schedule()
            }
        }
    }

    private fun apply(update: Update) {
        val current = section ?: return
        if (!current.isEquivalent(update.section.normalize().removeQuery().removeFragment())) return
        val view = cachedView?.get()?.takeIf { matches(it, current) } ?: findView(root(), current)
        if (view == null) return
        cachedView = WeakReference(view)
        hasSelection = update.selected
        if (!update.selected || !continuous() || update.width <= 0 || update.y < 0) return
        val scale = view.width / update.width
        if (!scale.isFinite() || scale !in 0.1..16.0) return
        val maximum = (view.contentHeight.toDouble() * scale - view.height).coerceAtLeast(0.0)
        val target = (update.y * scale).coerceIn(0.0, maximum.coerceAtMost(Int.MAX_VALUE.toDouble())).roundToInt()
        // A host scroll can produce another DOM event; do not feed it back if already synchronized.
        if (abs(view.scrollY.toLong() - target) > 1) view.scrollTo(view.scrollX, target)
    }

    private fun matches(view: WebView, current: Url): Boolean {
        if (!view.isAttachedToWindow || !view.isShown || view.width <= 0) return false
        val visible = Rect()
        if (!view.getGlobalVisibleRect(visible) || visible.isEmpty) return false
        val url = view.url ?: return false
        val actual = Uri.parse(url).path ?: return false
        val path = current.path?.trimStart('/') ?: return false
        return path.isNotEmpty() && (actual == path || actual.endsWith("/" + path))
    }

    private fun findView(view: View?, current: Url): WebView? {
        if (view == null || !view.isShown) return null
        if (view is WebView) return view.takeIf { matches(it, current) }
        if (view !is ViewGroup) return null
        var best: WebView? = null
        var bestArea = 0L
        val visible = Rect()
        for (i in 0 until view.childCount) {
            val candidate = findView(view.getChildAt(i), current) ?: continue
            if (candidate.getGlobalVisibleRect(visible)) {
                val area = visible.width().toLong() * visible.height()
                if (area > bestArea) { best = candidate; bestArea = area }
            }
        }
        return best
    }

    fun pause() {
        pending.set(null)
        handler.removeCallbacks(drain)
        scheduled.set(false)
    }

    fun close() {
        closed.set(true)
        pause()
        hasSelection = false
        section = null
        cachedView = null
    }
}
