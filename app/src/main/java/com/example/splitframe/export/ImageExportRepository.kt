package com.example.splitframe.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.splitframe.domain.CollageGradient
import com.example.splitframe.domain.ExportResult
import com.example.splitframe.domain.ImageDimensions
import com.example.splitframe.domain.ImageSource
import com.example.splitframe.domain.ImageTransform
import com.example.splitframe.domain.LayoutMath
import com.example.splitframe.domain.MergeProject
import com.example.splitframe.domain.RectPx
import com.example.splitframe.domain.TemplateKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

class ImageExportRepository(
    private val context: Context,
    private val imageSourceReader: ImageSourceReader,
) {
    fun export(project: MergeProject): ExportResult {
        val dimensions = project.assignedImages.mapNotNull { (cell, source) ->
            imageSourceReader.dimensions(source)?.let { cell to it }
        }.toMap()
        val outputSize = LayoutMath.outputSizeForResolution(project.template, project.exportResolution, dimensions)
        return try {
            val rendered = render(project, outputSize.widthPx, outputSize.heightPx)
            val savedUri = saveBitmap(rendered)
            rendered.recycle()
            ExportResult.Success(savedUri.toString())
        } catch (oom: OutOfMemoryError) {
            ExportResult.Failure("Not enough memory for this export size.")
        } catch (throwable: Throwable) {
            ExportResult.Failure(throwable.message ?: "Unknown export error.")
        }
    }

    private fun render(
        project: MergeProject,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap {
        val output = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = project.backgroundColor.toArgbInt()
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = project.borderColor.toArgbInt()
            strokeWidth = project.borderWidthDp
        }

        canvas.drawGradient(project.backgroundGradient, widthPx.toFloat(), heightPx.toFloat())
        val spacingPx = project.spacingDp
        val cornerRadiusPx = project.cornerRadiusDp
        val targetDecodeEdge = widthPx.coerceAtLeast(heightPx)

        if (project.template.kind == TemplateKind.BeforeAfter) {
            val drewAll = drawBeforeAfter(
                canvas = canvas,
                project = project,
                widthPx = widthPx,
                heightPx = heightPx,
                cornerRadiusPx = cornerRadiusPx,
                fillPaint = fillPaint,
                borderPaint = borderPaint,
                targetDecodeEdge = targetDecodeEdge,
            )
            if (!drewAll) {
                output.recycle()
                error("Could not decode one or more photos.")
            }
        } else {
            project.template.cells.forEach { cell ->
                val rect = LayoutMath.cellFrame(cell, widthPx.toFloat(), heightPx.toFloat(), spacingPx)
                val drewCell = drawCell(
                    canvas = canvas,
                    source = project.assignedImages[cell.index],
                    transform = project.imageTransforms[cell.index] ?: ImageTransform.Default,
                    rect = rect,
                    cornerRadiusPx = cornerRadiusPx,
                    fillPaint = fillPaint,
                    borderPaint = borderPaint,
                    targetDecodeEdge = targetDecodeEdge,
                )
                if (!drewCell) {
                    output.recycle()
                    error("Could not decode one or more photos.")
                }
            }
        }
        return output
    }

    private fun drawBeforeAfter(
        canvas: Canvas,
        project: MergeProject,
        widthPx: Int,
        heightPx: Int,
        cornerRadiusPx: Float,
        fillPaint: Paint,
        borderPaint: Paint,
        targetDecodeEdge: Int,
    ): Boolean {
        val rect = RectPx(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        val roundedPath = Path().apply {
            addRoundRect(RectF(rect.left, rect.top, rect.right, rect.bottom), cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(roundedPath)
        canvas.drawRect(RectF(rect.left, rect.top, rect.right, rect.bottom), fillPaint)

        val dividerX = widthPx * project.beforeAfterSlider.coerceIn(0.05f, 0.95f)
        canvas.save()
        canvas.clipRect(0f, 0f, dividerX, heightPx.toFloat())
        val drewLeft = drawSourceCrop(
            canvas = canvas,
            source = project.assignedImages[0],
            transform = project.imageTransforms[0] ?: ImageTransform.Default,
            rect = rect,
            targetDecodeEdge = targetDecodeEdge,
        )
        canvas.restore()

        canvas.save()
        canvas.clipRect(dividerX, 0f, widthPx.toFloat(), heightPx.toFloat())
        val drewRight = drawSourceCrop(
            canvas = canvas,
            source = project.assignedImages[1],
            transform = project.imageTransforms[1] ?: ImageTransform.Default,
            rect = rect,
            targetDecodeEdge = targetDecodeEdge,
        )
        canvas.restore()

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 5f
        }
        canvas.drawLine(dividerX, 0f, dividerX, heightPx.toFloat(), dividerPaint)
        canvas.restore()
        canvas.drawRoundRect(RectF(rect.left, rect.top, rect.right, rect.bottom), cornerRadiusPx, cornerRadiusPx, borderPaint)
        return drewLeft && drewRight
    }

    private fun drawCell(
        canvas: Canvas,
        source: ImageSource?,
        transform: ImageTransform,
        rect: RectPx,
        cornerRadiusPx: Float,
        fillPaint: Paint,
        borderPaint: Paint,
        targetDecodeEdge: Int,
    ): Boolean {
        val androidRect = RectF(rect.left, rect.top, rect.right, rect.bottom)
        val path = Path().apply {
            addRoundRect(androidRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawRect(androidRect, fillPaint)
        val drewSource = drawSourceCrop(canvas, source, transform, rect, targetDecodeEdge)
        canvas.restore()
        canvas.drawRoundRect(androidRect, cornerRadiusPx, cornerRadiusPx, borderPaint)
        return drewSource
    }

    private fun drawSourceCrop(
        canvas: Canvas,
        source: ImageSource?,
        transform: ImageTransform,
        rect: RectPx,
        targetDecodeEdge: Int,
    ): Boolean {
        if (source == null) return false
        val bitmap = imageSourceReader.decodeBitmap(source, targetDecodeEdge) ?: return false
        return try {
            drawBitmapCrop(canvas, bitmap, transform, rect)
            true
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawBitmapCrop(
        canvas: Canvas,
        bitmap: Bitmap?,
        transform: ImageTransform,
        rect: RectPx,
    ) {
        if (bitmap == null) return
        val source = LayoutMath.cropToFillSourceRect(
            sourceWidthPx = bitmap.width.toFloat(),
            sourceHeightPx = bitmap.height.toFloat(),
            destinationWidthPx = rect.width,
            destinationHeightPx = rect.height,
            transform = transform,
        )
        val srcRect = Rect(
            source.left.toInt(),
            source.top.toInt(),
            ceil(source.right).toInt(),
            ceil(source.bottom).toInt(),
        )
        val dstRect = RectF(rect.left, rect.top, rect.right, rect.bottom)
        canvas.drawBitmap(bitmap, srcRect, dstRect, null)
    }

    private fun saveBitmap(bitmap: Bitmap): Uri {
        val resolver = context.contentResolver
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "SplitFrame_$timestamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SplitFrame")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = requireNotNull(resolver.insert(collection, values)) {
            "Could not create MediaStore entry."
        }
        resolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)
        } ?: error("Could not open export stream.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    private fun ULong.toArgbInt(): Int = toLong().toInt()

    private fun Canvas.drawGradient(gradient: CollageGradient, widthPx: Float, heightPx: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                widthPx,
                heightPx,
                intArrayOf(
                    gradient.startColor.toArgbInt(),
                    gradient.centerColor.toArgbInt(),
                    gradient.endColor.toArgbInt(),
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        drawRect(0f, 0f, widthPx, heightPx, paint)
    }
}
