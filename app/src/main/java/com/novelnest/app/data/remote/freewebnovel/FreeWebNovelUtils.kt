package com.novelnest.app.data.remote.freewebnovel

import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object FreeWebNovelUtils {
    private val releaseThumbnail = Regex("""^(/files/article/image/\d+/(\d+)/)\2ss(\.(?:jpg|jpeg|png|webp))$""")

    /** Release rows use 40px ss thumbnails; the same book has a normal s cover.
     * Restrict the upgrade to this known FWN path; preserve unrelated/invalid URLs.
     * Also used when hydrating older cached records, without extra detail requests.
     */
    fun posterUrl(value: String): String = runCatching {
        val uri = URI(FreeWebNovelUrls.resolve(value))
        val match = releaseThumbnail.matchEntire(uri.path) ?: return@runCatching value
        val path = match.groupValues[1] + match.groupValues[2] + "s" + match.groupValues[3]
        URI(uri.scheme, uri.authority, path, uri.query, null).toASCIIString()
    }.getOrDefault(value)

    private val ratingNumber = Regex("""^([0-5](?:[.,][0-9]+)?)(?:\s*/\s*5)?$""")

    /** Read only explicit rating elements, skipping decorative spans and invalid values. */
    fun rating(item: Element): Float = item.select(FreeWebNovelSelectors.RATING)
        .asSequence().mapNotNull { element ->
            val raw = element.attr("content").ifBlank { element.text() }.trim()
            ratingNumber.matchEntire(raw)?.groupValues?.get(1)?.replace(',', '.')?.toFloatOrNull()
                ?.takeIf { it > 0f && it <= 5f }
        }.firstOrNull() ?: 0f

    fun document(html: String, url: String): Document {
        val doc = Jsoup.parse(html, url)
        if (doc.title().contains("Just a moment", true) || html.contains("cf-chl-") || html.contains("cf-mitigated")) {
            throw FreeWebNovelException.Blocked()
        }
        if (doc.title().contains("404") || doc.title().contains("Not Found", true)) throw FreeWebNovelException.NotFound()
        return doc
    }
}
