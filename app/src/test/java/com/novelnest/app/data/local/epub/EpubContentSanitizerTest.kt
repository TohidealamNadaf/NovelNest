package com.novelnest.app.data.local.epub

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class EpubContentSanitizerTest {
    @Test fun retainsReadingMarkupAndLocalImages() {
        val html = EpubContentSanitizer.sanitize("<html><head><title>Book</title></head><body><h1 id='ch'>Chapter</h1><p>Hello <em>world</em>.</p><img src='../images/pic.jpg'/><a href='#ch'>Back</a></body></html>".toByteArray()).toString(Charsets.UTF_8)
        val doc = Jsoup.parse(html)
        assertEquals("world", doc.selectFirst("em")?.text())
        assertEquals("../images/pic.jpg", doc.selectFirst("img")?.attr("src"))
        assertEquals("#ch", doc.selectFirst("a")?.attr("href"))
    }
    @Test fun removesPublicationCodeAndAppliesOfflinePolicy() {
        val html = "<html><head><script src='https://evil.test/a.js'></script><meta http-equiv='refresh' content='0;url=https://evil.test'/></head><body onload='evil()'><p onclick='evil()'>Kept</p><iframe src='file:///private'></iframe><a href='java&#115;cript:evil()'>Link</a></body></html>"
        val doc = Jsoup.parse(EpubContentSanitizer.sanitize(html.toByteArray()).toString(Charsets.UTF_8))
        assertTrue(doc.select("script, iframe, [onload], [onclick]").isEmpty())
        assertFalse(doc.selectFirst("a")!!.hasAttr("href"))
        val policy = doc.selectFirst("meta[http-equiv=Content-Security-Policy]")!!.attr("content")
        assertTrue(policy.contains("connect-src 'none'")); assertTrue(policy.contains("img-src 'self' data:"))
        assertEquals("Kept", doc.selectFirst("p")!!.text())
    }
    @Test fun stripsFormsBaseAndRefreshWithoutDeletingChapterText() {
        val doc = Jsoup.parse(EpubContentSanitizer.sanitize("<base href='https://evil.test'/><p>Text</p><form><input/></form>".toByteArray()).toString(Charsets.UTF_8))
        assertTrue(doc.select("base, form").isEmpty()); assertEquals("Text", doc.selectFirst("p")!!.text())
    }

    @Test fun normalizesUtf16TextToUtf8WithoutAStaleXmlDeclaration() {
        val words = charArrayOf(0x4E16.toChar(), 0x754C.toChar()).concatToString()
        val original = "<?xml version='1.0' encoding='UTF-16'?><html><head><title>Book</title></head><body><p>" + words + "</p></body></html>"
        val result = EpubContentSanitizer.sanitize(original.toByteArray(Charsets.UTF_16)).toString(Charsets.UTF_8)
        assertEquals(words, Jsoup.parse(result).selectFirst("p")!!.text())
        assertFalse(result.contains("UTF-16", ignoreCase = true))
    }
}
