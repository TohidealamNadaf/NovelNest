package com.novelnest.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey
    val novelId: String,
    val lastReadChapterId: String,
    val lastReadChapterIndex: Int,
    val lastReadChapterTitle: String,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val readChapterIds: List<String> = emptyList()
)
