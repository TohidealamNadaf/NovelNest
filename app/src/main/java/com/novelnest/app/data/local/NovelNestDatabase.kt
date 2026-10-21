package com.novelnest.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
