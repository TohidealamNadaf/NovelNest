package com.novelnest.app.data.local.epub

import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url

internal data class EpubImageRef(val section: Url, val source: String, val inlineSvg: Boolean, val title: String)
internal data class EpubImageAsset(val bytes: ByteArray, val mimeType: String, val width: Int, val height: Int)

/** One section catalog and one bounded asset; no publication-wide image/bitmap cache. */
internal object EpubImages {
    const val MAX_IMAGES = 256
    private const val MAX_IMAGE_BYTES = 12 * 1024 * 1024
    private const val MAX_PIXELS = 16_000_000L
    private const val MAX_SVG_BYTES = 4 * 1024 * 1024
    private val rasterTypes = setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp")

    suspend fun catalog(publication: Publication, section: Url): List<EpubImageRef> {
        require(publication.containsReadingResource(section)) { "This section is not part of the book." }
        // Markup is bounded by EpubMarkupResource. Avoid length() here: it would sanitize
        // and allocate the same section a second time just to discover its transformed size.
        val resource = publication.get(section) ?: error("This EPUB section is missing.")
        val html = try { resource.read().getOrNull() ?: error("This EPUB section could not be read.") }
            finally { resource.close() }
        require(html.size <= EpubArchiveValidator.MAX_MARKUP_BYTES + 64 * 1024) { "This EPUB section is too large." }
        currentCoroutineContext().ensureActive()
        val doc = Jsoup.parse(html.inputStream(), "UTF-8", "")
        val visuals = doc.body().select("img, svg").asSequence()
            .filter { el -> el.parents().none { it.normalName() == "svg" } }
            .take(MAX_IMAGES + 1).toList()
        require(visuals.size <= MAX_IMAGES) { "The image viewer supports up to 256 illustrations per section." }
        return visuals.mapIndexed { index, el ->
            val svg = el.normalName() == "svg"
            EpubImageRef(section, if (svg) el.outerHtml() else el.attr("src"), svg,
                el.attr("alt").ifBlank { el.selectFirst("title")?.text().orEmpty() }
                    .ifBlank { "Illustration " + (index + 1) }.take(200))
        }
    }

    suspend fun load(publication: Publication, ref: EpubImageRef): EpubImageAsset {
        currentCoroutineContext().ensureActive()
        val (bytes, type) = if (ref.inlineSvg) ref.source.toByteArray() to "image/svg+xml"
            else readSource(publication, ref.section, ref.source, allowSvg = true)
        currentCoroutineContext().ensureActive()
        return if (type == "image/svg+xml") sanitizeSvg(publication, bytes,
            if (ref.inlineSvg) ref.section else resolve(publication, ref.section, ref.source))
        else raster(bytes)
    }

    private suspend fun read(publication: Publication, href: Url, limit: Int): ByteArray {
        val resource = publication.get(href) ?: error("The image or section is missing from this EPUB.")
        try {
            val length = resource.length().getOrNull()
                ?: error("The EPUB resource size could not be read.")
            require(length in 1..limit.toLong()) { "This illustration exceeds the supported size." }
            currentCoroutineContext().ensureActive()
            val bytes = resource.read(0L..(length - 1)).getOrNull()
                ?: error("The EPUB resource could not be read.")
            require(bytes.isNotEmpty() && bytes.size <= limit) { "This illustration exceeds the supported size." }
            return bytes
        } finally { resource.close() }
    }

    private fun resolve(publication: Publication, section: Url, source: String): Url {
        require(source.isNotBlank() && !source.startsWith("//") && !source.contains('\\')) {
            "This illustration has an invalid local path."
        }
        val relative = Url(source) ?: error("This illustration has an invalid local path.")
        val resolved = section.removeQuery().removeFragment().resolve(relative).normalize().removeQuery().removeFragment()
        require(resolved !is AbsoluteUrl && resolved.path?.split('/')?.none { it == ".." } == true) {
            "Remote illustrations cannot be opened in the offline viewer."
        }
        return (publication.resources + publication.readingOrder).firstOrNull {
            it.url().normalize().removeQuery().removeFragment().isEquivalent(resolved)
        }?.url() ?: error("This illustration is not declared in the EPUB manifest.")
    }

    private suspend fun readSource(publication: Publication, section: Url, source: String,
        allowSvg: Boolean): Pair<ByteArray, String> {
        if (source.startsWith("data:", ignoreCase = true)) {
            require(source.length <= MAX_IMAGE_BYTES * 4L / 3 + 128) { "This embedded illustration is too large." }
            val header = source.substringBefore(',').lowercase()
            val type = header.removePrefix("data:").substringBefore(';')
            require(type in rasterTypes && header.endsWith(";base64")) { "This embedded image format is unsupported." }
            val bytes = Base64.decode(source.substringAfter(',', ""), Base64.DEFAULT)
            require(bytes.size <= MAX_IMAGE_BYTES) { "This embedded illustration is too large." }
            return bytes to type
        }
        val href = resolve(publication, section, source)
        val link = (publication.resources + publication.readingOrder).first { it.url() == href }
        val type = link.mediaType?.toString()?.substringBefore(';')?.lowercase()
        val svg = type == "image/svg+xml" || href.path?.endsWith(".svg", true) == true
        require((allowSvg && svg) || type in rasterTypes || href.path?.substringAfterLast('.')?.lowercase() in
            setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")) { "This image format is unsupported." }
        return read(publication, href, if (svg) MAX_SVG_BYTES else MAX_IMAGE_BYTES) to
            (if (svg) "image/svg+xml" else type.orEmpty())
    }

    private fun raster(bytes: ByteArray): EpubImageAsset {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 &&
            bounds.outWidth.toLong() * bounds.outHeight <= MAX_PIXELS) {
            "This image is damaged or exceeds the 16-megapixel viewing limit."
        }
        require(bounds.outMimeType in rasterTypes) { "This image format is unsupported." }
        return EpubImageAsset(bytes, bounds.outMimeType, bounds.outWidth, bounds.outHeight)
    }

