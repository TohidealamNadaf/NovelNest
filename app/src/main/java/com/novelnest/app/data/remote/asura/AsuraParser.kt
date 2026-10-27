package com.novelnest.app.data.remote.asura

import com.novelnest.app.data.model.*
import com.novelnest.app.data.remote.RatingParser
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.util.Locale

/** Pure parsers. Missing contracts fail explicitly; no stock covers, ratings or sample titles. */
object AsuraParser {
    private val source = NovelSourceType.ASURA_SCANS
    private fun text(obj: JSONObject, key: String) = AsuraUtils.text(obj, key)
    private fun array(obj: JSONObject, key: String): JSONArray = obj.optJSONArray(key)
        ?: throw AsuraException.Parse("Missing $key list")
    private fun required(value: String, field: String) = value.takeIf { it.isNotBlank() }
        ?: throw AsuraException.Parse("Missing $field")
    private fun status(value: String) = value.ifBlank { "Unknown" }.replaceFirstChar { it.titlecase(Locale.ROOT) }
    private fun safeImage(value: String) = if (value.isBlank()) "" else AsuraUrls.image(value)

    fun novel(obj: JSONObject): Novel {
        val url = text(obj, "public_url").ifBlank { text(obj, "comic_public_url") }
            .ifBlank { "/comics/${text(obj, "slug").ifBlank { text(obj, "comic_slug") }}" }
        val slug = AsuraUrls.slug(url)
        val genres = obj.optJSONArray("genres")?.let { list -> (0 until list.length()).mapNotNull {
            (list.opt(it) as? JSONObject)?.let { row -> text(row, "name") } ?: (list.opt(it) as? String)
        }.filter(String::isNotBlank) }.orEmpty()
        val latest = text(obj, "latest_chapter_number").ifBlank { text(obj, "number") }
            .ifBlank { obj.optJSONArray("latest_chapters")?.optJSONObject(0)?.let { text(it, "number") }.orEmpty() }
        return Novel(
            AsuraUrls.novelId(slug), required(text(obj, "title").ifBlank { text(obj, "comic_name") }, "series title"),
            safeImage(text(obj, "cover_url").ifBlank { text(obj, "cover") }.ifBlank { text(obj, "comic_cover") }),
            source, AsuraUrls.seriesUrl(slug), synopsis = AsuraUtils.plain(text(obj, "description")),
            author = text(obj, "author").ifBlank { "Unknown" }, rating = RatingParser.score(text(obj, "rating"), 10f) ?: 0f,
            latestChapter = latest.takeIf(String::isNotBlank)?.let { "Chapter $it" }.orEmpty(), genres = genres,
            status = status(text(obj, "status")), views = text(obj, "view_count"),
            rank = obj.optInt("popularity_rank", 0).coerceAtLeast(0), chapterCount = obj.optInt("chapter_count", 0).coerceAtLeast(0)
        )
    }

    fun home(html: String): List<NovelSection> {
        val doc = AsuraUtils.document(html, AsuraUrls.BASE)
        fun entries(component: String, key: String) = AsuraUtils.island(doc, component)?.let {
            AsuraUtils.objects(array(it, key)).filter { row -> !text(row, "public_url").startsWith("/novels/") }.map(::novel).distinctBy { row -> row.id }
        }.orEmpty()
        val sections = listOf(
            NovelSection("asura-live-hero", "Featured Manhwa", type = SectionType.HERO_SLIDER, novels = entries(AsuraSelectors.HERO, "items")),
            NovelSection("asura-live-trending", "Trending Manhwa", novels = entries(AsuraSelectors.TRENDING, "items")),
            NovelSection("asura-live-popular", "Popular Manhwa", novels = entries(AsuraSelectors.POPULAR, "items")),
            NovelSection("asura-live-latest", "Recently Released", novels = entries(AsuraSelectors.LATEST, "chapters"))
        ).filter { it.novels.isNotEmpty() }
        if (sections.isEmpty()) throw AsuraException.Parse("No supported comic sections found")
        return sections.reuseRatings()
    }

