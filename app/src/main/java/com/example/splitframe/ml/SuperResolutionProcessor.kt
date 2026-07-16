package com.example.splitframe.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import com.example.splitframe.domain.ImageSource
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import kotlin.math.max
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.core.graphics.get

class SuperResolutionProcessor(
    private val context: Context,
) {
    fun enhance(source: ImageSource): ImageSource.Enhanced? {
        val originalUri = when (source) {
            is ImageSource.Enhanced -> source.originalUri
            is ImageSource.LocalUri -> source.uri
        }
        val outputFile = File(context.cacheDir, "enhanced_${originalUri.sha256()}.png")
        if (outputFile.exists() && outputFile.length() > 0L) {
            return ImageSource.Enhanced(originalUri, outputFile.absolutePath)
        }

        val original = decodeOriginal(originalUri) ?: return null
        val modelInput = original.centerCropTo(InputSize, InputSize)
        original.recycle()

        return try {
            Interpreter(loadModel(), Interpreter.Options().setNumThreads(4)).use { interpreter ->
                val input = Array(1) { Array(InputSize) { Array(InputSize) { FloatArray(3) } } }
                for (y in 0 until InputSize) {
                    for (x in 0 until InputSize) {
                        val pixel = modelInput[x, y]
                        input[0][y][x][0] = ((pixel shr 16) and 0xff).toFloat()
                        input[0][y][x][1] = ((pixel shr 8) and 0xff).toFloat()
                        input[0][y][x][2] = (pixel and 0xff).toFloat()
                    }
                }
                modelInput.recycle()

                val output = Array(1) { Array(OutputSize) { Array(OutputSize) { FloatArray(3) } } }
                interpreter.run(input, output)
                val enhanced = createBitmap(OutputSize, OutputSize)
                for (y in 0 until OutputSize) {
                    for (x in 0 until OutputSize) {
                        val red = output[0][y][x][0].toInt().coerceIn(0, 255)
                        val green = output[0][y][x][1].toInt().coerceIn(0, 255)
                        val blue = output[0][y][x][2].toInt().coerceIn(0, 255)
                        enhanced[x, y] = (0xff shl 24) or (red shl 16) or (green shl 8) or blue
                    }
                }
                outputFile.outputStream().use { outputStream ->
                    enhanced.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                enhanced.recycle()
                ImageSource.Enhanced(originalUri, outputFile.absolutePath)
            }
        } catch (throwable: Throwable) {
            modelInput.recycle()
            null
        }
    }

    private fun decodeOriginal(uri: String): Bitmap? =
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input)
        }

    private fun loadModel(): MappedByteBuffer {
        val descriptor = context.assets.openFd(ModelName)
        FileInputStream(descriptor.fileDescriptor).use { input ->
            return input.channel.map(
                FileChannel.MapMode.READ_ONLY,
                descriptor.startOffset,
                descriptor.declaredLength,
            )
        }
    }

    private fun Bitmap.centerCropTo(width: Int, height: Int): Bitmap {
        val scale = max(width / this.width.toFloat(), height / this.height.toFloat())
        val scaledWidth = this.width * scale
        val scaledHeight = this.height * scale
        val left = (width - scaledWidth) / 2f
        val top = (height - scaledHeight) / 2f
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(
            this,
            Rect(0, 0, this.width, this.height),
            RectF(left, top, left + scaledWidth, top + scaledHeight),
            null,
        )
        return output
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private companion object {
        const val ModelName = "ESRGAN.tflite"
        const val InputSize = 50
        const val OutputSize = 200
    }
}
