package com.novelnest.app.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Centralized database migrations for NovelNest.
 * Ensures schema version transitions are safe, tested, and tracked.
 */
object DatabaseMigrations {

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        // Future schema migrations will be registered here, e.g.:
        // MIGRATION_1_2
    )

    // Example migration template for future release:
    // val MIGRATION_1_2 = object : Migration(1, 2) {
    //     override fun migrate(db: SupportSQLiteDatabase) {
    //         db.execSQL("ALTER TABLE novels ADD COLUMN bookmarkTimestamp INTEGER DEFAULT 0 NOT NULL")
    //     }
    // }
}
