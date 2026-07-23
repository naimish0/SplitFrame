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
import android.provider.OpenableColumns
import com.rameshta.splitframe.domain.CollageGradient
import com.rameshta.splitframe.domain.CollageBackgroundKind
import com.rameshta.splitframe.domain.CollageRenderColors
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
import java.util.UUID
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class ImageExportRepository internal constructor(
    private val context: Context,
    private val imageSourceReader: ImageSourceReader,
    private val publicationJournal: ExportPublicationJournal,
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
            val savedOutput = saveBitmap(output)
            ExportResult.Success(
                savedUri = savedOutput.uri.toString(),
                sizeBytes = savedOutput.sizeBytes,
            )
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
            color = CollageRenderColors.BeforeAfterDividerArgb.toLong().toInt()
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

    private suspend fun saveBitmap(bitmap: Bitmap): SavedPhotoOutput {
        val processingContext = currentCoroutineContext()
        val resolver = context.contentResolver
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "SplitFrame_${timestamp}_${UUID.randomUUID().toString().take(8)}.png"
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val journalId = publicationJournal.prepare(collection.toString(), displayName)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SplitFrame")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = transactionalPhotoExport(
            insert = {
                processingContext.ensureActive()
                resolver.insert(collection, values)
            },
            afterInsert = { uri -> publicationJournal.recordWriting(journalId, uri.toString()) },
            write = { uri ->
                processingContext.ensureActive()
                resolver.openOutputStream(uri)?.use { output ->
                    writeLosslessPng(output) { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                } ?: error("Could not open export stream.")
                processingContext.ensureActive()
            },
            validate = { uri ->
                processingContext.ensureActive()
                validateEncodedImageOutput(
                    resolver = resolver,
                    uri = uri,
                    expectedWidthPx = bitmap.width,
                    expectedHeightPx = bitmap.height,
                    expectedMimeType = "image/png",
                )
                processingContext.ensureActive()
            },
            onReadyToPublish = { publicationJournal.markReadyToPublish(journalId) },
            publish = { uri ->
                processingContext.ensureActive()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val publishValues = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    check(resolver.update(uri, publishValues, null, null) == 1) {
                        "Could not publish photo export."
                    }
                }
                processingContext.ensureActive()
            },
            afterPublish = { publicationJournal.markPublished(journalId) },
            commit = { publicationJournal.remove(journalId) },
            rollback = { uri ->
                (resolver.delete(uri, null, null) > 0).also { deleted ->
                    if (deleted) publicationJournal.remove(journalId)
                }
            },
        )
        return SavedPhotoOutput(
            uri = uri,
            sizeBytes = resolver.mediaSizeBytes(uri),
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

private data class SavedPhotoOutput(
    val uri: Uri,
    val sizeBytes: Long?,
)

private fun android.content.ContentResolver.mediaSizeBytes(uri: Uri): Long? =
    runCatching {
        query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val column = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (column < 0 || cursor.isNull(column)) {
                null
            } else {
                cursor.getLong(column).takeIf { it > 0L }
            }
        }
    }.getOrNull() ?: runCatching {
        openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            descriptor.length.takeIf { it > 0L }
        }
    }.getOrNull()

internal fun writeLosslessPng(
    output: OutputStream,
    compress: (OutputStream) -> Boolean,
) {
    check(compress(output)) { "Could not encode lossless PNG export." }
    output.flush()
}

internal fun <Entry : Any> transactionalPhotoExport(
    insert: () -> Entry?,
    afterInsert: (Entry) -> Unit = {},
    write: (Entry) -> Unit,
    validate: (Entry) -> Unit = {},
    onReadyToPublish: (Entry) -> Unit = {},
    publish: (Entry) -> Unit,
    afterPublish: (Entry) -> Unit = {},
    commit: (Entry) -> Unit = {},
    rollback: (Entry) -> Boolean,
): Entry {
    val entry = insert() ?: error("Could not create MediaStore entry.")
    return try {
        afterInsert(entry)
        write(entry)
        validate(entry)
        onReadyToPublish(entry)
        publish(entry)
        afterPublish(entry)
        commit(entry)
        entry
    } catch (failure: Throwable) {
        val rollbackFailure = runCatching {
            check(rollback(entry)) { "Could not remove incomplete photo export." }
        }.exceptionOrNull()
        if (rollbackFailure != null && rollbackFailure !== failure) {
            failure.addSuppressed(rollbackFailure)
        }
        throw failure
    }
}
