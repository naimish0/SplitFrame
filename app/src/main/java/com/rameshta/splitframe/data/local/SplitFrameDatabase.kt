package com.rameshta.splitframe.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PreferenceEntity::class,
        ExportHistoryEntity::class,
        FavoriteTemplateEntity::class,
        RecentLayoutEntity::class,
        VideoProjectEntity::class,
        VideoExportWorkEntity::class,
        RecentProjectEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class SplitFrameDatabase : RoomDatabase() {
    abstract fun preferenceDao(): PreferenceDao
    abstract fun exportHistoryDao(): ExportHistoryDao
    abstract fun favoriteTemplateDao(): FavoriteTemplateDao
    abstract fun recentLayoutDao(): RecentLayoutDao
    abstract fun videoProjectDao(): VideoProjectDao
    abstract fun videoExportWorkDao(): VideoExportWorkDao
    abstract fun recentProjectDao(): RecentProjectDao

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

        val Migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recent_projects` (
                        `projectId` TEXT NOT NULL,
                        `projectType` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `projectFormatVersion` INTEGER NOT NULL,
                        `layoutVersion` INTEGER NOT NULL,
                        `thumbnailUri` TEXT,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        `deletedAtMillis` INTEGER,
                        `deletionToken` TEXT,
                        PRIMARY KEY(`projectId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_recent_projects_deletedAtMillis_updatedAtMillis`
                    ON `recent_projects` (`deletedAtMillis`, `updatedAtMillis`)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `recent_projects` (
                        `projectId`, `projectType`, `name`, `projectFormatVersion`, `layoutVersion`,
                        `thumbnailUri`, `createdAtMillis`, `updatedAtMillis`, `deletedAtMillis`, `deletionToken`
                    )
                    SELECT `id`, 'VIDEO', 'Video project', 1, 1, NULL,
                        `updatedAtMillis`, `updatedAtMillis`, NULL, NULL
                    FROM `video_projects`
                    """.trimIndent(),
                )
            }
        }

        val Migration5To6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recent_layouts` (
                        `templateId` TEXT NOT NULL,
                        `usedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`templateId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `recent_layouts` (`templateId`, `usedAtMillis`)
                    SELECT `templateId`, MAX(`createdAtMillis`)
                    FROM `export_history`
                    GROUP BY `templateId`
                    ORDER BY MAX(`createdAtMillis`) DESC, `templateId` ASC
                    LIMIT 20
                    """.trimIndent(),
                )
            }
        }
    }
}
