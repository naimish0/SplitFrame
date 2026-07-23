package com.rameshta.splitframe.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.splitframe.domain.MediaSource
import com.rameshta.splitframe.domain.OutputSize
import com.rameshta.splitframe.domain.VideoCanvasAspectRatio
import com.rameshta.splitframe.domain.VideoClip
import com.rameshta.splitframe.domain.VideoLayoutMath
import com.rameshta.splitframe.domain.VideoMergeProject
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportRecoveryDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resolver = context.contentResolver

    @Test
    fun previousProcessPendingRowIsDeletedFromRealMediaStore() {
        val journal = SharedPreferencesExportPublicationJournal(context)
        journal.entries().forEach { journal.remove(it.journalId) }
        val name = "SplitFrame_recovery_test_${System.nanoTime()}.jpg"
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val journalId = journal.prepare(collection.toString(), name)
        val uri = insertPendingImage(name)
        journal.recordWriting(journalId, uri.toString())

        val restartedJournal = SharedPreferencesExportPublicationJournal(context)
        val result = ExportPublicationReconciler(
            restartedJournal,
            ContentResolverOwnedPublicationAccess(resolver),
            File(context.cacheDir, "video_exports"),
        ).reconcile()

        assertEquals(1, result.removedIncomplete)
        assertNull(queryId(uri))
    }

    @Test
    fun durablePublishedMarkerRetainsExactRealMediaStoreRowWithoutProviderQuery() {
        val journal = SharedPreferencesExportPublicationJournal(context)
        journal.entries().forEach { journal.remove(it.journalId) }
        val name = "SplitFrame_recovery_published_test_${System.nanoTime()}.jpg"
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val journalId = journal.prepare(collection.toString(), name)
        val uri = insertPendingImage(name)
        journal.recordWriting(journalId, uri.toString())
        journal.markReadyToPublish(journalId)
        journal.markPublished(journalId)

        try {
            val result = ExportPublicationReconciler(
                SharedPreferencesExportPublicationJournal(context),
                ContentResolverOwnedPublicationAccess(resolver),
                File(context.cacheDir, "video_exports"),
            ).reconcile()

            assertEquals(1, result.retainedPublished)
            assertEquals(0, result.retryableFailures)
            assertNotNull(queryId(uri))
        } finally {
            resolver.delete(uri, null, null)
        }
    }

    @Test
    fun connectedDeviceSupportsNormalFhdExportPreflight() {
        val clips = listOf(clip(0), clip(1))
        val template = VideoLayoutMath.sequenceTemplateFor(2, VideoCanvasAspectRatio.RATIO_16_9)
        val project = VideoMergeProject(
            id = "device-preflight",
            template = template,
            mediaByCell = clips.mapIndexed { index, clip -> index to MediaSource.Video(clip) }.toMap(),
        )

        requireVideoExportCapacity(context, project, OutputSize(1920, 1080))
    }

    private fun insertPendingImage(name: String): Uri =
        checkNotNull(
            resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SplitFrame")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                },
            ),
        )

    private fun queryId(uri: Uri): Long? =
        resolver.query(uri, arrayOf(MediaStore.Images.Media._ID), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }

    private fun clip(index: Int) = VideoClip(
        id = "clip-$index",
        uri = "content://video/$index",
        durationMs = 5_000L,
        width = 1920,
        height = 1080,
        rotationDegrees = 0,
        frameRate = 30f,
    )
}
