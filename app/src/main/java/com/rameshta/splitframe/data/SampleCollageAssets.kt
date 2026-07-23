package com.rameshta.splitframe.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import com.rameshta.splitframe.R
import com.rameshta.splitframe.domain.ImageSource
import java.io.File

object SampleCollageAssets {
    fun ensure(context: Context): List<ImageSource.LocalUri> {
        val directory = File(context.filesDir, "sample_collage").also { folder ->
            check(folder.isDirectory || folder.mkdirs()) { "Could not create sample assets." }
        }
        val localeTag = context.resources.configuration.locales[0]
            .toLanguageTag()
            .replace(Regex("[^A-Za-z0-9-]"), "_")
        val watermark = context.getString(R.string.sample_watermark)
        return Palettes.mapIndexed { index, colors ->
            val file = File(directory, "sample_${localeTag}_${index + 1}.png")
            if (!file.isFile || file.length() == 0L) {
                writeSample(file, colors, index, watermark)
            }
            ImageSource.LocalUri(Uri.fromFile(file).toString())
        }
    }

    private fun writeSample(file: File, colors: IntArray, index: Int, watermark: String) {
        val bitmap = Bitmap.createBitmap(SizePx, SizePx, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    SizePx.toFloat(),
                    SizePx.toFloat(),
                    colors,
                    null,
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, SizePx.toFloat(), SizePx.toFloat(), paint)
            paint.shader = null
            paint.color = Color.WHITE
            paint.alpha = 110
            canvas.drawCircle(
                SizePx * (0.28f + index * 0.18f),
                SizePx * 0.42f,
                SizePx * 0.2f,
                paint,
            )
            paint.alpha = 230
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 46f
            paint.isFakeBoldText = true
            canvas.drawText(watermark, SizePx / 2f, SizePx * 0.86f, paint)
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private const val SizePx = 720
    private val Palettes = listOf(
        intArrayOf(Color.rgb(12, 91, 102), Color.rgb(255, 112, 92)),
        intArrayOf(Color.rgb(72, 52, 133), Color.rgb(61, 179, 158)),
        intArrayOf(Color.rgb(218, 125, 48), Color.rgb(39, 80, 120)),
    )
}