    // The section HTML parser can lowercase SVG names; standalone SVG is case-sensitive.
    private fun restoreSvgCase(root: Element) {
        val tags = listOf("linearGradient", "radialGradient", "clipPath", "textPath",
            "feGaussianBlur", "feOffset", "feBlend", "feColorMatrix", "feComponentTransfer",
            "feComposite", "feConvolveMatrix", "feDiffuseLighting", "feDisplacementMap",
            "feDistantLight", "feFlood", "feFuncA", "feFuncB", "feFuncG", "feFuncR", "feImage",
            "feMerge", "feMergeNode", "feMorphology", "fePointLight", "feSpecularLighting",
            "feSpotLight", "feTile", "feTurbulence").associateBy { it.lowercase() }
        val attributes = listOf("viewBox", "preserveAspectRatio", "gradientUnits", "gradientTransform",
            "patternUnits", "patternContentUnits", "patternTransform", "markerWidth", "markerHeight",
            "markerUnits", "refX", "refY", "textLength", "lengthAdjust", "filterUnits", "primitiveUnits",
            "stdDeviation", "baseFrequency", "numOctaves", "kernelMatrix", "kernelUnitLength",
            "specularConstant", "specularExponent", "diffuseConstant", "surfaceScale", "xChannelSelector",
            "yChannelSelector", "targetX", "targetY", "edgeMode", "preserveAlpha", "pointsAtX", "pointsAtY",
            "pointsAtZ", "limitingConeAngle").associateBy { it.lowercase() }
        root.getAllElements().forEach { element ->
            tags[element.normalName()]?.let(element::tagName)
            element.attributes().asList().forEach { attribute ->
                attributes[attribute.key.lowercase()]?.let { canonical ->
                    if (attribute.key != canonical) { element.removeAttr(attribute.key); element.attr(canonical, attribute.value) }
                }
            }
        }
    }

    private suspend fun sanitizeSvg(publication: Publication, bytes: ByteArray, base: Url): EpubImageAsset {
        require(bytes.size <= MAX_SVG_BYTES) { "This vector illustration is too large." }
        val doc = Jsoup.parse(bytes.inputStream(), null, "", Parser.xmlParser())
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml).prettyPrint(false)
        val root = doc.selectFirst("svg") ?: error("This SVG illustration is invalid.")
        restoreSvgCase(root)
        root.select("script, style, foreignObject, iframe, object, embed, animate, animateTransform, animateMotion, animateColor, mpath, discard, set").remove()
        root.getAllElements().forEach { el ->
            el.attributes().asList().forEach { attr ->
                val value = attr.value.lowercase().replace(Regex("\\s+"), "")
                if (attr.key.startsWith("on", true) || attr.key.equals("xml:base", true) ||
                    (value.contains("url(") && !Regex("^url\\(['\"]?#[^)]*\\)$").matches(value)) ||
                    value.contains("javascript:") || value.contains("@import")) el.removeAttr(attr.key)
            }
            if (el.normalName() != "image") listOf("href", "xlink:href", "src").forEach { key ->
                if (el.hasAttr(key) && !el.attr(key).startsWith('#')) el.removeAttr(key)
            }
        }
        val images = root.select("image")
        require(images.size <= 16) { "This SVG contains too many embedded illustrations." }
        var embeddedBytes = 0L
        images.forEach { image ->
            currentCoroutineContext().ensureActive()
            val source = image.attr("href").ifBlank { image.attr("xlink:href") }
            val (data, _) = readSource(publication, base, source, allowSvg = false)
            val asset = raster(data)
            embeddedBytes += data.size
            require(embeddedBytes <= 6 * 1024 * 1024) { "This vector illustration contains too much image data." }
            image.removeAttr("xlink:href")
            image.attr("href", "data:" + asset.mimeType + ";base64," + Base64.encodeToString(data, Base64.NO_WRAP))
        }
        val box = root.attr("viewBox").trim().split(Regex("[ ,]+"))
        fun dimension(name: String, index: Int, fallback: Double): Int =
            (Regex("^([0-9]+(?:\\.[0-9]+)?)(?:px)?$", RegexOption.IGNORE_CASE)
                .matchEntire(root.attr(name).trim())?.groupValues?.get(1)?.toDoubleOrNull()
                ?: box.getOrNull(index)?.toDoubleOrNull() ?: fallback)
                .takeIf { it.isFinite() && it > 0 && it <= 16000 }?.toInt()?.coerceAtLeast(1)
                ?: error("This SVG has invalid dimensions.")
        val width = dimension("width", 2, 1024.0)
        val height = dimension("height", 3, 1448.0)
        require(width.toLong() * height <= MAX_PIXELS) { "This SVG exceeds the supported image dimensions." }
        root.attr("xmlns", "http://www.w3.org/2000/svg").attr("width", width.toString()).attr("height", height.toString())
        val output = root.outerHtml().toByteArray(Charsets.UTF_8)
        require(output.size <= MAX_IMAGE_BYTES) { "This SVG illustration is too large to view." }
        return EpubImageAsset(output, "image/svg+xml", width, height)
    }
}
