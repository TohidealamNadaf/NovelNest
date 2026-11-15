package com.novelnest.app.data.local.epub

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_books",
    indices = [Index(value = ["sha256"], unique = true), Index(value = ["collectionId"])],
    foreignKeys = [ForeignKey(entity = EpubCollectionEntity::class, parentColumns = ["id"],
        childColumns = ["collectionId"], onDelete = ForeignKey.SET_NULL)])
data class LocalBookEntity(
    @PrimaryKey val id: String,
    val sha256: String,
    val title: String,
    val author: String,
    val description: String,
    val importedAt: Long,
    val byteCount: Long,
    val collectionId: String? = null,
    val volumeLabel: String? = null,
    @ColumnInfo(defaultValue = "0") val volumeOrder: Long = 0
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

data class EpubPositionPreview(val bookId: String, val progression: Double, val chapterTitle: String, val updatedAt: Long)

internal fun EpubPositionEntity.preview() = EpubPositionPreview(bookId, progression, chapterTitle, updatedAt)

data class LocalBookEntry(@Embedded val book: LocalBookEntity,
    @Relation(parentColumn = "id", entityColumn = "bookId", entity = EpubPositionEntity::class,
        projection = ["bookId", "progression", "chapterTitle", "updatedAt"])
    val position: EpubPositionPreview?)

@Dao
interface LocalBookDao {
    @Transaction @Query("SELECT id, sha256, title, author, '' AS description, importedAt, byteCount, collectionId, volumeLabel, volumeOrder FROM local_books ORDER BY importedAt DESC")
    fun observeLibrary(): Flow<List<LocalBookEntry>>
    @Query("SELECT * FROM local_books WHERE id=:id") suspend fun book(id: String): LocalBookEntity?
    @Query("SELECT * FROM epub_positions WHERE bookId=:id") suspend fun position(id: String): EpubPositionEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(book: LocalBookEntity): Long
    @Upsert suspend fun savePosition(position: EpubPositionEntity)
    @Query("DELETE FROM local_books WHERE id=:id") suspend fun delete(id: String)
    @Query("SELECT id FROM local_books") suspend fun ids(): List<String>

    // The value is only an invalidation signal. Do not distinctUntilChanged this flow:
    // position/order edits must refresh the snapshot even when all row counts stay equal.
    @Query("SELECT (SELECT COUNT(*) FROM local_books) + (SELECT COUNT(*) FROM epub_positions) + (SELECT COUNT(*) FROM epub_collections)")
    fun observeShelfChanges(): Flow<Long>
    @Transaction @Query("SELECT id, sha256, title, author, '' AS description, importedAt, byteCount, collectionId, volumeLabel, volumeOrder FROM local_books")
    suspend fun libraryEntries(): List<LocalBookEntry>
    @Query("SELECT * FROM epub_collections ORDER BY createdAt, id")
    suspend fun collections(): List<EpubCollectionEntity>
    @Query("SELECT * FROM epub_collections WHERE id=:id")
    suspend fun collection(id: String): EpubCollectionEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCollection(collection: EpubCollectionEntity)
    @Query("UPDATE epub_collections SET name=:name, nameKey=:nameKey WHERE id=:id")
    suspend fun renameCollection(id: String, name: String, nameKey: String): Int
    @Query("DELETE FROM epub_collections WHERE id=:id")
    suspend fun deleteCollection(id: String)
    @Query("SELECT id FROM local_books WHERE collectionId=:id ORDER BY volumeOrder, importedAt, id")
    suspend fun collectionBookIds(id: String): List<String>
    @Query("SELECT COALESCE(MAX(volumeOrder), -1) FROM local_books WHERE collectionId=:id")
    suspend fun lastVolumeOrder(id: String): Long
    @Query("UPDATE local_books SET collectionId=:collectionId, volumeOrder=:order WHERE id=:id")
    suspend fun moveBook(id: String, collectionId: String?, order: Long): Int
    @Query("UPDATE local_books SET volumeLabel=:label WHERE id=:id")
    suspend fun labelVolume(id: String, label: String?): Int

    @Transaction suspend fun librarySnapshot() = EpubLibrarySnapshot(libraryEntries(), collections(), loaded = true)

    @Transaction suspend fun insertOrganized(book: LocalBookEntity, destination: String?): LocalBookEntity {
        val target = destination?.takeIf { collection(it) != null }
        val order = if (target == null) 0 else Math.addExact(lastVolumeOrder(target), 1)
        val saved = book.copy(collectionId = target, volumeOrder = order)
        check(insert(saved) != -1L) { "The imported book could not be recorded." }
        return saved
    }

    @Transaction suspend fun organizeBook(id: String, destination: String?) {
        val current = book(id) ?: error("This EPUB is no longer in the library.")
        require(destination == null || collection(destination) != null) { "This series no longer exists." }
        if (current.collectionId == destination) return
        val order = if (destination == null) 0 else Math.addExact(lastVolumeOrder(destination), 1)
        check(moveBook(id, destination, order) == 1) { "This EPUB is no longer in the library." }
    }

    /** Validate the entire selection before writing; a stale selection must never move a subset. */
    @Transaction suspend fun organizeBooks(ids: List<String>, destination: String?) {
        require(ids.isNotEmpty() && ids.distinct().size == ids.size) { "Select EPUBs to move." }
        require(destination == null || collection(destination) != null) { "This series no longer exists." }
        ids.forEach { require(book(it) != null) { "The selection changed. Select the EPUBs again." } }
        ids.forEach { organizeBook(it, destination) }
    }

    /** Removing from a series changes membership only, never publication/position rows. */
    @Transaction suspend fun detachBooks(ids: List<String>, sourceCollection: String) {
        require(ids.isNotEmpty() && ids.distinct().size == ids.size) { "Select EPUBs to remove from the series." }
        require(collection(sourceCollection) != null) { "This series no longer exists." }
        ids.forEach { id ->
            require(book(id)?.collectionId == sourceCollection) {
                "The series changed. Select its EPUBs again."
            }
        }
        ids.forEach { id -> check(moveBook(id, null, 0) == 1) { "The series changed. Select its EPUBs again." } }
    }

    @Transaction suspend fun removeShelfItems(bookIds: List<String>, collectionIds: List<String>) {
        require(bookIds.isNotEmpty() || collectionIds.isNotEmpty()) { "Select library items to remove." }
        bookIds.forEach { require(book(it) != null) { "The selection changed. Select the EPUBs again." } }
        collectionIds.forEach { require(collection(it) != null) { "The selection changed. Select the series again." } }
        bookIds.forEach { delete(it) }
        collectionIds.forEach { deleteCollection(it) } // SET NULL preserves unselected volumes.
    }

    @Transaction suspend fun reorderVolumes(id: String, orderedIds: List<String>) {
        require(collection(id) != null) { "This series no longer exists." }
        val existing = collectionBookIds(id)
        require(orderedIds.size == existing.size && orderedIds.toSet() == existing.toSet()) {
            "The series changed. Please reopen its volume order and try again."
        }
        orderedIds.forEachIndexed { index, bookId -> moveBook(bookId, id, index.toLong()) }
    }
}
