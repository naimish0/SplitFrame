package com.rameshta.splitframe.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.rameshta.splitframe.domain.CollageBackgroundKind
import com.rameshta.splitframe.domain.CollageBackgroundStyle
import com.rameshta.splitframe.domain.CollageBorderKind
import com.rameshta.splitframe.domain.CollageBorderStyle
import com.rameshta.splitframe.domain.CollageGradient
import com.rameshta.splitframe.domain.CollagePattern
import com.rameshta.splitframe.domain.CollageTextFont
import com.rameshta.splitframe.domain.CollageTextLayer
import com.rameshta.splitframe.domain.CollageTextMath
import com.rameshta.splitframe.domain.CropShape
import com.rameshta.splitframe.domain.CropShapePaths
import com.rameshta.splitframe.domain.NormalizedPathCommand
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

object CollageCreativeRenderer {
    fun drawBackground(
        canvas: Canvas,
        widthPx: Float,
        heightPx: Float,
        style: CollageBackgroundStyle,
        legacyGradient: CollageGradient,
        blurredBitmap: Bitmap? = null,
    ) {
        val safe = style.normalized()
        when (safe.kind) {
            CollageBackgroundKind.AdaptiveLinear -> canvas.drawLinearGradient(
                widthPx = widthPx,
                heightPx = heightPx,
                startColor = legacyGradient.startColor,
                centerColor = legacyGradient.centerColor,
                endColor = legacyGradient.endColor,
                angleDegrees = safe.angleDegrees,
            )
            CollageBackgroundKind.Solid -> canvas.drawColor(safe.primaryColor.toArgbInt())
            CollageBackgroundKind.LinearGradient -> canvas.drawLinearGradient(
                widthPx,
                heightPx,
                safe.primaryColor,
                safe.secondaryColor,
                safe.tertiaryColor,
                safe.angleDegrees,
            )
            CollageBackgroundKind.RadialGradient -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        widthPx / 2f,
                        heightPx / 2f,
                        max(widthPx, heightPx) * 0.72f,
                        intArrayOf(
                            safe.primaryColor.toArgbInt(),
                            safe.secondaryColor.toArgbInt(),
                            safe.tertiaryColor.toArgbInt(),
                        ),
                        floatArrayOf(0f, 0.58f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawRect(0f, 0f, widthPx, heightPx, paint)
            }
            CollageBackgroundKind.MediaBlur -> {
                if (blurredBitmap != null && !blurredBitmap.isRecycled) {
                    canvas.drawBitmapCenterCrop(blurredBitmap, widthPx, heightPx)
                } else {
                    canvas.drawLinearGradient(
                        widthPx,
                        heightPx,
                        legacyGradient.startColor,
                        legacyGradient.centerColor,
                        legacyGradient.endColor,
                        safe.angleDegrees,
                    )
                }
            }
            CollageBackgroundKind.Pattern -> canvas.drawPattern(widthPx, heightPx, safe)
        }
    }

    fun cropPath(
        shape: CropShape,
        bounds: RectF,
        cornerRadiusPx: Float,
    ): Path {
        if (shape == CropShape.Rectangle) {
            return Path().apply {
                addRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
            }
        }
        return Path().apply {
            CropShapePaths.commands(shape).forEach { command ->
                when (command) {
                    is NormalizedPathCommand.MoveTo -> moveTo(
                        bounds.left + command.x * bounds.width(),
                        bounds.top + command.y * bounds.height(),
                    )
                    is NormalizedPathCommand.LineTo -> lineTo(
                        bounds.left + command.x * bounds.width(),
                        bounds.top + command.y * bounds.height(),
                    )
                    is NormalizedPathCommand.CubicTo -> cubicTo(
                        bounds.left + command.control1X * bounds.width(),
                        bounds.top + command.control1Y * bounds.height(),
                        bounds.left + command.control2X * bounds.width(),
                        bounds.top + command.control2Y * bounds.height(),
                        bounds.left + command.x * bounds.width(),
                        bounds.top + command.y * bounds.height(),
                    )
                    NormalizedPathCommand.Close -> close()
                }
            }
        }
    }

