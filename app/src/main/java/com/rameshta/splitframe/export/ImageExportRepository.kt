package com.rameshta.splitframe.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.rameshta.splitframe.domain.CollageGradient
import com.rameshta.splitframe.domain.CollageBackgroundKind
import com.rameshta.splitframe.domain.CropShape
import com.rameshta.splitframe.domain.ExportResult
import com.rameshta.splitframe.domain.ImageDimensions
import com.rameshta.splitframe.domain.ImageSource
import com.rameshta.splitframe.domain.ImageTransform
import com.rameshta.splitframe.domain.LayoutMath
import com.rameshta.splitframe.domain.MergeProject
import com.rameshta.splitframe.domain.RectPx
import com.rameshta.splitframe.domain.TemplateKind
import com.rameshta.splitframe.render.BitmapBlur
import com.rameshta.splitframe.render.CollageCreativeRenderer
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class ImageExportRepository(
    private val context: Context,
    private val imageSourceReader: ImageSourceReader,
) {
    suspend fun export(project: MergeProject): ExportResult {
        var rendered: Bitmap? = null
        return try {
            currentCoroutineContext().ensureActive()
            val dimensions = project.assignedImages.mapNotNull { (cell, source) ->
                imageSourceReader.dimensions(source)?.let { cell to it }
            }.toMap()
            val outputSize = LayoutMath.outputSizeForResolution(project.template, project.exportResolution, dimensions)
            val output = render(project, outputSize.widthPx, outputSize.heightPx)
            rendered = output
            val savedUri = saveBitmap(output)
            ExportResult.Success(savedUri.toString())
        } catch (oom: OutOfMemoryError) {
            ExportResult.Failure("Not enough memory for this export size.")
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ExportResult.Failure(actionableExportFailure(throwable, "Unknown export error."))
        } finally {
            rendered?.recycleBestEffort()
        }
    }

    private suspend fun render(
        project: MergeProject,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap {
        val processingContext = currentCoroutineContext()
        processingContext.ensureActive()
        val output = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        var blurredBackground: Bitmap? = null
        return try {
            val canvas = Canvas(output)
            val renderMetrics = LayoutMath.collageRenderMetrics(
                canvasWidthPx = widthPx.toFloat(),
                spacingDp = project.spacingDp,
                cornerRadiusDp = project.cornerRadiusDp,
                borderWidthDp = project.borderWidthDp,
            )
            if (project.backgroundStyle.kind == CollageBackgroundKind.MediaBlur) {
                processingContext.ensureActive()
                val backgroundSource = project.backgroundStyle.blurSourceCellIndex
                    ?.let(project.assignedImages::get)
                    ?: project.template.cells.firstNotNullOfOrNull { cell ->
                        project.assignedImages[cell.index]
                    }
                val decoded = backgroundSource?.let { source ->
                    imageSourceReader.decodeBitmap(source, targetLongEdgePx = BlurBackgroundDecodeEdge)
                }
                if (decoded != null) {
                    blurredBackground = try {
                        BitmapBlur.blur(decoded, project.backgroundStyle.blurRadius)
                    } finally {
                        decoded.recycleBestEffort()
                    }
                }
                processingContext.ensureActive()
            }
            CollageCreativeRenderer.drawBackground(
                canvas = canvas,
                widthPx = widthPx.toFloat(),
                heightPx = heightPx.toFloat(),
                style = project.backgroundStyle,
                legacyGradient = project.backgroundGradient,
                blurredBitmap = blurredBackground,
            )
            val spacingPx = renderMetrics.spacingPx
            val cornerRadiusPx = renderMetrics.cornerRadiusPx
            val targetDecodeEdge = widthPx.coerceAtLeast(heightPx)

            if (project.template.kind == TemplateKind.BeforeAfter) {
                check(
                    drawBeforeAfter(
                        canvas = canvas,
                        project = project,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        cornerRadiusPx = cornerRadiusPx,
                        dividerWidthPx = renderMetrics.dividerWidthPx,
                        targetDecodeEdge = targetDecodeEdge,
                        ensureActive = processingContext::ensureActive,
                    ),
                ) { "Could not decode one or more photos." }
            } else {
                project.template.cells.forEach { cell ->
                    processingContext.ensureActive()
                    val rect = LayoutMath.cellFrame(cell, widthPx.toFloat(), heightPx.toFloat(), spacingPx)
                    check(
                        drawCell(
                            canvas = canvas,
                            source = project.assignedImages[cell.index],
                            transform = project.imageTransforms[cell.index] ?: ImageTransform.Default,
                            rect = rect,
                            shape = project.cropShapes[cell.index] ?: CropShape.Rectangle,
                            cornerRadiusPx = cornerRadiusPx,
                            borderWidthPx = renderMetrics.borderWidthPx,
                            project = project,
                            canvasWidthPx = widthPx.toFloat(),
                            targetDecodeEdge = targetDecodeEdge,
                        ),
                    ) { "Could not decode one or more photos." }
                }
            }
            CollageCreativeRenderer.drawTextLayers(
                canvas = canvas,
                layers = project.textLayers,
                canvasWidthPx = widthPx.toFloat(),
                canvasHeightPx = heightPx.toFloat(),
            )
            processingContext.ensureActive()
            output
        } catch (failure: Throwable) {
            output.recycleBestEffort()
            throw failure
        } finally {
            blurredBackground?.recycleBestEffort()
        }
    }

    private fun drawBeforeAfter(
        canvas: Canvas,
        project: MergeProject,
        widthPx: Int,
        heightPx: Int,
        cornerRadiusPx: Float,
        dividerWidthPx: Float,
        targetDecodeEdge: Int,
        ensureActive: () -> Unit,
    ): Boolean {
        val rect = RectPx(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        val bounds = RectF(rect.left, rect.top, rect.right, rect.bottom)
        val roundedPath = CollageCreativeRenderer.cropPath(CropShape.Rectangle, bounds, cornerRadiusPx)
        canvas.save()
        canvas.clipPath(roundedPath)

        val dividerX = LayoutMath.beforeAfterDividerX(widthPx.toFloat(), project.beforeAfterSlider)
        canvas.save()
        canvas.clipRect(0f, 0f, dividerX, heightPx.toFloat())
        val drewLeft = drawSourceCrop(
            canvas = canvas,
            source = project.assignedImages[0],
            transform = project.imageTransforms[0] ?: ImageTransform.Default,
            rect = rect,
            targetDecodeEdge = targetDecodeEdge,
        )
        ensureActive()
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
        ensureActive()
        canvas.restore()

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = dividerWidthPx
        }
        canvas.drawLine(dividerX, 0f, dividerX, heightPx.toFloat(), dividerPaint)
        canvas.restore()
        CollageCreativeRenderer.drawBorder(
            canvas = canvas,
            path = roundedPath,
            bounds = bounds,
            widthPx = LayoutMath.collageRenderMetrics(
                canvasWidthPx = widthPx.toFloat(),
                spacingDp = project.spacingDp,
                cornerRadiusDp = project.cornerRadiusDp,
                borderWidthDp = project.borderWidthDp,
            ).borderWidthPx,
            style = project.borderStyle,
            legacyColor = project.borderColor,
            canvasWidthPx = widthPx.toFloat(),
        )
        return drewLeft && drewRight
    }

    private fun drawCell(
        canvas: Canvas,
        source: ImageSource?,
        transform: ImageTransform,
        rect: RectPx,
        shape: CropShape,
        cornerRadiusPx: Float,
        borderWidthPx: Float,
        project: MergeProject,
        canvasWidthPx: Float,
        targetDecodeEdge: Int,
    ): Boolean {
        val androidRect = RectF(rect.left, rect.top, rect.right, rect.bottom)
        val path = CollageCreativeRenderer.cropPath(shape, androidRect, cornerRadiusPx)
        canvas.save()
        canvas.clipPath(path)
        val drewSource = drawSourceCrop(canvas, source, transform, rect, targetDecodeEdge)
        canvas.restore()
        CollageCreativeRenderer.drawBorder(
            canvas = canvas,
            path = path,
            bounds = androidRect,
            widthPx = borderWidthPx,
            style = project.borderStyle,
            legacyColor = project.borderColor,
            canvasWidthPx = canvasWidthPx,
        )
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

    private suspend fun saveBitmap(bitmap: Bitmap): Uri {
        val processingContext = currentCoroutineContext()
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
        return transactionalPhotoExport(
            insert = {
                processingContext.ensureActive()
                resolver.insert(collection, values)
            },
            write = { uri ->
                processingContext.ensureActive()
                resolver.openOutputStream(uri)?.use { output ->
                    writeCompressedJpeg(output) { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)
                    }
                } ?: error("Could not open export stream.")
                processingContext.ensureActive()
            },
            publish = { uri ->
                processingContext.ensureActive()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val publishValues = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    check(resolver.update(uri, publishValues, null, null) == 1) {
                        "Could not publish JPEG export."
                    }
                }
                processingContext.ensureActive()
            },
            rollback = { uri -> resolver.delete(uri, null, null) > 0 },
        )
    }

    private fun Bitmap.recycleBestEffort() {
        if (!isRecycled) runCatching { recycle() }
    }

    private fun ULong.toArgbInt(): Int = toLong().toInt()

    private companion object {
        const val BlurBackgroundDecodeEdge = 512
    }
}

internal fun writeCompressedJpeg(
    output: OutputStream,
    compress: (OutputStream) -> Boolean,
) {
    check(compress(output)) { "Could not encode JPEG export." }
    output.flush()
}

internal fun <Entry : Any> transactionalPhotoExport(
    insert: () -> Entry?,
    write: (Entry) -> Unit,
    publish: (Entry) -> Unit,
    rollback: (Entry) -> Boolean,
): Entry {
    val entry = insert() ?: error("Could not create MediaStore entry.")
    return try {
        write(entry)
        publish(entry)
        entry
    } catch (failure: Throwable) {
        val rollbackFailure = runCatching {
            check(rollback(entry)) { "Could not remove incomplete JPEG export." }
        }.exceptionOrNull()
        if (rollbackFailure != null && rollbackFailure !== failure) {
            failure.addSuppressed(rollbackFailure)
        }
        throw failure
    }
}
