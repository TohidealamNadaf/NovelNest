package com.novelnest.app.data.dictionary

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/** Small, bounded data models only. API text is rendered as native text, never HTML or JavaScript. */
internal object DictionaryParser {
    fun parse(raw: String, word: String, fetchedAtMs: Long): DictionaryResult = try {
        parseEntries(JSONArray(raw), word, fetchedAtMs)
    } catch (error: DictionaryException) { throw error }
    catch (_: Exception) { invalid() }

    private fun parseEntries(array: JSONArray, word: String, fetchedAtMs: Long): DictionaryResult {
        require(array.length() in 1..16)
        var definitionCount = 0
        val entries = (0 until array.length()).map { index ->
            val entry = array.getJSONObject(index)
            val meanings = entry.getJSONArray("meanings")
            require(meanings.length() in 1..64)
            val parsedMeanings = (0 until meanings.length()).map { i ->
                val meaning = meanings.getJSONObject(i)
                val definitions = meaning.getJSONArray("definitions")
                require(definitions.length() in 1..256)
                definitionCount += definitions.length()
                require(definitionCount <= 256)
                DictionaryMeaning(text(meaning, "partOfSpeech", 100),
                    (0 until definitions.length()).map { d ->
                        val value = definitions.getJSONObject(d)
                        DictionaryDefinition(text(value, "definition", 8000), optional(value, "example", 8000),
                            strings(value, "synonyms"), strings(value, "antonyms"))
                    }, strings(meaning, "synonyms"), strings(meaning, "antonyms"))
            }
            val phonetics = entry.optJSONArray("phonetics") ?: JSONArray()
            require(phonetics.length() <= 32)
            val pronunciation = (0 until phonetics.length()).mapNotNull {
                optional(phonetics.getJSONObject(it), "text", 256)
            } + listOfNotNull(optional(entry, "phonetic", 256))
            val license = entry.optJSONObject("license")?.let {
                optional(it, "name", 300)?.let { name -> DictionaryLicense(name, https(optional(it, "url", 2048))) }
            }
            DictionaryEntry(text(entry, "word", 200), pronunciation.distinct(), parsedMeanings,
                strings(entry, "sourceUrls", 32, 2048).mapNotNull(::https).distinct(), license)
        }
        return DictionaryResult(word, entries, fetchedAtMs)
    }

    fun encode(result: DictionaryResult, key: String): String {
        fun list(values: List<String>) = JSONArray(values)
        val entries = JSONArray()
        result.entries.forEach { entry ->
            val meanings = JSONArray()
            entry.meanings.forEach { meaning ->
                val definitions = JSONArray()
                meaning.definitions.forEach { definition ->
                    definitions.put(JSONObject().put("definition", definition.text).put("example", definition.example)
                        .put("synonyms", list(definition.synonyms)).put("antonyms", list(definition.antonyms)))
                }
                meanings.put(JSONObject().put("partOfSpeech", meaning.partOfSpeech).put("definitions", definitions)
                    .put("synonyms", list(meaning.synonyms)).put("antonyms", list(meaning.antonyms)))
            }
            val phonetics = JSONArray()
            entry.phonetics.forEach { phonetics.put(JSONObject().put("text", it)) }
            val json = JSONObject().put("word", entry.word).put("phonetics", phonetics).put("meanings", meanings)
                .put("sourceUrls", list(entry.sources))
            entry.license?.let { json.put("license", JSONObject().put("name", it.name).put("url", it.url)) }
            entries.put(json)
        }
        return JSONObject().put("schema", 1).put("key", key).put("query", result.query)
            .put("fetchedAt", result.fetchedAtMs).put("entries", entries).toString()
    }

    fun decode(raw: String, word: String, key: String): DictionaryResult {
        val json = JSONObject(raw)
        require(json.getInt("schema") == 1 && json.getString("key") == key && json.getString("query") == word)
        val time = json.getLong("fetchedAt")
        require(time > 0)
        return parseEntries(json.getJSONArray("entries"), word, time)
    }

    private fun strings(json: JSONObject, name: String, count: Int = 128, length: Int = 200): List<String> {
        val values = json.optJSONArray(name) ?: return emptyList()
        require(values.length() <= count)
        return (0 until values.length()).map { i ->
            val value = values.get(i)
            require(value is String && value.length <= length)
            value.trim()
        }.filter { it.isNotEmpty() }.distinct()
    }
    private fun text(json: JSONObject, name: String, max: Int): String =
        optional(json, name, max)?.takeIf { it.isNotBlank() } ?: invalid()
    private fun optional(json: JSONObject, name: String, max: Int): String? {
        if (!json.has(name) || json.isNull(name)) return null
        val value = json.get(name)
        require(value is String && value.length <= max)
        return value.trim().takeIf { it.isNotEmpty() }
    }
    private fun https(value: String?): String? = value?.toHttpUrlOrNull()?.takeIf { it.isHttps }?.toString()
    private fun invalid(): Nothing = throw DictionaryException(DictionaryError.INVALID_RESPONSE,
        "The dictionary returned an unsupported response. Please try again later.")
}
