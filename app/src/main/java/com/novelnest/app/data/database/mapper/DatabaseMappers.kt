package com.novelnest.app.data.database.mapper

import com.novelnest.app.data.database.entity.NovelEntity
import com.novelnest.app.data.database.entity.SectionEntity
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.model.SectionType

/**
 * Extension mapper functions to convert between Room database entities and clean domain models.
 */
fun NovelEntity.toDomain(): Novel {
    return Novel(
        id = id,
        title = title,
        coverUrl = coverUrl,
        source = NovelSourceType.entries.firstOrNull { it.name == source } ?: NovelSourceType.NOVEL_PHOENIX,
        sourceUrl = sourceUrl,
        synopsis = synopsis,
        author = author,
        rating = rating,
        latestChapter = latestChapter,
        chapterCount = chapterCount,
        genres = genres,
        status = status,
        views = views,
        rank = rank
    )
}

fun Novel.toEntity(): NovelEntity {
    return NovelEntity(
        id = id,
        title = title,
        coverUrl = coverUrl,
        source = source.name,
        sourceUrl = sourceUrl,
        synopsis = synopsis,
        author = author,
        rating = rating,
        latestChapter = latestChapter,
        chapterCount = chapterCount,
        genres = genres,
        status = status,
        views = views,
        rank = rank,
        lastUpdatedTimestamp = System.currentTimeMillis()
    )
}

fun SectionEntity.toDomain(novels: List<Novel> = emptyList()): NovelSection {
    return NovelSection(
        id = sectionId,
        title = title,
        iconEmoji = iconEmoji,
        subtitle = subtitle,
        type = runCatching { SectionType.valueOf(sectionType) }.getOrDefault(SectionType.HORIZONTAL_LIST),
        novels = novels
    )
}

fun NovelSection.toEntity(source: NovelSourceType, displayOrder: Int = 0): SectionEntity {
    return SectionEntity(
        sectionId = id,
        source = source.name,
        title = title,
        iconEmoji = iconEmoji,
        subtitle = subtitle,
        sectionType = type.name,
        displayOrder = displayOrder,
        lastFetchedTimestamp = System.currentTimeMillis()
    )
}
