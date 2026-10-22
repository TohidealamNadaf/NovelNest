package com.novelnest.app.data.remote.freewebnovel

import com.novelnest.app.data.remote.RatingParser
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

    /** Skip decorative spans and read only explicit source rating elements. */
    fun rating(item: Element): Float = item.select(FreeWebNovelSelectors.RATING).firstNotNullOfOrNull { element ->
        sequenceOf(element.attr("data-rating"), element.attr("data-score"), element.attr("content"), element.text())
            .firstNotNullOfOrNull { RatingParser.score(it) }
    } ?: 0f

    fun document(html: String, url: String): Document {
        val doc = Jsoup.parse(html, url)
        if (doc.title().contains("Just a moment", true) || html.contains("cf-chl-") || html.contains("cf-mitigated")) {
            throw FreeWebNovelException.Blocked()
        }
        if (doc.title().contains("404") || doc.title().contains("Not Found", true)) throw FreeWebNovelException.NotFound()
        return doc
    }
}
