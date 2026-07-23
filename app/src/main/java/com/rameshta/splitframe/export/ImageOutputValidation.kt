package com.rameshta.splitframe.export

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.max

internal data class EncodedImageMetadata(
    val widthPx: Int,
    val heightPx: Int,
    val mimeType: String?,
)

internal fun validateEncodedImageMetadata(
    actual: EncodedImageMetadata,
    expectedWidthPx: Int,
    expectedHeightPx: Int,
    expectedMimeType: String,
) {
    check(actual.widthPx == expectedWidthPx && actual.heightPx == expectedHeightPx) {
        "Saved image dimensions do not match the requested output."
    }
    check(actual.mimeType.equals(expectedMimeType, ignoreCase = true)) {
        "Saved image format does not match the requested output."
    }
}

internal fun validateEncodedImageOutput(
    resolver: ContentResolver,
    uri: Uri,
    expectedWidthPx: Int,
    expectedHeightPx: Int,
    expectedMimeType: String,
) {
    require(expectedWidthPx > 0 && expectedHeightPx > 0)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsInput = resolver.openInputStream(uri)
        ?: error("Could not reopen saved image for validation.")
    boundsInput.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }

    validateEncodedImageMetadata(
        actual = EncodedImageMetadata(
            widthPx = bounds.outWidth,
            heightPx = bounds.outHeight,
            mimeType = bounds.outMimeType,
        ),
        expectedWidthPx = expectedWidthPx,
        expectedHeightPx = expectedHeightPx,
        expectedMimeType = expectedMimeType,
    )

    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = imageValidationSampleSize(
            widthPx = expectedWidthPx,
            heightPx = expectedHeightPx,
        )
    }
    val decoded = resolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, decodeOptions)
    } ?: error("Could not decode saved image for validation.")
    try {
        check(decoded.width > 0 && decoded.height > 0) {
            "Saved image could not be decoded."
        }
    } finally {
        decoded.recycleBestEffort()
    }
}

internal fun imageValidationSampleSize(
    widthPx: Int,
    heightPx: Int,
    targetLongEdgePx: Int = 512,
): Int {
    require(widthPx > 0 && heightPx > 0)
    require(targetLongEdgePx > 0)
    val longEdge = max(widthPx, heightPx)
    var sampleSize = 1
    while (longEdge / (sampleSize * 2) >= targetLongEdgePx) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun Bitmap.recycleBestEffort() {
    if (!isRecycled) runCatching { recycle() }
}
