package com.novelnest.app.data.remote.freewebnovel

import java.net.URI

object FreeWebNovelUrls {
    const val BASE_URL = "https://freewebnovel.com"
    private val legacySlugs = mapOf(
        "1" to "martial-world", "2" to "chrysalis", "3" to "coiling-dragon",
        "4" to "birth-of-the-demonic-sword", "5" to "warlock-of-the-magus-world"
    )

    fun resolve(value: String): String {
        val raw = value.trim()
        require(raw.isNotEmpty() && !raw.startsWith("javascript:") && !raw.startsWith("data:")) { "Invalid FreeWebNovel URL" }
        val uri = URI(BASE_URL + "/").resolve(raw)
        require(uri.scheme in listOf("https", "http") && uri.host in listOf("freewebnovel.com", "www.freewebnovel.com")) {
            "URL does not belong to FreeWebNovel"
        }
        return URI("https", "freewebnovel.com", uri.path, uri.query, null).toASCIIString()
    }

    fun slug(value: String): String {
        val raw = value.trim().removePrefix("fwn-")
        legacySlugs[raw]?.let { return it }
        val path = if (raw.contains("/") || raw.startsWith("http")) URI(resolve(raw)).path else raw.substringBefore('?').substringBefore('#')
        val parts = path.trim('/').split('/')
        val slug = (if (parts.firstOrNull() == "novel") parts.getOrNull(1) else parts.firstOrNull())
            .orEmpty().removeSuffix(".html")
        require(slug.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_-]*"))) { "Invalid FreeWebNovel novel identifier" }
        return slug
    }

    fun novelUrl(value: String) = "$BASE_URL/novel/${slug(value)}"
    fun novelId(value: String) = "fwn-${slug(value)}"
    fun chapterId(value: String): String {
        val path = URI(resolve(value)).path.trimEnd('/')
        require(path.matches(Regex("/novel/[^/]+/chapter-[^/]+"))) { "Invalid FreeWebNovel chapter URL" }
        return path.substringAfterLast('/')
    }
}
