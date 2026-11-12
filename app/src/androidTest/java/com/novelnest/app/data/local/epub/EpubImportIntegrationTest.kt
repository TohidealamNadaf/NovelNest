package com.novelnest.app.data.local.epub

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelnest.app.data.database.NovelNestDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Real Readium parsing and Room persistence; no provider/network or fake reader is used. */
@RunWith(AndroidJUnit4::class)
class EpubImportIntegrationTest {
    private fun fixture(file: File, version: Int): File {
        val toc = if (version == 3) "nav.xhtml" else "toc.ncx"
        val entries = linkedMapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="EPUB/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            "EPUB/book.opf" to ("""<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" unique-identifier="id" version="""" + (if (version == 3) "3.0" else "2.0") + """"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">test-book</dc:identifier><dc:title>Fixture Book</dc:title><dc:creator>Fixture Author</dc:creator><dc:language>en</dc:language>""" + (if (version == 3) """<meta property="dcterms:modified">2026-11-09T00:00:00Z</meta>""" else "") + """</metadata><manifest><item id="two" href="two.xhtml" media-type="application/xhtml+xml"/><item id="one" href="one.xhtml" media-type="application/xhtml+xml"/>""" + (if (version == 3) """<item id="toc" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""" else """<item id="toc" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""") + """</manifest>""" + (if (version == 2) """<spine toc="toc">""" else "<spine>") + """<itemref idref="one"/><itemref idref="two"/></spine></package>"""),
            "EPUB/" + toc to if (version == 3) """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body><nav epub:type="toc"><ol><li><a href="one.xhtml#first">First</a></li><li><a href="two.xhtml#second">Second</a></li></ol></nav></body></html>""" else """<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><head/><docTitle><text>Fixture Book</text></docTitle><navMap><navPoint id="a" playOrder="1"><navLabel><text>First</text></navLabel><content src="one.xhtml#first"/></navPoint><navPoint id="b" playOrder="2"><navLabel><text>Second</text></navLabel><content src="two.xhtml#second"/></navPoint></navMap></ncx>""",
            "EPUB/one.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>First</title></head><body><h1 id="first">First</h1><p>The first chapter.</p></body></html>""",
            "EPUB/two.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Second</title></head><body><h1 id="second">Second</h1><p>The second chapter.</p></body></html>"""
        )
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, text) ->
                val bytes = text.toByteArray()
                val entry = ZipEntry(name)
                if (name == "mimetype") {
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.compressedSize = bytes.size.toLong()
                    entry.crc = java.util.zip.CRC32().apply { update(bytes) }.value
                }
                zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry()
            }
        }
        return file
    }
    private fun exercise(version: Int) = runBlocking {
        val original = ApplicationProvider.getApplicationContext<Context>()
        val root = File(original.cacheDir, "epub-integration-" + java.util.UUID.randomUUID()).apply { mkdirs() }
        val context = object : ContextWrapper(original) {
            override fun getFilesDir(): File = File(root, "private").apply { mkdirs() }
            override fun getApplicationContext(): Context = this
        }
        val database = Room.inMemoryDatabaseBuilder(context, NovelNestDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val libraryRoot = File(context.filesDir, "epub_library").apply { mkdirs() }
            val unexplained = File(libraryRoot, "epub-" + "a".repeat(64)).apply { mkdirs() }
            File(unexplained, "book.epub").writeText("preserve unexplained durable data")
            val unfinished = File(libraryRoot, "epub-" + "b".repeat(64)).apply { mkdirs() }
            File(unfinished, ".pending-import").writeText("1")
            File(unfinished, "book.epub").writeText("uncommitted import")
            val staged = File(libraryRoot, ".import-interrupted").apply { mkdirs() }
            File(staged, "book.epub").writeText("partial copy")
            val repository = EpubRepository(context, database.localBookDao(), scope = scope)
            val source = fixture(File(root, "sample.epub"), version)
            repository.import(Uri.fromFile(source))
            val result = withTimeout(15000) { repository.importState.first { it is EpubImportState.Complete || it is EpubImportState.Failed } }
            assertTrue(result.toString(), result is EpubImportState.Complete)
            assertTrue(File(unexplained, "book.epub").isFile)
            assertFalse(unfinished.exists()); assertFalse(staged.exists())
            val book = (result as EpubImportState.Complete).book
            assertEquals("Fixture Book", book.title); assertEquals("Fixture Author", book.author)
            val pub = repository.engine.open(repository.bookFile(book.id))
            try {
                assertEquals(listOf("one.xhtml", "two.xhtml"), pub.readingOrder.map { it.href.toString().substringAfterLast('/') })
                assertEquals(2, pub.tableOfContents.size)
                assertTrue(pub.tableOfContents[1].href.toString().endsWith("#second"))
                val resource = pub.get(pub.readingOrder[0])!!
                try {
                    val html = resource.read().getOrNull()!!.toString(Charsets.UTF_8)
                    assertTrue(html.contains("The first chapter.")); assertTrue(html.contains("Content-Security-Policy"))
                } finally { resource.close() }
                val locator = pub.locatorFromLink(pub.readingOrder[1])!!
                repository.savePosition(EpubPositionEntity(book.id, locator.toJSON().toString(), 0.5, "Second", 100))
            } finally { pub.close() }
            repository.import(Uri.fromFile(source))
            val duplicate = withTimeout(15000) { repository.importState.first { it is EpubImportState.Complete || it is EpubImportState.Failed } }
            assertTrue(duplicate is EpubImportState.Complete && duplicate.duplicate)
            assertEquals(0.5, repository.position(book.id)!!.progression, 0.0)
            assertEquals(1, database.localBookDao().observeLibrary().first().size)
            // Backup restoration without the large publication is repaired by importing the same file.
            repository.bookFile(book.id).delete()
            repository.import(Uri.fromFile(source))
            withTimeout(15000) { repository.importState.first { it is EpubImportState.Complete || it is EpubImportState.Failed } }
            assertTrue(repository.bookFile(book.id).isFile)
            assertEquals(0.5, repository.position(book.id)!!.progression, 0.0)
            source.delete()
            repository.engine.open(repository.bookFile(book.id)).close() // original is no longer needed
            repository.delete(book.id)
            assertNull(repository.book(book.id)); assertNull(repository.position(book.id))
            assertFalse(repository.bookFile(book.id).exists())
        } finally { scope.cancel(); database.close(); root.deleteRecursively() }
    }
    @Test fun epub2ImportsAndRestoresWithoutNetwork() = exercise(2)
    @Test fun epub3ImportsAndRestoresWithoutNetwork() = exercise(3)
}
