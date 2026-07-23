package com.rameshta.splitframe.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

enum class CollageBackgroundKind {
    AdaptiveLinear,
    Solid,
    LinearGradient,
    RadialGradient,
    MediaBlur,
    Pattern,
}

enum class CollagePattern {
    Dots,
    DiagonalStripes,
}

data class CollageBackgroundStyle(
    val kind: CollageBackgroundKind = CollageBackgroundKind.AdaptiveLinear,
    val primaryColor: ULong = CollageGradient.Neutral.startColor,
    val secondaryColor: ULong = CollageGradient.Neutral.centerColor,
    val tertiaryColor: ULong = CollageGradient.Neutral.endColor,
    val angleDegrees: Float = 45f,
    val blurSourceCellIndex: Int? = null,
    val blurRadius: Int = 16,
    val pattern: CollagePattern = CollagePattern.Dots,
) {
    fun normalized(): CollageBackgroundStyle = copy(
        angleDegrees = angleDegrees.takeIf(Float::isFinite)?.coerceIn(-180f, 180f) ?: 45f,
        blurRadius = blurRadius.coerceIn(1, 32),
    )
}

enum class CollageBorderKind {
    LegacySolid,
    Solid,
    LinearGradient,
    Dashed,
}

data class CollageBorderStyle(
    val kind: CollageBorderKind = CollageBorderKind.LegacySolid,
    val primaryColor: ULong = 0xFF000000uL,
    val secondaryColor: ULong = 0xFFFFFFFFuL,
    val dashLengthDp: Float = 10f,
    val gapLengthDp: Float = 6f,
) {
    fun normalized(): CollageBorderStyle = copy(
        dashLengthDp = dashLengthDp.takeIf(Float::isFinite)?.coerceIn(1f, 48f) ?: 10f,
        gapLengthDp = gapLengthDp.takeIf(Float::isFinite)?.coerceIn(1f, 48f) ?: 6f,
    )
}

enum class CropShape {
    Rectangle,
    Circle,
    Heart,
    Hexagon,
    Star,
}

enum class CollageTextFont {
    SansSerif,
    Serif,
    Monospace,
}

data class CollageTextLayer(
    val id: String,
    val text: String,
    val font: CollageTextFont = CollageTextFont.SansSerif,
    val fontSize: Float = 32f,
    val color: ULong = 0xFFFFFFFFuL,
    val outlineColor: ULong = 0xFF000000uL,
    val outlineWidth: Float = 0f,
    val shadowColor: ULong = 0x99000000uL,
    val shadowRadius: Float = 0f,
    val shadowOffsetX: Float = 0f,
    val shadowOffsetY: Float = 0f,
    val opacity: Float = 1f,
    val rotationDegrees: Float = 0f,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val scale: Float = 1f,
) {
    fun normalized(): CollageTextLayer = copy(
        text = text.take(MaxTextLength),
        fontSize = fontSize.finiteOr(32f).coerceIn(8f, 144f),
        outlineWidth = outlineWidth.finiteOr(0f).coerceIn(0f, 12f),
        shadowRadius = shadowRadius.finiteOr(0f).coerceIn(0f, 24f),
        shadowOffsetX = shadowOffsetX.finiteOr(0f).coerceIn(-32f, 32f),
        shadowOffsetY = shadowOffsetY.finiteOr(0f).coerceIn(-32f, 32f),
        opacity = opacity.finiteOr(1f).coerceIn(0f, 1f),
        rotationDegrees = rotationDegrees.finiteOr(0f).coerceIn(-180f, 180f),
        centerX = centerX.finiteOr(0.5f).coerceIn(0f, 1f),
        centerY = centerY.finiteOr(0.5f).coerceIn(0f, 1f),
        scale = scale.finiteOr(1f).coerceIn(0.25f, 4f),
    )

    companion object {
        const val MaxTextLength = 1_000
    }
}