    fun browse(html: String, url: String): BrowsePage {
        val canonical = AsuraUrls.browse(url)
        val props = AsuraUtils.island(AsuraUtils.document(html, canonical), AsuraSelectors.BROWSE)
            ?: throw AsuraException.Parse("BrowseFilters component missing")
        val novels = AsuraUtils.objects(array(props, "initialSeries")).map(::novel).distinctBy { it.id }
        val categories = AsuraUtils.objects(array(props, "availableGenres")).map {
            BrowseCategory(required(text(it, "name"), "genre name"), AsuraUrls.genre(required(text(it, "slug"), "genre slug")))
        }
        val page = props.optInt("initialCurrentPage", 1)
        val totalPages = props.optInt("initialTotalPages", -1)
        if (page !in 1..1000 || totalPages !in 0..1000 || (novels.isEmpty() && props.optInt("totalCount", -1) != 0))
            throw AsuraException.Parse("Invalid browse pagination")
        return BrowsePage(novels, categories, if (page < totalPages) AsuraUrls.nextBrowse(canonical, page + 1) else null)
    }

    fun search(json: String): List<Novel> {
        val root = JSONObject(json)
        if (!root.has("data") || root.optJSONObject("meta") == null) throw AsuraException.Parse("Invalid search response")
        // The live API encodes genuine zero results as data:null, not always an empty array.
        if (root.isNull("data")) return emptyList()
        return AsuraUtils.objects(array(root, "data")).map(::novel).distinctBy { it.id }
    }

    fun details(html: String, url: String): ManhwaDetails {
        val slug = AsuraUrls.slug(url)
        val doc = AsuraUtils.document(html, AsuraUrls.seriesUrl(slug))
        val schema = doc.select(AsuraSelectors.JSON_LD).mapNotNull { runCatching { JSONObject(it.data()) }.getOrNull() }
            .firstOrNull { it.optString("@type") == "ComicSeries" } ?: throw AsuraException.Parse("ComicSeries metadata missing")
        if (AsuraUrls.slug(required(text(schema, "url"), "series URL")) != slug) throw AsuraException.Parse("Wrong series returned")
        val props = AsuraUtils.island(doc, AsuraSelectors.CHAPTER_LIST) ?: throw AsuraException.Parse("Chapter list missing")
        val chapters = chapters(array(props, "chapters"), slug)
        val expected = props.optInt("totalChapters", -1)
        if (expected !in 0..AsuraSelectors.MAX_CHAPTERS || chapters.size != expected) throw AsuraException.Parse("Incomplete chapter list")
        val rating = schema.optJSONObject("aggregateRating")
        val rawRating = rating?.let { text(it, "ratingValue").toFloatOrNull() }?.takeIf { it.isFinite() && it in 0f..10f && it > 0f }
        val cover = schema.opt("image").let { if (it is JSONObject) text(it, "url") else it?.toString().orEmpty() }
        val genres = schema.optJSONArray("genre")?.let { items -> (0 until items.length()).map { items.getString(it) } }.orEmpty()
        val novel = Novel(
            AsuraUrls.novelId(slug), required(text(schema, "name"), "title"), safeImage(cover), source, AsuraUrls.seriesUrl(slug),
            synopsis = doc.selectFirst(AsuraSelectors.DESCRIPTION)?.text().orEmpty().ifBlank { AsuraUtils.plain(text(schema, "description")) },
            author = schema.optJSONObject("author")?.let { text(it, "name") }.orEmpty().ifBlank { "Unknown" },
            rating = RatingParser.structured(doc) ?: 0f, latestChapter = chapters.lastOrNull()?.title.orEmpty(),
            genres = genres, status = status(label(doc, "Status")), views = label(doc, "Views"),
            rank = Regex("RANK\\s*#?\\s*(\\d+)", RegexOption.IGNORE_CASE).find(doc.selectFirst(AsuraSelectors.RANK)?.text().orEmpty())
                ?.groupValues?.get(1)?.toIntOrNull() ?: 0, chapterCount = chapters.size
        )
        val seriesId = AsuraUtils.island(doc, "SeriesViewTracker.")?.optLong("seriesId", 0)?.takeIf { it > 0 }
        return ManhwaDetails(novel, chapters, seriesId, schema.optJSONObject("illustrator")?.let { text(it, "name") }.orEmpty(),
            doc.selectFirst(AsuraSelectors.ALT_TITLES)?.text()?.split('•')?.map(String::trim)?.filter(String::isNotBlank).orEmpty(),
            label(doc, "Type").ifBlank { "Unknown" }, rawRating, bookmarkCount = label(doc, "Bookmarks"))
    }

    private fun label(doc: Document, name: String): String = (doc.selectFirst("main") ?: doc).allElements.firstOrNull { it.ownText().equals(name, true) }
        ?.parent()?.text()?.replace(Regex("\\b${Regex.escape(name)}\\b", RegexOption.IGNORE_CASE), "")?.trim().orEmpty()

