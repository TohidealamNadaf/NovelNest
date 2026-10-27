package com.novelnest.app.data.remote.asura

import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URI
import java.math.BigDecimal

/** Central origin/path policy. Never follow arbitrary links supplied by page content. */
object AsuraUrls {
    const val BASE = "https://asurascans.com"
    const val API = "https://api.asurascans.com"
    val imageHosts = setOf("cdn.asurascans.com", "asurascans.com")
    private val siteHosts = setOf("asurascans.com", "www.asurascans.com", "asuracomic.net", "www.asuracomic.net")
    private val deploymentSuffix = Regex("-[a-f0-9]{8}$")
    private val validSlug = Regex("[a-z0-9][a-z0-9-]{0,199}")
    fun site(value: String): String {
        val uri = URI("$BASE/").resolve(value.trim())
        require(uri.scheme in setOf("http", "https") && uri.host in siteHosts && uri.userInfo == null && uri.port == -1) { "Invalid Asura URL" }
        require(!uri.rawPath.orEmpty().contains('%') && !uri.path.orEmpty().contains("..")) { "Invalid Asura path" }
        return URI("https", "asurascans.com", uri.path, uri.query, null).toASCIIString()
    }
    fun slug(value: String): String {
        val raw = value.trim().removePrefix("asura-")
        val path = if (raw.contains('/')) URI(site(raw)).path.trim('/') else raw
        val parts = path.split('/')
        val result = (if (parts.firstOrNull() in setOf("comics", "series")) parts.getOrNull(1).orEmpty() else path).replace(deploymentSuffix, "")
        require(validSlug.matches(result) && !result.all(Char::isDigit)) { "Invalid Asura series identifier" }
        return result
    }
    fun novelId(value: String) = "asura-${slug(value)}"
    fun seriesUrl(value: String) = "$BASE/comics/${slug(value)}"
    fun number(value: String): String {
        require(value.matches(Regex("[0-9]{1,7}(?:\\.[0-9]{1,4})?"))) { "Invalid Asura chapter number" }
        return BigDecimal(value).stripTrailingZeros().toPlainString()
    }
    fun chapterUrl(series: String, number: String) = "${seriesUrl(series)}/chapter/${number(number)}"
    fun chapterParts(value: String): Pair<String, String> {
        val parts = URI(site(value)).path.trim('/').split('/')
        require(parts.size == 4 && parts[0] == "comics" && parts[2] == "chapter") { "Invalid Asura chapter URL" }
        return slug(parts[1]) to number(parts[3])
    }
    fun chapterId(series: String, number: String) = "${novelId(series)}:chapter:${number(number)}"
    fun chapterApi(value: String): String = chapterParts(value).let { (slug, number) -> "$API/api/series/$slug/chapters/$number" }
    fun image(value: String): String {
        val uri = URI("$BASE/").resolve(value.trim())
        require(uri.scheme == "https" && uri.host in imageHosts && uri.userInfo == null && uri.port == -1) { "Invalid Asura image URL" }
        require(uri.path.startsWith("/asura-images/")) { "Unexpected Asura image path" }
        return uri.toASCIIString()
    }
    fun browse(value: String? = null): String {
        val url = site(value ?: "$BASE/browse").toHttpUrl()
        require(url.encodedPath == "/browse") { "Invalid Asura browse URL" }
        val allowed = setOf("q", "genres", "page", "sort", "order", "status", "type", "author", "artist", "min_chapters")
        require(url.queryParameterNames.all { it in allowed && url.queryParameterValues(it).size == 1 }) { "Unsupported Asura browse parameters" }
        url.queryParameter("page")?.let { require(it.toIntOrNull() in 1..1000) { "Invalid Asura browse page" } }
        url.queryParameter("genres")?.let { require(it.matches(Regex("[a-z0-9,-]{1,200}"))) { "Invalid Asura genre" } }
        require(url.toString().length <= 2048) { "Asura browse URL is too long" }
        return url.toString()
    }
    fun genre(slug: String) = browse("$BASE/browse".toHttpUrl().newBuilder().addQueryParameter("genres", slug).build().toString())
    fun nextBrowse(url: String, page: Int) = browse(url.toHttpUrl().newBuilder().setQueryParameter("page", page.toString()).build().toString())
    fun search(query: String) = "$API/api/series".toHttpUrl().newBuilder()
        .addQueryParameter("search", query.trim().take(200)).addQueryParameter("sort", "latest")
        .addQueryParameter("order", "desc").addQueryParameter("limit", "50").addQueryParameter("offset", "0").build().toString()
    fun request(value: String): String {
        val uri = URI(value)
        if (uri.host != "api.asurascans.com") return site(value)
        require(uri.scheme == "https" && uri.userInfo == null && uri.port == -1 && uri.path.startsWith("/api/")) { "Invalid Asura API URL" }
        return uri.toASCIIString()
    }
}
