package com.rameshta.splitframe.export

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.rameshta.splitframe.domain.ImageDimensions
import com.rameshta.splitframe.domain.ImageSource
import kotlin.math.max

class ImageSourceReader(
    private val contentResolver: ContentResolver,
) {
    fun dimensions(source: ImageSource): ImageDimensions? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        decode(source, options)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            val orientation = exifOrientation(source)
            if (orientation.swapsDimensions) {
                ImageDimensions(options.outHeight, options.outWidth)
            } else {
                ImageDimensions(options.outWidth, options.outHeight)
            }
        } else {
            null
        }
    }

    fun validate(source: ImageSource): ImageValidationResult {
        if (!hasSupportedMimeType(source)) {
            return ImageValidationResult.UnsupportedFormat
        }
        val dimensions = dimensions(source) ?: return ImageValidationResult.Unreadable
        return ImageValidationResult.Valid(dimensions)
    }

    fun decodeBitmap(source: ImageSource, targetLongEdgePx: Int): Bitmap? {
        val dimensions = dimensions(source) ?: return null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSize(dimensions, targetLongEdgePx)
        }
        val bitmap = decode(source, options) ?: return null
        return bitmap.applyOrientation(exifOrientation(source))
    }

    private fun decode(source: ImageSource, options: BitmapFactory.Options): Bitmap? =
        when (source) {
            is ImageSource.LocalUri -> {
                contentResolver.openInputStream(Uri.parse(source.uri))?.use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                }
            }
        }

    private fun hasSupportedMimeType(source: ImageSource): Boolean =
        when (source) {
            is ImageSource.LocalUri -> {
                val mimeType = contentResolver.getType(Uri.parse(source.uri))?.lowercase()
                mimeType == null ||
                    mimeType in SupportedMimeTypes
            }
        }

    private fun exifOrientation(source: ImageSource): ExifOrientation =
        try {
            val exif = when (source) {
                is ImageSource.LocalUri -> contentResolver.openInputStream(Uri.parse(source.uri))?.use(::ExifInterface)
            }
            when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> ExifOrientation.Rotate90
                ExifInterface.ORIENTATION_ROTATE_180 -> ExifOrientation.Rotate180
                ExifInterface.ORIENTATION_ROTATE_270 -> ExifOrientation.Rotate270
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifOrientation.FlipHorizontal
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifOrientation.FlipVertical
                ExifInterface.ORIENTATION_TRANSPOSE -> ExifOrientation.Transpose
                ExifInterface.ORIENTATION_TRANSVERSE -> ExifOrientation.Transverse
                else -> ExifOrientation.Normal
            }
        } catch (_: Throwable) {
            ExifOrientation.Normal
        }

    private fun Bitmap.applyOrientation(orientation: ExifOrientation): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifOrientation.Normal -> return this
            ExifOrientation.Rotate90 -> matrix.postRotate(90f)
            ExifOrientation.Rotate180 -> matrix.postRotate(180f)
            ExifOrientation.Rotate270 -> matrix.postRotate(270f)
            ExifOrientation.FlipHorizontal -> matrix.postScale(-1f, 1f)
            ExifOrientation.FlipVertical -> matrix.postScale(1f, -1f)
            ExifOrientation.Transpose -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifOrientation.Transverse -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also { rotated ->
            if (rotated != this) recycle()
        }
    }

    private fun sampleSize(dimensions: ImageDimensions, targetLongEdgePx: Int): Int {
        if (targetLongEdgePx <= 0) return 1
        var sample = 1
        var currentLongEdge = max(dimensions.widthPx, dimensions.heightPx)
        while (currentLongEdge / 2 >= targetLongEdgePx) {
            sample *= 2
            currentLongEdge /= 2
        }
        return sample
    }

    private enum class ExifOrientation(val swapsDimensions: Boolean) {
        Normal(swapsDimensions = false),
        Rotate90(swapsDimensions = true),
        Rotate180(swapsDimensions = false),
        Rotate270(swapsDimensions = true),
        FlipHorizontal(swapsDimensions = false),
        FlipVertical(swapsDimensions = false),
        Transpose(swapsDimensions = true),
        Transverse(swapsDimensions = true),
    }

    companion object {
        private val SupportedMimeTypes = setOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/heic",
            "image/heif",
            "image/avif",
        )
    }
}

sealed interface ImageValidationResult {
    data class Valid(val dimensions: ImageDimensions) : ImageValidationResult
    data object UnsupportedFormat : ImageValidationResult
    data object Unreadable : ImageValidationResult
}
