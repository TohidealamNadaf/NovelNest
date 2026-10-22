package com.novelnest.app.data.remote

import org.jsoup.nodes.Document
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Source metadata only; never substitutes a default score for missing ratings. */
object RatingParser {
    private val number = Regex("""^([0-9]+(?:[.,][0-9]+)?)(?:\s*/\s*([0-9]+(?:[.,][0-9]+)?))?$""")
    fun score(raw: String, best: Float = 5f): Float? {
        val match = number.matchEntire(raw.trim()) ?: return null
        val value = match.groupValues[1].replace(',', '.').toFloatOrNull() ?: return null
        val scale = match.groupValues[2].replace(',', '.').toFloatOrNull() ?: best
        if (!value.isFinite() || !scale.isFinite() || scale <= 0 || value <= 0 || value > scale) return null
        return (value / scale * 5f).takeIf { it.isFinite() && it > 0 && it <= 5 }
    }

    fun structured(document: Document): Float? {
        fun find(value: Any?): Float? = when (value) {
            is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { find(value.opt(it)) }
            is JSONObject -> {
                val aggregate = value.opt("aggregateRating")
                val direct = if (value.has("ratingValue")) {
                    val count = value.opt("ratingCount") ?: value.opt("reviewCount")
                    if (count?.toString()?.toDoubleOrNull() == 0.0) null
                    else score(value.optString("ratingValue"), value.optString("bestRating").toFloatOrNull() ?: 5f)
                } else null
                direct ?: find(aggregate) ?: find(value.opt("@graph"))
            }
            else -> null
        }
        return document.select("script[type=application/ld+json]").firstNotNullOfOrNull { script ->
            runCatching { find(JSONTokener(script.data()).nextValue()) }.getOrNull()
        }
    }
}
