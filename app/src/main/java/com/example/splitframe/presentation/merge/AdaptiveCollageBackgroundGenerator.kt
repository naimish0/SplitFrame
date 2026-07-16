package com.example.splitframe.presentation.merge

import android.graphics.Bitmap
import android.graphics.Color
import com.example.splitframe.domain.AdaptiveGradientMath
import com.example.splitframe.domain.CollageGradient
import com.example.splitframe.domain.ImageSource
import com.example.splitframe.export.ImageSourceReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

internal class AdaptiveCollageBackgroundGenerator(
    private val imageSourceReader: ImageSourceReader,
) {
    private val colorCache = object : LinkedHashMap<String, ULong>(MaxCacheEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ULong>?): Boolean =
            size > MaxCacheEntries
    }

    suspend fun generate(sources: List<ImageSource>): CollageGradient {
        if (sources.isEmpty()) return CollageGradient.Neutral
        val colors = sources.distinctBy { it.gradientCacheKey() }
            .mapNotNull { source -> colorFor(source) }
        return AdaptiveGradientMath.gradientFromArgbColors(colors)
    }

    private suspend fun colorFor(source: ImageSource): ULong? {
        val key = source.gradientCacheKey()
        synchronized(colorCache) {
            colorCache[key]?.let { return it }
        }

        val bitmap = withContext(Dispatchers.IO) {
            imageSourceReader.decodeBitmap(source, targetLongEdgePx = 96)
        } ?: return null
        val color = try {
            withContext(Dispatchers.Default) {
                bitmap.extractRepresentativeColor()
            }
        } finally {
            bitmap.recycle()
        } ?: return null

        synchronized(colorCache) {
            colorCache[key] = color
        }
        return color
    }

    private fun ImageSource.gradientCacheKey(): String =
        when (this) {
            is ImageSource.LocalUri -> uri
        }

    private fun Bitmap.extractRepresentativeColor(): ULong? {
        val allPixels = mutableListOf<WeightedColor>()
        val buckets = mutableMapOf<Int, WeightedColor>()
        val stepX = max(1, width / 32)
        val stepY = max(1, height / 32)
        val hsv = FloatArray(3)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = getPixel(x, y)
                if (Color.alpha(pixel) >= 160) {
                    Color.RGBToHSV(Color.red(pixel), Color.green(pixel), Color.blue(pixel), hsv)
                    val saturation = hsv[1]
                    val value = hsv[2]
                    val weight = (0.35f + saturation * 0.65f) * (0.65f + (1f - abs(value - 0.58f)) * 0.35f)
                    val color = WeightedColor(
                        red = Color.red(pixel) * weight,
                        green = Color.green(pixel) * weight,
                        blue = Color.blue(pixel) * weight,
                        weight = weight,
                    )
                    allPixels += color
                    if (value in 0.08f..0.96f && !(value > 0.9f && saturation < 0.08f)) {
                        val key = ((hsv[0] / 24f).toInt().coerceIn(0, 14) shl 4) or
                            ((saturation * 3f).toInt().coerceIn(0, 2) shl 2) or
                            (value * 3f).toInt().coerceIn(0, 2)
                        buckets[key] = buckets[key]?.plus(color) ?: color
                    }
                }
                x += stepX
            }
            y += stepY
        }

        val selected = buckets.values.maxByOrNull { it.weight } ?: allPixels.reduceOrNull { acc, color -> acc.plus(color) }
        return selected?.toArgb()
    }

    private data class WeightedColor(
        val red: Float,
        val green: Float,
        val blue: Float,
        val weight: Float,
    ) {
        fun plus(other: WeightedColor): WeightedColor =
            WeightedColor(
                red = red + other.red,
                green = green + other.green,
                blue = blue + other.blue,
                weight = weight + other.weight,
            )

        fun toArgb(): ULong? {
            if (weight <= 0f) return null
            val r = (red / weight).toInt().coerceIn(0, 255)
            val g = (green / weight).toInt().coerceIn(0, 255)
            val b = (blue / weight).toInt().coerceIn(0, 255)
            return (0xFFuL shl 24) or (r.toULong() shl 16) or (g.toULong() shl 8) or b.toULong()
        }
    }

    private companion object {
        const val MaxCacheEntries = 64
    }
}
