package com.rameshta.splitframe.export

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.rameshta.splitframe.domain.ImageSource
import com.rameshta.splitframe.domain.NormalizedRect
import com.rameshta.splitframe.domain.SingleImageOutputFormat
import com.rameshta.splitframe.domain.SingleImageOutputMetadata
import com.rameshta.splitframe.domain.SingleImageDecodeMath
import com.rameshta.splitframe.domain.SingleImagePlanError
import com.rameshta.splitframe.domain.SingleImagePlanResult
import com.rameshta.splitframe.domain.SingleImageResizePlan
import com.rameshta.splitframe.domain.SingleImageResizePlanner
import com.rameshta.splitframe.domain.SingleImageResizeRequest
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class SingleImageProcessingRepository(
    private val context: Context,
    private val imageSourceReader: ImageSourceReader,
) {
    fun isReadable(source: ImageSource): Boolean = imageSourceReader.dimensions(source) != null

    fun plan(source: ImageSource, request: SingleImageResizeRequest): SingleImagePlanResult {
        val dimensions = imageSourceReader.dimensions(source)
            ?: return SingleImagePlanResult.Invalid(SingleImagePlanError.InvalidDimensions)
        return SingleImageResizePlanner.plan(dimensions, request)
    }

    suspend fun process(
        source: ImageSource,
        request: SingleImageResizeRequest,
        onProgress: (Float) -> Unit,
    ): SingleImageProcessResult {
        var input: Bitmap? = null
        var rendered: Bitmap? = null
        return try {
            currentCoroutineContext().ensureActive()
            onProgress(0.05f)
            val plan = when (val result = plan(source, request)) {
                is SingleImagePlanResult.Valid -> result.plan
                is SingleImagePlanResult.Invalid -> return SingleImageProcessResult.Failure(result.reason.name)
            }
            val originalBytes = mediaSizeBytes(Uri.parse((source as ImageSource.LocalUri).uri))
            val decodeLongEdgePx = SingleImageDecodeMath.targetLongEdgePx(
                original = plan.originalDimensions,
                output = plan.outputDimensions,
                geometry = plan.canvasGeometry,
            )
            val decoded = imageSourceReader.decodeBitmap(source, targetLongEdgePx = decodeLongEdgePx)
                ?: return SingleImageProcessResult.Failure("Could not decode source image.")
            input = decoded
            currentCoroutineContext().ensureActive()
            onProgress(0.25f)

            val output = render(decoded, plan, request)
            rendered = output
            if (output !== decoded) {
                releaseDistinctBestEffort(decoded, null) { bitmap ->
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
                if (decoded.isRecycled) input = null
            }
            currentCoroutineContext().ensureActive()
            onProgress(0.78f)

            val savedUri = saveBitmap(output, request)
            val outputBytes = mediaSizeBytes(savedUri)
            SingleImageProcessResult.Success(
                source = ImageSource.LocalUri(savedUri.toString()),
                savedUri = savedUri.toString(),
                plan = plan,
                metadata = SingleImageOutputMetadata(
                    originalDimensions = plan.originalDimensions,
                    outputDimensions = com.rameshta.splitframe.domain.ImageDimensions(output.width, output.height),
                    originalBytes = originalBytes,
                    outputBytes = outputBytes,
                    outputFormat = request.outputFormat,
                    encodingQuality = request.encodingQuality.takeUnless {
                        request.outputFormat == SingleImageOutputFormat.Png
                    },
                    contentMode = request.contentMode,
                ),
            )
        } catch (oom: OutOfMemoryError) {
            SingleImageProcessResult.Failure("Not enough memory for this image size.")
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            SingleImageProcessResult.Failure(actionableExportFailure(throwable, "Image processing failed."))
        } finally {
            releaseDistinctBestEffort(rendered, input) { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    private fun mediaSizeBytes(uri: Uri): Long? {
        val resolver = context.contentResolver
        val descriptorLength = runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor -> descriptor.length }
        }.getOrNull()?.takeIf { it >= 0L }
        if (descriptorLength != null) return descriptorLength
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val column = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (column < 0 || cursor.isNull(column)) null else cursor.getLong(column).takeIf { it >= 0L }
            }
        }.getOrNull()
    }

    private suspend fun render(
        input: Bitmap,
        plan: SingleImageResizePlan,
        request: SingleImageResizeRequest,
    ): Bitmap {
        val output = Bitmap.createBitmap(
            plan.outputDimensions.widthPx,
            plan.outputDimensions.heightPx,
            Bitmap.Config.ARGB_8888,
        )
        return try {
            val canvas = Canvas(output)
            if (request.outputFormat == SingleImageOutputFormat.Jpeg) {
                canvas.drawColor(Color.WHITE)
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            val sourceRect = plan.canvasGeometry.sourceRect.toBitmapRect(input.width, input.height)
            val destinationRect = plan.canvasGeometry.destinationRect.toCanvasRect(output.width, output.height)
            canvas.drawBitmap(
                input,
                sourceRect,
                destinationRect,
                paint,
            )
            currentCoroutineContext().ensureActive()
            output
        } catch (failure: Throwable) {
            releaseDistinctBestEffort(output, null) { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            throw failure
        }
    }

    private suspend fun saveBitmap(bitmap: Bitmap, request: SingleImageResizeRequest): Uri {
        val processingContext = currentCoroutineContext()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "SplitFrame_Image_$timestamp.${request.outputFormat.extension}")
            put(MediaStore.Images.Media.MIME_TYPE, request.outputFormat.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SplitFrame")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        return transactionalSingleImageExport(
            insert = {
                processingContext.ensureActive()
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            },
            write = { uri ->
                processingContext.ensureActive()
                resolver.openOutputStream(uri)?.use { output ->
                    processingContext.ensureActive()
                    writeCompressedSingleImage(output) { stream ->
                        bitmap.compress(
                            request.outputFormat.compressFormat(Build.VERSION.SDK_INT),
                            request.encodingQuality.coerceIn(60, 100),
                            stream,
                        )
                    }
                } ?: error("Could not write output image.")
            },
            publish = { uri ->
                processingContext.ensureActive()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val publishValues = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    check(resolver.update(uri, publishValues, null, null) == 1) {
                        "Could not publish output image."
                    }
                }
                processingContext.ensureActive()
            },
            rollback = { uri -> resolver.delete(uri, null, null) > 0 },
        )
    }
}

