package com.novelnest.app.data.database.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey
    val novelId: String,
    val lastReadChapterId: String,
    val lastReadChapterIndex: Int,
    val lastReadChapterTitle: String,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val readChapterIds: List<String> = emptyList(),
    @ColumnInfo(defaultValue = "-1") val manhwaPageIndex: Int = -1,
    @ColumnInfo(defaultValue = "0") val manhwaPageOffset: Float = 0f,
    @ColumnInfo(defaultValue = "0") val manhwaPageCount: Int = 0
)
