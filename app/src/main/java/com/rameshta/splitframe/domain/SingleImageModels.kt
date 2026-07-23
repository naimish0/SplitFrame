package com.rameshta.splitframe.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class SingleImageOutputFormat(
    val mimeType: String,
    val extension: String,
    val supportsTransparency: Boolean,
) {
    Jpeg("image/jpeg", "jpg", supportsTransparency = false),
    Png("image/png", "png", supportsTransparency = true),
    Webp("image/webp", "webp", supportsTransparency = true),
}

data class SingleImageResizeRequest(
    val preset: SingleImageResizePreset = SingleImageResizePreset.Scale2x,
    val outputFormat: SingleImageOutputFormat = SingleImageOutputFormat.Jpeg,
    val encodingQuality: Int = 94,
    val resizePercent: Int = 100,
    val targetSizeValue: Int? = null,
    val targetSizeUnit: TargetSizeUnit = TargetSizeUnit.KB,
    val metadataPolicy: ImageMetadataPolicy = ImageMetadataPolicy.RemoveMetadata,
    val customWidthPx: Int? = null,
    val customHeightPx: Int? = null,
    val lockAspectRatio: Boolean = true,
    val contentMode: ExportContentMode = ExportContentMode.Fit,
    val deviceWallpaperDimensions: ImageDimensions? = null,
) {
    val targetSizeBytes: Long?
        get() = targetSizeValue?.takeIf { it > 0 }?.let(targetSizeUnit::toBytes)
}

enum class TargetSizeUnit(val bytesPerUnit: Long) {
    KB(1_024L),
    MB(1_024L * 1_024L);

    fun toBytes(value: Int): Long =
        value.toLong().coerceAtMost(Long.MAX_VALUE / bytesPerUnit) * bytesPerUnit
}

enum class ImageMetadataPolicy {
    PreserveDetails,
    RemoveMetadata,
}

enum class ExportContentMode {
    Fit,
    Fill,
}

data class SingleImageExportSettings(
    val preset: SingleImageResizePreset = SingleImageResizePreset.Scale2x,
    val outputFormat: SingleImageOutputFormat = SingleImageOutputFormat.Jpeg,
    val encodingQuality: Int = 94,
    val resizePercent: Int = 100,
    val targetSizeValue: Int? = null,
    val targetSizeUnit: TargetSizeUnit = TargetSizeUnit.KB,
    val metadataPolicy: ImageMetadataPolicy = ImageMetadataPolicy.RemoveMetadata,
    val customWidthPx: Int? = null,
    val customHeightPx: Int? = null,
    val lockAspectRatio: Boolean = true,
    val contentMode: ExportContentMode = ExportContentMode.Fit,
) {
    fun toRequest(deviceWallpaperDimensions: ImageDimensions? = null): SingleImageResizeRequest =
        SingleImageResizeRequest(
            preset = preset,
            outputFormat = outputFormat,
            encodingQuality = encodingQuality,
            resizePercent = resizePercent,
            targetSizeValue = targetSizeValue,
            targetSizeUnit = targetSizeUnit,
            metadataPolicy = metadataPolicy,
            customWidthPx = customWidthPx,
            customHeightPx = customHeightPx,
            lockAspectRatio = lockAspectRatio,
            contentMode = contentMode,
            deviceWallpaperDimensions = deviceWallpaperDimensions,
        )

    companion object {
        fun from(request: SingleImageResizeRequest): SingleImageExportSettings =
            SingleImageExportSettings(
                preset = request.preset,
                outputFormat = request.outputFormat,
                encodingQuality = request.encodingQuality,
                resizePercent = request.resizePercent,
                targetSizeValue = request.targetSizeValue,
                targetSizeUnit = request.targetSizeUnit,
                metadataPolicy = request.metadataPolicy,
                customWidthPx = request.customWidthPx,
                customHeightPx = request.customHeightPx,
                lockAspectRatio = request.lockAspectRatio,
                contentMode = request.contentMode,
            )
    }
}

data class SavedResizePreset(
    val name: String,
    val settings: SingleImageExportSettings,
)

data class SingleImageCanvasGeometry(
    val sourceRect: NormalizedRect,
    val destinationRect: NormalizedRect,
    val cropsContent: Boolean,
    val addsPadding: Boolean,
)

