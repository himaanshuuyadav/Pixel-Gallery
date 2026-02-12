package com.prantiux.pixelgallery.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FavoriteEntity::class, MediaLabelEntity::class, MediaEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun mediaLabelDao(): MediaLabelDao
    abstract fun mediaDao(): MediaDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create media_labels table for ML-based search
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `media_labels` (
                        `mediaId` INTEGER NOT NULL PRIMARY KEY,
                        `labels` TEXT NOT NULL,
                        `processedTimestamp` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
        
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add labelsWithConfidence column for confidence-based filtering
                db.execSQL("""
                    ALTER TABLE `media_labels` 
                    ADD COLUMN `labelsWithConfidence` TEXT NOT NULL DEFAULT ''
                """.trimIndent())
            }
        }
        
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create media_cache table for instant startup
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `media_cache` (
                        `id` INTEGER NOT NULL PRIMARY KEY,
                        `uri` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `dateAdded` INTEGER NOT NULL,
                        `size` INTEGER NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `bucketId` TEXT,
                        `bucketName` TEXT,
                        `isVideo` INTEGER NOT NULL,
                        `duration` INTEGER,
                        `width` INTEGER,
                        `height` INTEGER,
                        `path` TEXT NOT NULL
                    )
                """.trimIndent())
                
                // Create index on dateAdded for fast sorting
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_media_cache_dateAdded` 
                    ON `media_cache` (`dateAdded` DESC)
                """.trimIndent())
            }
        }
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pixel_gallery_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
