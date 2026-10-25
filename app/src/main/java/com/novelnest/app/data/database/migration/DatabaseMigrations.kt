package com.novelnest.app.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Centralized database migrations for NovelNest.
 * Ensures schema version transitions are safe, tested, and tracked.
 */
object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reading_progress` (" +
                    "`novelId` TEXT NOT NULL, " +
                    "`lastReadChapterId` TEXT NOT NULL, " +
                    "`lastReadChapterIndex` INTEGER NOT NULL, " +
                    "`lastReadChapterTitle` TEXT NOT NULL, " +
                    "`lastReadTimestamp` INTEGER NOT NULL, " +
                    "`readChapterIds` TEXT NOT NULL, " +
                    "PRIMARY KEY(`novelId`)" +
                ")"
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE novels ADD COLUMN chapterCount INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `chapter_notes` (`id` TEXT NOT NULL, `source` TEXT NOT NULL, `novelId` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `title` TEXT NOT NULL, `markdown` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapter_notes_source_novelId_chapterId` ON `chapter_notes` (`source`, `novelId`, `chapterId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `term_collections` (`id` TEXT NOT NULL, `source` TEXT NOT NULL, `novelId` TEXT NOT NULL, `name` TEXT NOT NULL, `nameKey` TEXT NOT NULL, `target` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `lastChapter` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_term_collections_source_novelId_nameKey` ON `term_collections` (`source`, `novelId`, `nameKey`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `term_discoveries` (`id` TEXT NOT NULL, `collectionId` TEXT NOT NULL, `termKey` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `meaning` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `chapterTitle` TEXT NOT NULL, `fingerprint` TEXT NOT NULL, `paragraphId` INTEGER NOT NULL, `evidence` TEXT NOT NULL, `provider` TEXT NOT NULL, `model` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`collectionId`) REFERENCES `term_collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_term_discoveries_collectionId_termKey_createdAt` ON `term_discoveries` (`collectionId`, `termKey`, `createdAt`)")
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4
    )
}
