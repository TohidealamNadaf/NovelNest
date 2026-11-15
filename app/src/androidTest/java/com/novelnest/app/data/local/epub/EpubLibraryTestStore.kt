package com.novelnest.app.data.local.epub

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.novelnest.app.data.database.NovelNestDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class EpubLibraryTestStore(engine: EpubEngine? = null) : AutoCloseable {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    val root = File(app.cacheDir, "epub-library-test-" + UUID.randomUUID()).apply { check(mkdirs()) }
    val context = object : ContextWrapper(app) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir() = root
    }
    val database = Room.inMemoryDatabaseBuilder(app, NovelNestDatabase::class.java).build()
    val dao = database.localBookDao()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository = EpubRepository(context, dao, engine ?: ReadiumEpubEngine(context), scope = scope)
    fun book(index: Int, collectionId: String? = null, order: Long = 0): LocalBookEntity {
        val hash = index.toString(16).padStart(64, '0')
        return LocalBookEntity("epub-" + hash, hash, "Book " + index, "Author", "", index.toLong(), 10,
            collectionId = collectionId, volumeOrder = order)
    }
    suspend fun snapshot(predicate: (EpubLibrarySnapshot) -> Boolean = { it.loaded }) = withTimeout(10_000) { repository.shelf.first(predicate) }
    fun fixture(volume: Int): Uri {
        val path = File(root, "volume-" + volume + ".epub")
        val entries = linkedMapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="EPUB/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            "EPUB/book.opf" to ("""<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="id" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:uuid:""" + UUID.randomUUID() + """</dc:identifier><dc:title>Volume """ + volume + """</dc:title><dc:creator>Author</dc:creator><dc:language>en</dc:language><meta property="dcterms:modified">2026-11-14T00:00:00Z</meta></metadata><manifest><item id="one" href="one.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/></manifest><spine><itemref idref="one"/></spine></package>"""),
            "EPUB/nav.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body><nav epub:type="toc"><ol><li><a href="one.xhtml">Chapter</a></li></ol></nav></body></html>""",
            "EPUB/one.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Chapter</title></head><body><h1>Chapter</h1><p>Text for an offline EPUB integration check.</p></body></html>"""
        )
        ZipOutputStream(path.outputStream()).use { zip -> entries.forEach { (name, text) ->
            val bytes = text.toByteArray()
            val entry = ZipEntry(name)
            if (name == "mimetype") { entry.method = ZipEntry.STORED; entry.size = bytes.size.toLong(); entry.crc = CRC32().apply { update(bytes) }.value }
            zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry()
        } }
        return Uri.fromFile(path)
    }
    override fun close() {
        runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        database.close()
        root.deleteRecursively()
    }
}
