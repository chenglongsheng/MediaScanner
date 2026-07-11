package com.txzing.media.scanner.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 媒体扫描器数据库
 */
@Database(
    entities = [
        MediaEntity::class,
        StorageEntity::class,
        ScanTaskEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MediaDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao

    companion object {
        private const val DATABASE_NAME = "media_scanner.db"

        @Volatile
        private var INSTANCE: MediaDatabase? = null

        /**
         * 获取数据库单例
         */
        fun getInstance(context: Context): MediaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): MediaDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MediaDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
