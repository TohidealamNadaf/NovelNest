package com.novelnest.app.data.local.epub

import com.novelnest.app.data.preferences.EpubImageHorizontalAlignment
import com.novelnest.app.data.preferences.EpubImageVerticalAlignment
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Only the reading system's injected scripts run; publication code and network resources are blocked. */
object EpubContentSanitizer {
    @Volatile
    private var verticalAlignment: EpubImageVerticalAlignment = EpubImageVerticalAlignment.CENTER

    @Volatile
    private var horizontalAlignment: EpubImageHorizontalAlignment = EpubImageHorizontalAlignment.CENTER

    fun updateImageAlignment(
        vertical: EpubImageVerticalAlignment,
        horizontal: EpubImageHorizontalAlignment
    ) {
        verticalAlignment = vertical
        horizontalAlignment = horizontal
    }

    fun buildImageAlignmentCss(
        vertical: EpubImageVerticalAlignment = verticalAlignment,
        horizontal: EpubImageHorizontalAlignment = horizontalAlignment
    ): String {
        val hPos = horizontal.cssPosition
        val vPos = vertical.cssPosition
        val marginLeft = if (horizontal == EpubImageHorizontalAlignment.LEFT) "0" else "auto"
        val marginRight = if (horizontal == EpubImageHorizontalAlignment.RIGHT) "0" else "auto"
        val inlineTopMargin = when (vertical) {
            EpubImageVerticalAlignment.TOP -> "8px"
            EpubImageVerticalAlignment.CENTER -> "16px"
            EpubImageVerticalAlignment.BOTTOM -> "24px"
        }
        val inlineBottomMargin = when (vertical) {
            EpubImageVerticalAlignment.TOP -> "24px"
            EpubImageVerticalAlignment.CENTER -> "16px"
            EpubImageVerticalAlignment.BOTTOM -> "8px"
        }

        return """
            html body.nn-illustration-page {
                margin: 0 !important;
                padding: 12px 0 !important;
                box-sizing: border-box !important;
                text-align: $hPos !important;
            }
            html body.nn-illustration-page div,
            html body.nn-illustration-page p,
            html body.nn-illustration-page figure {
                margin: 0 !important;
                padding: 0 !important;
                text-align: $hPos !important;
            }
            html body.nn-illustration-page img {
                display: block !important;
                width: 100% !important;
                height: calc(100vh - 24px) !important;
                max-width: 100% !important;
                max-height: calc(100vh - 24px) !important;
                object-fit: contain !important;
                object-position: $hPos $vPos !important;
                margin-left: $marginLeft !important;
                margin-right: $marginRight !important;
                margin-top: 0 !important;
                margin-bottom: 0 !important;
                break-inside: avoid !important;
            }
            html body.nn-illustration-page svg {
                display: block !important;
                width: 100% !important;
                height: calc(100vh - 24px) !important;
                max-width: 100% !important;
                max-height: calc(100vh - 24px) !important;
                margin-left: $marginLeft !important;
                margin-right: $marginRight !important;
                margin-top: 0 !important;
                margin-bottom: 0 !important;
                break-inside: avoid !important;
            }
            html body:not(.nn-illustration-page) img,
            html body:not(.nn-illustration-page) svg {
                display: block !important;
                max-width: 100% !important;
                height: auto !important;
                object-fit: contain !important;
                object-position: $hPos $vPos !important;
                margin-left: $marginLeft !important;
                margin-right: $marginRight !important;
                margin-top: $inlineTopMargin !important;
                margin-bottom: $inlineBottomMargin !important;
            }
        """.trimIndent()
    }

    fun buildLiveUpdateJs(
        vertical: EpubImageVerticalAlignment,
        horizontal: EpubImageHorizontalAlignment
    ): String {
        val css = buildImageAlignmentCss(vertical, horizontal)
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", " ")
        val preserveAspect = "${horizontal.svgAlign}${vertical.svgAlign} meet"
        return """
            (function() {
                if (!document || !document.head || !document.body) return;
                var hasMedia = document.querySelectorAll('img, svg, image').length > 0;
                var textLen = (document.body.innerText || '').trim().length;
                if (hasMedia && textLen <= 40) {
                    document.body.classList.add('nn-illustration-page');
                }
                var styleEl = document.getElementById('nn-epub-image-style');
                if (!styleEl) {
                    styleEl = document.createElement('style');
                    styleEl.id = 'nn-epub-image-style';
                    document.head.appendChild(styleEl);
                }
                styleEl.textContent = '$css';
                var svgs = document.querySelectorAll('svg');
                for (var i = 0; i < svgs.length; i++) {
                    svgs[i].setAttribute('preserveAspectRatio', '$preserveAspect');
                }
            })();
        """.trimIndent()
    }

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
        val vAlign = verticalAlignment
        val hAlign = horizontalAlignment
        if (doc.body().text().trim().length <= 40 && doc.select("img, svg, image").isNotEmpty()) {
            doc.body().addClass("nn-illustration-page")
        }
        val preserveAspect = "${hAlign.svgAlign}${vAlign.svgAlign} meet"
        doc.select("svg").forEach { svg ->
            svg.attr("preserveAspectRatio", preserveAspect)
        }
        doc.head().appendElement("style")
            .attr("id", "nn-epub-image-style")
            .text(buildImageAlignmentCss(vAlign, hAlign))
        doc.head().prependElement("meta").attr("http-equiv", "Content-Security-Policy").attr("content",
            "default-src 'self' data:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'none'; frame-src 'none'; object-src 'none'; media-src 'none'; form-action 'none'; base-uri 'none'")
        doc.head().prependElement("meta").attr("charset", "utf-8")
        return doc.outerHtml().toByteArray(Charsets.UTF_8)
    }
}
