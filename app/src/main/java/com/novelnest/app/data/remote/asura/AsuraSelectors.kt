package com.novelnest.app.data.remote.asura

/** Semantic component names survive Astro's hashed JS filenames and CSS restyling. */
object AsuraSelectors {
    const val ISLANDS = "astro-island[component-url][props]"
    const val HERO = "HeroCarouselEmbla."
    const val TRENDING = "TrendingSection."
    const val LATEST = "LatestUpdates."
    const val POPULAR = "PopularSidebar."
    const val BROWSE = "BrowseFilters."
    const val CHAPTER_LIST = "ChapterListReact."
    const val READER = "ChapterReader."
    const val JSON_LD = "script[type=application/ld+json]"
    const val DESCRIPTION = "#description-text"
    const val ALT_TITLES = "#alt-titles"
    const val RANK = "#alt-container"
    const val PAGE_IMAGES = "img[data-page-index]"
    const val MAX_CHAPTERS = 10_000
    const val MAX_PAGES = 1_000
}
