package com.rameshta.splitframe.export

import org.junit.Assert.assertEquals
import org.junit.Test

class FadeThroughBlackEffectTest {
    @Test
    fun scaleFadesAtBothEndsAndKeepsTheMiddleVisible() {
        val duration = 2_000_000L
        val fade = 250_000L

        assertEquals(0f, fadeThroughBlackScale(0L, duration, fade), 0.001f)
        assertEquals(0.5f, fadeThroughBlackScale(125_000L, duration, fade), 0.001f)
        assertEquals(1f, fadeThroughBlackScale(1_000_000L, duration, fade), 0.001f)
        assertEquals(0.5f, fadeThroughBlackScale(1_875_000L, duration, fade), 0.001f)
        assertEquals(0f, fadeThroughBlackScale(duration, duration, fade), 0.001f)
    }

    @Test
    fun shortClipsUseAtMostHalfTheirDurationForEachFade() {
        assertEquals(
            1f,
            fadeThroughBlackScale(
                presentationTimeUs = 100_000L,
                durationUs = 200_000L,
                fadeDurationUs = 250_000L,
            ),
            0.001f,
        )
    }
}
