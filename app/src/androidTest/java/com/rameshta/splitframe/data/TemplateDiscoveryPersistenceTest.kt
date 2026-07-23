package com.rameshta.splitframe.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.splitframe.data.local.SplitFrameDatabase
import com.rameshta.splitframe.domain.ExportContentMode
import com.rameshta.splitframe.domain.SingleImageExportSettings
import com.rameshta.splitframe.domain.SingleImageOutputFormat
import com.rameshta.splitframe.domain.SingleImageResizePreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemplateDiscoveryPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: SplitFrameDatabase? = null
    private var now = 1_000L

    @Before
    fun removeDatabase() {
        context.deleteDatabase(DatabaseName)
    }

    @After
    fun closeAndRemoveDatabase() {
        database?.close()
        context.deleteDatabase(DatabaseName)
    }

    @Test
    fun favoritesAndBoundedRecentLayoutsSurviveDatabaseReopen() = runBlocking {
        var opened = openDatabase()
        var store = projectStore(opened)
        store.setTemplateFavorite("side_by_side", favorite = true)
        now += 1L
        store.setTemplateFavorite("side_by_side", favorite = true)

        repeat(25) { index ->
            now += 1L
            store.recordRecentLayout("layout-$index")
        }
        now += 1L
        store.recordRecentLayout("layout-5")

        assertEquals(listOf("side_by_side"), store.observeFavoriteTemplates().first())
        val beforeReopen = store.observeRecentLayouts().first()
        assertEquals(20, beforeReopen.size)
        assertEquals("layout-5", beforeReopen.first())
        assertEquals(beforeReopen.size, beforeReopen.distinct().size)

        opened.close()
        database = null
        opened = openDatabase()
        store = projectStore(opened)

        assertEquals(listOf("side_by_side"), store.observeFavoriteTemplates().first())
        assertEquals(beforeReopen, store.observeRecentLayouts().first())

        store.setTemplateFavorite("side_by_side", favorite = false)
        assertTrue(store.observeFavoriteTemplates().first().isEmpty())
    }

    @Test
    fun singleImageExportSettingsSurviveDatabaseReopen() = runBlocking {
        val expected = SingleImageExportSettings(
            preset = SingleImageResizePreset.InstagramPortraitPost,
            outputFormat = SingleImageOutputFormat.Png,
            encodingQuality = 91,
            customWidthPx = 1777,
            customHeightPx = 2222,
            lockAspectRatio = false,
            contentMode = ExportContentMode.Fill,
        )
        var opened = openDatabase()
        projectStore(opened).setSingleImageExportSettings(expected)

        opened.close()
        database = null
        opened = openDatabase()

        assertEquals(expected, projectStore(opened).getSingleImageExportSettings())
    }

    private fun openDatabase(): SplitFrameDatabase =
        Room.databaseBuilder(context, SplitFrameDatabase::class.java, DatabaseName)
            .build()
            .also { database = it }

    private fun projectStore(opened: SplitFrameDatabase): ProjectStore =
        ProjectStore(
            preferenceDao = opened.preferenceDao(),
            exportHistoryDao = opened.exportHistoryDao(),
            favoriteTemplateDao = opened.favoriteTemplateDao(),
            recentLayoutDao = opened.recentLayoutDao(),
            clock = { now },
        )

    private companion object {
        const val DatabaseName = "template-discovery-test.db"
    }
}
