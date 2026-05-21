package com.downtify.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DownloadedTrackEntity::class, MonitoredPlaylistEntity::class, DownloadedVideoEntity::class],
    version = 3,
    exportSchema = false
)
abstract class DowntifyDatabase : RoomDatabase() {

    abstract fun downloadedTrackDao(): DownloadedTrackDao
    abstract fun monitoredPlaylistDao(): MonitoredPlaylistDao
    abstract fun downloadedVideoDao(): DownloadedVideoDao

    companion object {
        @Volatile
        private var INSTANCE: DowntifyDatabase? = null

        fun getDatabase(context: Context): DowntifyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DowntifyDatabase::class.java,
                    "downtify_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
