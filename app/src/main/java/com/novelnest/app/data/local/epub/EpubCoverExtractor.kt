package com.novelnest.app.data.local.epub

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url
import java.io.File

internal data class EpubCoverSnapshot(val file: File, val modifiedAt: Long, val byteCount: Long)

/** Import-time work only. No renderer, network fallback, full-size decode or retained bitmap. */
internal object EpubCoverExtractor {
    private const val MAX_IMAGE_BYTES = 12 * 1024 * 1024
    private const val MAX_MARKUP_BYTES = 256 * 1024
    private const val MAX_TOTAL_BYTES = 24L * 1024 * 1024
    private const val MAX_PIXELS = 64_000_000L
    private val coverName = Regex("(?:^|[/_.\\s-])(?:front[-_ ]?cover|cover(?:[-_ ]?image)?)[0-9]*(?:$|[/_.\\s-])", RegexOption.IGNORE_CASE)
    private class Budget(var remaining: Long = MAX_TOTAL_BYTES)

    fun isUsable(file: File): Boolean = try {
        if (!file.isFile || file.length() !in 1..(2L * 1024 * 1024)) false else {
            val bitmap = decode(file.readBytes(), heuristic = false)
            val valid = bitmap != null
            bitmap?.recycle()
            valid
        }
    } catch (_: Exception) { false }
    catch (_: OutOfMemoryError) { false }

