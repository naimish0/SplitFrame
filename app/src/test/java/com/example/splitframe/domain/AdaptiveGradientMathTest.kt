package com.example.splitframe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AdaptiveGradientMathTest {
    @Test
    fun grayInputsUseNeutralFallback() {
        val gradient = AdaptiveGradientMath.gradientFromArgbColors(
            listOf(0xFFEEEEEEuL, 0xFFDDDDDDuL),
        )

        assertEquals(CollageGradient.Neutral, gradient)
    }

    @Test
    fun saturatedInputsProduceAdaptiveGradient() {
        val gradient = AdaptiveGradientMath.gradientFromArgbColors(
            listOf(0xFFE11D48uL, 0xFF0F766EuL, 0xFF2563EBuL),
        )

        assertFalse(gradient.isFallback)
        assertFalse(gradient.startColor == CollageGradient.Neutral.startColor && gradient.endColor == CollageGradient.Neutral.endColor)
    }
}
