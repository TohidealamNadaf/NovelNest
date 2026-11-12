package com.novelnest.app.data.local.epub

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_books", indices = [Index(value = ["sha256"], unique = true)])
data class LocalBookEntity(
    @PrimaryKey val id: String,
    val sha256: String,
    val title: String,
    val author: String,
    val description: String,
    val importedAt: Long,
    val byteCount: Long
)

@Entity(tableName = "epub_positions", foreignKeys = [ForeignKey(
    entity = LocalBookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)])
data class EpubPositionEntity(
    @PrimaryKey val bookId: String,
    val locatorJson: String,
    val progression: Double,
    val chapterTitle: String,
    val updatedAt: Long
)

data class LocalBookEntry(@Embedded val book: LocalBookEntity,
    @Relation(parentColumn = "id", entityColumn = "bookId") val position: EpubPositionEntity?)

@Dao
interface LocalBookDao {
    @Transaction @Query("SELECT id, sha256, title, author, '' AS description, importedAt, byteCount FROM local_books ORDER BY importedAt DESC")
    fun observeLibrary(): Flow<List<LocalBookEntry>>
    @Query("SELECT * FROM local_books WHERE id=:id") suspend fun book(id: String): LocalBookEntity?
    @Query("SELECT * FROM epub_positions WHERE bookId=:id") suspend fun position(id: String): EpubPositionEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(book: LocalBookEntity): Long
    @Upsert suspend fun savePosition(position: EpubPositionEntity)
    @Query("DELETE FROM local_books WHERE id=:id") suspend fun delete(id: String)
    @Query("SELECT id FROM local_books") suspend fun ids(): List<String>
}
