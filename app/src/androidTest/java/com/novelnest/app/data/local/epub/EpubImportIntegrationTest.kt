package com.novelnest.app.data.local.epub

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.Lifecycle
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.feature.epub.EpubReaderActivity
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Link
import org.json.JSONObject
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
        val fixtureId = java.util.UUID.randomUUID().toString()
        val toc = if (version == 3) "nav.xhtml" else "toc.ncx"
        val entries = linkedMapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="EPUB/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            "EPUB/book.opf" to ("""<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" unique-identifier="id" version="""" + (if (version == 3) "3.0" else "2.0") + """"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">""" + fixtureId + """</dc:identifier><dc:title>Fixture Book</dc:title><dc:creator>Fixture Author</dc:creator><dc:language>en</dc:language>""" + (if (version == 3) """<meta property="dcterms:modified">2026-11-09T00:00:00Z</meta>""" else "") + """</metadata><manifest><item id="two" href="two.xhtml" media-type="application/xhtml+xml"/><item id="one" href="one.xhtml" media-type="application/xhtml+xml"/>""" + (if (version == 3) """<item id="toc" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""" else """<item id="toc" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""") + """</manifest>""" + (if (version == 2) """<spine toc="toc">""" else "<spine>") + """<itemref idref="one"/><itemref idref="two"/></spine></package>"""),
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
                    assertTrue("Chapter text missing: " + html.take(1600), html.contains("The first chapter."))
                    assertTrue("Offline policy missing: " + html.take(1600), html.contains("Content-Security-Policy"))
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

    private suspend fun waitForChapter(scenario: ActivityScenario<EpubReaderActivity>, suffix: String) = withTimeout(20000) {
        while (true) {
            var matches = false
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("local-epub") as? EpubNavigatorFragment
                matches = fragment?.isResumed == true && fragment.view != null && fragment.currentLocator.value.href.toString().substringBefore('#').endsWith(suffix)
            }
            if (matches) break
            delay(100)
        }
    }
    private fun webViews(view: View): List<WebView> = when (view) {
        is WebView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { webViews(view.getChildAt(it)) }
        else -> emptyList()
    }
    private suspend fun waitForRenderedText(scenario: ActivityScenario<EpubReaderActivity>, expected: String) = withTimeout(20000) {
        while (true) {
            val results = mutableListOf<CompletableDeferred<String>>()
            scenario.onActivity { activity ->
                webViews(activity.window.decorView).forEach { webView ->
                    val result = CompletableDeferred<String>()
                    results.add(result)
                    webView.evaluateJavascript("document.body.innerText") { result.complete(it.orEmpty()) }
                }
            }
            if (results.any { withTimeoutOrNull(1000) { it.await() }?.contains(expected) == true }) break
            delay(100)
        }
    }
    @Test fun readerRendersAndResumesAcrossRotationAndBackground() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<NovelNestApplication>()
        val source = fixture(File(app.cacheDir, "epub-reader-test-" + java.util.UUID.randomUUID() + ".epub"), 3)
        val repository = app.epubRepository
        var importedId: String? = null
        var scenario: ActivityScenario<EpubReaderActivity>? = null
        try {
            repository.import(Uri.fromFile(source))
            val result = withTimeout(15000) { repository.importState.first { it is EpubImportState.Complete || it is EpubImportState.Failed } }
            assertTrue(result.toString(), result is EpubImportState.Complete)
            val id = (result as EpubImportState.Complete).book.id
            importedId = id
            val reader = ActivityScenario.launch<EpubReaderActivity>(Intent(app, EpubReaderActivity::class.java)
                .putExtra("local_epub_id", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            scenario = reader
            waitForChapter(reader, "one.xhtml")
            waitForRenderedText(reader, "The first chapter.")
            reader.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("local-epub") as EpubNavigatorFragment
                val second = checkNotNull(Link.fromJSON(JSONObject("""{"href":"EPUB/two.xhtml#second","type":"application/xhtml+xml"}""")))
                assertTrue(fragment.go(second, animated = false))
            }
            waitForChapter(reader, "two.xhtml")
            waitForRenderedText(reader, "The second chapter.")
            reader.recreate()
            waitForChapter(reader, "two.xhtml")
            waitForRenderedText(reader, "The second chapter.")
            reader.moveToState(Lifecycle.State.CREATED)
            withTimeout(5000) {
                while (repository.position(id)?.locatorJson?.contains("two.xhtml") != true) delay(100)
            }
            reader.moveToState(Lifecycle.State.RESUMED)
            waitForChapter(reader, "two.xhtml")
        } finally {
            scenario?.close()
            importedId?.let { repository.delete(it) }
            source.delete()
        }
    }

}
