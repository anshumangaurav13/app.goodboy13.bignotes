package com.antigrav.bignotes.core.search.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HandwritingSearchEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SearchDatabase : RoomDatabase() {

    abstract fun handwritingSearchDao(): HandwritingSearchDao

    companion object {
        @Volatile
        private var INSTANCE: SearchDatabase? = null

        fun getInstance(context: Context): SearchDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SearchDatabase::class.java,
                    "bignotes_search.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
