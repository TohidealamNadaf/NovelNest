package com.novelnest.app.data.model

enum class NovelSourceType(
    val displayName: String,
    val shortName: String,
    val baseUrl: String,
    val subtitle: String,
    val isManhwa: Boolean = false
) {
    NOVEL_FIRE(
        displayName = "NovelFire",
        shortName = "NF",
        baseUrl = "https://novelfire.net",
        subtitle = "Web & Light Novels"
    ),
    FREE_WEB_NOVEL(
        displayName = "FreeWebNovel",
        shortName = "FWN",
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
            return entries.getOrElse(index) { NOVEL_FIRE }
        }
    }
}
