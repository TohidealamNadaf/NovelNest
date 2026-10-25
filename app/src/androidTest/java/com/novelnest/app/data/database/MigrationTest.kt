package com.novelnest.app.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import com.novelnest.app.data.database.migration.DatabaseMigrations
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NovelNestDatabase::class.java
    )

    @Test
    fun openAndValidateCurrentSchemaVersion1() {
        // Validates that version 1 schema opens, builds tables, and matches the exported schema JSON
        val db = helper.createDatabase(testDb, 1)
        db.close()
    }

    @Test
    fun migrateExistingLibraryFrom3To4WithoutLosingReadingProgress() {
        helper.createDatabase("study-migration", 3).apply {
            execSQL("INSERT INTO reading_progress VALUES ('kept-book', 'chapter-87', 86, 'Chapter 87', 123, '[]')")
            close()
        }
        helper.runMigrationsAndValidate("study-migration", 4, true, DatabaseMigrations.MIGRATION_3_4).apply {
            query("SELECT lastReadChapterId FROM reading_progress WHERE novelId='kept-book'").use {
                it.moveToFirst(); assertEquals("chapter-87", it.getString(0))
            }
            // User notes do not require a cached novels row or a bookmark.
            execSQL("INSERT INTO chapter_notes VALUES ('note', 'NOVEL_PHOENIX', 'uncached-book', 'chapter', 'Title', '**Saved**', 123)")
            close()
        }
        helper.runMigrationsAndValidate("study-migration", 4, true).apply {
            query("SELECT markdown FROM chapter_notes WHERE id='note'").use {
                it.moveToFirst(); assertEquals("**Saved**", it.getString(0))
            }
            close()
        }
    }

    @Test
    fun migrateOldestSupportedDatabaseToCurrentSchema() {
        helper.createDatabase("study-migration-all", 1).close()
        helper.runMigrationsAndValidate("study-migration-all", 4, true, *DatabaseMigrations.ALL_MIGRATIONS).close()
    }
}
