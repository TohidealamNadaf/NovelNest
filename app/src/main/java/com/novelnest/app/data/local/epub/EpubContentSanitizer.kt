package com.novelnest.app.data.local.epub

import com.novelnest.app.data.preferences.EpubImageHorizontalAlignment
import com.novelnest.app.data.preferences.EpubImageVerticalAlignment
import com.novelnest.app.data.preferences.EpubMultiImageLayout
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Only the reading system's injected scripts run; publication code and network resources are blocked. */
object EpubContentSanitizer {
    @Volatile
    private var verticalAlignment: EpubImageVerticalAlignment = EpubImageVerticalAlignment.CENTER

    @Volatile
    private var horizontalAlignment: EpubImageHorizontalAlignment = EpubImageHorizontalAlignment.CENTER

    @Volatile
    private var multiImageLayout: EpubMultiImageLayout = EpubMultiImageLayout.COMPACT

    fun updateImageAlignment(
        vertical: EpubImageVerticalAlignment,
        horizontal: EpubImageHorizontalAlignment,
        layout: EpubMultiImageLayout = multiImageLayout
    ) {
        verticalAlignment = vertical
        horizontalAlignment = horizontal
        multiImageLayout = layout
    }

    fun buildImageAlignmentCss(
        vertical: EpubImageVerticalAlignment = verticalAlignment,
        horizontal: EpubImageHorizontalAlignment = horizontalAlignment,
        layout: EpubMultiImageLayout = multiImageLayout
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

        val compactCss = if (layout == EpubMultiImageLayout.COMPACT) """
            html body.nn-illustration-page.nn-multi-image-page img,
            html body.nn-illustration-page.nn-multi-image-page svg {
                height: auto !important;
                max-height: none !important;
                margin-top: 0 !important;
                margin-bottom: 8px !important;
            }
            html body.nn-multi-image-page div,
            html body.nn-multi-image-page p,
            html body.nn-multi-image-page figure {
                min-height: 0 !important;
                height: auto !important;
            }
        """.trimIndent() else ""
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
        """.trimIndent() + "\n" + compactCss
    }

    fun buildLiveUpdateJs(
        vertical: EpubImageVerticalAlignment,
        horizontal: EpubImageHorizontalAlignment,
        layout: EpubMultiImageLayout = multiImageLayout
    ): String {
        val css = buildImageAlignmentCss(vertical, horizontal, layout)
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", " ")
        val preserveAspect = "${horizontal.svgAlign}${vertical.svgAlign} meet"
        return """
            (function() {
                if (!document || !document.head || !document.body) return;
                var visuals = Array.from(document.body.querySelectorAll('img, svg')).filter(function(el) { return !el.parentElement.closest('svg'); });
                var hasMedia = visuals.length > 0;
                var textLen = (document.body.innerText || '').trim().length;
                document.body.classList.toggle('nn-illustration-page', hasMedia && textLen <= 40);
                document.body.classList.toggle('nn-multi-image-page', visuals.length > 1 && textLen <= 40);
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
        val visuals = doc.body().select("img, svg").filter { el -> el.parents().none { it.normalName() == "svg" } }
        if (doc.body().text().trim().length <= 40 && visuals.isNotEmpty()) {
            doc.body().addClass("nn-illustration-page")
            if (visuals.size > 1) doc.body().addClass("nn-multi-image-page")
        }
        val preserveAspect = "${hAlign.svgAlign}${vAlign.svgAlign} meet"
        doc.select("svg").forEach { svg ->
            svg.attr("preserveAspectRatio", preserveAspect)
        }
        doc.head().appendElement("style")
            .attr("id", "nn-epub-image-style")
            .text(buildImageAlignmentCss(vAlign, hAlign))
        doc.head().appendElement("style")
            .attr("id", "nn-epub-selection-style")
            .text(EpubSelectionStyle.css)
        doc.head().prependElement("meta").attr("http-equiv", "Content-Security-Policy").attr("content",
            "default-src 'self' data:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'none'; frame-src 'none'; object-src 'none'; media-src 'none'; form-action 'none'; base-uri 'none'")
        doc.head().prependElement("meta").attr("charset", "utf-8")
        return doc.outerHtml().toByteArray(Charsets.UTF_8)
    }
}
