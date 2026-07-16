package com.example.splitframe.domain

import kotlin.math.roundToInt

enum class SingleImageQualityMode {
    Standard,
    Enhanced,
}

enum class SingleImageResizePreset {
    LongEdge1080,
    LongEdge2K,
    LongEdge4K,
    Scale2x,
    Scale4x,
    Custom,
}

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
    val qualityMode: SingleImageQualityMode = SingleImageQualityMode.Standard,
    val outputFormat: SingleImageOutputFormat = SingleImageOutputFormat.Jpeg,
    val jpegQuality: Int = 94,
    val customWidthPx: Int? = null,
    val customHeightPx: Int? = null,
    val lockAspectRatio: Boolean = true,
)

data class SingleImageResizePlan(
    val originalDimensions: ImageDimensions,
    val outputDimensions: ImageDimensions,
    val estimatedBytes: Long,
    val isUpscale: Boolean,
    val warning: SingleImageResizeWarning? = null,
)

enum class SingleImageResizeWarning {
    AspectRatioUnlocked,
    LargeOutput,
    WouldDownscale,
}

sealed interface SingleImagePlanResult {
    data class Valid(val plan: SingleImageResizePlan) : SingleImagePlanResult
    data class Invalid(val reason: SingleImagePlanError) : SingleImagePlanResult
}

enum class SingleImagePlanError {
    InvalidDimensions,
    OutputTooLarge,
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
        val output = outputDimensions(original, request)
        if (output.widthPx <= 0 || output.heightPx <= 0) {
            return SingleImagePlanResult.Invalid(SingleImagePlanError.InvalidDimensions)
        }
        if (output.longEdgePx > MaxOutputLongEdgePx || output.widthPx.toLong() * output.heightPx > MaxOutputPixels) {
            return SingleImagePlanResult.Invalid(SingleImagePlanError.OutputTooLarge)
        }

        val originalPixels = original.widthPx.toLong() * original.heightPx
        val outputPixels = output.widthPx.toLong() * output.heightPx
        val warning = when {
            !request.lockAspectRatio -> SingleImageResizeWarning.AspectRatioUnlocked
            outputPixels < originalPixels -> SingleImageResizeWarning.WouldDownscale
            outputPixels > 16_000_000 -> SingleImageResizeWarning.LargeOutput
            else -> null
        }
        return SingleImagePlanResult.Valid(
            SingleImageResizePlan(
                originalDimensions = original,
                outputDimensions = output,
                estimatedBytes = estimatedBytes(output, request.outputFormat, request.jpegQuality),
                isUpscale = outputPixels > originalPixels,
                warning = warning,
            ),
        )
    }

    private fun outputDimensions(
        original: ImageDimensions,
        request: SingleImageResizeRequest,
    ): ImageDimensions =
        when (request.preset) {
            SingleImageResizePreset.LongEdge1080 -> dimensionsForLongEdge(original, 1080)
            SingleImageResizePreset.LongEdge2K -> dimensionsForLongEdge(original, 2560)
            SingleImageResizePreset.LongEdge4K -> dimensionsForLongEdge(original, 3840)
            SingleImageResizePreset.Scale2x -> ImageDimensions(original.widthPx * 2, original.heightPx * 2)
            SingleImageResizePreset.Scale4x -> ImageDimensions(original.widthPx * 4, original.heightPx * 4)
            SingleImageResizePreset.Custom -> customDimensions(original, request)
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
        val requestedWidth = request.customWidthPx ?: original.widthPx
        val requestedHeight = request.customHeightPx ?: original.heightPx
        if (!request.lockAspectRatio) {
            return ImageDimensions(requestedWidth, requestedHeight)
        }
        val aspectRatio = original.widthPx / original.heightPx.toFloat()
        return if (request.customWidthPx != null) {
            ImageDimensions(
                widthPx = requestedWidth,
                heightPx = (requestedWidth / aspectRatio).roundToInt().coerceAtLeast(1),
            )
        } else {
            ImageDimensions(
                widthPx = (requestedHeight * aspectRatio).roundToInt().coerceAtLeast(1),
                heightPx = requestedHeight,
            )
        }
    }

    fun estimatedBytes(
        dimensions: ImageDimensions,
        format: SingleImageOutputFormat,
        jpegQuality: Int,
    ): Long {
        val pixels = dimensions.widthPx.toLong() * dimensions.heightPx
        return when (format) {
            SingleImageOutputFormat.Jpeg -> (pixels * (0.22f + jpegQuality.coerceIn(60, 100) / 100f)).toLong()
            SingleImageOutputFormat.Png -> pixels * 3L
            SingleImageOutputFormat.Webp -> (pixels * 0.85f).toLong()
        }.coerceAtLeast(64_000L)
    }
}