data class SingleImageResizePlan(
    val originalDimensions: ImageDimensions,
    val outputDimensions: ImageDimensions,
    val estimatedBytes: Long,
    val isUpscale: Boolean,
    val canvasGeometry: SingleImageCanvasGeometry = SingleImageCanvasMath.geometry(
        source = originalDimensions,
        canvas = outputDimensions,
        contentMode = ExportContentMode.Fit,
    ),
    val warnings: Set<SingleImageResizeWarning> = emptySet(),
) {
    val canvasAspectRatio: Float
        get() = outputDimensions.widthPx / outputDimensions.heightPx.toFloat()

    val warning: SingleImageResizeWarning?
        get() = warnings.firstOrNull()
}

data class SingleImageOutputMetadata(
    val originalDimensions: ImageDimensions,
    val outputDimensions: ImageDimensions,
    val originalBytes: Long?,
    val outputBytes: Long?,
    val outputFormat: SingleImageOutputFormat,
    val encodingQuality: Int?,
    val contentMode: ExportContentMode,
    val metadataPolicy: ImageMetadataPolicy = ImageMetadataPolicy.RemoveMetadata,
) {
    val comparison: SingleImageComparisonStats
        get() = SingleImageComparisonMath.calculate(originalBytes, outputBytes)
}

fun SingleImageOutputMetadata.matches(
    plan: SingleImageResizePlan,
    request: SingleImageResizeRequest,
): Boolean =
    originalDimensions == plan.originalDimensions &&
        outputDimensions == plan.outputDimensions &&
        outputFormat == request.outputFormat &&
        encodingMatches(request) &&
        contentMode == request.contentMode &&
        metadataPolicy == request.metadataPolicy

private fun SingleImageOutputMetadata.encodingMatches(request: SingleImageResizeRequest): Boolean {
    if (request.outputFormat == SingleImageOutputFormat.Png) return encodingQuality == null
    val target = request.targetSizeBytes ?: return encodingQuality == request.encodingQuality
    return encodingQuality != null &&
        encodingQuality in 40..request.encodingQuality &&
        outputBytes?.let { it <= target } == true
}

data class SingleImageComparisonStats(
    val originalBytes: Long?,
    val outputBytes: Long?,
    val bytesSaved: Long?,
    val percentageReduction: Int?,
) {
    val reduced: Boolean
        get() = bytesSaved != null && bytesSaved >= 0L
}

object SingleImageComparisonMath {
    fun calculate(originalBytes: Long?, outputBytes: Long?): SingleImageComparisonStats {
        val validOriginal = originalBytes?.takeIf { it > 0L }
        val validOutput = outputBytes?.takeIf { it >= 0L }
        val saved = if (validOriginal != null && validOutput != null) validOriginal - validOutput else null
        val percentage = if (saved != null && validOriginal != null) {
            ((saved * 100.0) / validOriginal).roundToInt()
        } else {
            null
        }
        return SingleImageComparisonStats(
            originalBytes = validOriginal,
            outputBytes = validOutput,
            bytesSaved = saved,
            percentageReduction = percentage,
        )
    }
}

enum class SingleImageResizeWarning {
    ContentCropped,
    LargeOutput,
    WouldDownscale,
}

sealed interface SingleImagePlanResult {
    data class Valid(val plan: SingleImageResizePlan) : SingleImagePlanResult
    data class Invalid(val reason: SingleImagePlanError) : SingleImagePlanResult
}

enum class SingleImagePlanError {
    InvalidDimensions,
    InvalidOutputDimensions,
    DeviceWallpaperUnavailable,
    OutputTooLarge,
}

