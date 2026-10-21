package com.novelnest.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.novelnest.app.data.model.SectionType

@Entity(
    tableName = "sections",
    indices = [Index(value = ["source", "displayOrder"])]
)
data class SectionEntity(
    @PrimaryKey
    val sectionId: String,
    val source: String,
    val title: String,
    val iconEmoji: String = "",
    val subtitle: String? = null,
    val sectionType: String = SectionType.HORIZONTAL_LIST.name,
    val displayOrder: Int = 0,
    val lastFetchedTimestamp: Long = System.currentTimeMillis()
)
