package com.rameshta.splitframe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleImageResizePlannerTest {
    @Test
    fun `percentage preset scales original dimensions`() {
        val result = SingleImageResizePlanner.plan(
            original = ImageDimensions(4000, 3000),
            request = SingleImageResizeRequest(
                preset = SingleImageResizePreset.Percentage,
                resizePercent = 25,
            ),
        )

        val plan = result as SingleImagePlanResult.Valid
        assertEquals(ImageDimensions(1000, 750), plan.plan.outputDimensions)
    }

    @Test
    fun `percentage outside supported range fails closed`() {
        listOf(0, 401).forEach { percent ->
            val result = SingleImageResizePlanner.plan(
                original = ImageDimensions(4000, 3000),
                request = SingleImageResizeRequest(
                    preset = SingleImageResizePreset.Percentage,
                    resizePercent = percent,
                ),
            )
            assertEquals(
                SingleImagePlanError.InvalidOutputDimensions,
                (result as SingleImagePlanResult.Invalid).reason,
            )
        }
    }
    @Test
    fun `restored output metadata includes fit or fill semantics`() {
        val request = SingleImageResizeRequest(
            preset = SingleImageResizePreset.InstagramSquarePost,
            contentMode = ExportContentMode.Fill,
        )
        val plan = (SingleImageResizePlanner.plan(ImageDimensions(1200, 800), request) as SingleImagePlanResult.Valid).plan
        val metadata = SingleImageOutputMetadata(
            originalDimensions = plan.originalDimensions,
            outputDimensions = plan.outputDimensions,
            originalBytes = 10L,
            outputBytes = 5L,
            outputFormat = request.outputFormat,
            encodingQuality = request.encodingQuality,
            contentMode = ExportContentMode.Fill,
        )

        assertTrue(metadata.matches(plan, request))
        assertFalse(metadata.copy(contentMode = ExportContentMode.Fit).matches(plan, request))
    }

    @Test
    fun `actual comparison calculates bytes and percentage reduction`() {
        val stats = SingleImageComparisonMath.calculate(
            originalBytes = 8_200_000L,
            outputBytes = 1_100_000L,
        )

        assertEquals(7_100_000L, stats.bytesSaved)
        assertEquals(87, stats.percentageReduction)
        assertTrue(stats.reduced)
    }

    @Test
    fun `comparison safely handles missing zero and larger outputs`() {
        assertNull(SingleImageComparisonMath.calculate(null, 10L).bytesSaved)
        assertNull(SingleImageComparisonMath.calculate(0L, 10L).percentageReduction)
        val larger = SingleImageComparisonMath.calculate(100L, 125L)
        assertEquals(-25L, larger.bytesSaved)
        assertEquals(-25, larger.percentageReduction)
        assertFalse(larger.reduced)
    }
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
    fun customUnlockedAspectUsesExactCanvasAndFitDoesNotDistort() {
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
        assertTrue(result.plan.canvasGeometry.addsPadding)
        assertFalse(result.plan.canvasGeometry.cropsContent)
        assertNull(result.plan.warning)
    }

    @Test
    fun requestedFixedPresetsResolveToTheirExactCanvasDimensions() {
        val expected = mapOf(
            SingleImageResizePreset.InstagramSquarePost to ImageDimensions(1080, 1080),
            SingleImageResizePreset.InstagramPortraitPost to ImageDimensions(1080, 1350),
            SingleImageResizePreset.InstagramStoryReel to ImageDimensions(1080, 1920),
            SingleImageResizePreset.WhatsAppStatus to ImageDimensions(1080, 1920),
            SingleImageResizePreset.YouTubeThumbnail to ImageDimensions(1280, 720),
            SingleImageResizePreset.PinterestPin to ImageDimensions(1000, 1500),
        )

        expected.forEach { (preset, dimensions) ->
            val result = SingleImageResizePlanner.plan(
                original = ImageDimensions(1600, 1200),
                request = SingleImageResizeRequest(preset = preset),
            ) as SingleImagePlanResult.Valid

            assertEquals(preset.name, dimensions, result.plan.outputDimensions)
        }
    }

    @Test
    fun catalogDefinesEveryPresetExactlyOnce() {
        assertEquals(SingleImageResizePreset.entries.size, ExportPresetCatalog.definitions.size)
        assertEquals(
            SingleImageResizePreset.entries.toSet(),
            ExportPresetCatalog.definitions.map { it.id }.toSet(),
        )
    }

    @Test
    fun regionalOrderingPromotesWhatsAppWithoutChangingTheCatalog() {
        assertEquals(
            SingleImageResizePreset.WhatsAppStatus,
            ExportPresetCatalog.socialAndCommonForRegion("BR").first().id,
        )
        assertEquals(
            ExportPresetCatalog.socialAndCommon,
            ExportPresetCatalog.socialAndCommonForRegion("unknown"),
        )
        assertEquals(
            ExportPresetCatalog.socialAndCommon.map { it.id }.toSet(),
            ExportPresetCatalog.socialAndCommonForRegion("IN").map { it.id }.toSet(),
        )
    }

    @Test
    fun fitUsesFullSourceAndCenteredPadding() {
        val geometry = SingleImageCanvasMath.geometry(
            source = ImageDimensions(1200, 800),
            canvas = ImageDimensions(1080, 1080),
            contentMode = ExportContentMode.Fit,
        )

        assertEquals(NormalizedRect.Full, geometry.sourceRect)
        assertEquals(0f, geometry.destinationRect.x, 0.0001f)
        assertEquals(1f / 6f, geometry.destinationRect.y, 0.0001f)
        assertEquals(1f, geometry.destinationRect.width, 0.0001f)
        assertEquals(2f / 3f, geometry.destinationRect.height, 0.0001f)
        assertTrue(geometry.addsPadding)
        assertFalse(geometry.cropsContent)
    }

    @Test
    fun fillUsesCenteredSourceCropAndWarnsBeforeExport() {
        val result = SingleImageResizePlanner.plan(
            original = ImageDimensions(1200, 800),
            request = SingleImageResizeRequest(
                preset = SingleImageResizePreset.InstagramSquarePost,
                contentMode = ExportContentMode.Fill,
            ),
        ) as SingleImagePlanResult.Valid

        assertEquals(1f / 6f, result.plan.canvasGeometry.sourceRect.x, 0.0001f)
        assertEquals(2f / 3f, result.plan.canvasGeometry.sourceRect.width, 0.0001f)
        assertEquals(NormalizedRect.Full, result.plan.canvasGeometry.destinationRect)
        assertTrue(result.plan.canvasGeometry.cropsContent)
        assertEquals(SingleImageResizeWarning.ContentCropped, result.plan.warning)
    }

    @Test
    fun fitDownscaleUsesRenderedContentScaleInsteadOfCanvasArea() {
        val result = SingleImageResizePlanner.plan(
            original = ImageDimensions(4000, 1000),
            request = SingleImageResizeRequest(
                preset = SingleImageResizePreset.Custom,
                customWidthPx = 3000,
                customHeightPx = 3000,
                lockAspectRatio = false,
                contentMode = ExportContentMode.Fit,
            ),
        ) as SingleImagePlanResult.Valid

        assertFalse(result.plan.isUpscale)
        assertTrue(SingleImageResizeWarning.WouldDownscale in result.plan.warnings)
    }

    @Test
    fun fillUpscaleUsesRenderedContentScaleInsteadOfCanvasArea() {
        val result = SingleImageResizePlanner.plan(
            original = ImageDimensions(4000, 1000),
            request = SingleImageResizeRequest(
                preset = SingleImageResizePreset.Custom,
                customWidthPx = 1200,
                customHeightPx = 1200,
                lockAspectRatio = false,
                contentMode = ExportContentMode.Fill,
            ),
        ) as SingleImagePlanResult.Valid

        assertTrue(result.plan.isUpscale)
        assertFalse(SingleImageResizeWarning.WouldDownscale in result.plan.warnings)
        assertTrue(SingleImageResizeWarning.ContentCropped in result.plan.warnings)
    }

    @Test
    fun cropDownscaleAndLargeOutputWarningsCanCoexist() {
        val result = SingleImageResizePlanner.plan(
            original = ImageDimensions(10_000, 10_000),
            request = SingleImageResizeRequest(
                preset = SingleImageResizePreset.Custom,
                customWidthPx = 4500,
                customHeightPx = 4000,
                lockAspectRatio = false,
                contentMode = ExportContentMode.Fill,
            ),
        ) as SingleImagePlanResult.Valid

        assertEquals(
            setOf(
                SingleImageResizeWarning.ContentCropped,
                SingleImageResizeWarning.WouldDownscale,
                SingleImageResizeWarning.LargeOutput,
            ),
            result.plan.warnings,
        )
    }

    @Test
    fun portraitSourceExercisesFitAndFillVerticalGeometry() {
        val fit = SingleImageCanvasMath.geometry(
            source = ImageDimensions(800, 1200),
            canvas = ImageDimensions(1600, 900),
            contentMode = ExportContentMode.Fit,
        )
        val fill = SingleImageCanvasMath.geometry(
            source = ImageDimensions(800, 1200),
            canvas = ImageDimensions(1600, 900),
            contentMode = ExportContentMode.Fill,
        )

        assertEquals(0.375f, fit.destinationRect.width, 0.0001f)
        assertEquals(0.3125f, fit.destinationRect.x, 0.0001f)
        assertEquals(0.375f, fill.sourceRect.height, 0.0001f)
        assertEquals(0.3125f, fill.sourceRect.y, 0.0001f)
    }

    @Test
    fun fillDecodeTargetAccountsForNarrowSourceCrop() {
        val landscape = SingleImageResizePlanner.plan(
            original = ImageDimensions(4000, 1000),
            request = SingleImageResizeRequest(
                preset = SingleImageResizePreset.InstagramStoryReel,
                contentMode = ExportContentMode.Fill,
            ),
        ) as SingleImagePlanResult.Valid
        val portrait = SingleImageResizePlanner.plan(
            original = ImageDimensions(1000, 4000),
            request = SingleImageResizeRequest(
                preset = SingleImageResizePreset.YouTubeThumbnail,
                contentMode = ExportContentMode.Fill,
            ),
        ) as SingleImagePlanResult.Valid

        assertEquals(
            4000,
            SingleImageDecodeMath.targetLongEdgePx(
                landscape.plan.originalDimensions,
                landscape.plan.outputDimensions,
                landscape.plan.canvasGeometry,
            ),
        )
        assertEquals(
            4000,
            SingleImageDecodeMath.targetLongEdgePx(
                portrait.plan.originalDimensions,
                portrait.plan.outputDimensions,
                portrait.plan.canvasGeometry,
            ),
        )
    }

    @Test
    fun deviceWallpaperUsesSuppliedDimensionsWithoutPersistingAStaticPresetSize() {
        val dimensions = ImageDimensions(1440, 3200)
        val result = SingleImageResizePlanner.plan(
            original = ImageDimensions(1200, 800),
            request = SingleImageResizeRequest(
                preset = SingleImageResizePreset.DeviceWallpaper,
                deviceWallpaperDimensions = dimensions,
            ),
        ) as SingleImagePlanResult.Valid

        assertEquals(dimensions, result.plan.outputDimensions)
        assertEquals(1440f / 3200f, result.plan.canvasAspectRatio, 0.0001f)
    }

    @Test
    fun missingWallpaperAndInvalidCustomDimensionsAreRejected() {
        assertEquals(
            SingleImagePlanResult.Invalid(SingleImagePlanError.DeviceWallpaperUnavailable),
            SingleImageResizePlanner.plan(
                original = ImageDimensions(1200, 800),
                request = SingleImageResizeRequest(preset = SingleImageResizePreset.DeviceWallpaper),
            ),
        )
        assertEquals(
            SingleImagePlanResult.Invalid(SingleImagePlanError.InvalidOutputDimensions),
            SingleImageResizePlanner.plan(
                original = ImageDimensions(1200, 800),
                request = SingleImageResizeRequest(
                    preset = SingleImageResizePreset.Custom,
                    customWidthPx = 0,
                    customHeightPx = 1080,
                    lockAspectRatio = false,
                ),
            ),
        )
    }

    @Test
    fun customHeightCanDriveLockedAspectAndAmbiguousLockedInputIsRejected() {
        val heightOnly = SingleImageResizePlanner.plan(
            original = ImageDimensions(4000, 3000),
            request = SingleImageResizeRequest(
                preset = SingleImageResizePreset.Custom,
                customHeightPx = 1200,
                lockAspectRatio = true,
            ),
        ) as SingleImagePlanResult.Valid
        val ambiguous = SingleImageResizePlanner.plan(
            original = ImageDimensions(4000, 3000),
            request = SingleImageResizeRequest(
                preset = SingleImageResizePreset.Custom,
                customWidthPx = 1600,
                customHeightPx = 1200,
                lockAspectRatio = true,
            ),
        )

        assertEquals(ImageDimensions(1600, 1200), heightOnly.plan.outputDimensions)
        assertEquals(SingleImagePlanResult.Invalid(SingleImagePlanError.InvalidOutputDimensions), ambiguous)
    }

    @Test
    fun excessiveCustomPixelCountIsRejectedBeforeAllocation() {
        val result = SingleImageResizePlanner.plan(
            original = ImageDimensions(1200, 800),
            request = SingleImageResizeRequest(
                preset = SingleImageResizePreset.Custom,
                customWidthPx = 8192,
                customHeightPx = 8192,
                lockAspectRatio = false,
            ),
        )

        assertEquals(SingleImagePlanResult.Invalid(SingleImagePlanError.OutputTooLarge), result)
    }

    @Test
    fun previewAspectAndExportCanvasShareTheResolvedRatio() {
        val result = SingleImageResizePlanner.plan(
            original = ImageDimensions(2048, 1536),
            request = SingleImageResizeRequest(preset = SingleImageResizePreset.InstagramPortraitPost),
        ) as SingleImagePlanResult.Valid

        assertEquals(
            result.plan.outputDimensions.widthPx / result.plan.outputDimensions.heightPx.toFloat(),
            result.plan.canvasAspectRatio,
            0f,
        )
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
            encodingQuality = 90,
        )
        val pngBytes = SingleImageResizePlanner.estimatedBytes(
            dimensions = dimensions,
            format = SingleImageOutputFormat.Png,
            encodingQuality = 90,
        )

        assertTrue(pngBytes > jpegBytes)
    }
}
