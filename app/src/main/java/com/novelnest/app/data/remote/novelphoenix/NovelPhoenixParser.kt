package com.novelnest.app.data.remote.novelphoenix

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Pure parsing functions for NovelPhoenix HTML & JSON payloads.
 *
 * Rules:
 * - Pure functions with NO networking logic inside.
 * - String input -> Domain/Parser models.
 * - Throw [PhoenixException.ParseError] when a mandatory structure is broken.
 * - Throw [PhoenixException.SourceBlockedError] when a Cloudflare challenge is returned.
 * - Throw [PhoenixException.ResourceNotFoundError] when a 404 page is returned.
 */
object NovelPhoenixParser {

    /**
     * Parses the novel detail page.
     */
    fun parseNovelDetail(html: String, pageUrl: String = ""): PhoenixNovelDetail {
        if (NovelPhoenixUtils.isCloudflareChallenge(html)) {
            throw PhoenixException.SourceBlockedError(isCloudflareChallenge = true)
        }
        val doc = Jsoup.parse(html)
        if (html.contains("404 Not Found") ||
            doc.title().contains("Page Not Found", ignoreCase = true) ||
            doc.title().contains("404", ignoreCase = true)
        ) {
            throw PhoenixException.ResourceNotFoundError(pageUrl)
        }

        // Title
        val title = selectFirstText(doc, NovelPhoenixSelectors.DETAIL_TITLE_FALLBACKS)
            ?.replace(" - Novel Phoenix", "")
            ?.trim()
            ?: throw PhoenixException.ParseError("title", "Novel title element not found")

        // Slug derived from canonical link, og:url, or pageUrl
        val canonical = doc.selectFirst("link[rel=canonical]")?.attr("href")
            ?: doc.selectFirst("meta[property=og:url]")?.attr("content")
            ?: pageUrl
        val slug = canonical.trimEnd('/').substringAfterLast('/')

        // Author
        val author = selectFirstText(doc, NovelPhoenixSelectors.DETAIL_AUTHOR_FALLBACKS)?.trim() ?: "Unknown"

        // Cover URL
        val rawCover = doc.selectFirst(".fixed-img .cover img")?.attr("src")
            ?: doc.selectFirst(".glass-background img")?.attr("src")
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
        val coverUrl = NovelPhoenixUtils.resolveUrl(rawCover)

        // Synopsis
        val synopsisParagraphs = doc.select(NovelPhoenixSelectors.DETAIL_SYNOPSIS_FALLBACKS[0])
            .map { it.text().trim() }
            .filter { it.isNotBlank() && !it.equals("Show More", ignoreCase = true) }
        val synopsis = if (synopsisParagraphs.isNotEmpty()) {
            synopsisParagraphs.joinToString("\n\n")
        } else {
            doc.selectFirst(".summary .content")?.ownText()?.trim().orEmpty()
        }

        // Status
        val statusText = doc.selectFirst(".header-stats strong.ongoing")?.text()
            ?: doc.selectFirst(".header-stats strong.completed")?.text()
            ?: selectFirstText(doc, NovelPhoenixSelectors.DETAIL_STATUS_FALLBACKS)
            ?: "Ongoing"
        val status = if (statusText.contains("Completed", ignoreCase = true)) "Completed" else "Ongoing"

        // Ratings: Try JSON-LD first for high accuracy
        var ratingVal = 4.5f
        var ratingCount = 0
        val jsonLdScripts = doc.select("script[type=application/ld+json]")
        for (script in jsonLdScripts) {
            val content = script.data()
            if (content.contains("AggregateRating", ignoreCase = true)) {
                val ratingValMatch = Regex("\"ratingValue\"\\s*:\\s*\"?([0-9.]+)\"?").find(content)
                val ratingCountMatch = Regex("\"ratingCount\"\\s*:\\s*\"?([0-9]+)\"?").find(content)
                if (ratingValMatch != null) {
                    ratingValMatch.groupValues[1].toFloatOrNull()?.let { ratingVal = it }
                }
                if (ratingCountMatch != null) {
                    ratingCountMatch.groupValues[1].toIntOrNull()?.let { ratingCount = it }
                }
                if (ratingCount > 0) break
            }
        }
        if (ratingCount == 0) {
            val nubText = doc.selectFirst(".rating strong.nub, .rating .my-rating")?.text().orEmpty()
            nubText.toFloatOrNull()?.let { ratingVal = it }
        }

        // Rank
        val rankRaw = selectFirstText(doc, NovelPhoenixSelectors.DETAIL_RANK_FALLBACKS).orEmpty()
        val rankNumber = rankRaw.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

        // Stats: views, bookmarks, chapters
        val totalChaptersRaw = doc.selectFirst(".header-stats span:has(small:contains(Chapters)) strong")?.text()
            ?: doc.selectFirst(".header-stats span:nth-of-type(1) strong")?.text().orEmpty()
        val totalChapters = totalChaptersRaw.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

        val viewsRaw = doc.selectFirst(".header-stats span:has(small:contains(Views)) strong")?.text()
            ?: doc.selectFirst(".header-stats span:nth-of-type(2) strong")?.text().orEmpty()
        val viewsApprox = NovelPhoenixUtils.parseMetricCount(viewsRaw)

        val bookmarksRaw = doc.selectFirst(".header-stats span:has(small:contains(Bookmarked)) strong")?.text()
            ?: doc.selectFirst(".header-stats span:nth-of-type(3) strong")?.text().orEmpty()
        val bookmarksApprox = NovelPhoenixUtils.parseMetricCount(bookmarksRaw)

        // Genres
        val genres = doc.select(NovelPhoenixSelectors.DETAIL_GENRES_SELECTOR)
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        // Tags
        val tags = doc.select(NovelPhoenixSelectors.DETAIL_TAGS_SELECTOR)
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        // Latest chapter & updated at
        val latestChTitle = doc.selectFirst(NovelPhoenixSelectors.DETAIL_LATEST_CHAPTER_TITLE)?.text().orEmpty()
        val rawUpdatedAt = doc.selectFirst(NovelPhoenixSelectors.DETAIL_LATEST_CHAPTER_UPDATE)?.text().orEmpty()
        val approxUpdatedTimestamp = NovelPhoenixUtils.parseRelativeDate(rawUpdatedAt)

        return PhoenixNovelDetail(
            id = "np-$slug",
            slug = slug,
            title = title,
            author = author,
            coverUrl = coverUrl,
            synopsis = synopsis,
            status = status,
            rating = ratingVal,
            ratingCount = ratingCount,
            rank = rankNumber,
            viewsRaw = viewsRaw,
            viewsApprox = viewsApprox,
            bookmarksRaw = bookmarksRaw,
            bookmarksApprox = bookmarksApprox,
            totalChapters = totalChapters,
            genres = genres,
            tags = tags,
            latestChapterTitle = latestChTitle,
            rawUpdatedAt = rawUpdatedAt,
            approxUpdatedTimestamp = approxUpdatedTimestamp
        )
    }

