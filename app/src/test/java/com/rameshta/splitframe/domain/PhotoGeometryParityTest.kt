package com.rameshta.splitframe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoGeometryParityTest {
    @Test
    fun `before-after divider uses theme-independent export white`() {
        assertEquals(0xFFFFFFFFuL, CollageRenderColors.BeforeAfterDividerArgb)
    }

    @Test
    fun `original output keeps aspect while capping allocation risk`() {
        val square = LayoutMath.outputSizeForResolution(
            template = template(1f),
            resolution = ExportResolution.ORIGINAL,
            sourceDimensions = mapOf(0 to ImageDimensions(20_000, 20_000)),
        )

        assertTrue(square.widthPx.toLong() * square.heightPx <= LayoutMath.MaxPhotoOutputPixels)
        assertEquals(1f, square.widthPx / square.heightPx.toFloat(), 0.001f)
    }
    @Test
    fun canonicalCanvasUsesEditorDesignUnits() {
        val metrics = LayoutMath.collageRenderMetrics(
            canvasWidthPx = 360f,
            spacingDp = 8f,
            cornerRadiusDp = 18f,
            borderWidthDp = 3f,
        )

        assertEquals(8f, metrics.spacingPx, Tolerance)
        assertEquals(18f, metrics.cornerRadiusPx, Tolerance)
        assertEquals(3f, metrics.borderWidthPx, Tolerance)
        assertEquals(4f, metrics.dividerWidthPx, Tolerance)
    }

    @Test
    fun fhdOutputDimensionsCoverRepresentativeAspectRatios() {
        listOf(
            1f to OutputSize(1920, 1920),
            (16f / 9f) to OutputSize(1920, 1080),
            (4f / 5f) to OutputSize(1536, 1920),
        ).forEach { (aspectRatio, expected) ->
            val actual = LayoutMath.outputSizeForResolution(
                template = template(aspectRatio),
                resolution = ExportResolution.FHD_1080,
                sourceDimensions = emptyMap(),
            )

            assertEquals(expected, actual)
        }
    }

    @Test
    fun previewAndExportKeepStyleAndCellGeometryProportional() {
        listOf(1f, 16f / 9f, 4f / 5f).forEach { aspectRatio ->
            val template = template(aspectRatio)
            val previewWidth = 324f
            val previewHeight = previewWidth / aspectRatio
            val output = LayoutMath.outputSizeForResolution(
                template = template,
                resolution = ExportResolution.FHD_1080,
                sourceDimensions = emptyMap(),
            )
            val previewMetrics = metrics(previewWidth)
            val exportMetrics = metrics(output.widthPx.toFloat())

            assertNormalizedEqual(previewMetrics.spacingPx, previewWidth, exportMetrics.spacingPx, output.widthPx.toFloat())
            assertNormalizedEqual(
                previewMetrics.cornerRadiusPx,
                previewWidth,
                exportMetrics.cornerRadiusPx,
                output.widthPx.toFloat(),
            )
            assertNormalizedEqual(
                previewMetrics.borderWidthPx,
                previewWidth,
                exportMetrics.borderWidthPx,
                output.widthPx.toFloat(),
            )
            assertNormalizedEqual(
                previewMetrics.dividerWidthPx,
                previewWidth,
                exportMetrics.dividerWidthPx,
                output.widthPx.toFloat(),
            )

            template.cells.forEach { cell ->
                val previewFrame = LayoutMath.cellFrame(
                    cell,
                    previewWidth,
                    previewHeight,
                    previewMetrics.spacingPx,
                )
                val exportFrame = LayoutMath.cellFrame(
                    cell,
                    output.widthPx.toFloat(),
                    output.heightPx.toFloat(),
                    exportMetrics.spacingPx,
                )
                assertFramesNormalizedEqual(
                    previewFrame = previewFrame,
                    previewWidth = previewWidth,
                    previewHeight = previewHeight,
                    exportFrame = exportFrame,
                    exportWidth = output.widthPx.toFloat(),
                    exportHeight = output.heightPx.toFloat(),
                )
            }
        }
    }

    @Test
    fun zeroEffectsRemainZeroAtEveryCanvasWidth() {
        listOf(0f, 320f, 1920f, 3840f).forEach { width ->
            val metrics = LayoutMath.collageRenderMetrics(
                canvasWidthPx = width,
                spacingDp = 0f,
                cornerRadiusDp = 0f,
                borderWidthDp = 0f,
            )

            assertEquals(0f, metrics.spacingPx, Tolerance)
            assertEquals(0f, metrics.cornerRadiusPx, Tolerance)
            assertEquals(0f, metrics.borderWidthPx, Tolerance)
        }
    }

    @Test
    fun maximumEffectsNeverInvertCatalogCellFrames() {
        val canvasWidth = 320f
        TemplateRepository().templates().forEach { template ->
            val canvasHeight = canvasWidth / template.aspectRatio
            val metrics = LayoutMath.collageRenderMetrics(
                canvasWidthPx = canvasWidth,
                spacingDp = 36f,
                cornerRadiusDp = 64f,
                borderWidthDp = 12f,
            )
            assertTrue(metrics.cornerRadiusPx.isFinite())
            assertTrue(metrics.borderWidthPx.isFinite())

            template.cells.forEach { cell ->
                val frame = LayoutMath.cellFrame(cell, canvasWidth, canvasHeight, metrics.spacingPx)
                assertTrue("${template.id}:${cell.index} left", frame.left.isFinite() && frame.left >= -Tolerance)
                assertTrue("${template.id}:${cell.index} top", frame.top.isFinite() && frame.top >= -Tolerance)
                assertTrue(
                    "${template.id}:${cell.index} right",
                    frame.right.isFinite() && frame.right <= canvasWidth + Tolerance,
                )
                assertTrue(
                    "${template.id}:${cell.index} bottom",
                    frame.bottom.isFinite() && frame.bottom <= canvasHeight + Tolerance,
                )
                assertTrue("${template.id}:${cell.index} width", frame.width > 0f)
                assertTrue("${template.id}:${cell.index} height", frame.height > 0f)
                assertTrue(
                    "${template.id}:${cell.index} minimum width",
                    frame.width + Tolerance >= cell.rect.width * canvasWidth * MinimumCellExtentFraction,
                )
                assertTrue(
                    "${template.id}:${cell.index} minimum height",
                    frame.height + Tolerance >= cell.rect.height * canvasHeight * MinimumCellExtentFraction,
                )
            }
        }
    }

    @Test
    fun extremeCropMatchesForEquivalentOrientedDestinationAspects() {
        val transform = ImageTransform(zoom = 5f, panX = 1f, panY = -1f)
        val previewCrop = LayoutMath.cropToFillSourceRect(
            sourceWidthPx = 1200f,
            sourceHeightPx = 1600f,
            destinationWidthPx = 180f,
            destinationHeightPx = 320f,
            transform = transform,
        )
        val exportCrop = LayoutMath.cropToFillSourceRect(
            sourceWidthPx = 1200f,
            sourceHeightPx = 1600f,
            destinationWidthPx = 1080f,
            destinationHeightPx = 1920f,
            transform = transform,
        )

        assertRectEquals(previewCrop, exportCrop)
    }

    @Test
    fun beforeAfterDividerPositionAndThicknessStayProportionalAndClamped() {
        val previewWidth = 324f
        val exportWidth = 3840f
        val previewDivider = metrics(previewWidth).dividerWidthPx
        val exportDivider = metrics(exportWidth).dividerWidthPx

        assertNormalizedEqual(previewDivider, previewWidth, exportDivider, exportWidth)
        listOf(-1f to 0.05f, 0.5f to 0.5f, 2f to 0.95f).forEach { (position, expected) ->
            val previewX = LayoutMath.beforeAfterDividerX(previewWidth, position)
            val exportX = LayoutMath.beforeAfterDividerX(exportWidth, position)
            assertNormalizedEqual(previewX, previewWidth, exportX, exportWidth)
            assertEquals(expected, previewX / previewWidth, Tolerance)
        }
    }

    private fun metrics(widthPx: Float): CollageRenderMetrics =
        LayoutMath.collageRenderMetrics(
            canvasWidthPx = widthPx,
            spacingDp = 36f,
            cornerRadiusDp = 64f,
            borderWidthDp = 3f,
        )

    private fun template(aspectRatio: Float): LayoutTemplate =
        LayoutTemplate(
            id = "parity-$aspectRatio",
            name = "Parity",
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 0.5f, 1f), 0),
                LayoutCell(NormalizedRect(0.5f, 0f, 0.5f, 1f), 1),
            ),
            defaultSpacingDp = 8f,
            defaultCornerRadiusDp = 18f,
            aspectRatio = aspectRatio,
        )

    private fun assertNormalizedEqual(
        previewValue: Float,
        previewExtent: Float,
        exportValue: Float,
        exportExtent: Float,
    ) {
        assertEquals(previewValue / previewExtent, exportValue / exportExtent, Tolerance)
    }

    private fun assertFramesNormalizedEqual(
        previewFrame: RectPx,
        previewWidth: Float,
        previewHeight: Float,
        exportFrame: RectPx,
        exportWidth: Float,
        exportHeight: Float,
    ) {
        assertNormalizedEqual(previewFrame.left, previewWidth, exportFrame.left, exportWidth)
        assertNormalizedEqual(previewFrame.right, previewWidth, exportFrame.right, exportWidth)
        assertNormalizedEqual(previewFrame.top, previewHeight, exportFrame.top, exportHeight)
        assertNormalizedEqual(previewFrame.bottom, previewHeight, exportFrame.bottom, exportHeight)
    }

    private fun assertRectEquals(expected: RectPx, actual: RectPx) {
        assertEquals(expected.left, actual.left, Tolerance)
        assertEquals(expected.top, actual.top, Tolerance)
        assertEquals(expected.right, actual.right, Tolerance)
        assertEquals(expected.bottom, actual.bottom, Tolerance)
    }

    private companion object {
        const val Tolerance = 0.001f
        const val MinimumCellExtentFraction = 0.05f
    }
}