object SingleImageCanvasMath {
    fun geometry(
        source: ImageDimensions,
        canvas: ImageDimensions,
        contentMode: ExportContentMode,
    ): SingleImageCanvasGeometry {
        require(source.widthPx > 0 && source.heightPx > 0)
        require(canvas.widthPx > 0 && canvas.heightPx > 0)
        val sourceAspect = source.widthPx / source.heightPx.toFloat()
        val canvasAspect = canvas.widthPx / canvas.heightPx.toFloat()
        if (abs(sourceAspect - canvasAspect) <= AspectTolerance) {
            return SingleImageCanvasGeometry(
                sourceRect = NormalizedRect.Full,
                destinationRect = NormalizedRect.Full,
                cropsContent = false,
                addsPadding = false,
            )
        }

        return when (contentMode) {
            ExportContentMode.Fit -> {
                val destination = if (sourceAspect > canvasAspect) {
                    val height = canvasAspect / sourceAspect
                    NormalizedRect(0f, (1f - height) / 2f, 1f, height)
                } else {
                    val width = sourceAspect / canvasAspect
                    NormalizedRect((1f - width) / 2f, 0f, width, 1f)
                }
                SingleImageCanvasGeometry(
                    sourceRect = NormalizedRect.Full,
                    destinationRect = destination,
                    cropsContent = false,
                    addsPadding = true,
                )
            }
            ExportContentMode.Fill -> {
                val sourceRect = if (sourceAspect > canvasAspect) {
                    val width = canvasAspect / sourceAspect
                    NormalizedRect((1f - width) / 2f, 0f, width, 1f)
                } else {
                    val height = sourceAspect / canvasAspect
                    NormalizedRect(0f, (1f - height) / 2f, 1f, height)
                }
                SingleImageCanvasGeometry(
                    sourceRect = sourceRect,
                    destinationRect = NormalizedRect.Full,
                    cropsContent = true,
                    addsPadding = false,
                )
            }
        }
    }

    fun contentScale(
        source: ImageDimensions,
        canvas: ImageDimensions,
        contentMode: ExportContentMode,
    ): Float {
        require(source.widthPx > 0 && source.heightPx > 0)
        require(canvas.widthPx > 0 && canvas.heightPx > 0)
        val widthScale = canvas.widthPx / source.widthPx.toFloat()
        val heightScale = canvas.heightPx / source.heightPx.toFloat()
        return when (contentMode) {
            ExportContentMode.Fit -> min(widthScale, heightScale)
            ExportContentMode.Fill -> max(widthScale, heightScale)
        }
    }

    private const val AspectTolerance = 0.0001f
}

object SingleImageDecodeMath {
    fun targetLongEdgePx(
        original: ImageDimensions,
        output: ImageDimensions,
        geometry: SingleImageCanvasGeometry,
    ): Int {
        require(original.widthPx > 0 && original.heightPx > 0)
        require(output.widthPx > 0 && output.heightPx > 0)
        val sourceCropWidth = original.widthPx * geometry.sourceRect.width
        val sourceCropHeight = original.heightPx * geometry.sourceRect.height
        val destinationWidth = output.widthPx * geometry.destinationRect.width
        val destinationHeight = output.heightPx * geometry.destinationRect.height
        val requiredScale = max(
            destinationWidth / sourceCropWidth.coerceAtLeast(1f),
            destinationHeight / sourceCropHeight.coerceAtLeast(1f),
        ).coerceAtMost(1f)
        val requiredLongEdge = ceil(original.longEdgePx * requiredScale).toInt().coerceAtLeast(1)
        return minOf(
            original.longEdgePx,
            requiredLongEdge,
            SingleImageResizePlanner.MaxOutputLongEdgePx,
        )
    }
}

object SingleImageResizePlanner {
    const val MaxOutputLongEdgePx = 8192
    const val MaxOutputPixels = 24_000_000

    fun plan(
        original: ImageDimensions,
        request: SingleImageResizeRequest,
    ): SingleImagePlanResult {
        if (original.widthPx <= 0 || original.heightPx <= 0) {
            return SingleImagePlanResult.Invalid(SingleImagePlanError.InvalidDimensions)
        }
        val definition = ExportPresetCatalog.definition(request.preset)
        if (
            definition.canvasRule == ExportCanvasRule.DeviceWallpaper &&
            request.deviceWallpaperDimensions == null
        ) {
            return SingleImagePlanResult.Invalid(SingleImagePlanError.DeviceWallpaperUnavailable)
        }
        val output = outputDimensions(original, request, definition.canvasRule)
        if (
            definition.canvasRule == ExportCanvasRule.Custom &&
            request.lockAspectRatio &&
            request.customWidthPx != null &&
            request.customHeightPx != null
        ) {
            return SingleImagePlanResult.Invalid(SingleImagePlanError.InvalidOutputDimensions)
        }
        if (output.widthPx <= 0 || output.heightPx <= 0) {
            return SingleImagePlanResult.Invalid(SingleImagePlanError.InvalidOutputDimensions)
        }
        if (output.longEdgePx > MaxOutputLongEdgePx || output.widthPx.toLong() * output.heightPx > MaxOutputPixels) {
            return SingleImagePlanResult.Invalid(SingleImagePlanError.OutputTooLarge)
        }

        val outputPixels = output.widthPx.toLong() * output.heightPx
        val geometry = SingleImageCanvasMath.geometry(original, output, request.contentMode)
        val contentScale = SingleImageCanvasMath.contentScale(original, output, request.contentMode)
        val warnings = buildSet {
            if (geometry.cropsContent) add(SingleImageResizeWarning.ContentCropped)
            if (contentScale < 1f - ScaleTolerance) add(SingleImageResizeWarning.WouldDownscale)
            if (outputPixels > 16_000_000) add(SingleImageResizeWarning.LargeOutput)
        }
        return SingleImagePlanResult.Valid(
            SingleImageResizePlan(
                originalDimensions = original,
                outputDimensions = output,
                canvasGeometry = geometry,
                estimatedBytes = estimatedBytes(output, request.outputFormat, request.encodingQuality),
                isUpscale = contentScale > 1f + ScaleTolerance,
                warnings = warnings,
            ),
        )
    }

