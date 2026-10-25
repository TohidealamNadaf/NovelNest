package com.novelnest.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.novelnest.app.data.database.dao.NovelDao
import com.novelnest.app.data.database.dao.SectionDao
import com.novelnest.app.data.database.entity.NovelEntity
import com.novelnest.app.data.database.entity.SectionEntity
import com.novelnest.app.data.database.entity.SectionNovelCrossRef
import com.novelnest.app.data.database.migration.DatabaseMigrations

@Database(
    entities = [
        NovelEntity::class,
        SectionEntity::class,
        SectionNovelCrossRef::class,
        com.novelnest.app.data.database.entity.ReadingProgressEntity::class,
        com.novelnest.app.data.study.ChapterNoteEntity::class,
        com.novelnest.app.data.study.TermCollectionEntity::class,
        com.novelnest.app.data.study.TermDiscoveryEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NovelNestDatabase : RoomDatabase() {

    abstract fun novelDao(): NovelDao
    abstract fun sectionDao(): SectionDao
    abstract fun readingProgressDao(): com.novelnest.app.data.database.dao.ReadingProgressDao
    abstract fun studyDao(): com.novelnest.app.data.study.StudyDao

    companion object {
        @Volatile
        private var INSTANCE: NovelNestDatabase? = null

        fun getInstance(context: Context): NovelNestDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NovelNestDatabase::class.java,
                    "novelnest_cache.db"
                )
                    .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                    // Notes and extracted terms are user data: unknown downgrades must never erase them.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
