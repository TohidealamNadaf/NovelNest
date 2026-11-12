package com.novelnest.app.data.local.epub

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

class EpubImportException(message: String) : IOException(message)

/** Bounds apply to actual streamed bytes, including entries whose ZIP sizes are dishonest. */
object EpubArchiveValidator {
    const val MAX_FILE_BYTES = 100L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 512L * 1024 * 1024
    const val MAX_ENTRY_BYTES = 20L * 1024 * 1024
    const val MAX_MARKUP_BYTES = 4L * 1024 * 1024
    const val MAX_ENTRIES = 10000

    fun copyAndHash(input: InputStream, target: File, checkCancelled: () -> Unit = {}, onBytes: (Long) -> Unit = {}): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        target.outputStream().use { output ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                checkCancelled()
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                if (total > MAX_FILE_BYTES) throw EpubImportException("This EPUB exceeds the 100 MiB import limit.")
                digest.update(buffer, 0, count)
                output.write(buffer, 0, count)
                onBytes(total)
            }
            output.fd.sync()
        }
        if (total == 0L) throw EpubImportException("The selected file is empty.")
        return digest.digest().joinToString("") { "%02x".format(it) } to total
    }

    fun validate(file: File, checkCancelled: () -> Unit = {}) {
        if (file.length() > MAX_FILE_BYTES) throw EpubImportException("This EPUB exceeds the 100 MiB import limit.")
        ZipFile(file).use { zip ->
            val seen = HashSet<String>()
            var total = 0L
            var count = 0
            val entries = zip.entries()
            val buffer = ByteArray(32 * 1024)
            while (entries.hasMoreElements()) {
                checkCancelled()
                val entry = entries.nextElement()
                if (++count > MAX_ENTRIES) throw EpubImportException("This EPUB contains too many resources.")
                val name = entry.name
                if (!safePath(name) || !seen.add(name)) throw EpubImportException("The EPUB has unsafe or duplicate archive paths.")
                if (entry.isDirectory) continue
                val markup = name.substringAfterLast('.', "").lowercase() in setOf("xhtml", "html", "htm", "xml", "opf", "ncx", "svg", "css")
                val limit = if (markup) MAX_MARKUP_BYTES else MAX_ENTRY_BYTES
                if (entry.size > limit) throw EpubImportException("An EPUB resource is too large to open safely.")
                var inflated = 0L
                zip.getInputStream(entry).use { input ->
                    while (true) {
                        checkCancelled()
                        val n = input.read(buffer)
                        if (n < 0) break
                        inflated += n; total += n
                        if (inflated > limit || total > MAX_TOTAL_BYTES)
                            throw EpubImportException("The expanded EPUB exceeds safe import limits.")
                    }
                }
            }
            val mime = zip.getEntry("mimetype") ?: throw EpubImportException("The selected file is not an EPUB publication.")
            if (mime.size !in 1..100 || zip.getInputStream(mime).use { it.reader(Charsets.US_ASCII).readText().trim() } != "application/epub+zip")
                throw EpubImportException("The selected file is not an EPUB publication.")
            if (zip.getEntry("META-INF/container.xml") == null) throw EpubImportException("The EPUB package information is missing.")
            // ENTITY declarations are never necessary for the supported offline publication format.
            zip.entries().asSequence().filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in setOf("xml", "opf", "ncx", "svg", "xhtml", "html") }.forEach { entry ->
                checkCancelled()
                val data = zip.getInputStream(entry).use { it.readBytes() }
                val text = when {
                    data.size >= 4 && ((data[0] == 0.toByte() && data[1] == 0.toByte()) || (data[2] == 0.toByte() && data[3] == 0.toByte())) ->
                        throw EpubImportException("UTF-32 EPUB markup is unsupported.")
                    data.size >= 2 && data[0] == 0xff.toByte() && data[1] == 0xfe.toByte() -> data.toString(Charsets.UTF_16LE)
                    data.size >= 2 && data[0] == 0xfe.toByte() && data[1] == 0xff.toByte() -> data.toString(Charsets.UTF_16BE)
                    data.size >= 2 && data[0] == 0.toByte() -> data.toString(Charsets.UTF_16BE)
                    data.size >= 2 && data[1] == 0.toByte() -> data.toString(Charsets.UTF_16LE)
                    else -> data.toString(Charsets.UTF_8)
                }
                if (text.contains("<!ENTITY", ignoreCase = true)) throw EpubImportException("EPUBs containing XML entity declarations are unsupported.")
                if (entry.name.endsWith(".svg", true) && Regex("<script|\\son[a-z]+\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(text))
                    throw EpubImportException("This EPUB contains active SVG content, which is unsupported.")
            }
        }
    }

    internal fun safePath(name: String): Boolean = name.isNotBlank() && !name.startsWith('/') &&
        !name.contains('\\') && !name.contains(':') && !name.contains('\u0000') &&
        name.split('/').none { it == ".." || it == "." }
}
