package com.rameshta.splitframe.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object AdaptiveGradientMath {
    fun gradientFromArgbColors(colors: List<ULong>): CollageGradient {
        val candidates = colors.mapNotNull(::rgbFromArgb)
            .filter { it.value in 0.08f..0.96f }
        if (candidates.isEmpty()) return CollageGradient.Neutral

        val averageSaturation = candidates.map { it.saturation }.average().toFloat()
        if (averageSaturation < 0.06f) return CollageGradient.Neutral

        val primary = candidates.maxWith(
            compareBy<RgbColor> { it.saturation }
                .thenBy { colorBalanceScore(it) },
        )
        val secondary = candidates
            .filter { hueDistance(primary.hue, it.hue) >= 34f }
            .maxByOrNull { it.saturation + colorBalanceScore(it) }
            ?: primary.complementary()

        val tertiary = candidates
            .filter { hueDistance(primary.hue, it.hue) >= 18f && hueDistance(secondary.hue, it.hue) >= 18f }
            .maxByOrNull { colorBalanceScore(it) }
            ?: blend(primary, secondary, 0.45f)

        val start = soften(primary, colorWeight = 0.2f)
        val center = soften(tertiary, colorWeight = 0.14f)
        val end = soften(secondary, colorWeight = 0.18f)

        if (gradientContrast(start, center, end) > 0.24f) {
            return CollageGradient(
                startColor = soften(primary, colorWeight = 0.15f).toArgb(),
                centerColor = soften(tertiary, colorWeight = 0.1f).toArgb(),
                endColor = soften(secondary, colorWeight = 0.13f).toArgb(),
            )
        }

        return CollageGradient(
            startColor = start.toArgb(),
            centerColor = center.toArgb(),
            endColor = end.toArgb(),
        )
    }

    private fun rgbFromArgb(argb: ULong): RgbColor? {
        val value = argb.toLong().toInt()
        val alpha = (value ushr 24) and 0xFF
        if (alpha < 128) return null
        val red = (value ushr 16) and 0xFF
        val green = (value ushr 8) and 0xFF
        val blue = value and 0xFF
        return RgbColor(red / 255f, green / 255f, blue / 255f)
    }

    private fun colorBalanceScore(color: RgbColor): Float =
        color.saturation * 0.7f + (1f - abs(color.value - 0.58f)) * 0.3f

    private fun hueDistance(first: Float, second: Float): Float {
        val distance = abs(first - second) % 360f
        return min(distance, 360f - distance)
    }

    private fun blend(first: RgbColor, second: RgbColor, amount: Float): RgbColor =
        RgbColor(
            red = first.red + (second.red - first.red) * amount,
            green = first.green + (second.green - first.green) * amount,
            blue = first.blue + (second.blue - first.blue) * amount,
        )

    private fun soften(color: RgbColor, colorWeight: Float): RgbColor {
        val neutral = RgbColor(0.96f, 0.985f, 0.975f)
        return blend(neutral, color.withSaturation(max(color.saturation, 0.18f)), colorWeight)
    }

    private fun gradientContrast(vararg colors: RgbColor): Float {
        var minLuminance = Float.MAX_VALUE
        var maxLuminance = Float.MIN_VALUE
        colors.forEach { color ->
            val luminance = color.luminance()
            minLuminance = min(minLuminance, luminance)
            maxLuminance = max(maxLuminance, luminance)
        }
        return maxLuminance - minLuminance
    }

    private data class RgbColor(
        val red: Float,
        val green: Float,
        val blue: Float,
    ) {
        val maxChannel: Float = max(red, max(green, blue))
        val minChannel: Float = min(red, min(green, blue))
        val value: Float = maxChannel
        val saturation: Float = if (maxChannel == 0f) 0f else (maxChannel - minChannel) / maxChannel
        val hue: Float = when {
            maxChannel == minChannel -> 0f
            maxChannel == red -> (60f * ((green - blue) / (maxChannel - minChannel)) + 360f) % 360f
            maxChannel == green -> 60f * ((blue - red) / (maxChannel - minChannel)) + 120f
            else -> 60f * ((red - green) / (maxChannel - minChannel)) + 240f
        }

        fun complementary(): RgbColor =
            fromHsv((hue + 180f) % 360f, saturation.coerceAtLeast(0.2f), value.coerceIn(0.28f, 0.78f))

        fun withSaturation(targetSaturation: Float): RgbColor =
            fromHsv(hue, targetSaturation.coerceIn(0f, 1f), value.coerceIn(0.2f, 0.92f))

        fun luminance(): Float =
            0.2126f * red + 0.7152f * green + 0.0722f * blue

        fun toArgb(): ULong {
            val r = (red.coerceIn(0f, 1f) * 255f).toInt()
            val g = (green.coerceIn(0f, 1f) * 255f).toInt()
            val b = (blue.coerceIn(0f, 1f) * 255f).toInt()
            return ((0xFFuL shl 24) or (r.toULong() shl 16) or (g.toULong() shl 8) or b.toULong())
        }

        companion object {
            fun fromHsv(hue: Float, saturation: Float, value: Float): RgbColor {
                val chroma = value * saturation
                val secondary = chroma * (1f - abs((hue / 60f) % 2f - 1f))
                val match = value - chroma
                val (redPrime, greenPrime, bluePrime) = when {
                    hue < 60f -> Triple(chroma, secondary, 0f)
                    hue < 120f -> Triple(secondary, chroma, 0f)
                    hue < 180f -> Triple(0f, chroma, secondary)
                    hue < 240f -> Triple(0f, secondary, chroma)
                    hue < 300f -> Triple(secondary, 0f, chroma)
                    else -> Triple(chroma, 0f, secondary)
                }
                return RgbColor(redPrime + match, greenPrime + match, bluePrime + match)
            }
        }
    }
}
