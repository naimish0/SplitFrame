package com.example.splitframe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleImageResizePlannerTest {
    @Test
    fun scale2xPreservesAspectRatioAndMarksUpscale() {
        val result = SingleImageResizePlanner.plan(
            original = ImageDimensions(widthPx = 1200, heightPx = 800),
            request = SingleImageResizeRequest(preset = SingleImageResizePreset.Scale2x),
        ) as SingleImagePlanResult.Valid

        assertEquals(ImageDimensions(widthPx = 2400, heightPx = 1600), result.plan.outputDimensions)
        assertTrue(result.plan.isUpscale)
    }

    @Test
    fun longEdgePresetPreservesPortraitOrientation() {
        val result = SingleImageResizePlanner.plan(
            original = ImageDimensions(widthPx = 900, heightPx = 1600),
            request = SingleImageResizeRequest(preset = SingleImageResizePreset.LongEdge4K),
        ) as SingleImagePlanResult.Valid

        assertEquals(3840, result.plan.outputDimensions.heightPx)
        assertEquals(2160, result.plan.outputDimensions.widthPx)
    }

    @Test
    fun customWidthLocksAspectRatioByDefault() {
        val result = SingleImageResizePlanner.plan(
            original = ImageDimensions(widthPx = 4000, heightPx = 3000),
            request = SingleImageResizeRequest(
                preset = SingleImageResizePreset.Custom,
                customWidthPx = 2000,
                lockAspectRatio = true,
            ),
        ) as SingleImagePlanResult.Valid

        assertEquals(ImageDimensions(widthPx = 2000, heightPx = 1500), result.plan.outputDimensions)
        assertEquals(SingleImageResizeWarning.WouldDownscale, result.plan.warning)
        assertFalse(result.plan.isUpscale)
    }

    @Test
    fun customUnlockedAspectRatioWarnsAboutDistortion() {
        val result = SingleImageResizePlanner.plan(
            original = ImageDimensions(widthPx = 1200, heightPx = 800),
            request = SingleImageResizeRequest(
                preset = SingleImageResizePreset.Custom,
                customWidthPx = 1500,
                customHeightPx = 1500,
                lockAspectRatio = false,
            ),
        ) as SingleImagePlanResult.Valid

        assertEquals(ImageDimensions(widthPx = 1500, heightPx = 1500), result.plan.outputDimensions)
        assertEquals(SingleImageResizeWarning.AspectRatioUnlocked, result.plan.warning)
    }

    @Test
    fun outputTooLargeIsRejectedBeforeProcessing() {
        val result = SingleImageResizePlanner.plan(
            original = ImageDimensions(widthPx = 3000, heightPx = 3000),
            request = SingleImageResizeRequest(preset = SingleImageResizePreset.Scale4x),
        )

        assertEquals(SingleImagePlanResult.Invalid(SingleImagePlanError.OutputTooLarge), result)
    }

    @Test
    fun pngEstimateIsLargerThanJpegForTransparencyPreservingOutput() {
        val dimensions = ImageDimensions(widthPx = 2000, heightPx = 1000)

        val jpegBytes = SingleImageResizePlanner.estimatedBytes(
            dimensions = dimensions,
            format = SingleImageOutputFormat.Jpeg,
            jpegQuality = 90,
        )
        val pngBytes = SingleImageResizePlanner.estimatedBytes(
            dimensions = dimensions,
            format = SingleImageOutputFormat.Png,
            jpegQuality = 90,
        )

        assertTrue(pngBytes > jpegBytes)
    }
}
