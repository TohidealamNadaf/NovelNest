package com.novelnest.app.data.local.epub

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException

class EpubArchiveValidatorTest {
    @get:Rule val temp = TemporaryFolder()
    private fun zip(vararg extra: Pair<String, String>, mime: String = "application/epub+zip"): File {
        val file = temp.newFile()
        ZipOutputStream(file.outputStream()).use { out ->
            for ((name, value) in listOf("mimetype" to mime, "META-INF/container.xml" to "<container/>") + extra) {
                out.putNextEntry(ZipEntry(name)); out.write(value.toByteArray()); out.closeEntry()
            }
        }
        return file
    }
    @Test fun acceptsBoundedEpubContainer() { EpubArchiveValidator.validate(zip("text/one.xhtml" to "<p>Chapter one</p>")) }
    @Test fun checksActualMimeRatherThanFilename() {
        assertThrows(EpubImportException::class.java) { EpubArchiveValidator.validate(zip(mime = "application/zip")) }
    }
    @Test fun rejectsTraversal() {
        assertThrows(EpubImportException::class.java) { EpubArchiveValidator.validate(zip("../private.txt" to "unsafe")) }
    }
    @Test fun rejectsAbsoluteAndWindowsPaths() {
        listOf("/root/book", "C:/book", "book\\private", "a/./b", "a/../b").forEach { assertFalse(it, EpubArchiveValidator.safePath(it)) }
        assertTrue(EpubArchiveValidator.safePath("EPUB/Text/chapter 1.xhtml"))
    }
    @Test fun rejectsEntityDeclarations() {
        assertThrows(EpubImportException::class.java) { EpubArchiveValidator.validate(zip("book.opf" to "<!ENTITY x SYSTEM 'file:///private'>")) }
    }
    @Test fun rejectsOversizedInflatedMarkup() {
        assertThrows(EpubImportException::class.java) { EpubArchiveValidator.validate(zip("one.xhtml" to "x".repeat(EpubArchiveValidator.MAX_MARKUP_BYTES.toInt() + 1))) }
    }
    @Test fun rejectsActiveSvg() {
        assertThrows(EpubImportException::class.java) { EpubArchiveValidator.validate(zip("cover.svg" to "<svg onload='alert(1)'/>")) }
    }
    @Test fun copyHashesStreamWithoutDependingOnFilename() {
        val data = "abc".toByteArray()
        val one = EpubArchiveValidator.copyAndHash(ByteArrayInputStream(data), temp.newFile())
        val two = EpubArchiveValidator.copyAndHash(ByteArrayInputStream(data), temp.newFile())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", one.first)
        assertEquals(one, two); assertEquals(3L, one.second)
    }
    @Test fun rejectsEmptyInput() {
        assertThrows(EpubImportException::class.java) { EpubArchiveValidator.copyAndHash(ByteArrayInputStream(byteArrayOf()), temp.newFile()) }
    }
    @Test fun cancellationStopsCopy() {
        assertThrows(CancellationException::class.java) {
            EpubArchiveValidator.copyAndHash(ByteArrayInputStream(ByteArray(100)), temp.newFile(), { throw CancellationException() })
        }
    }
    @Test fun cancellationStopsArchiveScan() {
        assertThrows(CancellationException::class.java) { EpubArchiveValidator.validate(zip()) { throw CancellationException() } }
    }
    @Test fun rejectsNonArchiveInput() {
        val file = temp.newFile().apply { writeText("not an epub") }
        assertThrows(java.io.IOException::class.java) { EpubArchiveValidator.validate(file) }
    }

    @Test fun rejectsUtf16EntityDeclarations() {
        val file = temp.newFile()
        ZipOutputStream(file.outputStream()).use { out ->
            val entries = listOf("mimetype" to "application/epub+zip".toByteArray(),
                "META-INF/container.xml" to "<container/>".toByteArray(),
                "book.opf" to "<?xml version='1.0' encoding='UTF-16'?><!ENTITY x SYSTEM 'file:///private'>".toByteArray(Charsets.UTF_16))
            entries.forEach { (name, bytes) -> out.putNextEntry(ZipEntry(name)); out.write(bytes); out.closeEntry() }
        }
        assertThrows(EpubImportException::class.java) { EpubArchiveValidator.validate(file) }
    }
}
