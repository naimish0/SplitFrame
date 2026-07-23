package com.rameshta.splitframe.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.splitframe.data.local.SplitFrameDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentProjectMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: SplitFrameDatabase? = null

    @Before
    fun removeOldDatabase() {
        context.deleteDatabase(DatabaseName)
    }

    @After
    fun closeAndRemoveDatabase() {
        database?.close()
        context.deleteDatabase(DatabaseName)
    }

    @Test
    fun migrationFromVersion4BackfillsProjectAndRecentLayoutMetadata() = runBlocking {
        createVersion4Database()

        val opened = Room.databaseBuilder(context, SplitFrameDatabase::class.java, DatabaseName)
            .addMigrations(
                SplitFrameDatabase.Migration4To5,
                SplitFrameDatabase.Migration5To6,
            )
            .build()
        database = opened

        val metadata = opened.recentProjectDao().get(ProjectId)
        val project = opened.videoProjectDao().get(ProjectId)

        assertNotNull(project)
        assertEquals("Video project", metadata?.name)
        assertEquals("VIDEO", metadata?.projectType)
        assertEquals(UpdatedAt, metadata?.createdAtMillis)
        assertEquals(UpdatedAt, metadata?.updatedAtMillis)
        assertEquals(
            listOf("top_bottom", "side_by_side"),
            opened.recentLayoutDao().observeRecent().first().map { it.templateId },
        )
    }

    private fun createVersion4Database() {
        val callback = object : SupportSQLiteOpenHelper.Callback(4) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE `preferences` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
                db.execSQL(
                    """
                    CREATE TABLE `export_history` (
                        `id` TEXT NOT NULL, `templateId` TEXT NOT NULL, `savedUri` TEXT NOT NULL,
                        `resolution` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `export_history`
                        (`id`, `templateId`, `savedUri`, `resolution`, `createdAtMillis`)
                    VALUES
                        ('export-1', 'side_by_side', 'content://exports/1', 'FHD_1080', 100),
                        ('export-2', 'side_by_side', 'content://exports/2', 'FHD_1080', 150),
                        ('export-3', 'top_bottom', 'content://exports/3', 'FHD_1080', 200)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE `favorite_templates` (
                        `templateId` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`templateId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE `video_projects` (
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
                        `templateId` TEXT,
                        `selectedCellIndex` INTEGER,
                        `primaryAudioMediaId` TEXT,
                        `mediaItems` TEXT,
                        `mergeMode` TEXT NOT NULL DEFAULT 'SEQUENCE',
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE `video_export_work` (
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
                db.execSQL(
                    """
                    INSERT INTO `video_projects` (
                        `id`, `layout`, `canvasAspectRatio`, `exportResolution`, `primaryAudioSource`,
                        `durationMode`, `spacingDp`, `cornerRadiusDp`, `backgroundColor`, `updatedAtMillis`,
                        `clip0`, `clip1`, `templateId`, `selectedCellIndex`, `primaryAudioMediaId`,
                        `mediaItems`, `mergeMode`
                    ) VALUES (?, 'SIDE_BY_SIDE', 'RATIO_16_9', 'FHD_1080', 'NONE', 'LOOP_SHORTER',
                        0, 0, -16311802, ?, NULL, NULL, 'side_by_side', 0, NULL, '', 'SEQUENCE')
                    """.trimIndent(),
                    arrayOf<Any>(ProjectId, UpdatedAt),
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DatabaseName)
            .callback(callback)
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            helper.writableDatabase
        }
    }

    private companion object {
        const val DatabaseName = "splitframe-migration-test.db"
        const val ProjectId = "99999999-9999-4999-8999-999999999999"
        const val UpdatedAt = 123_456L
    }
}
