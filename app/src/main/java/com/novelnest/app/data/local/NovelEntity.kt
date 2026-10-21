package com.novelnest.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType

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
) {
    fun toDomainModel(): Novel {
        return Novel(
            id = id,
            title = title,
            coverUrl = coverUrl,
            source = NovelSourceType.entries.firstOrNull { it.name == source } ?: NovelSourceType.NOVEL_FIRE,
            sourceUrl = sourceUrl,
            synopsis = synopsis,
            author = author,
            rating = rating,
            latestChapter = latestChapter,
            genres = genres,
            status = status,
            views = views,
            rank = rank
        )
    }

    companion object {
        fun fromDomainModel(novel: Novel): NovelEntity {
            return NovelEntity(
                id = novel.id,
                title = novel.title,
                coverUrl = novel.coverUrl,
                source = novel.source.name,
                sourceUrl = novel.sourceUrl,
                synopsis = novel.synopsis,
                author = novel.author,
                rating = novel.rating,
                latestChapter = novel.latestChapter,
                genres = novel.genres,
                status = novel.status,
                views = novel.views,
                rank = novel.rank,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
        }
    }
}
