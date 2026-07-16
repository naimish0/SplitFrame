package com.example.splitframe.export

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
import com.example.splitframe.domain.ImageSource
import com.example.splitframe.domain.SingleImageOutputFormat
import com.example.splitframe.domain.SingleImagePlanError
import com.example.splitframe.domain.SingleImagePlanResult
import com.example.splitframe.domain.SingleImageResizePlan
import com.example.splitframe.domain.SingleImageResizePlanner
import com.example.splitframe.domain.SingleImageResizeRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class SingleImageProcessingRepository(
    private val context: Context,
    private val imageSourceReader: ImageSourceReader,
) {
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
        return try {
            currentCoroutineContext().ensureActive()
            onProgress(0.05f)
            val plan = when (val result = plan(source, request)) {
                is SingleImagePlanResult.Valid -> result.plan
                is SingleImagePlanResult.Invalid -> return SingleImageProcessResult.Failure(result.reason.name)
            }
            val decodeLongEdgePx = minOf(
                plan.originalDimensions.longEdgePx,
                plan.outputDimensions.longEdgePx,
            )
            val input = imageSourceReader.decodeBitmap(source, targetLongEdgePx = decodeLongEdgePx)
                ?: return SingleImageProcessResult.Failure("Could not decode source image.")
            currentCoroutineContext().ensureActive()
            onProgress(0.25f)

            val rendered = render(input, plan, request)
            if (rendered != input) input.recycle()
            currentCoroutineContext().ensureActive()
            onProgress(0.78f)

            val savedUri = saveBitmap(rendered, request)
            rendered.recycle()
            onProgress(1f)
            SingleImageProcessResult.Success(
                source = ImageSource.LocalUri(savedUri.toString()),
                savedUri = savedUri.toString(),
                plan = plan,
            )
        } catch (oom: OutOfMemoryError) {
            SingleImageProcessResult.Failure("Not enough memory for this image size.")
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            SingleImageProcessResult.Failure(throwable.message ?: "Image processing failed.")
        }
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
        val canvas = Canvas(output)
        if (request.outputFormat == SingleImageOutputFormat.Jpeg) {
            canvas.drawColor(Color.WHITE)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(
            input,
            Rect(0, 0, input.width, input.height),
            RectF(0f, 0f, output.width.toFloat(), output.height.toFloat()),
            paint,
        )
        currentCoroutineContext().ensureActive()
        return output
    }

    private fun saveBitmap(bitmap: Bitmap, request: SingleImageResizeRequest): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "SplitFrame_Image_$timestamp.${request.outputFormat.extension}")
            put(MediaStore.Images.Media.MIME_TYPE, request.outputFormat.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SplitFrame")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) {
            "Could not create output image."
        }
        context.contentResolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(request.outputFormat.compressFormat(), request.jpegQuality.coerceIn(60, 100), output)
        } ?: error("Could not write output image.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        return uri
    }

    private fun SingleImageOutputFormat.compressFormat(): Bitmap.CompressFormat =
        when (this) {
            SingleImageOutputFormat.Jpeg -> Bitmap.CompressFormat.JPEG
            SingleImageOutputFormat.Png -> Bitmap.CompressFormat.PNG
            SingleImageOutputFormat.Webp -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        }
}

sealed interface SingleImageProcessResult {
    data class Success(
        val source: ImageSource.LocalUri,
        val savedUri: String,
        val plan: SingleImageResizePlan,
    ) : SingleImageProcessResult

    data class Failure(val reason: String) : SingleImageProcessResult
}
