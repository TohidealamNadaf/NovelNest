package com.novelnest.app.data.local.epub

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url

/** Reads just the active spine resource; never caches a book or sends markup to the clipboard. */
internal object EpubChapterText {
    // Leave ample room below Android's shared Binder transaction buffer, including parcel overhead.
    private const val MAX_CLIPBOARD_CHARACTERS = 120_000

    suspend fun read(publication: Publication, section: Url): String {
        require(publication.containsReadingResource(section)) { "This section is not part of the book." }
        val resource = publication.get(section) ?: error("This EPUB section is missing.")
        val bytes = try { resource.read().getOrNull() ?: error("This EPUB section could not be read.") }
            finally { resource.close() }
        require(bytes.size <= EpubArchiveValidator.MAX_MARKUP_BYTES + 64 * 1024) {
            "This EPUB section is too large to copy."
        }
        currentCoroutineContext().ensureActive()
        val doc = Jsoup.parse(bytes.inputStream(), "UTF-8", "")
        doc.select("script, style, template, noscript, svg, [hidden], [aria-hidden=true]").remove()
        // wholeText preserves preformatted content; add explicit boundaries for adjacent HTML blocks.
        doc.body().select("p, div, section, article, h1, h2, h3, h4, h5, h6, li, blockquote, pre, tr")
            .forEach { it.before(TextNode("\n")); it.after(TextNode("\n")) }
        val text = doc.body().wholeText().replace("\r\n", "\n")
            .replace(Regex("\n[ \t]*\n(?:[ \t]*\n)+"), "\n\n").trim()
        currentCoroutineContext().ensureActive()
        require(text.isNotBlank()) { "This section has no text to copy." }
        require(text.length <= MAX_CLIPBOARD_CHARACTERS) {
            "This chapter is too large for the clipboard. Select a smaller passage to copy."
        }
        return text
    }
}