    /**
     * Parses a paginated chapter list HTML page (e.g. /novel/{slug}/chapters?page=N).
     */
    fun parseChapterList(html: String): List<PhoenixChapterItem> {
        if (NovelPhoenixUtils.isCloudflareChallenge(html)) {
            throw PhoenixException.SourceBlockedError(isCloudflareChallenge = true)
        }
        val doc = Jsoup.parse(html)
        val items = doc.select(NovelPhoenixSelectors.CHAPTER_LIST_ITEMS)
        val chapters = mutableListOf<PhoenixChapterItem>()

        for (item in items) {
            val linkEl = item.selectFirst(NovelPhoenixSelectors.CHAPTER_ITEM_LINK) ?: continue
            val href = linkEl.attr("href").trim()
            if (href.isBlank() || href.startsWith("javascript:")) continue

            // The chapter URL path is its unique identity
            val urlPath = if (href.startsWith("http")) {
                val parsed = java.net.URI(href).path
                parsed
            } else {
                href
            }

            val chNumber = item.selectFirst(NovelPhoenixSelectors.CHAPTER_ITEM_NO)?.text()?.trim().orEmpty()
            val chTitle = item.selectFirst(NovelPhoenixSelectors.CHAPTER_ITEM_TITLE)?.text()?.trim()
                ?: linkEl.attr("title").trim()
            val rawTime = item.selectFirst(NovelPhoenixSelectors.CHAPTER_ITEM_TIME)?.text()?.trim().orEmpty()
            val approxTime = NovelPhoenixUtils.parseRelativeDate(rawTime)

            // Slug chapter ID
            val id = urlPath.trimEnd('/').substringAfterLast('/')

            chapters.add(
                PhoenixChapterItem(
                    id = id,
                    chapterNumber = chNumber,
                    title = chTitle,
                    urlPath = urlPath,
                    rawReleaseDate = rawTime,
                    approxReleaseTimestamp = approxTime,
                    isLocked = false
                )
            )
        }

        return chapters
    }

