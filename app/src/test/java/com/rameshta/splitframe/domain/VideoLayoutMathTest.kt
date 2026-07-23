package com.rameshta.splitframe.domain

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
    fun edgeToEdgeExportFramesHaveNoGapBetweenCells() {
        val sideBySide = VideoLayoutMath.templateFor(VideoLayout.SIDE_BY_SIDE, VideoCanvasAspectRatio.RATIO_16_9)
        val topBottom = VideoLayoutMath.templateFor(VideoLayout.TOP_BOTTOM, VideoCanvasAspectRatio.RATIO_16_9)

        val sideBySideFrames = VideoLayoutMath.edgeToEdgeCellFrames(sideBySide, OutputSize(1920, 1080))
        val topBottomFrames = VideoLayoutMath.edgeToEdgeCellFrames(topBottom, OutputSize(1920, 1080))
        val left = sideBySideFrames.getValue(0)
        val right = sideBySideFrames.getValue(1)
        val top = topBottomFrames.getValue(0)
        val bottom = topBottomFrames.getValue(1)

        assertEquals(0f, left.left, 0.001f)
        assertTrue(left.right > right.left)
        assertEquals(1920f, right.right, 0.001f)
        assertEquals(0f, top.top, 0.001f)
        assertTrue(top.bottom > bottom.top)
        assertEquals(1080f, bottom.bottom, 0.001f)
    }

    @Test
    fun edgeToEdgeExportFramesOverlapFractionalInternalEdges() {
        val template = TemplateRepository().templates().first { it.id == TemplateIds.MOSAIC_5 }
        val frames = VideoLayoutMath.edgeToEdgeCellFrames(template, OutputSize(1001, 1001))
        val large = frames.getValue(0)
        val rightColumnTop = frames.getValue(1)
        val bottomLeft = frames.getValue(3)
        val bottomRight = frames.getValue(4)

        assertEquals(0f, large.left, 0.001f)
        assertEquals(0f, large.top, 0.001f)
        assertTrue(large.right > rightColumnTop.left)
        assertTrue(large.bottom > bottomLeft.top)
        assertTrue(bottomLeft.right > bottomRight.left)
        assertEquals(1001f, bottomRight.right, 0.001f)
        assertEquals(1001f, bottomRight.bottom, 0.001f)
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
    fun explicitVideoExportResolutionsUseSelectedLongEdge() {
        mapOf(
            ExportResolution.SD_480 to 854,
            ExportResolution.HD_720 to 1280,
            ExportResolution.FHD_1080 to 1920,
            ExportResolution.QHD_1440 to 2560,
            ExportResolution.UHD_2160 to 3840,
        ).forEach { (resolution, longEdge) ->
            val landscape = VideoLayoutMath.outputSizeForMedia(
                aspectRatio = VideoCanvasAspectRatio.RATIO_16_9,
                resolution = resolution,
                mediaByCell = emptyMap(),
            )
            val portrait = VideoLayoutMath.outputSizeForMedia(
                aspectRatio = VideoCanvasAspectRatio.RATIO_9_16,
                resolution = resolution,
                mediaByCell = emptyMap(),
            )

            assertEquals(longEdge, landscape.widthPx)
            assertEquals(longEdge, portrait.heightPx)
        }
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
    fun estimatedMp4SizeIncludesFrameRateAudioAndContainerOverhead() {
        val fiveMinuteClip = clip(duration = 300_000L).copy(
            frameRate = 30f,
            hasAudio = true,
        )

        val estimate = VideoLayoutMath.estimateMp4Bytes(
            OutputSize(1920, 1080),
            clips = listOf(fiveMinuteClip),
        )

        assertTrue(estimate in 280_000_000L..330_000_000L)
    }

    @Test
    fun estimatedMp4SizeUsesEachClipsTrimmedDurationAndFrameRate() {
        val thirtyFps = clip(duration = 60_000L).copy(frameRate = 30f)
        val sixtyFps = clip(duration = 60_000L).copy(frameRate = 60f)

        val thirtyFpsEstimate = VideoLayoutMath.estimateMp4Bytes(
            OutputSize(1920, 1080),
            clips = listOf(thirtyFps),
        )
        val sixtyFpsEstimate = VideoLayoutMath.estimateMp4Bytes(
            OutputSize(1920, 1080),
            clips = listOf(sixtyFps),
        )

        assertTrue(sixtyFpsEstimate >= thirtyFpsEstimate * 19L / 10L)
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
