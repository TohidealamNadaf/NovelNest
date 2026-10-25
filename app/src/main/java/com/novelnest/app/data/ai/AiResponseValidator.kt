package com.novelnest.app.data.ai

import org.json.JSONArray
import org.json.JSONObject

/** Structural/evidence checks are deliberately local. They cannot prove semantic fidelity. */
object AiResponseValidator {
    fun parse(raw: String, action: AiAction, fingerprint: String, route: AiRoute,
        paragraphs: List<AiParagraph>): AiResult {
        try {
            require(raw.toByteArray(Charsets.UTF_8).size <= 256 * 1024)
            val json = JSONObject(raw)
            if (action == AiAction.REWRITE) {
                fields(json, setOf("paragraphs"))
                val values = json.getJSONArray("paragraphs")
                require(values.length() == paragraphs.size)
                val result = paragraphs.mapIndexed { i, original ->
                    val row = values.getJSONObject(i)
                    fields(row, setOf("id", "text"))
                    require(int(row, "id") == original.id)
                    val text = text(row, "text", 96_000, allowBlank = original.text.isBlank())
                    if (original.text.isBlank()) require(text.isBlank())
                    // Catch gross expansion, embedded markup and lost numerical facts.
                    require(text.length <= original.text.length * 3 + 256)
                    require(!text.contains(Regex("</?(?:script|iframe|html|body)\\b", RegexOption.IGNORE_CASE)))
                    require(numbers(original.text) == numbers(text))
                    text
                }
                return AiResult(action, fingerprint, route, rewrittenParagraphs = result)
            }
            fields(json, if (action == AiAction.EXTRACT) setOf("terms") else setOf("summary", "events", "terms"))
            val byId = paragraphs.associateBy { it.id }
            val events = if (action == AiAction.EXTRACT) JSONArray() else json.getJSONArray("events")
            val terms = json.getJSONArray("terms")
            require(events.length() <= 8 && terms.length() <= if (action == AiAction.EXTRACT) 320 else 20)
            val parsedEvents = (0 until events.length()).map { i -> events.getJSONObject(i).let { event ->
                fields(event, setOf("text", "paragraphIds"))
                val refs = event.getJSONArray("paragraphIds")
                require(refs.length() in 1..8)
                val ids = (0 until refs.length()).map { index ->
                    val value = refs.get(index)
                    require(value is Number && value.toDouble() == value.toInt().toDouble())
                    value.toInt().also { require(it in byId) }
                }
                require(ids.distinct().size == ids.size)
                AiEvent(text(event, "text", 1_500), ids)
            } }
            val parsedTerms = (0 until terms.length()).map { i -> terms.getJSONObject(i).let { term ->
                fields(term, setOf("name", "kind", "meaning", "paragraphId", "evidence"))
                val id = int(term, "paragraphId")
                val source = byId[id]?.text ?: error("Invalid reference")
                val evidence = text(term, "evidence", 200)
                val name = text(term, "name", 160)
                val kind = text(term, "kind", 32)
                require(kind in setOf("CHARACTER", "LOCATION", "ABILITY", "ITEM", "ORGANIZATION", "CONCEPT"))
                require(source.contains(evidence) && source.contains(name))
                AiTerm(name, kind, text(term, "meaning", 1_000), id, evidence)
            } }.distinctBy { if (action == AiAction.EXTRACT) listOf(it.name, it.paragraphId, it.meaning) else it.name.lowercase(java.util.Locale.ROOT) }
            if (action == AiAction.EXTRACT) return AiResult(action, fingerprint, route, extractedTerms = parsedTerms)
            return AiResult(action, fingerprint, route, AiSummary(text(json, "summary", 6_000), parsedEvents, parsedTerms))
        } catch (_: Exception) {
            throw AiException(AiErrorCode.INVALID_OUTPUT, "The model returned incomplete or invalid chapter text. The original is unchanged.")
        }
    }

    fun outputJson(result: AiResult): JSONObject = if (result.action == AiAction.EXTRACT) JSONObject().put("terms", termsJson(result.extractedTerms))
        else result.summary?.let(::summaryJson) ?: JSONObject().put("paragraphs",
        JSONArray().apply { result.rewrittenParagraphs.forEachIndexed { index, text ->
            put(JSONObject().put("id", index).put("text", text))
        } })

    fun summaryJson(summary: AiSummary): JSONObject = JSONObject().put("summary", summary.text)
        .put("events", JSONArray().apply { summary.events.forEach { put(JSONObject().put("text", it.text)
            .put("paragraphIds", JSONArray(it.paragraphIds))) } })
        .put("terms", termsJson(summary.terms))

    private fun termsJson(terms: List<AiTerm>) = JSONArray().apply { terms.forEach { put(JSONObject().put("name", it.name).put("kind", it.kind)
            .put("meaning", it.meaning).put("paragraphId", it.paragraphId).put("evidence", it.evidence)) } }

    private fun fields(json: JSONObject, expected: Set<String>) { require(json.keys().asSequence().toSet() == expected) }
    private fun text(json: JSONObject, key: String, max: Int, allowBlank: Boolean = false): String {
        val value = json.get(key)
        require(value is String && value.length <= max && (allowBlank || value.isNotBlank()))
        require(value.none { it.code < 32 && it !in "\n\r\t" })
        return value
    }
    private fun int(json: JSONObject, key: String): Int {
        val value = json.get(key)
        require(value is Number && value.toDouble() == value.toInt().toDouble())
        return value.toInt()
    }
    private fun numbers(text: String) = Regex("\\p{N}+(?:[.,]\\p{N}+)*").findAll(text).map { it.value }.toList()
}
