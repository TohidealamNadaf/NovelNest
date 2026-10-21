package com.novelnest.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNovels(novels: List<NovelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNovel(novel: NovelEntity)

    @Query("SELECT * FROM novels WHERE id = :id LIMIT 1")
    fun getNovelById(id: String): Flow<NovelEntity?>

    @Query("SELECT * FROM novels WHERE source = :source AND (title LIKE '%' || :query || '%' OR synopsis LIKE '%' || :query || '%')")
    fun searchNovels(source: String, query: String): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels WHERE source = :source")
    fun getNovelsBySource(source: String): Flow<List<NovelEntity>>
}
