package com.novelnest.app.data.remote.asura

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

internal object AsuraUtils {
    fun document(html: String, url: String): Document {
        val doc = Jsoup.parse(html, url)
        if (doc.title().contains("Just a moment", true) || doc.selectFirst("#challenge-form, #cf-challenge-running") != null) throw AsuraException.Blocked()
        return doc
    }
    fun plain(html: String) = Jsoup.parseBodyFragment(html).text().trim()
    fun text(obj: JSONObject, key: String): String = obj.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
    fun objects(array: JSONArray?): List<JSONObject> = if (array == null) emptyList() else
        (0 until array.length()).map { array.optJSONObject(it) ?: throw AsuraException.Parse("Invalid object in list") }
    fun island(doc: Document, component: String): JSONObject? = doc.select(AsuraSelectors.ISLANDS)
        .filter { it.attr("component-url").substringAfterLast('/').startsWith(component) }
        .firstOrNull { !it.attr("props").contains("\"/novels/") }?.let { element ->
            try { decodeObject(JSONObject(element.attr("props")), 0) }
            catch (error: AsuraException) { throw error }
            catch (error: Exception) { throw AsuraException.Parse("Invalid $component data", error) }
        }
    private fun decodeObject(value: JSONObject, depth: Int): JSONObject {
        if (depth > 32) throw AsuraException.Parse("Astro data is too deeply nested")
        return JSONObject().also { result -> value.keys().forEach { result.put(it, decode(value.getJSONArray(it), depth + 1)) } }
    }
    private fun decode(tag: JSONArray, depth: Int): Any {
        if (depth > 32) throw AsuraException.Parse("Astro data is too deeply nested")
        val value = tag.opt(1) ?: JSONObject.NULL
        return when (tag.optInt(0, -1)) {
            0 -> if (value is JSONObject) decodeObject(value, depth + 1) else value
            1 -> {
                val array = value as? JSONArray ?: throw AsuraException.Parse("Invalid Astro array")
                JSONArray().also { out -> for (i in 0 until array.length()) out.put(decode(array.getJSONArray(i), depth + 1)) }
            }
            else -> throw AsuraException.Parse("Unsupported Astro value type")
        }
    }
}
