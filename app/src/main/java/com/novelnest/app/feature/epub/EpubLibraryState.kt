package com.novelnest.app.feature.epub

import com.novelnest.app.data.local.epub.*

sealed interface EpubShelfItem {
    val key: String
    val title: String
    val representative: LocalBookEntry?
    val lastReadAt: Long?
    val addedAt: Long
    data class Book(val entry: LocalBookEntry) : EpubShelfItem {
        override val key get() = entry.book.id
        override val title get() = entry.book.title
        override val representative get() = entry
        override val lastReadAt get() = entry.position?.updatedAt
        override val addedAt get() = entry.book.importedAt
    }
    data class Series(val collection: EpubCollectionEntity, val volumes: List<LocalBookEntry>) : EpubShelfItem {
        override val key get() = collection.id
        override val title get() = collection.name
        override val representative = volumes.filter { it.position != null }.maxWithOrNull(
            compareBy<LocalBookEntry> { it.position!!.updatedAt }.thenBy { it.book.id }) ?: volumes.firstOrNull()
        override val lastReadAt get() = representative?.position?.updatedAt
        override val addedAt = maxOf(collection.createdAt, volumes.maxOfOrNull { it.book.importedAt } ?: 0)
    }
}

data class EpubLibraryUiState(
    val loaded: Boolean = false,
    val collection: EpubCollectionEntity? = null,
    val collectionMissing: Boolean = false,
    val collections: List<EpubCollectionEntity> = emptyList(),
    val items: List<EpubShelfItem> = emptyList(),
    val visibleItems: List<EpubShelfItem> = emptyList(),
    val recentlyRead: List<EpubShelfItem> = emptyList(),
    val continueBook: LocalBookEntry? = null,
    val continueSeriesName: String? = null,
    val bookCount: Int = 0,
    val volumeNumbers: Map<String, Int> = emptyMap()
)

internal fun buildEpubLibraryState(snapshot: EpubLibrarySnapshot, collectionId: String?, query: String): EpubLibraryUiState {
    val collections = snapshot.collections.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    val selected = collections.firstOrNull { it.id == collectionId }
    val groups = snapshot.books.groupBy { it.book.collectionId }
    val volumeOrder = compareBy<LocalBookEntry> { it.book.volumeOrder }.thenBy { it.book.importedAt }.thenBy { it.book.id }
    val books = if (collectionId == null) snapshot.books else groups[collectionId].orEmpty().sortedWith(volumeOrder)
    val shelf: List<EpubShelfItem> = if (collectionId != null) books.map(EpubShelfItem::Book) else {
        val known = collections.map { it.id }.toSet()
        val folders = collections.map { EpubShelfItem.Series(it, groups[it.id].orEmpty().sortedWith(volumeOrder)) }
        val standalone = snapshot.books.filter { it.book.collectionId == null || it.book.collectionId !in known }.map(EpubShelfItem::Book)
        (folders + standalone).sortedWith(compareByDescending<EpubShelfItem> { it.lastReadAt != null }
            .thenByDescending { it.lastReadAt ?: it.addedAt }.thenBy { it.key })
    }
    val text = query.trim()
    fun matches(entry: LocalBookEntry) = entry.book.title.contains(text, true) || entry.book.author.contains(text, true) ||
        entry.book.volumeLabel.orEmpty().contains(text, true)
    val visible = if (text.isEmpty()) shelf else shelf.filter { item ->
        item.title.contains(text, true) || when (item) {
            is EpubShelfItem.Book -> matches(item.entry)
            is EpubShelfItem.Series -> item.volumes.any(::matches)
        }
    }
    val recent = if (collectionId == null) shelf.filter { it.lastReadAt != null }
        .sortedWith(compareByDescending<EpubShelfItem> { it.lastReadAt }.thenBy { it.key }).take(10) else emptyList()
    val current = books.filter { it.position != null }.maxWithOrNull(compareBy<LocalBookEntry> { it.position!!.updatedAt }.thenBy { it.book.id })
    return EpubLibraryUiState(snapshot.loaded, selected, snapshot.loaded && collectionId != null && selected == null,
        collections, shelf, visible, recent, current,
        current?.book?.collectionId?.let { id -> collections.firstOrNull { it.id == id }?.name?.let { name ->
            name + if (current.book.volumeLabel == null) " - Volume " + (groups[id].orEmpty().sortedWith(volumeOrder).indexOfFirst { it.book.id == current.book.id } + 1) else ""
        } }, books.size, if (collectionId == null) emptyMap() else books.mapIndexed { index, entry -> entry.book.id to index + 1 }.toMap())
}

internal fun epubProgress(entry: LocalBookEntry?): Float = entry?.position?.progression
    ?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)?.toFloat() ?: 0f