data class CollageTextRenderMetrics(
    val centerXPx: Float,
    val centerYPx: Float,
    val textSizePx: Float,
    val outlineWidthPx: Float,
    val shadowRadiusPx: Float,
    val shadowOffsetXPx: Float,
    val shadowOffsetYPx: Float,
)

object CollageTextMath {
    fun metrics(
        layer: CollageTextLayer,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
    ): CollageTextRenderMetrics {
        val safe = layer.normalized()
        val scale = canvasWidthPx.coerceAtLeast(1f) / ReferenceCanvasWidth
        return CollageTextRenderMetrics(
            centerXPx = safe.centerX * canvasWidthPx,
            centerYPx = safe.centerY * canvasHeightPx,
            textSizePx = safe.fontSize * safe.scale * scale,
            outlineWidthPx = safe.outlineWidth * scale,
            shadowRadiusPx = safe.shadowRadius * scale,
            shadowOffsetXPx = safe.shadowOffsetX * scale,
            shadowOffsetYPx = safe.shadowOffsetY * scale,
        )
    }

    private const val ReferenceCanvasWidth = 360f
}

sealed interface NormalizedPathCommand {
    data class MoveTo(val x: Float, val y: Float) : NormalizedPathCommand
    data class LineTo(val x: Float, val y: Float) : NormalizedPathCommand
    data class CubicTo(
        val control1X: Float,
        val control1Y: Float,
        val control2X: Float,
        val control2Y: Float,
        val x: Float,
        val y: Float,
    ) : NormalizedPathCommand
    data object Close : NormalizedPathCommand
}

object CropShapePaths {
    fun commands(shape: CropShape): List<NormalizedPathCommand> = when (shape) {
        CropShape.Rectangle -> listOf(
            NormalizedPathCommand.MoveTo(0f, 0f),
            NormalizedPathCommand.LineTo(1f, 0f),
            NormalizedPathCommand.LineTo(1f, 1f),
            NormalizedPathCommand.LineTo(0f, 1f),
            NormalizedPathCommand.Close,
        )
        CropShape.Circle -> circle()
        CropShape.Heart -> heart()
        CropShape.Hexagon -> polygon(sides = 6, rotationRadians = PI / 6.0)
        CropShape.Star -> star()
    }

    private fun circle(): List<NormalizedPathCommand> {
        val k = 0.2761424f
        return listOf(
            NormalizedPathCommand.MoveTo(0.5f, 0f),
            NormalizedPathCommand.CubicTo(0.5f + k, 0f, 1f, 0.5f - k, 1f, 0.5f),
            NormalizedPathCommand.CubicTo(1f, 0.5f + k, 0.5f + k, 1f, 0.5f, 1f),
            NormalizedPathCommand.CubicTo(0.5f - k, 1f, 0f, 0.5f + k, 0f, 0.5f),
            NormalizedPathCommand.CubicTo(0f, 0.5f - k, 0.5f - k, 0f, 0.5f, 0f),
            NormalizedPathCommand.Close,
        )
    }

    private fun heart(): List<NormalizedPathCommand> = listOf(
        NormalizedPathCommand.MoveTo(0.5f, 0.95f),
        NormalizedPathCommand.CubicTo(0.42f, 0.82f, 0.05f, 0.62f, 0.05f, 0.32f),
        NormalizedPathCommand.CubicTo(0.05f, 0.08f, 0.34f, 0.02f, 0.5f, 0.24f),
        NormalizedPathCommand.CubicTo(0.66f, 0.02f, 0.95f, 0.08f, 0.95f, 0.32f),
        NormalizedPathCommand.CubicTo(0.95f, 0.62f, 0.58f, 0.82f, 0.5f, 0.95f),
        NormalizedPathCommand.Close,
    )

