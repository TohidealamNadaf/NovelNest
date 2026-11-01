package com.novelnest.app.data.ai

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Structural/evidence checks are deliberately local. They cannot prove semantic fidelity. */
object AiResponseValidator {
    fun parse(raw: String, action: AiAction, fingerprint: String, route: AiRoute,
        paragraphs: List<AiParagraph>, extractionTarget: String? = null): AiResult {
        // Keep diagnostics static: never include model text, names, evidence or credentials.
        var problem = "the response exceeds the supported size"
        try {
            require(raw.toByteArray(Charsets.UTF_8).size <= 256 * 1024)
            problem = "the response is not one complete JSON object"
            val json = jsonObject(raw)
            if (action == AiAction.REWRITE) {
                problem = "the required paragraph fields are missing or unexpected fields were added"
                fields(json, setOf("paragraphs"))
                val values = json.getJSONArray("paragraphs")
                problem = "the response omitted or added source paragraphs"
                require(values.length() == paragraphs.size)
                val result = paragraphs.mapIndexed { i, original ->
                    problem = "a rewritten paragraph has missing or unexpected fields"
                    val row = values.getJSONObject(i)
                    fields(row, setOf("id", "text"))
                    problem = "the rewritten paragraphs have invalid or reordered source IDs"
                    require(int(row, "id") == original.id)
                    problem = "a rewritten paragraph is empty, too long or contains unsupported characters"
                    val text = text(row, "text", 96_000, allowBlank = original.text.isBlank())
                    if (original.text.isBlank()) require(text.isBlank())
                    // Catch gross expansion, embedded markup and lost numerical facts.
                    require(text.length <= original.text.length * 3 + 256)
                    require(!text.contains(Regex("</?(?:script|iframe|html|body)\\b", RegexOption.IGNORE_CASE)))
                    problem = "a rewritten paragraph changed the source's numerical facts"
                    require(numbers(original.text) == numbers(text))
                    text
                }
                return AiResult(action, fingerprint, route, rewrittenParagraphs = result)
            }
            problem = if (action == AiAction.EXTRACT) "the required terms array is missing or unexpected fields were added"
                else "the required summary, events or terms fields are missing or have the wrong format"
            fields(json, if (action == AiAction.EXTRACT) setOf("terms") else setOf("summary", "events", "terms"))
            val byId = paragraphs.associateBy { it.id }
            val allowedKinds = if (action == AiAction.EXTRACT) AiExtractionPolicy.forTarget(extractionTarget).kinds else AiExtractionPolicy.allKinds
            val events = if (action == AiAction.EXTRACT) JSONArray() else json.getJSONArray("events")
            val terms = json.getJSONArray("terms")
            problem = "the response returned too many events or terms"
            require(events.length() <= AiPromptFactory.MAX_EVENTS && terms.length() <= if (action == AiAction.EXTRACT) 320 else 20)
            problem = "the summary text is missing, empty, too long or contains unsupported characters"
            val summaryText = if (action == AiAction.SUMMARY) text(json, "summary", 6_000) else null
            val parsedEvents = (0 until events.length()).map { i ->
                problem = "an event has missing or unexpected fields"
                val event = events.getJSONObject(i)
                fields(event, setOf("text", "paragraphIds"))
                problem = "an event's paragraphIds field is missing or is not an array"
                val refs = event.getJSONArray("paragraphIds")
                problem = "an event has no supporting paragraph references"
                require(refs.length() > 0)
                problem = "an event exceeds the supported limit of eight paragraph references"
                require(refs.length() <= AiPromptFactory.MAX_EVENT_REFERENCES)
                val ids = (0 until refs.length()).map { index ->
                    problem = "an event's source paragraph reference is not an integer"
                    val value = refs.get(index)
                    require(value is Number && value.toDouble() == value.toInt().toDouble())
                    problem = "an event references a source paragraph that was not supplied"
                    value.toInt().also { require(it in byId) }
                    // Repeating the same valid citation carries no additional evidence. Canonicalize it
                    // without dropping unknown IDs, guessing a numbering offset or making a repair call.
                }.distinct()
                problem = "an event description is empty, too long or contains unsupported characters"
                AiEvent(text(event, "text", 1_500), ids)
            }
            val parsedTerms = (0 until terms.length()).map { i ->
                problem = "a term has missing or unexpected fields"
                val term = terms.getJSONObject(i)
                fields(term, setOf("name", "kind", "meaning", "paragraphId", "evidence"))
                problem = "a term references a source paragraph that was not supplied"
                val id = int(term, "paragraphId")
                val source = byId[id]?.text ?: error("Invalid reference")
                problem = "a term's name or evidence is empty, too long or contains unsupported characters"
                val evidence = text(term, "evidence", 200)
                val name = text(term, "name", 160)
                problem = if (action == AiAction.EXTRACT) "a term's category does not match the requested extraction topic"
                    else "a term uses an unsupported category"
                val kind = text(term, "kind", 32)
                require(kind in allowedKinds)
                problem = "a term's name or evidence does not exactly match its source paragraph"
                require(source.contains(evidence) && source.contains(name))
                if (action == AiAction.EXTRACT) {
                    problem = "a term's evidence does not contain the extracted name or subject phrase"
                    require(evidence.contains(name))
                }
                problem = "a term's meaning is empty, too long or contains unsupported characters"
                AiTerm(name, kind, text(term, "meaning", 1_000), id, evidence)
            }.distinctBy { if (action == AiAction.EXTRACT) listOf(it.name, it.paragraphId, it.meaning) else it.name.lowercase(java.util.Locale.ROOT) }
            if (action == AiAction.EXTRACT) return AiResult(action, fingerprint, route, extractedTerms = parsedTerms)
            return AiResult(action, fingerprint, route, AiSummary(requireNotNull(summaryText), parsedEvents, parsedTerms))
        } catch (_: Exception) {
            val label = when (action) { AiAction.SUMMARY -> "Summary"; AiAction.REWRITE -> "Rewrite"; AiAction.EXTRACT -> "Term-extraction" }
            throw AiException(AiErrorCode.INVALID_OUTPUT,
                "$label validation failed: $problem. Try another configured model. The chapter and saved results are unchanged.")
        }
    }

    /** Accept only transport decoration around an otherwise complete object. Never extract a JSON
     * substring out of prose/reasoning, repair truncated output, or ignore trailing provider text. */
    private fun jsonObject(raw: String): JSONObject {
        var payload = raw.trim().removePrefix("\uFEFF").trim()
        if (payload.startsWith("```")) {
            val firstLine = payload.indexOf('\n')
            val lastLine = payload.lastIndexOf('\n')
            require(firstLine >= 0 && lastLine > firstLine)
            require(payload.substring(0, firstLine).trim().lowercase(java.util.Locale.ROOT) in setOf("```", "```json"))
            require(payload.substring(lastLine + 1).trim() == "```")
            payload = payload.substring(firstLine + 1, lastLine).trim()
        }
        val tokens = JSONTokener(payload)
        val value = tokens.nextValue()
        require(value is JSONObject && tokens.nextClean() == '\u0000')
        return value
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
