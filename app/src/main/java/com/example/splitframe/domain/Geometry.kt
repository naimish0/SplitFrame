package com.example.splitframe.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class NormalizedRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height

    companion object {
        val Full = NormalizedRect(0f, 0f, 1f, 1f)
    }
}

data class RectPx(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

data class OutputSize(
    val widthPx: Int,
    val heightPx: Int,
)

data class ImageDimensions(
    val widthPx: Int,
    val heightPx: Int,
) {
    val longEdgePx: Int get() = max(widthPx, heightPx)
}

object LayoutMath {
    fun cellFrame(
        cell: LayoutCell,
        outputWidthPx: Float,
        outputHeightPx: Float,
        spacingPx: Float,
    ): RectPx {
        val baseLeft = cell.rect.x * outputWidthPx
        val baseTop = cell.rect.y * outputHeightPx
        val baseRight = cell.rect.right * outputWidthPx
        val baseBottom = cell.rect.bottom * outputHeightPx
        val outerGap = spacingPx
        val innerGap = spacingPx / 2f

        val leftInset = if (cell.rect.x <= 0f) outerGap else innerGap
        val topInset = if (cell.rect.y <= 0f) outerGap else innerGap
        val rightInset = if (cell.rect.right >= 1f) outerGap else innerGap
        val bottomInset = if (cell.rect.bottom >= 1f) outerGap else innerGap

        return RectPx(
            left = min(baseRight, baseLeft + leftInset),
            top = min(baseBottom, baseTop + topInset),
            right = max(baseLeft, baseRight - rightInset),
            bottom = max(baseTop, baseBottom - bottomInset),
        )
    }

    fun cropToFillSourceRect(
        sourceWidthPx: Float,
        sourceHeightPx: Float,
        destinationWidthPx: Float,
        destinationHeightPx: Float,
    ): RectPx =
        cropToFillSourceRect(
            sourceWidthPx = sourceWidthPx,
            sourceHeightPx = sourceHeightPx,
            destinationWidthPx = destinationWidthPx,
            destinationHeightPx = destinationHeightPx,
            transform = ImageTransform.Default,
        )

    fun cropToFillSourceRect(
        sourceWidthPx: Float,
        sourceHeightPx: Float,
        destinationWidthPx: Float,
        destinationHeightPx: Float,
        transform: ImageTransform,
    ): RectPx {
        if (
            sourceWidthPx <= 0f ||
            sourceHeightPx <= 0f ||
            destinationWidthPx <= 0f ||
            destinationHeightPx <= 0f
        ) {
            return RectPx(0f, 0f, sourceWidthPx, sourceHeightPx)
        }

        val scale = max(destinationWidthPx / sourceWidthPx, destinationHeightPx / sourceHeightPx)
        val baseCropWidth = destinationWidthPx / scale
        val baseCropHeight = destinationHeightPx / scale
        val normalized = transform.normalized()
        val cropWidth = (baseCropWidth / normalized.zoom).coerceIn(1f, sourceWidthPx)
        val cropHeight = (baseCropHeight / normalized.zoom).coerceIn(1f, sourceHeightPx)
        val maxCenterShiftX = max(0f, (sourceWidthPx - cropWidth) / 2f)
        val maxCenterShiftY = max(0f, (sourceHeightPx - cropHeight) / 2f)
        val centerX = (sourceWidthPx / 2f) + normalized.panX * maxCenterShiftX
        val centerY = (sourceHeightPx / 2f) + normalized.panY * maxCenterShiftY
        val left = (centerX - cropWidth / 2f).coerceIn(0f, sourceWidthPx - cropWidth)
        val top = (centerY - cropHeight / 2f).coerceIn(0f, sourceHeightPx - cropHeight)

        return RectPx(
            left = left,
            top = top,
            right = left + cropWidth,
            bottom = top + cropHeight,
        )
    }

    fun transformAfterGesture(
        sourceDimensions: ImageDimensions?,
        destinationWidthPx: Float,
        destinationHeightPx: Float,
        current: ImageTransform,
        panXpx: Float,
        panYpx: Float,
        zoomChange: Float,
    ): ImageTransform {
        val nextZoom = (current.zoom * zoomChange).coerceIn(ImageTransform.MIN_ZOOM, ImageTransform.MAX_ZOOM)
        val next = current.copy(zoom = nextZoom).normalized()
        if (destinationWidthPx <= 0f || destinationHeightPx <= 0f) return next
        val dimensions = sourceDimensions ?: return next.copy(
            panX = next.panX - (panXpx / max(1f, destinationWidthPx)) * 2f,
            panY = next.panY - (panYpx / max(1f, destinationHeightPx)) * 2f,
        ).normalized()

        val crop = cropToFillSourceRect(
            sourceWidthPx = dimensions.widthPx.toFloat(),
            sourceHeightPx = dimensions.heightPx.toFloat(),
            destinationWidthPx = destinationWidthPx,
            destinationHeightPx = destinationHeightPx,
            transform = next,
        )
        val drawScale = destinationWidthPx / crop.width.coerceAtLeast(1f)
        val maxCenterShiftX = max(0f, (dimensions.widthPx.toFloat() - crop.width) / 2f)
        val maxCenterShiftY = max(0f, (dimensions.heightPx.toFloat() - crop.height) / 2f)
        val panDeltaX = if (maxCenterShiftX > 0f) {
            (-panXpx / drawScale) / maxCenterShiftX
        } else {
            0f
        }
        val panDeltaY = if (maxCenterShiftY > 0f) {
            (-panYpx / drawScale) / maxCenterShiftY
        } else {
            0f
        }
        return next.copy(
            panX = next.panX + panDeltaX,
            panY = next.panY + panDeltaY,
        ).normalized()
    }

    fun transformAfterDoubleTap(
        sourceDimensions: ImageDimensions?,
        destinationWidthPx: Float,
        destinationHeightPx: Float,
        current: ImageTransform,
        tapXInFramePx: Float,
        tapYInFramePx: Float,
    ): ImageTransform {
        if (current.normalized().zoom > 1.05f) return ImageTransform.Default
        val target = ImageTransform(zoom = 2.2f).normalized()
        val dimensions = sourceDimensions ?: return target
        if (destinationWidthPx <= 0f || destinationHeightPx <= 0f) return target

        val currentCrop = cropToFillSourceRect(
            sourceWidthPx = dimensions.widthPx.toFloat(),
            sourceHeightPx = dimensions.heightPx.toFloat(),
            destinationWidthPx = destinationWidthPx,
            destinationHeightPx = destinationHeightPx,
            transform = current,
        )
        val sourceTapX = currentCrop.left + (tapXInFramePx / destinationWidthPx).coerceIn(0f, 1f) * currentCrop.width
        val sourceTapY = currentCrop.top + (tapYInFramePx / destinationHeightPx).coerceIn(0f, 1f) * currentCrop.height
        val targetCrop = cropToFillSourceRect(
            sourceWidthPx = dimensions.widthPx.toFloat(),
            sourceHeightPx = dimensions.heightPx.toFloat(),
            destinationWidthPx = destinationWidthPx,
            destinationHeightPx = destinationHeightPx,
            transform = target,
        )
        val maxCenterShiftX = max(0f, (dimensions.widthPx.toFloat() - targetCrop.width) / 2f)
        val maxCenterShiftY = max(0f, (dimensions.heightPx.toFloat() - targetCrop.height) / 2f)
        return target.copy(
            panX = if (maxCenterShiftX > 0f) {
                (sourceTapX - dimensions.widthPx / 2f) / maxCenterShiftX
            } else {
                0f
            },
            panY = if (maxCenterShiftY > 0f) {
                (sourceTapY - dimensions.heightPx / 2f) / maxCenterShiftY
            } else {
                0f
            },
        ).normalized()
    }

    fun outputSizeForResolution(
        template: LayoutTemplate,
        resolution: ExportResolution,
        sourceDimensions: Map<Int, ImageDimensions>,
    ): OutputSize {
        if (resolution == ExportResolution.ORIGINAL && sourceDimensions.isNotEmpty()) {
            return originalOutputSize(template, sourceDimensions)
        }

        val longEdge = if (resolution.longEdgePx > 0) {
            resolution.longEdgePx
        } else {
            ExportResolution.FHD_1080.longEdgePx
        }
        val aspectRatio = template.aspectRatio.coerceAtLeast(0.1f)
        return if (aspectRatio >= 1f) {
            OutputSize(
                widthPx = longEdge,
                heightPx = (longEdge / aspectRatio).roundToInt().coerceAtLeast(1),
            )
        } else {
            OutputSize(
                widthPx = (longEdge * aspectRatio).roundToInt().coerceAtLeast(1),
                heightPx = longEdge,
            )
        }
    }

    fun estimatedJpegSizeBytes(size: OutputSize): Long {
        val pixels = size.widthPx.toLong() * size.heightPx.toLong()
        return (pixels * 1.2f).toLong().coerceAtLeast(120_000L)
    }

    private fun originalOutputSize(
        template: LayoutTemplate,
        sourceDimensions: Map<Int, ImageDimensions>,
    ): OutputSize {
        val aspectRatio = template.aspectRatio.coerceAtLeast(0.1f)
        var requiredLongEdge = 0f
        template.cells.forEach { cell ->
            val dimensions = sourceDimensions[cell.index] ?: return@forEach
            val requiredCanvasWidth = dimensions.widthPx / cell.rect.width.coerceAtLeast(0.01f)
            val requiredCanvasHeight = dimensions.heightPx / cell.rect.height.coerceAtLeast(0.01f)
            val requiredSize = if (aspectRatio >= 1f) {
                max(requiredCanvasWidth, requiredCanvasHeight * aspectRatio)
            } else {
                max(requiredCanvasHeight, requiredCanvasWidth / aspectRatio)
            }
            requiredLongEdge = max(requiredLongEdge, requiredSize)
        }

        val longEdge = requiredLongEdge.roundToInt().coerceIn(480, 8192)
        return if (aspectRatio >= 1f) {
            OutputSize(longEdge, (longEdge / aspectRatio).roundToInt().coerceAtLeast(1))
        } else {
            OutputSize((longEdge * aspectRatio).roundToInt().coerceAtLeast(1), longEdge)
        }
    }
}
