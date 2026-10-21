package com.novelnest.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SectionDao {

    @Query("SELECT * FROM sections WHERE source = :source ORDER BY displayOrder ASC")
    fun getSectionsForSource(source: String): Flow<List<SectionEntity>>

    @Query("""
        SELECT n.* FROM novels n
        INNER JOIN section_novel_cross_ref ref ON n.id = ref.novelId
        WHERE ref.sectionId = :sectionId
        ORDER BY ref.positionIndex ASC
    """)
    fun getNovelsForSection(sectionId: String): Flow<List<NovelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<SectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(crossRefs: List<SectionNovelCrossRef>)

    @Query("DELETE FROM section_novel_cross_ref WHERE sectionId = :sectionId")
    suspend fun clearCrossRefsForSection(sectionId: String)

    @Transaction
    suspend fun saveSectionWithNovels(
        section: SectionEntity,
        novels: List<NovelEntity>
    ) {
        insertSections(listOf(section))
        clearCrossRefsForSection(section.sectionId)
        val refs = novels.mapIndexed { index, novel ->
            SectionNovelCrossRef(
                sectionId = section.sectionId,
                novelId = novel.id,
                positionIndex = index
            )
        }
        insertCrossRefs(refs)
    }
}
