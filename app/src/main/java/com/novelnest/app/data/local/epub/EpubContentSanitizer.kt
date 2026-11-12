package com.novelnest.app.data.local.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Only the reading system's injected scripts run; publication code and network resources are blocked. */
object EpubContentSanitizer {
    fun sanitize(bytes: ByteArray): ByteArray {
        val doc = Jsoup.parse(bytes.inputStream(), null, "")
        doc.childNodes().filter { it is org.jsoup.nodes.XmlDeclaration || (it is org.jsoup.nodes.Comment && it.isXmlDeclaration) }.forEach { it.remove() }
        doc.outputSettings().charset(Charsets.UTF_8).syntax(Document.OutputSettings.Syntax.xml).prettyPrint(false)
        doc.select("script, iframe, object, embed, base, form, foreignObject").remove()
        doc.select("meta[http-equiv]").remove()
        doc.getAllElements().forEach { element ->
            element.attributes().asList().forEach { attr ->
                val key = attr.key.lowercase()
                val value = attr.value.filterNot { it.isWhitespace() || it.code < 32 }.lowercase()
                if (key.startsWith("on") || key == "srcdoc" ||
                    (key in setOf("href", "src", "xlink:href", "action") && (value.startsWith("javascript:") || value.startsWith("vbscript:") || value.startsWith("data:text/html"))))
                    element.removeAttr(attr.key)
            }
        }
        doc.head().prependElement("meta").attr("http-equiv", "Content-Security-Policy").attr("content",
            "default-src 'self' data:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'none'; frame-src 'none'; object-src 'none'; media-src 'none'; form-action 'none'; base-uri 'none'")
        doc.head().prependElement("meta").attr("charset", "utf-8")
        return doc.outerHtml().toByteArray(Charsets.UTF_8)
    }
}
