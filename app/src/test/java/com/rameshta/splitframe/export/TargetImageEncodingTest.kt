package com.rameshta.splitframe.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetImageEncodingTest {
    @Test
    fun choosesHighestQualityAtOrBelowTarget() {
        val result = chooseTargetEncoding(
            targetBytes = 75L,
            maximumQuality = 94,
            minimumQuality = 40,
        ) { quality -> ByteArray(quality) }

        assertEquals(75, result.quality)
        assertEquals(75, result.bytes.size)
    }

    @Test
    fun keepsRequestedQualityWhenItAlreadyFits() {
        val result = chooseTargetEncoding(
            targetBytes = 200L,
            maximumQuality = 94,
            minimumQuality = 40,
        ) { quality -> ByteArray(quality) }

        assertEquals(94, result.quality)
    }

    @Test
    fun impossibleTargetFailsRatherThanClaimingSuccess() {
        val failure = assertThrows(IllegalStateException::class.java) {
            chooseTargetEncoding(
                targetBytes = 39L,
                maximumQuality = 94,
                minimumQuality = 40,
            ) { quality -> ByteArray(quality) }
        }

        assertTrue(failure.message.orEmpty().contains("too small"))
    }
}
