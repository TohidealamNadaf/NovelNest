package com.novelnest.app.feature.epub

import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url

/** Compare normalized publication URLs; suffix matching can confuse similarly named sections. */
internal fun resolveEpubContentsIndex(toc: List<Pair<Int, Link>>, href: Url?, title: String?): Int {
    if (href == null || toc.isEmpty()) return 0
    val resource = href.removeQuery().removeFragment()
    val candidates = toc.indices.filter { toc[it].second.url().removeQuery().removeFragment().isEquivalent(resource) }
    if (candidates.isEmpty()) return 0
    // EPUBs may contain several TOC anchors in one spine resource. Preserve exact anchors/titles.
    val exact = candidates.firstOrNull { toc[it].second.url().removeQuery().isEquivalent(href.removeQuery()) }
    val titled = title?.takeIf { it.isNotBlank() }?.let { currentTitle ->
        candidates.firstOrNull { toc[it].second.title?.trim().equals(currentTitle.trim(), ignoreCase = true) }
    }
    val anchored = exact?.takeIf { href.toString().contains('#') }
    return anchored ?: titled ?: exact ?: candidates.first()
}
