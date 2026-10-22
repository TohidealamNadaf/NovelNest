package com.novelnest.app.data.remote.freewebnovel

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.model.SectionType
import com.novelnest.app.feature.reader.Chapter
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONException

object FreeWebNovelParser {

    fun document(html: String, url: String): Document {
        return FreeWebNovelUtils.document(html, url)
    }

    private fun cover(item: Element): String {
        val img = item.selectFirst(FreeWebNovelSelectors.IMAGE) ?: return ""
        return listOf(img.attr("data-src"), img.attr("src"))
            .firstNotNullOfOrNull { runCatching { FreeWebNovelUtils.posterUrl(FreeWebNovelUrls.resolve(it)) }.getOrNull() }.orEmpty()
    }

    private fun card(item: Element): Novel? {
        val link = item.selectFirst(FreeWebNovelSelectors.CARD_TITLE) ?: return null
        val href = link.attr("href")
        val url = runCatching { FreeWebNovelUrls.novelUrl(href) }.getOrNull() ?: return null
        if (!href.contains("/novel/")) return null
        val title = link.text().trim().ifBlank { link.attr("title").trim() }
        if (title.isBlank()) return null
        val latestRaw = item.selectFirst(FreeWebNovelSelectors.LATEST_CHAPTER)?.attr("title")
            ?.ifBlank { item.selectFirst(FreeWebNovelSelectors.CHAPTER_LINK)?.text().orEmpty() }
            ?: item.select(FreeWebNovelSelectors.LINK).firstOrNull { it.text().contains("Chapters", true) }?.text().orEmpty()
        val shortCaption = latestRaw.removePrefix(title).trimStart(' ', ':', '-')
        val latest = if (shortCaption.startsWith("Chapter ", true) || shortCaption.startsWith("Ch.", true))
            shortCaption else latestRaw
        return Novel(
            id = FreeWebNovelUrls.novelId(url), title = title, coverUrl = cover(item),
            source = NovelSourceType.FREE_WEB_NOVEL, sourceUrl = url,
            rating = FreeWebNovelUtils.rating(item),
            latestChapter = latest, genres = item.select(FreeWebNovelSelectors.GENRE).map { it.text() }.distinct(),
            status = if (item.select(FreeWebNovelSelectors.COMPLETED).isNotEmpty() || latest.contains("Full", true)) "Completed" else "Ongoing"
        )
    }

    fun parseListing(html: String, url: String): List<Novel> {
        val doc = document(html, url)
        val container = doc.selectFirst(FreeWebNovelSelectors.LISTING)
        if (container == null && !doc.text().contains("No results", true) && !doc.text().contains("No novels", true)) {
            throw FreeWebNovelException.ParseError("FreeWebNovel listing layout was not recognized")
        }
        val novels = container?.select(FreeWebNovelSelectors.LISTING_ITEM)?.mapNotNull { card(it) }?.distinctBy { it.id }.orEmpty()
        if (novels.isEmpty() && doc.select(FreeWebNovelSelectors.EMPTY_LIST).isEmpty() &&
            !doc.text().contains("No novels", true) && !doc.text().contains("No results", true)) {
            throw FreeWebNovelException.ParseError("FreeWebNovel listing layout was not recognized")
        }
        return novels
    }

    fun parseHome(html: String, popularHtml: String): List<NovelSection> {
        val doc = document(html, FreeWebNovelUrls.BASE_URL + "/home")
        val recommended = doc.select(FreeWebNovelSelectors.RECOMMENDATIONS).mapNotNull { card(it) }.distinctBy { it.id }
        val released = doc.select(FreeWebNovelSelectors.RELEASES).mapNotNull { card(it) }.distinctBy { it.id }
        val popular = parseListing(popularHtml, FreeWebNovelUrls.BASE_URL + "/sort/most-popular")
        if (recommended.isEmpty() || released.isEmpty() || popular.isEmpty()) throw FreeWebNovelException.ParseError("FreeWebNovel home sections were not recognized")
        return listOf(
            NovelSection("fwn-live-hero-v1", "FreeWebNovel Highlights", "", "Featured on FreeWebNovel", SectionType.HERO_SLIDER, recommended.take(3)),
            NovelSection("fwn-live-recommended-v1", "Recommended Novels", "", "Recommendations from FreeWebNovel", SectionType.HORIZONTAL_LIST, recommended),
            NovelSection("fwn-live-popular-v1", "Most Popular", "", "FreeWebNovel rankings", SectionType.HORIZONTAL_LIST, popular),
            NovelSection("fwn-live-latest-v1", "Recently Released", "", "Latest chapter updates", SectionType.HORIZONTAL_LIST, released)
        )
    }

