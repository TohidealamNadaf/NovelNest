package com.novelnest.app.data.remote.freewebnovel

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object FreeWebNovelUtils {
    fun document(html: String, url: String): Document {
        val doc = Jsoup.parse(html, url)
        if (doc.title().contains("Just a moment", true) || html.contains("cf-chl-") || html.contains("cf-mitigated")) {
            throw FreeWebNovelException.Blocked()
        }
        if (doc.title().contains("404") || doc.title().contains("Not Found", true)) throw FreeWebNovelException.NotFound()
        return doc
    }
}
