package com.example.splitframe.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.splitframe.domain.ImageSource
import java.io.File
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SuperResolutionProcessorTest {
    @Test
    fun enhancesPortraitAndLandscapeImagesToDecodableOutputs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("enhanced_") }
            ?.forEach { it.delete() }
        val processor = SuperResolutionProcessor(context)
        val sources = listOf(
            createSourceImage("portrait", width = 64, height = 96) to true,
            createSourceImage("landscape", width = 96, height = 64) to false,
        )

        sources.forEach { (source, portrait) ->
            val enhanced = processor.enhance(source)

            assertNotNull(enhanced)
            enhanced as ImageSource.Enhanced
            assertNotEquals(source.uri, enhanced.cachedEnhancedPath)
            val bitmap = BitmapFactory.decodeFile(enhanced.cachedEnhancedPath)
            assertNotNull(bitmap)
            assertTrue(bitmap.width > 0)
            assertTrue(bitmap.height > 0)
            if (portrait) {
                assertTrue(bitmap.height > bitmap.width)
            } else {
                assertTrue(bitmap.width > bitmap.height)
            }
            assertTrue(
                "Enhanced output lost horizontal pixel placement",
                averageRed(bitmap, bitmap.width * 4 / 5) > averageRed(bitmap, bitmap.width / 5) + 20,
            )
            assertTrue(
                "Enhanced output lost vertical pixel placement",
                averageGreen(bitmap, bitmap.height * 4 / 5) > averageGreen(bitmap, bitmap.height / 5) + 20,
            )
            assertTrue(File(enhanced.cachedEnhancedPath).length() > 0L)
            bitmap.recycle()
        }
    }

    private fun createSourceImage(name: String, width: Int, height: Int): ImageSource.LocalUri {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "super_resolution_${name}_${width}x$height.png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap.setPixel(
                    x,
                    y,
                    Color.rgb(
                        (x * 255 / width).coerceIn(0, 255),
                        (y * 255 / height).coerceIn(0, 255),
                        ((x + y) * 255 / (width + height)).coerceIn(0, 255),
                    ),
                )
            }
        }
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
        return ImageSource.LocalUri(Uri.fromFile(file).toString())
    }

    private fun averageRed(bitmap: Bitmap, x: Int): Int {
        var red = 0
        for (y in 0 until bitmap.height) {
            red += Color.red(bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y))
        }
        return red / bitmap.height
    }

    private fun averageGreen(bitmap: Bitmap, y: Int): Int {
        var green = 0
        for (x in 0 until bitmap.width) {
            green += Color.green(bitmap.getPixel(x, y.coerceIn(0, bitmap.height - 1)))
        }
        return green / bitmap.width
    }
}
