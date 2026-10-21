package com.novelnest.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "section_novel_cross_ref",
    primaryKeys = ["sectionId", "novelId"],
    indices = [
        Index(value = ["novelId"]),
        Index(value = ["sectionId"])
    ]
)
data class SectionNovelCrossRef(
    val sectionId: String,
    val novelId: String,
    val positionIndex: Int = 0
)
