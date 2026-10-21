package com.novelnest.app.data.remote.novelphoenix

/**
 * Centralized selector catalog and configuration for NovelPhoenix.
 *
 * In the event the site updates its DOM classes or layout, modifying the selectors
 * and fallback chains here adapts the parsers without altering core extraction logic.
 */
object NovelPhoenixSelectors {

    const val BASE_URL = "https://novelphoenix.com"

    // --- Novel Detail Selectors (Primary and Fallbacks) ---
    val DETAIL_TITLE_FALLBACKS = listOf(
        "h1.novel-title",
        ".novel-info .main-head h1",
        "meta[property=og:title]"
    )

    val DETAIL_AUTHOR_FALLBACKS = listOf(
        ".novel-info .author [itemprop=author]",
        ".novel-info .author a.property-item",
        ".author a"
    )

    val DETAIL_COVER_FALLBACKS = listOf(
        ".fixed-img .cover img[src]",
        ".glass-background img[src]",
        "meta[property=og:image]"
    )

    val DETAIL_SYNOPSIS_FALLBACKS = listOf(
        ".summary .content p",
        "#info .summary p"
    )

    // Structural and label-dependent fallbacks for header stats
    // Note: ":contains(Status)" is label-dependent; ".header-stats strong.ongoing, .header-stats strong.completed" is structural.
    val DETAIL_STATUS_FALLBACKS = listOf(
        ".header-stats strong.ongoing",
        ".header-stats strong.completed",
        ".header-stats span:has(small:contains(Status)) strong"
    )

    val DETAIL_RANK_FALLBACKS = listOf(
        ".novel-info .rating .rank strong",
        ".rating .rank"
    )

    val DETAIL_VIEWS_FALLBACKS = listOf(
        ".header-stats span:has(small:contains(Views)) strong",
        ".header-stats span:nth-of-type(2) strong"
    )

    val DETAIL_BOOKMARKS_FALLBACKS = listOf(
        ".header-stats span:has(small:contains(Bookmarked)) strong",
        ".header-stats span:nth-of-type(3) strong"
    )

    val DETAIL_TOTAL_CHAPTERS_FALLBACKS = listOf(
        ".header-stats span:has(small:contains(Chapters)) strong",
        ".header-stats span:nth-of-type(1) strong"
    )

    val DETAIL_GENRES_SELECTOR = ".categories:has(h4:contains(Genres)) ul li a, .categories ul li a"
    val DETAIL_TAGS_SELECTOR = ".tags ul.content li a.tag, .tags a.tag"

    val DETAIL_LATEST_CHAPTER_TITLE = ".chapter-latest-container p.latest"
    val DETAIL_LATEST_CHAPTER_UPDATE = ".chapter-latest-container p.update"

    // --- Chapter List Selectors ---
    val CHAPTER_LIST_ITEMS = "ul.chapter-list li"
    val CHAPTER_ITEM_LINK = "a"
    val CHAPTER_ITEM_NO = ".chapter-no"
    val CHAPTER_ITEM_TITLE = ".chapter-title"
    val CHAPTER_ITEM_TIME = "time.chapter-update, .chapter-update"
    val CHAPTER_RANGE_SELECT = "select[aria-label='Chapter range'], .chaprange select"

    // --- Chapter Content Selectors ---
    val CHAPTER_CONTENT_TITLE = "#content h4, .titles .chapter-title, .box-detail h1 .chapter-title"
    val CHAPTER_CONTENT_CONTAINER = "#content"
    val CHAPTER_CONTENT_PARAGRAPHS = "#content p"

    // Elements inside #content that must be discarded
    val NOISE_SELECTORS_TO_REMOVE = listOf(
        ".nf-ads",
        "ins.pubadx-slot",
        "ins",
        "iframe",
        "#frame",
        "script",
        ".sharethis-inline-share-buttons"
    )

    val PREV_CHAPTER_LINK = "a.prevchap[href], a.chnav.prev[href]"
    val NEXT_CHAPTER_LINK = "a.nextchap[href], a.chnav.next[href]"

    // --- Listing Card Selectors ---
    val LISTING_ITEMS = "li.novel-item, .novel-item"
    val LISTING_TITLE = ".novel-title, h4, a[title]"
    val LISTING_LINK = "a[href*='/novel/'], a"
    val LISTING_COVER_DATA_SRC = "img[data-src]"
    val LISTING_COVER_SRC = "img[src]"
    val LISTING_LATEST_CHAPTER = ".novel-stats"
    val LISTING_RATING_BADGE = ".badge._br, .badge"
    val LISTING_RANK_BADGE = ".badge._bl"
}
