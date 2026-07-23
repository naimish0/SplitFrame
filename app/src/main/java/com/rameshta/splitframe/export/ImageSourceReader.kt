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
    fun dimensions(source: ImageSource): ImageDimensions? =
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            decode(source, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                val orientation = exifOrientation(source)
                if (orientation.swapsDimensions) {
                    ImageDimensions(options.outHeight, options.outWidth)
                } else {
                    ImageDimensions(options.outWidth, options.outHeight)
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

    fun validate(source: ImageSource): ImageValidationResult =
        try {
            if (!hasSupportedMimeType(source)) {
                ImageValidationResult.UnsupportedFormat
            } else {
                dimensions(source)?.let(ImageValidationResult::Valid) ?: ImageValidationResult.Unreadable
            }
        } catch (_: Exception) {
            ImageValidationResult.Unreadable
        }

    fun decodeBitmap(source: ImageSource, targetLongEdgePx: Int): Bitmap? =
        try {
            val dimensions = dimensions(source) ?: return null
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = imageDecodeSampleSize(dimensions, targetLongEdgePx)
            }
            val bitmap = decode(source, options) ?: return null
            bitmap.applyOrientation(exifOrientation(source))
        } catch (_: Exception) {
            null
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
        } catch (_: Exception) {
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
        return transformOwnedResource(
            owned = this,
            transform = { source -> Bitmap.createBitmap(source, 0, 0, width, height, matrix, true) },
            release = Bitmap::recycle,
        )
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

internal fun imageDecodeSampleSize(
    dimensions: ImageDimensions,
    targetLongEdgePx: Int,
    maxDecodedPixels: Long = MaxDecodedPixels,
): Int {
    var sample = 1
    val sourceWidth = dimensions.widthPx.coerceAtLeast(1)
    val sourceHeight = dimensions.heightPx.coerceAtLeast(1)
    val target = targetLongEdgePx.coerceAtLeast(1)
    while (sample <= Int.MAX_VALUE / 2) {
        val nextSample = sample * 2
        val nextLongEdge = max(sourceWidth, sourceHeight) / nextSample
        val currentPixels = (sourceWidth / sample).coerceAtLeast(1).toLong() *
            (sourceHeight / sample).coerceAtLeast(1).toLong()
        if (nextLongEdge < target && currentPixels <= maxDecodedPixels) break
        sample = nextSample
    }
    return sample
}

private const val MaxDecodedPixels = 24_000_000L

internal fun <Resource : Any> transformOwnedResource(
    owned: Resource,
    transform: (Resource) -> Resource,
    release: (Resource) -> Unit,
): Resource {
    val transformed = try {
        transform(owned)
    } catch (failure: Throwable) {
        runCatching { release(owned) }
            .exceptionOrNull()
            ?.takeIf { it !== failure }
            ?.let(failure::addSuppressed)
        throw failure
    }
    if (transformed !== owned) {
        try {
            release(owned)
        } catch (failure: Throwable) {
            runCatching { release(transformed) }
                .exceptionOrNull()
                ?.takeIf { it !== failure }
                ?.let(failure::addSuppressed)
            throw failure
        }
    }
    return transformed
}

sealed interface ImageValidationResult {
    data class Valid(val dimensions: ImageDimensions) : ImageValidationResult
    data object UnsupportedFormat : ImageValidationResult
    data object Unreadable : ImageValidationResult
}
