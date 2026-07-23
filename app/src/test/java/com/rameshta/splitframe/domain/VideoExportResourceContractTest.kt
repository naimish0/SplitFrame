package com.rameshta.splitframe.domain

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoExportResourceContractTest {
    @Test
    fun `normal project passes resource contract`() {
        assertNull(videoExportResourceContractFailure(project(List(2) { clip(it, 5_000L) })))
    }

    @Test
    fun `clip count is bounded`() {
        val failure = videoExportResourceContractFailure(
            project(List(MixedMediaLimits.MaxItems + 1) { clip(it, 1_000L) }),
        )
        assertTrue(failure.orEmpty().contains(MixedMediaLimits.MaxItems.toString()))
    }

    @Test
    fun `total trimmed duration is bounded`() {
        val failure = videoExportResourceContractFailure(
            project(
                listOf(
                    clip(0, MixedMediaLimits.MaxTotalTrimmedDurationMs),
                    clip(1, 1_000L),
                ),
            ),
        )
        assertTrue(failure.orEmpty().contains("30 minutes"))
    }

    @Test
    fun `estimated output size is bounded`() {
        val long4kClips = listOf(
            clip(0, 15L * 60L * 1_000L, width = 3840, height = 2160, frameRate = 60f),
            clip(1, 15L * 60L * 1_000L, width = 3840, height = 2160, frameRate = 60f),
        )
        val failure = videoExportResourceContractFailure(
            project(long4kClips).copy(exportResolution = ExportResolution.UHD_2160),
        )
        assertTrue(failure.orEmpty().contains("too large"))
    }

    private fun project(clips: List<VideoClip>): VideoMergeProject {
        val template = VideoLayoutMath.sequenceTemplateFor(clips.size, VideoCanvasAspectRatio.RATIO_16_9)
        return VideoMergeProject(
            id = "project",
            template = template,
            mediaByCell = clips.mapIndexed { index, clip -> index to MediaSource.Video(clip) }.toMap(),
        )
    }

    private fun clip(
        index: Int,
        durationMs: Long,
        width: Int = 1920,
        height: Int = 1080,
        frameRate: Float = 30f,
    ) = VideoClip(
        id = "clip-$index",
        uri = "content://video/$index",
        durationMs = durationMs,
        width = width,
        height = height,
        rotationDegrees = 0,
        frameRate = frameRate,
    )
}