    fun drawBorder(
        canvas: Canvas,
        path: Path,
        bounds: RectF,
        widthPx: Float,
        style: CollageBorderStyle,
        legacyColor: ULong,
        canvasWidthPx: Float,
    ) {
        if (!widthPx.isFinite() || widthPx <= 0f) return
        val safe = style.normalized()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = widthPx
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            when (safe.kind) {
                CollageBorderKind.LegacySolid -> color = legacyColor.toArgbInt()
                CollageBorderKind.Solid -> color = safe.primaryColor.toArgbInt()
                CollageBorderKind.LinearGradient -> shader = LinearGradient(
                    bounds.left,
                    bounds.top,
                    bounds.right,
                    bounds.bottom,
                    safe.primaryColor.toArgbInt(),
                    safe.secondaryColor.toArgbInt(),
                    Shader.TileMode.CLAMP,
                )
                CollageBorderKind.Dashed -> {
                    color = safe.primaryColor.toArgbInt()
                    val scale = canvasWidthPx.coerceAtLeast(1f) / ReferenceCanvasWidth
                    pathEffect = DashPathEffect(
                        floatArrayOf(safe.dashLengthDp * scale, safe.gapLengthDp * scale),
                        0f,
                    )
                }
            }
        }
        canvas.drawPath(path, paint)
    }

    fun drawTextLayers(
        canvas: Canvas,
        layers: List<CollageTextLayer>,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
    ) {
        layers.forEach { layer -> drawTextLayer(canvas, layer.normalized(), canvasWidthPx, canvasHeightPx) }
    }

    private fun drawTextLayer(
        canvas: Canvas,
        layer: CollageTextLayer,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
    ) {
        if (layer.text.isEmpty() || layer.opacity <= 0f) return
        val metrics = CollageTextMath.metrics(layer, canvasWidthPx, canvasHeightPx)
        val layoutWidth = (canvasWidthPx * 0.9f).roundToInt().coerceAtLeast(1)
        val typeface = layer.font.typeface()
        val fillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = layer.color.withOpacity(layer.opacity)
            textSize = metrics.textSizePx
            this.typeface = typeface
            textAlign = Paint.Align.LEFT
            style = Paint.Style.FILL
            if (metrics.shadowRadiusPx > 0f) {
                setShadowLayer(
                    metrics.shadowRadiusPx,
                    metrics.shadowOffsetXPx,
                    metrics.shadowOffsetYPx,
                    layer.shadowColor.withOpacity(layer.opacity),
                )
            }
        }
        val fillLayout = layer.staticLayout(fillPaint, layoutWidth)
        canvas.save()
        canvas.rotate(layer.rotationDegrees, metrics.centerXPx, metrics.centerYPx)
        canvas.translate(
            metrics.centerXPx - layoutWidth / 2f,
            metrics.centerYPx - fillLayout.height / 2f,
        )
        if (metrics.outlineWidthPx > 0f) {
            val outlinePaint = TextPaint(fillPaint).apply {
                clearShadowLayer()
                color = layer.outlineColor.withOpacity(layer.opacity)
                style = Paint.Style.STROKE
                strokeWidth = metrics.outlineWidthPx * 2f
                strokeJoin = Paint.Join.ROUND
            }
            layer.staticLayout(outlinePaint, layoutWidth).draw(canvas)
        }
        fillLayout.draw(canvas)
        canvas.restore()
    }

    private fun CollageTextLayer.staticLayout(paint: TextPaint, widthPx: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .build()

    private fun Canvas.drawLinearGradient(
        widthPx: Float,
        heightPx: Float,
        startColor: ULong,
        centerColor: ULong,
        endColor: ULong,
        angleDegrees: Float,
    ) {
        val radians = angleDegrees / 180f * PI.toFloat()
        val halfLength = max(widthPx, heightPx) * 0.72f
        val dx = cos(radians) * halfLength
        val dy = sin(radians) * halfLength
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                widthPx / 2f - dx,
                heightPx / 2f - dy,
                widthPx / 2f + dx,
                heightPx / 2f + dy,
                intArrayOf(startColor.toArgbInt(), centerColor.toArgbInt(), endColor.toArgbInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        drawRect(0f, 0f, widthPx, heightPx, paint)
    }

    private fun Canvas.drawPattern(widthPx: Float, heightPx: Float, style: CollageBackgroundStyle) {
        drawColor(style.primaryColor.toArgbInt())
        val scale = widthPx.coerceAtLeast(1f) / ReferenceCanvasWidth
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.secondaryColor.toArgbInt()
            strokeWidth = (2f * scale).coerceAtLeast(1f)
        }
        when (style.pattern) {
            CollagePattern.Dots -> {
                val step = (24f * scale).coerceAtLeast(8f)
                val radius = (3f * scale).coerceAtLeast(1.5f)
                var y = step / 2f
                while (y < heightPx) {
                    var x = step / 2f
                    while (x < widthPx) {
                        drawCircle(x, y, radius, paint)
                        x += step
                    }
                    y += step
                }
            }
            CollagePattern.DiagonalStripes -> {
                val step = (28f * scale).coerceAtLeast(10f)
                var start = -heightPx
                while (start < widthPx) {
                    drawLine(start, heightPx, start + heightPx, 0f, paint)
                    start += step
                }
            }
        }
    }

    private fun Canvas.drawBitmapCenterCrop(bitmap: Bitmap, widthPx: Float, heightPx: Float) {
        val sourceAspect = bitmap.width / bitmap.height.toFloat()
        val targetAspect = widthPx / heightPx.coerceAtLeast(1f)
        val source = if (sourceAspect > targetAspect) {
            val cropWidth = bitmap.height * targetAspect
            RectF((bitmap.width - cropWidth) / 2f, 0f, (bitmap.width + cropWidth) / 2f, bitmap.height.toFloat())
        } else {
            val cropHeight = bitmap.width / targetAspect
            RectF(0f, (bitmap.height - cropHeight) / 2f, bitmap.width.toFloat(), (bitmap.height + cropHeight) / 2f)
        }
        drawBitmap(
            bitmap,
            Rect(
                source.left.roundToInt().coerceIn(0, bitmap.width - 1),
                source.top.roundToInt().coerceIn(0, bitmap.height - 1),
                source.right.roundToInt().coerceIn(1, bitmap.width),
                source.bottom.roundToInt().coerceIn(1, bitmap.height),
            ),
            RectF(0f, 0f, widthPx, heightPx),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
    }

    private fun CollageTextFont.typeface(): Typeface = when (this) {
        CollageTextFont.SansSerif -> Typeface.create("sans-serif", Typeface.NORMAL)
        CollageTextFont.Serif -> Typeface.create("serif", Typeface.NORMAL)
        CollageTextFont.Monospace -> Typeface.create("monospace", Typeface.NORMAL)
    }

    private fun ULong.withOpacity(opacity: Float): Int {
        val color = toArgbInt()
        val baseAlpha = Color.alpha(color)
        val alpha = (baseAlpha * opacity.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun ULong.toArgbInt(): Int = toLong().toInt()

    private const val ReferenceCanvasWidth = 360f
}

object BitmapBlur {
    fun blur(source: Bitmap, radius: Int): Bitmap {
        val safeRadius = radius.coerceIn(1, 32)
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
            ?: error("Could not allocate blurred background.")
        return try {
            val width = output.width
            val height = output.height
            if (width > 1 && height > 1) {
                val pixels = IntArray(width * height)
                val scratch = IntArray(width * height)
                output.getPixels(pixels, 0, width, 0, 0, width, height)
                boxBlurHorizontal(pixels, scratch, width, height, safeRadius)
                boxBlurVertical(scratch, pixels, width, height, safeRadius)
                output.setPixels(pixels, 0, width, 0, 0, width, height)
            }
            output
        } catch (failure: Throwable) {
            if (!output.isRecycled) runCatching { output.recycle() }
            throw failure
        }
    }

    private fun boxBlurHorizontal(source: IntArray, target: IntArray, width: Int, height: Int, radius: Int) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                var alpha = 0
                var red = 0
                var green = 0
                var blue = 0
                var count = 0
                for (sampleX in maxOf(0, x - radius)..minOf(width - 1, x + radius)) {
                    val color = source[y * width + sampleX]
                    alpha += Color.alpha(color)
                    red += Color.red(color)
                    green += Color.green(color)
                    blue += Color.blue(color)
                    count++
                }
                target[y * width + x] = Color.argb(alpha / count, red / count, green / count, blue / count)
            }
        }
    }

    private fun boxBlurVertical(source: IntArray, target: IntArray, width: Int, height: Int, radius: Int) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                var alpha = 0
                var red = 0
                var green = 0
                var blue = 0
                var count = 0
                for (sampleY in maxOf(0, y - radius)..minOf(height - 1, y + radius)) {
                    val color = source[sampleY * width + x]
                    alpha += Color.alpha(color)
                    red += Color.red(color)
                    green += Color.green(color)
                    blue += Color.blue(color)
                    count++
                }
                target[y * width + x] = Color.argb(alpha / count, red / count, green / count, blue / count)
            }
        }
    }
}
