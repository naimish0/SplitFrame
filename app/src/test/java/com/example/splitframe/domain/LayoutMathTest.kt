package com.example.splitframe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutMathTest {
    @Test
    fun sideBySideCellsApplyOuterAndInnerSpacing() {
        val template = TemplateRepository().templates().first { it.id == TemplateIds.SIDE_BY_SIDE }

        val left = LayoutMath.cellFrame(template.cells[0], 1000f, 1000f, 20f)
        val right = LayoutMath.cellFrame(template.cells[1], 1000f, 1000f, 20f)

        assertEquals(20f, left.left, 0.001f)
        assertEquals(490f, left.right, 0.001f)
        assertEquals(510f, right.left, 0.001f)
        assertEquals(980f, right.right, 0.001f)
    }

    @Test
    fun cropToFillCropsWidthForWideSourceIntoSquare() {
        val crop = LayoutMath.cropToFillSourceRect(
            sourceWidthPx = 400f,
            sourceHeightPx = 200f,
            destinationWidthPx = 100f,
            destinationHeightPx = 100f,
        )

        assertEquals(100f, crop.left, 0.001f)
        assertEquals(0f, crop.top, 0.001f)
        assertEquals(300f, crop.right, 0.001f)
        assertEquals(200f, crop.bottom, 0.001f)
    }

    @Test
    fun cropTransformZoomsIntoCenteredCrop() {
        val crop = LayoutMath.cropToFillSourceRect(
            sourceWidthPx = 400f,
            sourceHeightPx = 200f,
            destinationWidthPx = 100f,
            destinationHeightPx = 100f,
            transform = ImageTransform(zoom = 2f),
        )

        assertEquals(150f, crop.left, 0.001f)
        assertEquals(50f, crop.top, 0.001f)
        assertEquals(250f, crop.right, 0.001f)
        assertEquals(150f, crop.bottom, 0.001f)
    }

    @Test
    fun cropTransformPansWithinSourceBounds() {
        val crop = LayoutMath.cropToFillSourceRect(
            sourceWidthPx = 400f,
            sourceHeightPx = 200f,
            destinationWidthPx = 100f,
            destinationHeightPx = 100f,
            transform = ImageTransform(panX = 1f, panY = 1f),
        )

        assertEquals(200f, crop.left, 0.001f)
        assertEquals(0f, crop.top, 0.001f)
        assertEquals(400f, crop.right, 0.001f)
        assertEquals(200f, crop.bottom, 0.001f)
    }

    @Test
    fun cropTransformClampsExtremeValues() {
        val crop = LayoutMath.cropToFillSourceRect(
            sourceWidthPx = 400f,
            sourceHeightPx = 200f,
            destinationWidthPx = 100f,
            destinationHeightPx = 100f,
            transform = ImageTransform(zoom = 20f, panX = -8f, panY = 8f),
        )

        assertEquals(0f, crop.left, 0.001f)
        assertEquals(160f, crop.top, 0.001f)
        assertEquals(40f, crop.right, 0.001f)
        assertEquals(200f, crop.bottom, 0.001f)
    }

    @Test
    fun gesturePanMapsToBoundedSourceCrop() {
        val transform = LayoutMath.transformAfterGesture(
            sourceDimensions = ImageDimensions(widthPx = 400, heightPx = 200),
            destinationWidthPx = 100f,
            destinationHeightPx = 100f,
            current = ImageTransform(zoom = 2f),
            panXpx = 40f,
            panYpx = -40f,
            zoomChange = 1f,
        )

        val crop = LayoutMath.cropToFillSourceRect(
            sourceWidthPx = 400f,
            sourceHeightPx = 200f,
            destinationWidthPx = 100f,
            destinationHeightPx = 100f,
            transform = transform,
        )

        assertTrue(crop.left >= 0f)
        assertTrue(crop.top >= 0f)
        assertTrue(crop.right <= 400f)
        assertTrue(crop.bottom <= 200f)
    }

    @Test
    fun doubleTapZoomsInThenResetsWhenAlreadyZoomed() {
        val zoomed = LayoutMath.transformAfterDoubleTap(
            sourceDimensions = ImageDimensions(widthPx = 400, heightPx = 200),
            destinationWidthPx = 100f,
            destinationHeightPx = 100f,
            current = ImageTransform.Default,
            tapXInFramePx = 75f,
            tapYInFramePx = 50f,
        )

        assertEquals(2.2f, zoomed.zoom, 0.001f)

        val reset = LayoutMath.transformAfterDoubleTap(
            sourceDimensions = ImageDimensions(widthPx = 400, heightPx = 200),
            destinationWidthPx = 100f,
            destinationHeightPx = 100f,
            current = zoomed,
            tapXInFramePx = 75f,
            tapYInFramePx = 50f,
        )

        assertEquals(ImageTransform.Default, reset)
    }

    @Test
    fun resolutionUsesLongEdgeAndTemplateAspectRatio() {
        val template = LayoutTemplate(
            id = "wide",
            name = "wide",
            cells = listOf(LayoutCell(NormalizedRect(0f, 0f, 1f, 1f), 0)),
            defaultSpacingDp = 0f,
            defaultCornerRadiusDp = 0f,
            aspectRatio = 16f / 9f,
        )

        val size = LayoutMath.outputSizeForResolution(template, ExportResolution.FHD_1080, emptyMap())

        assertEquals(1920, size.widthPx)
        assertEquals(1080, size.heightPx)
    }

    @Test
    fun explicitExportResolutionsUseSelectedLongEdge() {
        val landscapeTemplate = LayoutTemplate(
            id = "wide",
            name = "wide",
            cells = listOf(LayoutCell(NormalizedRect(0f, 0f, 1f, 1f), 0)),
            defaultSpacingDp = 0f,
            defaultCornerRadiusDp = 0f,
            aspectRatio = 16f / 9f,
        )
        val portraitTemplate = landscapeTemplate.copy(id = "portrait", aspectRatio = 9f / 16f)

        mapOf(
            ExportResolution.SD_480 to 854,
            ExportResolution.HD_720 to 1280,
            ExportResolution.FHD_1080 to 1920,
            ExportResolution.QHD_1440 to 2560,
            ExportResolution.UHD_2160 to 3840,
        ).forEach { (resolution, longEdge) ->
            val landscape = LayoutMath.outputSizeForResolution(landscapeTemplate, resolution, emptyMap())
            val portrait = LayoutMath.outputSizeForResolution(portraitTemplate, resolution, emptyMap())

            assertEquals(longEdge, landscape.widthPx)
            assertEquals(longEdge, portrait.heightPx)
        }
    }
}
