package com.novelnest.app.data.dictionary

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.util.Locale

/** Fixed recording origins only. Source page IDs come from the dictionary, never guessed word URLs. */
internal object DictionaryAudioUrls {
    // Identify this client with a real project contact URL, as Wikimedia requires.
    const val USER_AGENT = "NovelNest-Dictionary/1.0 (https://github.com/TohidealamNadaf/NovelNest; Android)"
    private val commonsFile = Regex("/wikipedia/commons/[0-9a-f]/[0-9a-f]{2}/[^/]+\\.(ogg|oga|opus|mp3|wav|flac)", RegexOption.IGNORE_CASE)
    private val tracking = setOf("utm_source", "utm_campaign", "utm_content")

    private fun secure(value: String?): HttpUrl? {
        if (value.isNullOrBlank() || value.length > 2048) return null
        val url = (if (value.startsWith("//")) "https:$value" else value).toHttpUrlOrNull() ?: return null
        return url.takeIf { it.isHttps && it.port == 443 && it.username.isEmpty() && it.password.isEmpty() && it.fragment == null }
    }

    fun normalize(value: String?): String? {
        val url = secure(value) ?: return null
        val allowed = when (url.host) {
            "api.dictionaryapi.dev" -> url.query == null && url.encodedPath.startsWith("/media/pronunciations/")
            "ssl.gstatic.com" -> url.query == null && url.encodedPath.startsWith("/dictionary/static/sounds/")
            "upload.wikimedia.org" -> commonsFile.matches(url.encodedPath) && url.queryParameterNames.all { it in tracking }
            else -> false
        }
        // MediaWiki adds tracking parameters to the original file URL; they are not part of the asset identity.
        return if (allowed) url.newBuilder().query(null).build().toString() else null
    }

    fun commonsPageId(source: String?): Long? {
        val url = secure(source) ?: return null
        if (url.host != "commons.wikimedia.org" || url.encodedPath != "/w/index.php" ||
            url.queryParameterNames != setOf("curid") || url.queryParameterValues("curid").size != 1) return null
        val id = url.queryParameter("curid") ?: return null
        return id.takeIf { it.length in 1..15 && it.all { c -> c in '0'..'9' } }?.toLongOrNull()?.takeIf { it > 0 }
    }

    fun isDictionaryMirror(url: String): Boolean = url.toHttpUrl().host == "api.dictionaryapi.dev"

    fun metadataUrl(pageId: Long): String {
        require(pageId > 0)
        return "https://commons.wikimedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query").addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2").addQueryParameter("pageids", pageId.toString())
            .addQueryParameter("prop", "imageinfo").addQueryParameter("iiprop", "url|mime")
            // User-initiated lookup: MediaWiki permits omitting the bot-only maxlag guard.
            .addQueryParameter("iilimit", "1").build().toString()
    }

    fun originalRecording(raw: String, pageId: Long): String {
        try {
            val json = JSONObject(raw)
            if (json.has("error")) throw DictionaryAudioException("Wikimedia cannot resolve this recording right now. Please try later.")
            val pages = json.getJSONObject("query").getJSONArray("pages")
            require(pages.length() == 1)
            val page = pages.getJSONObject(0)
            if (page.has("missing") || page.has("invalid"))
                throw DictionaryAudioException("The original pronunciation recording is no longer available.")
            require(page.getLong("pageid") == pageId && page.getInt("ns") == 6)
            val info = page.getJSONArray("imageinfo").getJSONObject(0)
            require(isAudioMime(info.optString("mime")))
            return normalize(info.getString("url"))?.takeIf { it.toHttpUrl().host == "upload.wikimedia.org" }
                ?: throw DictionaryAudioException("The pronunciation source returned an unsupported recording address.")
        } catch (error: DictionaryAudioException) { throw error }
        catch (_: Exception) { throw DictionaryAudioException("The pronunciation source returned unsupported recording metadata.") }
    }

    fun isAudioMime(mime: String?): Boolean {
        val type = mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) ?: return false
        // Wikimedia serves valid Ogg/Vorbis pronunciations as application/ogg.
        return type.startsWith("audio/") || type == "application/ogg"
    }
}