    private fun outputDimensions(
        original: ImageDimensions,
        request: SingleImageResizeRequest,
        canvasRule: ExportCanvasRule,
    ): ImageDimensions =
        when (canvasRule) {
            is ExportCanvasRule.Fixed -> canvasRule.dimensions
            is ExportCanvasRule.OriginalLongEdge -> dimensionsForLongEdge(original, canvasRule.longEdgePx)
            is ExportCanvasRule.OriginalScale -> ImageDimensions(
                safeScale(original.widthPx, canvasRule.factor),
                safeScale(original.heightPx, canvasRule.factor),
            )
            ExportCanvasRule.OriginalPercentage -> dimensionsForPercentage(original, request.resizePercent)
            ExportCanvasRule.DeviceWallpaper -> request.deviceWallpaperDimensions ?: ImageDimensions(0, 0)
            ExportCanvasRule.Custom -> customDimensions(original, request)
        }

    private fun safeScale(value: Int, factor: Int): Int =
        (value.toLong() * factor.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun dimensionsForPercentage(original: ImageDimensions, percent: Int): ImageDimensions {
        if (percent !in MinResizePercent..MaxResizePercent) return ImageDimensions(0, 0)
        return ImageDimensions(
            widthPx = ((original.widthPx.toLong() * percent) / 100L)
                .coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            heightPx = ((original.heightPx.toLong() * percent) / 100L)
                .coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
    }

    private fun dimensionsForLongEdge(original: ImageDimensions, longEdge: Int): ImageDimensions {
        val scale = longEdge / original.longEdgePx.toFloat()
        return ImageDimensions(
            widthPx = (original.widthPx * scale).roundToInt().coerceAtLeast(1),
            heightPx = (original.heightPx * scale).roundToInt().coerceAtLeast(1),
        )
    }

    private fun customDimensions(
        original: ImageDimensions,
        request: SingleImageResizeRequest,
    ): ImageDimensions {
        if (!request.lockAspectRatio) {
            return ImageDimensions(
                request.customWidthPx ?: 0,
                request.customHeightPx ?: 0,
            )
        }
        val aspectRatio = original.widthPx / original.heightPx.toFloat()
        return when {
            request.customWidthPx != null -> {
                val requestedWidth = request.customWidthPx
                ImageDimensions(
                    widthPx = requestedWidth,
                    heightPx = (requestedWidth / aspectRatio).roundToInt().coerceAtLeast(1),
                )
            }
            request.customHeightPx != null -> {
                val requestedHeight = request.customHeightPx
                ImageDimensions(
                    widthPx = (requestedHeight * aspectRatio).roundToInt().coerceAtLeast(1),
                    heightPx = requestedHeight,
                )
            }
            else -> ImageDimensions(0, 0)
        }
    }

    fun estimatedBytes(
        dimensions: ImageDimensions,
        format: SingleImageOutputFormat,
        encodingQuality: Int,
    ): Long {
        val pixels = dimensions.widthPx.toLong() * dimensions.heightPx
        return when (format) {
            SingleImageOutputFormat.Jpeg -> (pixels * (0.22f + encodingQuality.coerceIn(60, 100) / 100f)).toLong()
            SingleImageOutputFormat.Png -> pixels * 3L
            SingleImageOutputFormat.Webp ->
                (pixels * (0.35f + encodingQuality.coerceIn(60, 100) / 200f)).toLong()
        }.coerceAtLeast(64_000L)
    }

    private const val ScaleTolerance = 0.0001f
    const val MinResizePercent = 1
    const val MaxResizePercent = 400
}
