package com.novelnest.app.data.model

enum class SectionType {
    HERO_SLIDER,
    HORIZONTAL_LIST,
    GRID
}

data class NovelSection(
    val id: String,
    val title: String,
    val iconEmoji: String = "",
    val subtitle: String? = null,
    val type: SectionType = SectionType.HORIZONTAL_LIST,
    val novels: List<Novel>
)
