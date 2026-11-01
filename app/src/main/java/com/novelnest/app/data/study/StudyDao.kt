package com.novelnest.app.data.study

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    @Query("SELECT id, title, SUBSTR(markdown, 1, 240) AS preview, updatedAt FROM chapter_notes WHERE source=:source AND novelId=:novel ORDER BY updatedAt DESC, id")
    fun notes(source: String, novel: String): Flow<List<ChapterNotePreview>>
    @Query("SELECT COUNT(*) FROM chapter_notes WHERE source=:source AND novelId=:novel AND chapterId=:chapter")
    suspend fun noteCount(source: String, novel: String, chapter: String): Int
    @Query("SELECT * FROM chapter_notes WHERE id=:id")
    suspend fun note(id: String): ChapterNoteEntity?
    @Upsert suspend fun saveNote(note: ChapterNoteEntity)
    @Query("DELETE FROM chapter_notes WHERE id=:id AND source=:source AND novelId=:novel")
    suspend fun deleteNote(id: String, source: String, novel: String)

    @Query("SELECT c.*, (SELECT COUNT(DISTINCT termKey) FROM term_discoveries WHERE collectionId=c.id) AS termCount FROM term_collections c WHERE source=:source AND novelId=:novel ORDER BY updatedAt DESC, id")
    fun collections(source: String, novel: String): Flow<List<TermCollectionRow>>
    @Query("SELECT * FROM term_collections WHERE id=:id")
    suspend fun collection(id: String): TermCollectionEntity?
    @Query("SELECT COUNT(*) FROM term_collections WHERE source=:source AND novelId=:novel")
    suspend fun collectionCount(source: String, novel: String): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCollection(collection: TermCollectionEntity): Long
    @Update suspend fun updateCollection(collection: TermCollectionEntity)
    @Query("DELETE FROM term_collections WHERE id=:id AND source=:source AND novelId=:novel")
    suspend fun deleteCollection(id: String, source: String, novel: String)
    @Query("SELECT * FROM term_discoveries WHERE collectionId=:id ORDER BY termKey, createdAt, id")
    fun discoveries(id: String): Flow<List<TermDiscoveryEntity>>
    @Query("SELECT id FROM term_discoveries WHERE collectionId=:id")
    suspend fun discoveryIds(id: String): List<String>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDiscoveries(rows: List<TermDiscoveryEntity>)
}