    /**
     * Parses the chapter reader page.
     * Extracts title, paragraphs, previous and next chapter URLs. Discards ad injections.
     */
    fun parseChapterContent(html: String): PhoenixChapterContent {
        if (NovelPhoenixUtils.isCloudflareChallenge(html)) {
            throw PhoenixException.SourceBlockedError(isCloudflareChallenge = true)
        }
        val doc = Jsoup.parse(html)

        // Chapter title
        val title = doc.selectFirst("#content h4")?.text()?.trim()
            ?: doc.selectFirst(".titles .chapter-title")?.text()?.trim()
            ?: doc.selectFirst(".box-detail h1 .chapter-title")?.text()?.trim()
            ?: doc.title().substringBefore(" - Novel Phoenix").trim()

        val contentContainer = doc.selectFirst(NovelPhoenixSelectors.CHAPTER_CONTENT_CONTAINER)
            ?: throw PhoenixException.ParseError("content", "Chapter content container (#content) not found")

        // Clean out noise elements (ads, ins, iframes, scripts)
        for (noiseSelector in NovelPhoenixSelectors.NOISE_SELECTORS_TO_REMOVE) {
            contentContainer.select(noiseSelector).remove()
        }

        // Extract clean paragraphs
        val paragraphs = contentContainer.select("p")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        // Navigation links: Prev
        val prevEl = doc.selectFirst("a.prevchap")
        val prevHref = prevEl?.attr("href")?.trim().orEmpty()
        val prevUrl = if (prevEl != null && !prevEl.hasClass("isDisabled") && prevHref.isNotBlank() && !prevHref.startsWith("javascript:")) {
            NovelPhoenixUtils.resolveUrl(prevHref)
        } else {
            null
        }

        // Navigation links: Next
        val nextEl = doc.selectFirst("a.nextchap")
        val nextHref = nextEl?.attr("href")?.trim().orEmpty()
        val nextUrl = if (nextEl != null && !nextEl.hasClass("isDisabled") && nextHref.isNotBlank() && !nextHref.startsWith("javascript:")) {
            NovelPhoenixUtils.resolveUrl(nextHref)
        } else {
            null
        }

        // Chapter identifier from canonical or og:url
        val chapterId = doc.selectFirst("meta[property=og:url]")?.attr("content")?.trimEnd('/')?.substringAfterLast('/')
            ?: "chapter"

        return PhoenixChapterContent(
            id = chapterId,
            title = title,
            paragraphs = paragraphs,
            prevChapterUrl = prevUrl,
            nextChapterUrl = nextUrl
        )
    }

    /**
     * Parses the live AJAX search JSON response.
     * Response schema: {"data": [{"title": "...", "slug": "...", "rank": 1, "total_chapter": 3221, "image": "server-1/..."}]}
     */
    fun parseLiveSearchJson(jsonString: String): List<PhoenixListingItem> {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw PhoenixException.ParseError("liveSearchJson", "Payload does not start and end with curly braces")
        }

        // Extract "data" array content: [ ... ]
        val dataStartIndex = trimmed.indexOf("\"data\"")
        if (dataStartIndex == -1) {
            throw PhoenixException.ParseError("liveSearchJson", "Missing 'data' field in JSON")
        }
        val arrayStart = trimmed.indexOf('[', dataStartIndex)
        val arrayEnd = trimmed.lastIndexOf(']')
        if (arrayStart == -1 || arrayEnd == -1 || arrayEnd < arrayStart) {
            throw PhoenixException.ParseError("liveSearchJson", "Malformed 'data' array in JSON")
        }

