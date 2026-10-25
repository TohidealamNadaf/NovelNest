package com.novelnest.app.feature.reader.components

import com.novelnest.app.data.study.TermDiscoveryEntity

data class TermReferenceUi(val id: String, val description: String, val chapterFound: String)

data class ExtractedTermItem(
    val term: String, val category: String, val description: String, val chapterFound: String,
    val id: String = term + chapterFound, val references: List<TermReferenceUi> = emptyList()
)

/** One dictionary entry per normalized term; references remain individually lazy-renderable. */
internal fun buildTermItems(rows: List<TermDiscoveryEntity>): List<ExtractedTermItem> = rows.groupBy { it.termKey }.map { (_, discoveries) ->
    val ordered = discoveries.sortedWith(compareBy<TermDiscoveryEntity> { it.createdAt }.thenBy { it.id })
    val latest = ordered.last()
    fun TermDiscoveryEntity.reference() = TermReferenceUi(id,
        meaning + "\n\nEvidence: “" + evidence + "”", chapterTitle + " · paragraph " + (paragraphId + 1))
    val reference = latest.reference()
    ExtractedTermItem(latest.name, latest.kind, reference.description, reference.chapterFound,
        latest.collectionId + ":" + latest.termKey, ordered.map { it.reference() })
}
