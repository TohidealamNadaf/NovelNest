package com.novelnest.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "novels",
    indices = [
        Index(value = ["source", "title"]),
        Index(value = ["sourceUrl"])
    ]
)
data class NovelEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val coverUrl: String,
    val source: String,
    val sourceUrl: String,
    val synopsis: String = "",
    val author: String = "Unknown",
    val rating: Float = 4.5f,
    val latestChapter: String = "Chapter 1",
    val genres: List<String> = emptyList(),
    val status: String = "Ongoing",
    val views: String = "",
    val rank: Int = 0,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
