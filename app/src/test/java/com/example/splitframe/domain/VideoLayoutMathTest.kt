package com.example.splitframe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoLayoutMathTest {
    @Test
    fun sideBySideTemplateUsesStableHalfWidthCells() {
        val template = VideoLayoutMath.templateFor(VideoLayout.SIDE_BY_SIDE, VideoCanvasAspectRatio.RATIO_16_9)

        assertEquals(2, template.cells.size)
        assertEquals(NormalizedRect(0f, 0f, 0.5f, 1f), template.cells[0].rect)
        assertEquals(NormalizedRect(0.5f, 0f, 0.5f, 1f), template.cells[1].rect)
        assertEquals(16f / 9f, template.aspectRatio, 0.001f)
    }

    @Test
    fun topBottomTemplateUsesStableHalfHeightCells() {
        val template = VideoLayoutMath.templateFor(VideoLayout.TOP_BOTTOM, VideoCanvasAspectRatio.RATIO_9_16)

        assertEquals(2, template.cells.size)
        assertEquals(NormalizedRect(0f, 0f, 1f, 0.5f), template.cells[0].rect)
        assertEquals(NormalizedRect(0f, 0.5f, 1f, 0.5f), template.cells[1].rect)
        assertEquals(9f / 16f, template.aspectRatio, 0.001f)
    }

    @Test
    fun outputResolutionPreservesLandscapeAspectRatio() {
        val size = VideoLayoutMath.outputSizeForResolution(
            aspectRatio = VideoCanvasAspectRatio.RATIO_16_9,
            resolution = ExportResolution.FHD_1080,
            clips = emptyMap(),
        )

        assertEquals(1920, size.widthPx)
        assertEquals(1080, size.heightPx)
    }

    @Test
    fun outputResolutionPreservesPortraitAspectRatio() {
        val size = VideoLayoutMath.outputSizeForResolution(
            aspectRatio = VideoCanvasAspectRatio.RATIO_9_16,
            resolution = ExportResolution.FHD_1080,
            clips = emptyMap(),
        )

        assertEquals(1080, size.widthPx)
        assertEquals(1920, size.heightPx)
    }

    @Test
    fun originalResolutionUsesLargestOrientedSourceLongEdge() {
        val size = VideoLayoutMath.outputSizeForResolution(
            aspectRatio = VideoCanvasAspectRatio.RATIO_1_1,
            resolution = ExportResolution.ORIGINAL,
            clips = mapOf(
                0 to clip(width = 1280, height = 720),
                1 to clip(width = 1080, height = 1920, rotation = 90),
            ),
        )

        assertEquals(1920, size.widthPx)
        assertEquals(1920, size.heightPx)
    }

    @Test
    fun trimRangeIsClampedToMinimumDuration() {
        val (start, end) = VideoLayoutMath.normalizeTrim(
            durationMs = 5_000L,
            startMs = 4_800L,
            endMs = 4_900L,
        )

        assertEquals(4_000L, start)
        assertEquals(5_000L, end)
    }

    @Test
    fun durationModesUseLongestOrShortestTrimmedClip() {
        val clips = mapOf(
            0 to clip(duration = 10_000L, trimStart = 1_000L, trimEnd = 6_000L),
            1 to clip(duration = 10_000L, trimStart = 2_000L, trimEnd = 10_000L),
        )

        assertEquals(8_000L, VideoLayoutMath.outputDurationMs(clips, MediaDurationMode.FREEZE_SHORTER))
        assertEquals(5_000L, VideoLayoutMath.outputDurationMs(clips, MediaDurationMode.STOP_AT_SHORTEST))
    }

    @Test
    fun freezeDurationIsOnlyTheRemainingTail() {
        val clip = clip(duration = 10_000L, trimStart = 0L, trimEnd = 4_000L)

        assertEquals(3_000L, VideoLayoutMath.freezeDurationMs(clip, outputDurationMs = 7_000L))
        assertEquals(0L, VideoLayoutMath.freezeDurationMs(clip, outputDurationMs = 3_000L))
    }

    @Test
    fun estimatedMp4SizeIsAlwaysPositive() {
        val estimate = VideoLayoutMath.estimateMp4Bytes(OutputSize(1920, 1080), durationMs = 3_000L)

        assertTrue(estimate >= 1_000_000L)
    }

    private fun clip(
        width: Int = 1920,
        height: Int = 1080,
        rotation: Int = 0,
        duration: Long = 10_000L,
        trimStart: Long = 0L,
        trimEnd: Long = duration,
    ): VideoClip =
        VideoClip(
            id = "$width-$height-$rotation-$duration",
            uri = "content://test/$width/$height",
            durationMs = duration,
            trimStartMs = trimStart,
            trimEndMs = trimEnd,
            width = width,
            height = height,
            rotationDegrees = rotation,
        )
}
