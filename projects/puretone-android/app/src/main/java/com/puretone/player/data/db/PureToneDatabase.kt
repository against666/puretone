package com.puretone.player.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Track::class, DownloadTask::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PureToneDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao

    abstract fun downloadTaskDao(): DownloadTaskDao

    companion object {
        private const val DB_NAME = "puretone.db"

        @Volatile
        private var instance: PureToneDatabase? = null

        fun get(context: Context): PureToneDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PureToneDatabase::class.java,
                    DB_NAME,
                ).build().also { instance = it }
            }
    }
}