    fun parseDetails(html: String, url: String): Novel {
        val doc = document(html, url)
        fun meta(name: String) = doc.selectFirst("meta[property='$name']")?.attr("content").orEmpty()
        val canonical = doc.selectFirst(FreeWebNovelSelectors.CANONICAL)?.attr("href").orEmpty().ifBlank { url }
        val title = doc.selectFirst(FreeWebNovelSelectors.DETAIL_TITLE)?.text().orEmpty().ifBlank { meta("og:novel:novel_name") }
        if (title.isBlank()) throw FreeWebNovelException.ParseError("FreeWebNovel novel title was not found")
        val synopsis = doc.select(FreeWebNovelSelectors.SYNOPSIS_PARAGRAPHS).joinToString("\n\n") { it.text().trim() }
            .ifBlank { doc.selectFirst(FreeWebNovelSelectors.SYNOPSIS)?.text().orEmpty() }
        return Novel(
            id = FreeWebNovelUrls.novelId(canonical), title = title,
            coverUrl = runCatching { FreeWebNovelUtils.posterUrl(FreeWebNovelUrls.resolve(meta("og:image"))) }.getOrDefault(""),
            source = NovelSourceType.FREE_WEB_NOVEL, sourceUrl = FreeWebNovelUrls.novelUrl(canonical),
            synopsis = synopsis, author = meta("og:novel:author").ifBlank { "Unknown" },
            rating = Regex("[0-9.]+").find(doc.selectFirst(FreeWebNovelSelectors.DETAIL_RATING)?.text().orEmpty())?.value?.toFloatOrNull() ?: 0f,
            latestChapter = meta("og:novel:lastest_chapter_name"),
            chapterCount = doc.selectFirst(FreeWebNovelSelectors.PAGINATION)?.attr("data-total-chapters")?.toIntOrNull() ?: 0,
            genres = meta("og:novel:genre").split(',').map { it.trim() }.filter { it.isNotBlank() },
            status = if (meta("og:novel:status").contains("complet", true)) "Completed" else "Ongoing"
        )
    }

    private fun chapterLinks(root: Element): List<Chapter> = root.select(FreeWebNovelSelectors.CHAPTER_LINK).mapNotNull { link ->
        val url = runCatching { FreeWebNovelUrls.resolve(link.attr("href")) }.getOrNull() ?: return@mapNotNull null
        val id = runCatching { FreeWebNovelUrls.chapterId(url) }.getOrNull() ?: return@mapNotNull null
        Chapter(id = id, title = link.text().trim().ifBlank { link.attr("title") }, url = url)
    }.distinctBy { it.url }

    fun parseChapterPage(html: String, url: String): FreeWebNovelChapterPage {
        if (html.trimStart().startsWith("{")) {
            try {
                val json = JSONObject(html)
                if (json.optInt("code") != 200) throw FreeWebNovelException.ParseError("Invalid FreeWebNovel chapter response")
                val chapters = chapterLinks(Jsoup.parse(json.getString("html"), url))
                return FreeWebNovelChapterPage(chapters, json.getInt("page"), json.getInt("totalPage"), json.getInt("totalChapters"))
            } catch (error: JSONException) {
                throw FreeWebNovelException.ParseError("FreeWebNovel chapter response layout was not recognized", error)
            }
        }
        val doc = document(html, url)
        val root = doc.selectFirst(FreeWebNovelSelectors.PAGINATION) ?: throw FreeWebNovelException.ParseError("FreeWebNovel chapter pagination was not found")
        val list = doc.selectFirst(FreeWebNovelSelectors.CHAPTER_LIST) ?: throw FreeWebNovelException.ParseError("FreeWebNovel chapter list was not found")
        return FreeWebNovelChapterPage(chapterLinks(list), root.attr("data-current-page").toIntOrNull() ?: 1,
            root.attr("data-total-page").toIntOrNull() ?: 1, root.attr("data-total-chapters").toIntOrNull() ?: 0)
    }

    fun parseContent(html: String, url: String): Chapter {
        val doc = document(html, url)
        val body = doc.selectFirst(FreeWebNovelSelectors.BODY) ?: throw FreeWebNovelException.ParseError("FreeWebNovel chapter body was not found")
        val title = body.selectFirst(FreeWebNovelSelectors.BODY_TITLE)?.text().orEmpty().ifBlank { doc.selectFirst(FreeWebNovelSelectors.FALLBACK_BODY_TITLE)?.text().orEmpty() }
        body.select(FreeWebNovelSelectors.BODY_NOISE).remove()
        val paragraphs = body.select(FreeWebNovelSelectors.PARAGRAPH).map { it.text().trim() }.filter { it.isNotBlank() }
            .ifEmpty { body.html().split(Regex("(?i)<br\\s*/?>")).map { Jsoup.parse(it).text().trim() }.filter { it.isNotBlank() } }
        if (paragraphs.isEmpty()) throw FreeWebNovelException.ParseError("FreeWebNovel chapter body is empty")
        fun navigation(selector: String): String? = doc.select(selector).firstNotNullOfOrNull { link ->
            runCatching { FreeWebNovelUrls.resolve(link.attr("href")) }.getOrNull()
                ?.takeIf { runCatching { FreeWebNovelUrls.chapterId(it) }.isSuccess }
        }
        return Chapter(FreeWebNovelUrls.chapterId(url), title, paragraphs, FreeWebNovelUrls.resolve(url),
            prevUrl = navigation(FreeWebNovelSelectors.PREVIOUS),
            nextUrl = navigation(FreeWebNovelSelectors.NEXT))
    }
}
