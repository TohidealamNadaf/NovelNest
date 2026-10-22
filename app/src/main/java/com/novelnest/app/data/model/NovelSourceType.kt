package com.novelnest.app.data.model

enum class NovelSourceType(
    val displayName: String,
    val shortName: String,
    val baseUrl: String,
    val subtitle: String,
    val isManhwa: Boolean = false
) {
    NOVEL_PHOENIX(
        displayName = "NovelPhoenix",
        shortName = "Phoenix",
        baseUrl = "https://novelphoenix.com",
        subtitle = "Web & Light Novels"
    ),
    FREE_WEB_NOVEL(
        displayName = "FreeWebNovel",
        shortName = "FreeWeb",
        baseUrl = "https://freewebnovel.com",
        subtitle = "Unlimited Web Novels"
    ),
    ASURA_SCANS(
        displayName = "Asura Scans",
        shortName = "Asura",
        baseUrl = "https://asuracomic.net",
        subtitle = "Action & Fantasy Manhwa",
        isManhwa = true
    );

    companion object {
        fun fromOrdinal(index: Int): NovelSourceType {
            return entries.getOrElse(index) { NOVEL_PHOENIX }
        }
    }
}
