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

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3
    )
}