    private fun chapter(obj: JSONObject, slug: String): ManhwaChapter {
        val number = AsuraUrls.number(required(text(obj, "number"), "chapter number"))
        val name = text(obj, "title").trim()
        return ManhwaChapter(AsuraUrls.chapterId(slug, number), "Chapter $number" + if (name.isBlank()) "" else ": $name", number,
            AsuraUrls.chapterUrl(slug, number), obj.optInt("page_count", 0).coerceAtLeast(0), obj.optBoolean("is_premium", false),
            text(obj, "published_at").ifBlank { null }, text(obj, "early_access_until").ifBlank { null })
    }
    private fun chapters(rows: JSONArray, slug: String): List<ManhwaChapter> {
        if (rows.length() > AsuraSelectors.MAX_CHAPTERS) throw AsuraException.Parse("Too many chapters")
        val result = AsuraUtils.objects(rows).map { chapter(it, slug) }
        if (result.distinctBy { it.id }.size != result.size) throw AsuraException.Parse("Duplicate chapter numbers")
        return result.sortedBy { it.number.toBigDecimal() }
    }
    private fun pages(rows: JSONArray): List<ManhwaPage> {
        if (rows.length() !in 1..AsuraSelectors.MAX_PAGES) throw AsuraException.Parse("Chapter returned no complete public page list")
        return AsuraUtils.objects(rows).mapIndexed { index, row ->
            ManhwaPage(index, AsuraUrls.image(required(text(row, "url"), "page image")),
                row.optInt("width", 0).takeIf { it > 0 }, row.optInt("height", 0).takeIf { it > 0 })
        }
    }
    fun chapterContent(json: String, url: String): ManhwaChapterContent {
        val (slug, number) = AsuraUrls.chapterParts(url)
        val data = JSONObject(json).optJSONObject("data") ?: throw AsuraException.Parse("Chapter data missing")
        if (!data.has("is_locked")) throw AsuraException.Parse("Chapter access state missing")
        if (text(data, "access_gate").isNotBlank()) throw AsuraException.AccessRequired()
        if (data.optBoolean("is_locked", false)) throw AsuraException.Locked(text(data, "unlock_time").ifBlank { null })
        val row = data.optJSONObject("chapter") ?: throw AsuraException.Parse("Chapter missing")
        val series = data.optJSONObject("series") ?: throw AsuraException.Parse("Chapter series missing")
        if (AsuraUrls.slug(required(text(series, "slug"), "chapter series")) != slug || AsuraUrls.number(text(row, "number")) != number)
            throw AsuraException.Parse("Wrong chapter returned")
        val chapter = chapter(row, slug)
        val pages = pages(array(row, "pages"))
        if (chapter.pageCount > 0 && chapter.pageCount != pages.size) throw AsuraException.Parse("Incomplete chapter images")
        fun neighbor(key: String) = data.optJSONObject(key)?.let { AsuraUrls.chapterUrl(slug, required(text(it, "number"), "neighbor chapter")) }
        return ManhwaChapterContent(chapter, pages, neighbor("prev_chapter"), neighbor("next_chapter"), mapOf("Referer" to chapter.url))
    }
    /** HTML contract fallback, used only when the public chapter API returns 404. */
    fun chapterHtml(html: String, url: String): ManhwaChapterContent {
        val (slug, number) = AsuraUrls.chapterParts(url)
        val doc = AsuraUtils.document(html, url)
        val props = AsuraUtils.island(doc, AsuraSelectors.READER) ?: throw AsuraException.Parse("ChapterReader missing")
        if (!props.has("isLocked")) throw AsuraException.Parse("Chapter access state missing")
        if (props.optBoolean("isLocked", false)) throw AsuraException.Locked(text(props, "unlockTime").ifBlank { null })
        if (AsuraUrls.slug(text(props, "seriesSlug")) != slug || AsuraUrls.number(text(props, "chapterNumber")) != number)
            throw AsuraException.Parse("Wrong chapter returned")
        val row = JSONObject().put("number", number).put("title", text(props, "chapterTitle")).put("is_premium", props.optBoolean("isPremium"))
        val chapter = chapter(row, slug)
        val pages = pages(array(props, "pages"))
        fun neighbor(key: String) = props.optJSONObject(key)?.let { AsuraUrls.chapterUrl(slug, text(it, "number")) }
        return ManhwaChapterContent(chapter.copy(pageCount = pages.size), pages, neighbor("prevChapter"), neighbor("nextChapter"), mapOf("Referer" to chapter.url))
    }
}
