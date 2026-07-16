package com.example.splitframe.ml

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.set
import androidx.exifinterface.media.ExifInterface
import com.example.splitframe.domain.ImageSource
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor

class SuperResolutionProcessor(
    private val context: Context,
) {
    fun enhance(source: ImageSource): ImageSource.Enhanced? {
        val originalUri = when (source) {
            is ImageSource.Enhanced -> source.originalUri
            is ImageSource.LocalUri -> source.uri
        }
        val outputFile = File(context.cacheDir, "enhanced_aspect_v4_${originalUri.sha256()}.png")
        if (outputFile.isDecodableImage()) {
            return ImageSource.Enhanced(originalUri, outputFile.absolutePath)
        }
        if (outputFile.exists()) outputFile.delete()

        val original = try {
            decodeOriginal(originalUri)
        } catch (throwable: Throwable) {
            logFailure("decode", throwable)
            null
        } ?: return null

        return try {
            Interpreter(loadModel(), Interpreter.Options().setNumThreads(4)).use { interpreter ->
                val contract = ModelContract.from(interpreter)
                logContract(contract)
                val modelInput = original.fitInside(contract.input, contract.output)
                original.recycle()

                val inputBuffer = createInputBuffer(modelInput.bitmap, contract.input)
                modelInput.bitmap.recycle()
                val outputBuffer = contract.output.newBuffer()
                outputBuffer.rewind()
                interpreter.run(inputBuffer, outputBuffer)
                outputBuffer.rewind()

                val outputValues = readOutputValues(outputBuffer, contract.output)
                logOutputRange(contract, outputValues.range)
                val enhanced = outputValues.toBitmap(contract.output)
                val aspectPreserved = enhanced.cropTo(modelInput.outputCrop)
                try {
                    writeCompletedImage(aspectPreserved, outputFile)
                } finally {
                    if (aspectPreserved != enhanced) aspectPreserved.recycle()
                    enhanced.recycle()
                }
                if (outputFile.isDecodableImage()) {
                    ImageSource.Enhanced(originalUri, outputFile.absolutePath)
                } else {
                    outputFile.delete()
                    null
                }
            }
        } catch (throwable: Throwable) {
            logFailure("inference", throwable)
            null
        } finally {
            if (!original.isRecycled) original.recycle()
        }
    }

    private fun createInputBuffer(bitmap: Bitmap, spec: TensorSpec): ByteBuffer {
        val values = FloatArray(spec.elementCount)
        for (y in 0 until spec.height) {
            for (x in 0 until spec.width) {
                val pixel = bitmap[x, y]
                values[spec.index(x, y, 0)] = ((pixel shr 16) and 0xff).toFloat()
                values[spec.index(x, y, 1)] = ((pixel shr 8) and 0xff).toFloat()
                values[spec.index(x, y, 2)] = (pixel and 0xff).toFloat()
                if (spec.channels > 3) {
                    values[spec.index(x, y, 3)] = ((pixel ushr 24) and 0xff).toFloat()
                }
            }
        }

        val buffer = spec.newBuffer()
        when (spec.dataType) {
            DataType.FLOAT32 -> values.forEach { value -> buffer.putFloat(value / 255f) }
            DataType.UINT8 -> values.forEach { value ->
                val quantized = spec.quantization.quantize(value, unsigned = true)
                buffer.put(quantized.coerceIn(0, 255).toByte())
            }
            DataType.INT8 -> values.forEach { value ->
                val quantized = spec.quantization.quantize(value, unsigned = false)
                buffer.put(quantized.coerceIn(-128, 127).toByte())
            }
            else -> error("Unsupported input tensor type ${spec.dataType}")
        }
        buffer.rewind()
        return buffer
    }

    private fun readOutputValues(buffer: ByteBuffer, spec: TensorSpec): OutputValues {
        val values = FloatArray(spec.elementCount)
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        when (spec.dataType) {
            DataType.FLOAT32 -> {
                repeat(values.size) { index ->
                    val value = buffer.getFloat()
                    values[index] = value
                    min = minOf(min, value)
                    max = maxOf(max, value)
                }
            }
            DataType.UINT8 -> {
                repeat(values.size) { index ->
                    val value = spec.quantization.dequantize(buffer.get().toInt() and 0xff)
                    values[index] = value
                    min = minOf(min, value)
                    max = maxOf(max, value)
                }
            }
            DataType.INT8 -> {
                repeat(values.size) { index ->
                    val value = spec.quantization.dequantize(buffer.get().toInt())
                    values[index] = value
                    min = minOf(min, value)
                    max = maxOf(max, value)
                }
            }
            else -> error("Unsupported output tensor type ${spec.dataType}")
        }
        return OutputValues(values, OutputRange(min, max))
    }

    private fun OutputValues.toBitmap(spec: TensorSpec): Bitmap {
        val bitmap = createBitmap(spec.width, spec.height)
        for (y in 0 until spec.height) {
            for (x in 0 until spec.width) {
                val red = values[spec.index(x, y, 0)].toColor(range)
                val green = values[spec.index(x, y, 1)].toColor(range)
                val blue = values[spec.index(x, y, 2)].toColor(range)
                val alpha = if (spec.channels > 3) values[spec.index(x, y, 3)].toColor(range) else 255
                bitmap[x, y] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        return bitmap
    }

    private fun Float.toColor(range: OutputRange): Int {
        val scaled = when {
            range.min >= -1.1f && range.max <= 2f -> this * 255f
            range.min >= -0.01f && range.max <= 1.5f -> this * 255f
            else -> this
        }
        return scaled.roundToInt().coerceIn(0, 255)
    }

    private fun writeCompletedImage(bitmap: Bitmap, outputFile: File) {
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
        if (tempFile.exists()) tempFile.delete()
        tempFile.outputStream().use { outputStream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream))
        }
        check(tempFile.isDecodableImage())
        if (outputFile.exists()) outputFile.delete()
        if (!tempFile.renameTo(outputFile)) {
            tempFile.copyTo(outputFile, overwrite = true)
            tempFile.delete()
        }
        check(outputFile.isDecodableImage())
    }

    private fun decodeOriginal(uri: String): Bitmap? {
        val parsed = Uri.parse(uri)
        val bitmap = context.contentResolver.openInputStream(parsed)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return null
        return bitmap.applyExifOrientation(parsed)
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

    private fun Bitmap.applyExifOrientation(uri: Uri): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        var changed = true
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> changed = false
        }
        if (!changed) return this
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also { rotated ->
            if (rotated != this) recycle()
        }
    }

    private fun Bitmap.fitInside(input: TensorSpec, output: TensorSpec): PreparedInput {
        val scale = minOf(input.width / width.toFloat(), input.height / height.toFloat())
        val scaledWidth = (width * scale).roundToInt().coerceIn(1, input.width)
        val scaledHeight = (height * scale).roundToInt().coerceIn(1, input.height)
        val left = (input.width - scaledWidth) / 2f
        val top = (input.height - scaledHeight) / 2f
        val bitmap = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawBitmap(
            this,
            Rect(0, 0, width, height),
            RectF(left, top, left + scaledWidth, top + scaledHeight),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
        )
        val scaleX = output.width / input.width.toFloat()
        val scaleY = output.height / input.height.toFloat()
        val outputLeft = (left * scaleX).roundToInt().coerceIn(0, output.width - 1)
        val outputTop = (top * scaleY).roundToInt().coerceIn(0, output.height - 1)
        val outputWidth = (scaledWidth * scaleX).roundToInt().coerceIn(1, output.width - outputLeft)
        val outputHeight = (scaledHeight * scaleY).roundToInt().coerceIn(1, output.height - outputTop)
        return PreparedInput(
            bitmap = bitmap,
            outputCrop = Rect(outputLeft, outputTop, outputLeft + outputWidth, outputTop + outputHeight),
        )
    }

    private fun Bitmap.cropTo(rect: Rect): Bitmap =
        if (rect.left == 0 && rect.top == 0 && rect.width() == width && rect.height() == height) {
            this
        } else {
            Bitmap.createBitmap(this, rect.left, rect.top, rect.width(), rect.height())
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

    private fun File.isDecodableImage(): Boolean {
        if (!exists() || length() <= 0L) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    private fun logFailure(stage: String, throwable: Throwable) {
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.e(LogTag, "Enhance Quality failed at $stage", throwable)
        }
    }

    private fun logContract(contract: ModelContract) {
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        Log.d(
            LogTag,
            "input shape=${contract.input.shapeText} type=${contract.input.dataType} layout=${contract.input.layout} " +
                "quantization=${contract.input.quantization} normalization=FLOAT32:0..1/RGB " +
                "output shape=${contract.output.shapeText} type=${contract.output.dataType} layout=${contract.output.layout} " +
                "quantization=${contract.output.quantization} scaleFactor=${contract.scaleFactor}",
        )
    }

    private fun logOutputRange(contract: ModelContract, range: OutputRange) {
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        Log.d(
            LogTag,
            "output range=${range.min}..${range.max} outputDimensions=${contract.output.width}x${contract.output.height}",
        )
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private data class ModelContract(
        val input: TensorSpec,
        val output: TensorSpec,
    ) {
        val scaleFactor: String =
            "${output.width / input.width.toFloat()}x${output.height / input.height.toFloat()}"

        companion object {
            fun from(interpreter: Interpreter): ModelContract {
                require(interpreter.inputTensorCount == 1) { "Expected one input tensor." }
                require(interpreter.outputTensorCount == 1) { "Expected one output tensor." }
                return ModelContract(
                    input = TensorSpec.from(interpreter.getInputTensor(0)),
                    output = TensorSpec.from(interpreter.getOutputTensor(0)),
                )
            }
        }
    }

    private data class TensorSpec(
        val shape: IntArray,
        val dataType: DataType,
        val quantization: Quantization,
        val layout: TensorLayout,
        val width: Int,
        val height: Int,
        val channels: Int,
    ) {
        val elementCount: Int = shape.fold(1) { total, value -> total * value }
        val shapeText: String = shape.joinToString(prefix = "[", postfix = "]")

        fun newBuffer(): ByteBuffer =
            ByteBuffer.allocateDirect(elementCount * bytesPerElement()).order(ByteOrder.nativeOrder())

        fun index(x: Int, y: Int, channel: Int): Int =
            when (layout) {
                TensorLayout.NHWC,
                TensorLayout.HWC -> ((y * width) + x) * channels + channel
                TensorLayout.NCHW,
                TensorLayout.CHW -> channel * height * width + y * width + x
            }

        private fun bytesPerElement(): Int =
            when (dataType) {
                DataType.FLOAT32 -> 4
                DataType.UINT8,
                DataType.INT8 -> 1
                else -> error("Unsupported tensor type $dataType")
            }

        companion object {
            fun from(tensor: Tensor): TensorSpec {
                val shape = tensor.shape()
                require(shape.all { it > 0 }) { "Dynamic or invalid tensor shape ${shape.joinToString()}" }
                val layout = when {
                    shape.size == 4 && shape[0] == 1 && shape[3] in 3..4 -> TensorLayout.NHWC
                    shape.size == 4 && shape[0] == 1 && shape[1] in 3..4 -> TensorLayout.NCHW
                    shape.size == 3 && shape[2] in 3..4 -> TensorLayout.HWC
                    shape.size == 3 && shape[0] in 3..4 -> TensorLayout.CHW
                    else -> error("Unsupported tensor shape ${shape.joinToString(prefix = "[", postfix = "]")}")
                }
                val height = when (layout) {
                    TensorLayout.NHWC -> shape[1]
                    TensorLayout.NCHW -> shape[2]
                    TensorLayout.HWC -> shape[0]
                    TensorLayout.CHW -> shape[1]
                }
                val width = when (layout) {
                    TensorLayout.NHWC -> shape[2]
                    TensorLayout.NCHW -> shape[3]
                    TensorLayout.HWC -> shape[1]
                    TensorLayout.CHW -> shape[2]
                }
                val channels = when (layout) {
                    TensorLayout.NHWC -> shape[3]
                    TensorLayout.NCHW -> shape[1]
                    TensorLayout.HWC -> shape[2]
                    TensorLayout.CHW -> shape[0]
                }
                return TensorSpec(
                    shape = shape,
                    dataType = tensor.dataType(),
                    quantization = Quantization.from(tensor),
                    layout = layout,
                    width = width,
                    height = height,
                    channels = channels,
                )
            }
        }
    }

    private enum class TensorLayout {
        NHWC,
        NCHW,
        HWC,
        CHW,
    }

    private data class Quantization(
        val scale: Float,
        val zeroPoint: Int,
    ) {
        fun quantize(value: Float, unsigned: Boolean): Int =
            if (scale > 0f) {
                (value / scale + zeroPoint).roundToInt()
            } else {
                if (unsigned) value.roundToInt() else value.roundToInt() + zeroPoint
            }

        fun dequantize(value: Int): Float =
            if (scale > 0f) {
                (value - zeroPoint) * scale
            } else {
                value.toFloat()
            }

        companion object {
            fun from(tensor: Tensor): Quantization {
                val params = tensor.quantizationParams()
                return Quantization(scale = params.scale, zeroPoint = params.zeroPoint)
            }
        }
    }

    private data class OutputValues(
        val values: FloatArray,
        val range: OutputRange,
    )

    private data class OutputRange(
        val min: Float,
        val max: Float,
    )

    private data class PreparedInput(
        val bitmap: Bitmap,
        val outputCrop: Rect,
    )

    private companion object {
        const val ModelName = "ESRGAN.tflite"
        const val LogTag = "SplitFrameEnhance"
    }
}