    private fun polygon(sides: Int, rotationRadians: Double): List<NormalizedPathCommand> {
        val points = (0 until sides).map { index ->
            val angle = rotationRadians + 2.0 * PI * index / sides
            (0.5f + 0.48f * cos(angle).toFloat()) to (0.5f + 0.48f * sin(angle).toFloat())
        }
        return buildList {
            add(NormalizedPathCommand.MoveTo(points.first().first, points.first().second))
            points.drop(1).forEach { (x, y) -> add(NormalizedPathCommand.LineTo(x, y)) }
            add(NormalizedPathCommand.Close)
        }
    }

    private fun star(): List<NormalizedPathCommand> {
        val points = (0 until 10).map { index ->
            val radius = if (index % 2 == 0) 0.48f else 0.22f
            val angle = -PI / 2.0 + PI * index / 5.0
            (0.5f + radius * cos(angle).toFloat()) to (0.5f + radius * sin(angle).toFloat())
        }
        return buildList {
            add(NormalizedPathCommand.MoveTo(points.first().first, points.first().second))
            points.drop(1).forEach { (x, y) -> add(NormalizedPathCommand.LineTo(x, y)) }
            add(NormalizedPathCommand.Close)
        }
    }
}

object AutoArrangeMath {
    /** Maps each target cell to the old cell whose media should move there. */
    fun assignments(
        template: LayoutTemplate,
        occupiedCellIndices: List<Int>,
        dimensionsByCell: Map<Int, ImageDimensions>,
    ): Map<Int, Int> {
        val sourceCells = occupiedCellIndices.distinct().filter { source ->
            template.cells.any { it.index == source }
        }
        if (sourceCells.isEmpty()) return emptyMap()
        val targetCells = template.cells.sortedBy(LayoutCell::index)
        val costs = sourceCells.mapIndexed { sourceOrder, sourceCell ->
            val sourceAspect = dimensionsByCell[sourceCell]?.let { dimensions ->
                dimensions.widthPx.toFloat() / dimensions.heightPx.coerceAtLeast(1)
            }
            targetCells.mapIndexed { targetOrder, targetCell ->
                val cellAspect = template.aspectRatio *
                    targetCell.rect.width.coerceAtLeast(0.0001f) /
                    targetCell.rect.height.coerceAtLeast(0.0001f)
                val shapeCost = sourceAspect?.let { abs(ln((it / cellAspect).coerceAtLeast(0.0001f))) }
                    ?: 1f
                shapeCost + abs(sourceOrder - targetOrder) * 0.0001f
            }
        }
        val maskCount = 1 shl targetCells.size
        val memo = Array(sourceCells.size + 1) { FloatArray(maskCount) { Float.NaN } }
        val choice = Array(sourceCells.size) { IntArray(maskCount) { -1 } }

        fun solve(sourceOrder: Int, usedMask: Int): Float {
            if (sourceOrder == sourceCells.size) return 0f
            memo[sourceOrder][usedMask].takeUnless(Float::isNaN)?.let { return it }
            var best = Float.POSITIVE_INFINITY
            var bestTarget = -1
            targetCells.indices.forEach { targetOrder ->
                val bit = 1 shl targetOrder
                if (usedMask and bit != 0) return@forEach
                val candidate = costs[sourceOrder][targetOrder] + solve(sourceOrder + 1, usedMask or bit)
                if (candidate < best - TieTolerance) {
                    best = candidate
                    bestTarget = targetOrder
                }
            }
            memo[sourceOrder][usedMask] = best
            choice[sourceOrder][usedMask] = bestTarget
            return best
        }

        solve(0, 0)
        val result = linkedMapOf<Int, Int>()
        var usedMask = 0
        sourceCells.indices.forEach { sourceOrder ->
            val targetOrder = choice[sourceOrder][usedMask]
            if (targetOrder >= 0) {
                result[targetCells[targetOrder].index] = sourceCells[sourceOrder]
                usedMask = usedMask or (1 shl targetOrder)
            }
        }
        return result
    }

    private const val TieTolerance = 0.000001f
}

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback
