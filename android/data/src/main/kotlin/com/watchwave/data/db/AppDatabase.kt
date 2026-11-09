package com.watchwave.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VideoMetadataEntity::class,
        RecentMediaEntity::class,
        RoomHistoryEntity::class,
        HostConfigurationEntity::class,
        TunnelConfigurationEntity::class,
        ApplicationSettingsEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun recentMediaDao(): RecentMediaDao
    abstract fun roomHistoryDao(): RoomHistoryDao
    abstract fun configurationDao(): ConfigurationDao

    companion object {
        const val VERSION = 2

        /** Adds an optional free-form note to room history (v1 -> v2). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE room_history ADD COLUMN notes TEXT")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "watchwave.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