private fun NormalizedRect.toBitmapRect(widthPx: Int, heightPx: Int): Rect {
    val left = floor(x.coerceIn(0f, 1f) * widthPx).toInt().coerceIn(0, widthPx - 1)
    val top = floor(y.coerceIn(0f, 1f) * heightPx).toInt().coerceIn(0, heightPx - 1)
    val right = ceil(right.coerceIn(0f, 1f) * widthPx).toInt().coerceIn(left + 1, widthPx)
    val bottom = ceil(bottom.coerceIn(0f, 1f) * heightPx).toInt().coerceIn(top + 1, heightPx)
    return Rect(left, top, right, bottom)
}

private fun NormalizedRect.toCanvasRect(widthPx: Int, heightPx: Int): RectF =
    RectF(
        x.coerceIn(0f, 1f) * widthPx,
        y.coerceIn(0f, 1f) * heightPx,
        right.coerceIn(0f, 1f) * widthPx,
        bottom.coerceIn(0f, 1f) * heightPx,
    )

@SuppressLint("NewApi") // The injected API level gates WEBP_LOSSY and keeps the mapping JVM-testable.
internal fun SingleImageOutputFormat.compressFormat(apiLevel: Int): Bitmap.CompressFormat =
    when (this) {
        SingleImageOutputFormat.Jpeg -> Bitmap.CompressFormat.JPEG
        SingleImageOutputFormat.Png -> Bitmap.CompressFormat.PNG
        SingleImageOutputFormat.Webp -> if (apiLevel >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
    }

internal fun writeCompressedSingleImage(
    output: OutputStream,
    compress: (OutputStream) -> Boolean,
) {
    check(compress(output)) { "Could not encode output image." }
    output.flush()
}

internal fun <Entry : Any> transactionalSingleImageExport(
    insert: () -> Entry?,
    write: (Entry) -> Unit,
    publish: (Entry) -> Unit,
    rollback: (Entry) -> Boolean,
): Entry {
    val entry = insert() ?: error("Could not create output image.")
    return try {
        write(entry)
        publish(entry)
        entry
    } catch (failure: Throwable) {
        val rollbackFailure = runCatching {
            check(rollback(entry)) { "Could not remove incomplete output image." }
        }.exceptionOrNull()
        if (rollbackFailure != null && rollbackFailure !== failure) {
            failure.addSuppressed(rollbackFailure)
        }
        throw failure
    }
}

internal fun <Resource : Any> releaseDistinctBestEffort(
    first: Resource?,
    second: Resource?,
    release: (Resource) -> Unit,
) {
    if (first != null) runCatching { release(first) }
    if (second != null && second !== first) runCatching { release(second) }
}

sealed interface SingleImageProcessResult {
    data class Success(
        val source: ImageSource.LocalUri,
        val savedUri: String,
        val plan: SingleImageResizePlan,
        val metadata: SingleImageOutputMetadata,
    ) : SingleImageProcessResult

    data class Failure(val reason: String) : SingleImageProcessResult
}
