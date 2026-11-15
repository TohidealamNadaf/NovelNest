package com.novelnest.app.data.local.epub

import android.content.Context
import java.io.File
import java.io.IOException
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.epub.EpubLayout
import org.readium.r2.shared.publication.presentation.presentation
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.util.*
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.http.*
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser

interface EpubEngine { suspend fun open(file: File): Publication }

class ReadiumEpubEngine(context: Context) : EpubEngine {
    private val offlineHttp = object : HttpClient {
        override suspend fun stream(request: HttpRequest): HttpTry<HttpStreamResponse> =
            Try.failure(HttpError.IO(IOException("Local EPUB network access is disabled.")))
    }
    private val retriever = AssetRetriever(context.contentResolver, offlineHttp)
    private val opener = PublicationOpener(EpubParser())
    // 3.0.3 skips the constructor callback and invokes the per-open callback twice.
    // Use the latter and make installation idempotent, so resources are sanitized exactly once.
    private val offlinePublication: Publication.Builder.() -> Unit = {
        if (container !is OfflineEpubContainer) {
            val htmlPaths = (manifest.readingOrder + manifest.resources).filter {
                it.mediaType?.toString()?.substringBefore(';')?.lowercase() in setOf("application/xhtml+xml", "text/html")
            }.map { it.url().removeQuery().removeFragment().toString().trimStart('/') }.toSet()
            container = OfflineEpubContainer(container, htmlPaths)
        }
    }
    override suspend fun open(file: File): Publication {
        val asset = retriever.retrieve(file.toUrl()).getOrElse { throw EpubImportException("Cannot access the EPUB file.") }
        val publication = try {
            opener.open(asset, allowUserInteraction = false, onCreatePublication = offlinePublication).getOrElse {
                throw EpubImportException("This EPUB is damaged, protected, or uses an unsupported format.")
            }
        } catch (error: Exception) { asset.close(); throw error }
        try {
            if (publication.isRestricted) throw EpubImportException("DRM-protected EPUBs are not supported.")
            if (publication.metadata.presentation.layout == EpubLayout.FIXED)
                throw EpubImportException("Fixed-layout EPUBs are not supported yet. Please choose a reflowable EPUB.")
            if (publication.readingOrder.any { Regex("^(?:[a-zA-Z][a-zA-Z0-9+.-]*:|//)").containsMatchIn(it.href.toString()) })
                throw EpubImportException("This EPUB requires remotely hosted chapters and cannot be imported for offline reading.")
            if (publication.readingOrder.isEmpty()) throw EpubImportException("This EPUB contains no readable sections.")
            publication.readingOrder.forEach { link ->
                val resource = publication.get(link) ?: throw EpubImportException("A required reading section is missing from this EPUB.")
                resource.close()
            }
            return publication
        } catch (error: Exception) { publication.close(); throw error }
    }
}

private class OfflineEpubContainer(
    private val original: Container<Resource>,
    private val htmlPaths: Set<String>
) : Container<Resource> by original {
    override fun get(url: Url): Resource? {
        val resource = original[url] ?: return null
        val path = url.removeQuery().removeFragment().toString().trimStart('/')
        return if (path in htmlPaths || path.substringAfterLast('.').lowercase() in setOf("xhtml", "html", "htm"))
            EpubMarkupResource(resource)
        else resource
    }
}
