package com.novelnest.app.data.study

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** User-owned data: deliberately independent of disposable novel metadata and bookmarks. */
@Entity(tableName = "chapter_notes", indices = [Index(value = ["source", "novelId", "chapterId"])])
data class ChapterNoteEntity(
    @PrimaryKey val id: String, val source: String, val novelId: String, val chapterId: String,
    val title: String, val markdown: String, val updatedAt: Long
)

@Entity(tableName = "term_collections", indices = [Index(value = ["source", "novelId", "nameKey"], unique = true)])
data class TermCollectionEntity(
    @PrimaryKey val id: String, val source: String, val novelId: String, val name: String,
    val nameKey: String, val target: String, val updatedAt: Long, val lastChapter: String
)

@Entity(tableName = "term_discoveries", foreignKeys = [ForeignKey(
    entity = TermCollectionEntity::class, parentColumns = ["id"], childColumns = ["collectionId"],
    onDelete = ForeignKey.CASCADE
)], indices = [Index(value = ["collectionId", "termKey", "createdAt"])])
data class TermDiscoveryEntity(
    @PrimaryKey val id: String, val collectionId: String, val termKey: String, val name: String,
    val kind: String, val meaning: String, val chapterId: String, val chapterTitle: String,
    val fingerprint: String, val paragraphId: Int, val evidence: String, val provider: String,
    val model: String, val createdAt: Long
)

data class TermCollectionRow(
    @androidx.room.Embedded val collection: TermCollectionEntity,
    val termCount: Int
)

data class StudyChapterKey(val source: String, val novelId: String, val chapterId: String)

data class ChapterNotePreview(val id: String, val title: String, val preview: String, val updatedAt: Long)
