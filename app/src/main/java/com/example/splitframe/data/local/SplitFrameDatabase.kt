package com.example.splitframe.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PreferenceEntity::class,
        ExportHistoryEntity::class,
        FavoriteTemplateEntity::class,
        VideoProjectEntity::class,
        VideoExportWorkEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class SplitFrameDatabase : RoomDatabase() {
    abstract fun preferenceDao(): PreferenceDao
    abstract fun exportHistoryDao(): ExportHistoryDao
    abstract fun favoriteTemplateDao(): FavoriteTemplateDao
    abstract fun videoProjectDao(): VideoProjectDao
    abstract fun videoExportWorkDao(): VideoExportWorkDao

    companion object {
        val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `video_projects` (
                        `id` TEXT NOT NULL,
                        `layout` TEXT NOT NULL,
                        `canvasAspectRatio` TEXT NOT NULL,
                        `exportResolution` TEXT NOT NULL,
                        `primaryAudioSource` TEXT NOT NULL,
                        `durationMode` TEXT NOT NULL,
                        `spacingDp` REAL NOT NULL,
                        `cornerRadiusDp` REAL NOT NULL,
                        `backgroundColor` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        `clip0` TEXT,
                        `clip1` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `video_export_work` (
                        `projectId` TEXT NOT NULL,
                        `workId` TEXT,
                        `state` TEXT NOT NULL,
                        `progress` REAL NOT NULL,
                        `outputUri` TEXT,
                        `errorMessage` TEXT,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`projectId`)
                    )
                    """.trimIndent(),
                )
            }
        }

        val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `video_projects` ADD COLUMN `templateId` TEXT")
                db.execSQL("ALTER TABLE `video_projects` ADD COLUMN `selectedCellIndex` INTEGER")
                db.execSQL("ALTER TABLE `video_projects` ADD COLUMN `primaryAudioMediaId` TEXT")
                db.execSQL("ALTER TABLE `video_projects` ADD COLUMN `mediaItems` TEXT")
            }
        }

        val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `video_projects` ADD COLUMN `mergeMode` TEXT NOT NULL DEFAULT 'SEQUENCE'")
            }
        }
    }
}
