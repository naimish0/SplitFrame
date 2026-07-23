package com.rameshta.splitframe.export

import android.content.pm.ServiceInfo
import com.rameshta.splitframe.domain.MediaSource
import com.rameshta.splitframe.domain.VideoClip
import com.rameshta.splitframe.domain.VideoMergeProject
import com.rameshta.splitframe.domain.VideoSupportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoExportWorkerTest {
    @Test
    fun restoredSourcePreflightRejectsRevokedAndChangedClips() {
        val project = project()
        assertEquals(
            "A selected video is missing or access was revoked. Choose it again.",
            videoExportPreflightFailure(project) {
                VideoMetadataResult.Unsupported(VideoSupportStatus.Unreadable)
            },
        )
        assertEquals(
            "A selected video changed since it was added. Choose it again.",
            videoExportPreflightFailure(project) { uri ->
                VideoMetadataResult.Valid(clip(uri, durationMs = 500L))
            },
        )
    }

    @Test
    fun restoredSourcePreflightAcceptsReadableUnchangedClips() {
        assertNull(
            videoExportPreflightFailure(project()) { uri ->
                VideoMetadataResult.Valid(clip(uri, durationMs = 2_000L))
            },
        )
    }
    @Test
    fun foregroundServiceTypeMatchesPlatformCapabilities() {
        assertNull(videoExportForegroundServiceType(28))
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            videoExportForegroundServiceType(29),
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            videoExportForegroundServiceType(34),
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            videoExportForegroundServiceType(35),
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            videoExportForegroundServiceType(36),
        )
    }

    private fun project(): VideoMergeProject = VideoMergeProject(
        id = "55555555-5555-4555-8555-555555555555",
        mediaByCell = mapOf(
            0 to MediaSource.Video(clip("content://video/one", 2_000L)),
            1 to MediaSource.Video(clip("content://video/two", 2_000L)),
        ),
    )

    private fun clip(uri: String, durationMs: Long): VideoClip = VideoClip(
        id = uri.substringAfterLast('/'),
        uri = uri,
        durationMs = durationMs,
        width = 1920,
        height = 1080,
        rotationDegrees = 0,
        trimEndMs = 1_000L.coerceAtMost(durationMs),
    )
}
