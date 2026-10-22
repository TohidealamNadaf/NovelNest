package com.novelnest.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.novelnest.app.data.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {

    @Query("SELECT * FROM reading_progress WHERE novelId = :novelId")
    fun getProgress(novelId: String): Flow<ReadingProgressEntity?>

    @Query("SELECT * FROM reading_progress WHERE novelId = :novelId")
    suspend fun getProgressOnce(novelId: String): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress ORDER BY lastReadTimestamp DESC")
    fun getAllProgress(): Flow<List<ReadingProgressEntity>>

    @Query("SELECT * FROM reading_progress ORDER BY lastReadTimestamp DESC")
    suspend fun getAllProgressOnce(): List<ReadingProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(progress: ReadingProgressEntity)

    @Query("DELETE FROM reading_progress WHERE novelId = :novelId")
    suspend fun deleteProgress(novelId: String)
}