        val arrayContent = trimmed.substring(arrayStart + 1, arrayEnd).trim()
        if (arrayContent.isEmpty()) return emptyList()

        val results = mutableListOf<PhoenixListingItem>()
        val objectRegex = Regex("\\{([^}]*)\\}")
        val matches = objectRegex.findAll(arrayContent)

        for (match in matches) {
            val objBody = match.groupValues[1]

            fun extractString(key: String): String {
                val pattern = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
                val raw = pattern.find(objBody)?.groupValues?.get(1).orEmpty()
                return raw.replace("\\/", "/")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }

            fun extractInt(key: String): Int {
                val pattern = Regex("\"$key\"\\s*:\\s*([0-9]+)")
                return pattern.find(objBody)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            }

            val slug = extractString("slug")
            val title = extractString("title")
            val totalCh = extractInt("total_chapter")
            val imageRel = extractString("image")
            val rank = extractInt("rank")

            if (slug.isNotBlank() && title.isNotBlank()) {
                results.add(
                    PhoenixListingItem(
                        slug = slug,
                        title = title,
                        coverUrl = NovelPhoenixUtils.resolveUrl(imageRel),
                        latestChapter = if (totalCh > 0) "$totalCh Chapters" else "Latest",
                        rating = 4.7f,
                        rankBadge = if (rank > 0) "R $rank" else null
                    )
                )
            }
        }
        return results
    }

    /**
     * Parses listing cards from browse, ranking, or fallback HTML search pages.
     */
    fun parseListingHtml(html: String): List<PhoenixListingItem> {
        if (NovelPhoenixUtils.isCloudflareChallenge(html)) {
            throw PhoenixException.SourceBlockedError(isCloudflareChallenge = true)
        }
        val doc = Jsoup.parse(html)
        val items = doc.select(NovelPhoenixSelectors.LISTING_ITEMS)
        val results = mutableListOf<PhoenixListingItem>()

        for (item in items) {
            val titleEl = item.selectFirst(NovelPhoenixSelectors.LISTING_TITLE)
            val title = titleEl?.text()?.ifBlank { titleEl.attr("title") }?.trim().orEmpty()
            val linkEl = item.selectFirst(NovelPhoenixSelectors.LISTING_LINK)
            val href = linkEl?.attr("href")?.trim().orEmpty()
            if (title.isBlank() || href.isBlank()) continue

            val slug = href.trimEnd('/').substringAfterLast('/')

            // Lazy loaded image: check data-src first, then src
            val imgEl = item.selectFirst("img")
            var cover = imgEl?.attr("data-src")?.ifBlank { imgEl.attr("src") }.orEmpty()
            if (cover.contains("data:image")) {
                cover = imgEl?.attr("src").orEmpty()
            }
            val coverUrl = NovelPhoenixUtils.resolveUrl(cover)

            val latestCh = item.selectFirst(NovelPhoenixSelectors.LISTING_LATEST_CHAPTER)?.text()?.trim().orEmpty()
            val ratingText = item.selectFirst(NovelPhoenixSelectors.LISTING_RATING_BADGE)?.text()
                ?.replace(Regex("[^0-9.]"), "")
            val rating = ratingText?.toFloatOrNull() ?: 4.5f

            val rankBadge = item.selectFirst(NovelPhoenixSelectors.LISTING_RANK_BADGE)?.text()?.trim()

            results.add(
                PhoenixListingItem(
                    slug = slug,
                    title = title,
                    coverUrl = coverUrl,
                    latestChapter = latestCh.ifBlank { "Latest" },
                    rating = rating,
                    rankBadge = rankBadge
                )
            )
        }

        return results
    }

    private fun selectFirstText(doc: Document, selectors: List<String>): String? {
        for (sel in selectors) {
            val el = doc.selectFirst(sel) ?: continue
            val text = el.text()
            if (text.isNotBlank()) return text
            val attrContent = el.attr("content")
            if (attrContent.isNotBlank()) return attrContent
        }
        return null
    }
}
