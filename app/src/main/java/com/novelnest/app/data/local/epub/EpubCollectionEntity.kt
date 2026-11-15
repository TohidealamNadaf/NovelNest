package com.novelnest.app.data.local.epub

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "epub_collections", indices = [Index(value = ["nameKey"], unique = true)])
data class EpubCollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameKey: String,
    val createdAt: Long
)

/** One transactional metadata snapshot; publications and chapter bodies never enter library state. */
data class EpubLibrarySnapshot(
    val books: List<LocalBookEntry> = emptyList(),
    val collections: List<EpubCollectionEntity> = emptyList(),
    val loaded: Boolean = false
)
