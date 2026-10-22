package com.novelnest.app.data.remote

import androidx.sqlite.db.SupportSQLiteDatabase
import com.novelnest.app.data.database.migration.DatabaseMigrations
import com.novelnest.app.data.database.mapper.toDomain
import com.novelnest.app.data.database.mapper.toEntity
import com.novelnest.app.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class ChapterCountPersistenceTest {
    @Test fun roomRoundTripKeepsTitleAndCountSeparate() {
        val novel = Novel("np-book", "Book", "", NovelSourceType.NOVEL_PHOENIX, "",
            latestChapter = "Chapter 100: Last Stand", chapterCount = 100)
        assertEquals(novel, novel.toEntity().toDomain())
    }
    @Test fun upgradeAddsTotalWithSafeDefaultWithoutDeletingExistingData() {
        val sql = mutableListOf<String>()
        val db = Proxy.newProxyInstance(SupportSQLiteDatabase::class.java.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
            if (method.name == "execSQL") { sql += args[0] as String; null } else error(method.name)
        } as SupportSQLiteDatabase
        DatabaseMigrations.MIGRATION_2_3.migrate(db)
        assertEquals(listOf("ALTER TABLE novels ADD COLUMN chapterCount INTEGER NOT NULL DEFAULT 0"), sql)
        assertTrue(DatabaseMigrations.ALL_MIGRATIONS.contains(DatabaseMigrations.MIGRATION_2_3))
    }
}
