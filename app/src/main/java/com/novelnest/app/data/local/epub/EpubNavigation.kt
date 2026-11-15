package com.novelnest.app.data.local.epub

import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url

/** Readium 3.0.3 can return true for go() even if no reading-order resource matched. */
internal fun Publication.containsReadingResource(href: Url): Boolean {
    val target = href.removeQuery().removeFragment()
    return readingOrder.any { it.url().removeQuery().removeFragment().isEquivalent(target) }
}
