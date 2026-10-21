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
        SectionNovelCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class NovelNestDatabase : RoomDatabase() {

    abstract fun novelDao(): NovelDao
    abstract fun sectionDao(): SectionDao

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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