    suspend fun save(publication: Publication, target: File): Boolean = withContext(Dispatchers.IO) {
        val temporary = File(target.parentFile, target.name + ".tmp")
        var cover: Bitmap? = null
        var flattened: Bitmap? = null
        try {
            currentCoroutineContext().ensureActive()
            val bitmap = find(publication) ?: return@withContext false
            cover = bitmap
            currentCoroutineContext().ensureActive()
            val output = if (bitmap.hasAlpha()) {
                Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565).also {
                    flattened = it
                    Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
                }
            } else bitmap
            temporary.outputStream().use {
                if (!output.compress(Bitmap.CompressFormat.JPEG, 85, it)) return@withContext false
            }
            currentCoroutineContext().ensureActive()
            temporary.renameTo(target)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false } // An optional thumbnail must not reject a readable book.
        catch (_: OutOfMemoryError) { false }
        finally {
            flattened?.recycle()
            cover?.recycle()
            temporary.delete()
        }
    }

    private suspend fun find(publication: Publication): Bitmap? {
        val budget = Budget()
        val links = publication.resources + publication.readingOrder
        val seen = mutableSetOf<String>()
        val declared = publication.linkWithRel("cover")
        val named = links.asSequence().filter { coverName.containsMatchIn(it.url().path?.substringAfterLast('/').orEmpty()) }.take(6)
        for (link in (listOfNotNull(declared).asSequence() + named).distinctBy { it.url().toString() }.take(6)) {
            currentCoroutineContext().ensureActive()
            val href = local(link.url()) ?: continue
            seen.add(href.toString())
            candidate(publication, link, href, explicit = true, budget = budget)?.let { return it }
        }
        // Only the first unlabelled resource can be inferred as an image-only cover page.
        // Later front pages must explicitly identify themselves as a cover.
        for ((index, link) in publication.readingOrder.take(3).withIndex()) {
            currentCoroutineContext().ensureActive()
            val href = local(link.url()) ?: continue
            if (!seen.add(href.toString())) continue
            candidate(publication, link, href, explicit = false, budget = budget,
                allowImageOnly = index == 0)?.let { return it }
        }
        return null
    }

    private suspend fun candidate(publication: Publication, link: Link, href: Url, explicit: Boolean,
        budget: Budget, allowImageOnly: Boolean = false): Bitmap? {
        return try {
            val type = link.mediaType?.toString()?.substringBefore(';').orEmpty()
            val extension = href.path?.substringAfterLast('.')?.lowercase().orEmpty()
            val markup = type in setOf("application/xhtml+xml", "text/html", "image/svg+xml") ||
                extension in setOf("xhtml", "html", "htm", "svg")
            if (!markup) {
                if (explicit) read(publication, href, MAX_IMAGE_BYTES, budget)?.let { decode(it, heuristic = false) } else null
            } else {
                val bytes = read(publication, href, MAX_MARKUP_BYTES, budget) ?: return null
                val doc = if (type == "image/svg+xml" || extension == "svg")
                    Jsoup.parse(bytes.inputStream(), null, "", Parser.xmlParser())
                    else Jsoup.parse(bytes.inputStream(), "UTF-8", "")
                val marked = explicit || coverName.containsMatchIn(href.path?.substringAfterLast('/').orEmpty()) || doc.getAllElements().any {
                    it.attr("epub:type").split(Regex("\\s+")).any { token -> token.equals("cover", true) } ||
                        it.attr("role").equals("doc-cover", true)
                }
                if (!marked && (!allowImageOnly || doc.body()?.text().orEmpty().length > 220)) return null
                val images = doc.select("img, image")
                if (images.size != 1) return null
                val image = images.first() ?: return null
                val source = image.attr("src").ifBlank { image.attr("href") }.ifBlank { image.attr("xlink:href") }
                val raster = if (source.startsWith("data:", true)) inlineRaster(source, budget) else {
                    val relative = Url(source.takeIf { it.isNotBlank() && !it.contains('\\') && !it.startsWith("//") } ?: return null)
                        ?: return null
                    val resolved = local(href.resolve(relative)) ?: return null
                    val asset = (publication.resources + publication.readingOrder).firstOrNull {
                        local(it.url())?.isEquivalent(resolved) == true
                    } ?: return null
                    read(publication, asset.url(), MAX_IMAGE_BYTES, budget)
                }
                raster?.let { decode(it, heuristic = !marked) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }

    private fun local(href: Url): Url? {
        val normalized = href.normalize().removeFragment()
        return normalized.takeIf {
            it !is AbsoluteUrl && !it.toString().startsWith("//") &&
                it.path?.split('/')?.none { part -> part == ".." || part.contains('\\') } == true
        }
    }

    private suspend fun read(publication: Publication, href: Url, limit: Int, budget: Budget): ByteArray? {
        currentCoroutineContext().ensureActive()
        if (local(href) == null) return null
        val resource = publication.get(href) ?: return null
        return try {
            // HTML is already bounded by the archive validator and sanitized wrapper. Do not call
            // length() on it: that would sanitize/allocate the same document a second time.
            val markup = href.path?.substringAfterLast('.')?.lowercase() in setOf("xhtml", "html", "htm")
            val bytes = if (markup) resource.read().getOrNull() else {
                val length = resource.length().getOrNull() ?: return null
                if (length !in 1..minOf(limit.toLong(), budget.remaining)) return null
                resource.read(0L..(length - 1)).getOrNull()
            } ?: return null
            currentCoroutineContext().ensureActive()
            if (bytes.isEmpty() || bytes.size > limit || bytes.size > budget.remaining) return null
            budget.remaining -= bytes.size
            bytes
        } finally { resource.close() }
    }

    private fun inlineRaster(source: String, budget: Budget): ByteArray? {
        if (source.length > MAX_MARKUP_BYTES) return null
        val header = source.substringBefore(',').lowercase()
        if (!header.endsWith(";base64") || header.substringBefore(';') !in
            setOf("data:image/jpeg", "data:image/png", "data:image/webp", "data:image/gif")) return null
        val bytes = Base64.decode(source.substringAfter(',', ""), Base64.DEFAULT)
        if (bytes.isEmpty() || bytes.size > budget.remaining) return null
        budget.remaining -= bytes.size
        return bytes
    }

    private fun decode(bytes: ByteArray, heuristic: Boolean): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0 || width.toLong() * height > MAX_PIXELS || maxOf(width, height) > 32000) return null
        // Unmarked first pages must have a substantial portrait image, never a tiny publisher logo.
        if (heuristic && (width < 160 || height < 240 || width.toDouble() / height !in 0.45..0.95)) return null
        var sample = 1
        while (width / sample > 720 || height / sample > 1000) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
