package com.rameshta.splitframe.export

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.splitframe.domain.SingleImageOutputFormat
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageOutputValidationDeviceTest {
    @Test
    fun collagePngEncodingPreservesRenderedPixelsExactly() {
        val source = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        val pixels = intArrayOf(
            Color.argb(255, 1, 2, 3),
            Color.argb(255, 40, 50, 60),
            Color.argb(255, 250, 120, 30),
            Color.argb(255, 9, 200, 100),
            Color.argb(255, 80, 70, 220),
            Color.argb(255, 255, 255, 255),
        )
        source.setPixels(pixels, 0, 3, 0, 0, 3, 2)
        try {
            val encoded = ByteArrayOutputStream().use { output ->
                writeLosslessPng(output) { stream ->
                    source.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                output.toByteArray()
            }
            val decoded = requireNotNull(BitmapFactory.decodeByteArray(encoded, 0, encoded.size))
            try {
                val actual = IntArray(pixels.size)
                decoded.getPixels(actual, 0, 3, 0, 0, 3, 2)
                assertArrayEquals(pixels, actual)
            } finally {
                decoded.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    @Test
    fun jpegPngAndWebpOutputsReopenWithExpectedMetadata() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(20, 110, 180))
        }
        try {
            listOf(
                SingleImageOutputFormat.Jpeg,
                SingleImageOutputFormat.Png,
                SingleImageOutputFormat.Webp,
            ).forEach { format ->
                val resolver = context.contentResolver
                val output = requireNotNull(
                    resolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        ContentValues().apply {
                            put(
                                MediaStore.Images.Media.DISPLAY_NAME,
                                "SplitFrame_validation_${System.nanoTime()}.${format.extension}",
                            )
                            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SplitFrame")
                                put(MediaStore.Images.Media.IS_PENDING, 1)
                            }
                        },
                    ),
                )
                try {
                    resolver.openOutputStream(output).use { stream ->
                        requireNotNull(stream)
                        check(
                            bitmap.compress(
                                format.compressFormat(android.os.Build.VERSION.SDK_INT),
                                94,
                                stream,
                            ),
                        )
                    }

                    validateEncodedImageOutput(
                        resolver = resolver,
                        uri = output,
                        expectedWidthPx = 64,
                        expectedHeightPx = 48,
                        expectedMimeType = format.mimeType,
                    )
                } finally {
                    resolver.delete(output, null, null)
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun corruptOutputIsRejected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val output = requireNotNull(
            resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "SplitFrame_corrupt_${System.nanoTime()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SplitFrame")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                },
            ),
        )
        try {
            resolver.openOutputStream(output).use { stream ->
                requireNotNull(stream).write(byteArrayOf(1, 2, 3, 4))
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                validateEncodedImageOutput(
                    resolver = resolver,
                    uri = output,
                    expectedWidthPx = 64,
                    expectedHeightPx = 48,
                    expectedMimeType = "image/jpeg",
                )
            }

            assertEquals(
                "Saved image dimensions do not match the requested output.",
                failure.message,
            )
        } finally {
            resolver.delete(output, null, null)
        }
    }
}
