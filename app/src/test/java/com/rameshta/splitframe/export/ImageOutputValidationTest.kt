package com.rameshta.splitframe.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ImageOutputValidationTest {
    @Test
    fun matchingDimensionsAndMimeTypeAreAccepted() {
        validateEncodedImageMetadata(
            actual = EncodedImageMetadata(
                widthPx = 1080,
                heightPx = 1920,
                mimeType = "IMAGE/WEBP",
            ),
            expectedWidthPx = 1080,
            expectedHeightPx = 1920,
            expectedMimeType = "image/webp",
        )
    }

    @Test
    fun dimensionMismatchIsRejected() {
        val failure = assertThrows(IllegalStateException::class.java) {
            validateEncodedImageMetadata(
                actual = EncodedImageMetadata(
                    widthPx = 1079,
                    heightPx = 1920,
                    mimeType = "image/jpeg",
                ),
                expectedWidthPx = 1080,
                expectedHeightPx = 1920,
                expectedMimeType = "image/jpeg",
            )
        }

        assertEquals(
            "Saved image dimensions do not match the requested output.",
            failure.message,
        )
    }

    @Test
    fun mimeTypeMismatchIsRejected() {
        val failure = assertThrows(IllegalStateException::class.java) {
            validateEncodedImageMetadata(
                actual = EncodedImageMetadata(
                    widthPx = 1080,
                    heightPx = 1920,
                    mimeType = "image/png",
                ),
                expectedWidthPx = 1080,
                expectedHeightPx = 1920,
                expectedMimeType = "image/jpeg",
            )
        }

        assertEquals(
            "Saved image format does not match the requested output.",
            failure.message,
        )
    }

    @Test
    fun validationDecodeUsesBoundedPowerOfTwoSample() {
        assertEquals(1, imageValidationSampleSize(400, 300))
        assertEquals(2, imageValidationSampleSize(1024, 768))
        assertEquals(16, imageValidationSampleSize(8192, 4096))
        assertEquals(16, imageValidationSampleSize(4096, 8192))
    }
}
