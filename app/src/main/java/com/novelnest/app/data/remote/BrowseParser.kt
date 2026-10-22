package com.novelnest.app.data.remote

import com.novelnest.app.data.model.*
import com.novelnest.app.data.remote.freewebnovel.*
import com.novelnest.app.data.remote.novelphoenix.*
import org.jsoup.Jsoup
import java.net.URI

/** Parses navigation links from the source, rather than inventing genre slugs. */
object BrowseParser {
    fun initialUrl(source: NovelSourceType) = when (source) {
        NovelSourceType.NOVEL_PHOENIX -> "${source.baseUrl}/genre-all/sort-new/status-all/all-novel"
        NovelSourceType.FREE_WEB_NOVEL -> "${FreeWebNovelUrls.BASE_URL}/sort/latest-novel"
        else -> error("This source uses its existing feed catalog")
    }

    fun validateUrl(source: NovelSourceType, url: String): String {
        val uri = URI(url)
        val expected = URI(initialUrl(source))
        require(uri.scheme == "https" && uri.host == expected.host && uri.userInfo == null && uri.port == -1) { "Invalid catalog host" }
        val validPath = when (source) {
            NovelSourceType.NOVEL_PHOENIX -> uri.path.matches(Regex("/genre-[a-z0-9-]+/sort-(new|popular)/status-[a-z-]+/all-novel"))
            NovelSourceType.FREE_WEB_NOVEL -> uri.path.matches(Regex("/(genre/[^/]+|sort/(latest-novel|most-popular))(/[0-9]+)?"))
            else -> false
        }
        require(validPath && (uri.query == null || uri.query.matches(Regex("page=[1-9][0-9]*")))) { "Invalid catalog URL" }
        return uri.toASCIIString()
    }

    fun parse(source: NovelSourceType, html: String, url: String): BrowsePage {
        validateUrl(source, url)
        val doc = if (source == NovelSourceType.FREE_WEB_NOVEL) FreeWebNovelUtils.document(html, url) else {
            if (NovelPhoenixUtils.isCloudflareChallenge(html)) throw PhoenixException.SourceBlockedError(isCloudflareChallenge = true)
            Jsoup.parse(html, url)
        }
        val novels = when (source) {
            NovelSourceType.FREE_WEB_NOVEL -> FreeWebNovelParser.parseListing(html, url)
            else -> NovelPhoenixParser.parseListingHtml(html).map { item ->
                Novel("np-${item.slug}", item.title, item.coverUrl, source,
                    "${source.baseUrl}/novel/${item.slug}", rating = item.rating, latestChapter = item.latestChapter)
            }.also {
                if (it.isEmpty() && !doc.text().contains("No novels", true) && !doc.text().contains("No results", true))
                    throw PhoenixException.ParseError("catalog", "Catalog layout was not recognized")
            }
        }
        fun safeLink(href: String): String? = runCatching { validateUrl(source, URI(url).resolve(href).toString()) }.getOrNull()
        val selector = if (source == NovelSourceType.FREE_WEB_NOVEL) FreeWebNovelSelectors.BROWSE_CATEGORIES
            else NovelPhoenixSelectors.BROWSE_CATEGORIES
        val categories = doc.select(selector).mapNotNull { link ->
            val target = safeLink(link.attr("href")) ?: return@mapNotNull null
            val name = link.text().trim()
            if (name.isBlank() || name.equals("All", true)) null else BrowseCategory(name, target)
        }.distinctBy { it.name.lowercase() }
        val next = if (source == NovelSourceType.FREE_WEB_NOVEL) {
            // FWN uses path pagination, with >> as its next link.
            doc.select(FreeWebNovelSelectors.BROWSE_PAGES).firstOrNull {
                it.text().trim() in listOf(">>", "›", "Next")
            }?.attr("href")?.let(::safeLink)
        } else doc.selectFirst(NovelPhoenixSelectors.BROWSE_NEXT)?.attr("href")?.let(::safeLink)
        return BrowsePage(novels.distinctBy { it.id }, categories, next?.takeUnless { it == url })
    }
}
