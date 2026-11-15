package com.novelnest.app.feature.epub

import android.view.View
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import kotlin.math.abs

/** Session-only return points. Bounded, main-thread-owned, and independent of the durable book checkpoint. */
internal class EpubChapterPositions {
    private val locations = object : LinkedHashMap<String, Locator>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Locator>?): Boolean = size > 128
    }

    fun remember(locator: Locator) {
        val progression = locator.locations.progression ?: return
        if (!progression.isFinite() || progression !in 0.0..1.0) return
        // No chapter text, selector, or TOC fragment is needed for a viewport return point.
        locations[resourceKey(locator.href)] = Locator(
            href = locator.href.removeQuery().removeFragment(), mediaType = locator.mediaType,
            title = locator.title?.take(1000), locations = Locator.Locations(progression = progression)
        )
    }

    fun get(href: Url): Locator? = locations[resourceKey(href)]
}

private fun resourceKey(href: Url): String = href.normalize().removeQuery().removeFragment().toString()

/**
 * Readium keeps ownership of gestures/pagination. A continuous resource jump is followed by one
 * explicit saved locator, after Readium's synchronous start/end setup has finished. No gesture
 * interception, reflection, polling, or repeated scroll correction is used.
 */
internal class EpubChapterReturnController(
    private val scope: CoroutineScope,
    private val positions: EpubChapterPositions,
    private val onCheckpoint: (Locator) -> Unit,
    private val onError: (String) -> Unit
) {
    private data class Pending(val root: View, val target: Locator, val action: Runnable, var applied: Boolean = false)
    private var pending: Pending? = null
    private var timeout: Job? = null
    private var applying = false
    private var explicit = false

    fun <T> explicitJump(action: () -> T): T {
        cancel()
        explicit = true
        return try { action() } finally { explicit = false }
    }

    fun onJump(fragment: EpubNavigatorFragment, destination: Locator, publication: Publication, continuous: Boolean) {
        if (applying || explicit) return
        val source = fragment.currentLocator.value
        val previous = pending
        cancel()
        if (!continuous || destination.href.toString().contains('#') || destination.locations.fragments.isNotEmpty() ||
            destination.text.highlight != null) return
        val sourceKey = resourceKey(source.href)
        val targetKey = resourceKey(destination.href)
        val order = publication.readingOrder
        val sourceIndex = order.indexOfFirst { resourceKey(it.url()) == sourceKey }
        val targetIndex = order.indexOfFirst { resourceKey(it.url()) == targetKey }
        if (sourceIndex < 0 || targetIndex < 0 || abs(sourceIndex - targetIndex) != 1) return
        // A fast second jump may arrive before the first locator settles. Keep the earlier
        // confirmed return point instead of replacing it with an intermediate position.
        if (previous == null) positions.remember(source)
        val target = positions.get(destination.href) ?: return
        val root = fragment.view ?: return
        lateinit var request: Pending
        val restore = Runnable {
            if (pending !== request) return@Runnable
            if (!fragment.isResumed || fragment.view !== root || !root.isAttachedToWindow) {
                cancel()
                return@Runnable
            }
            try {
                applying = true
                request.applied = fragment.go(target, animated = false)
                if (!request.applied) {
                    cancel()
                    onError("Could not restore this chapter's reading position. Try opening the chapter again.")
                }
            } catch (cancelled: CancellationException) {
                cancel()
                throw cancelled
            } catch (_: Exception) {
                cancel()
                onError("Could not restore this chapter's reading position. Try opening the chapter again.")
            } finally { applying = false }
        }
        request = Pending(root, target, restore)
        pending = request
        if (!root.post(restore)) { cancel(); return }
        timeout = scope.launch {
            delay(2500)
            if (pending === request) {
                val current = fragment.currentLocator.value
                timeout = null
                cancel()
                // A clamped/unchanged viewport may produce no new StateFlow value. Retain the
                // navigator's actual destination rather than a synthetic requested percentage.
                if (request.applied && resourceKey(current.href) == targetKey) onCheckpoint(current)
                else onError("This chapter is still loading. Its previous reading position has been kept.")
            }
        }
    }

    /** Excludes the destination's temporary default entry point from history and durable saves. */
    fun accept(locator: Locator): Boolean {
        val request = pending ?: return true
        if (request.applied && resourceKey(locator.href) == resourceKey(request.target.href)) {
            cancel()
            return true
        }
        return false
    }

    fun cancel() {
        pending?.let { it.root.removeCallbacks(it.action) }
        pending = null
        timeout?.cancel()
        timeout = null
    }
}
