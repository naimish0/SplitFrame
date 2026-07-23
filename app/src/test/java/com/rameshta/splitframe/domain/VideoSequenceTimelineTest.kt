package com.rameshta.splitframe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoSequenceTimelineTest {
    @Test
    fun startAndNegativePositionsMapToFirstClipStart() {
        val clips = listOf(clip("first", trimStart = 1_000L, trimEnd = 4_000L))

        listOf(-500L, 0L).forEach { positionMs ->
            val position = VideoLayoutMath.mergedVideoPositionAt(clips, positionMs)

            assertEquals(0, position?.clipIndex)
            assertEquals(0L, position?.positionInTrimMs)
            assertEquals(1_000L, position?.sourcePositionMs)
        }
    }

    @Test
    fun positionInsideTrimMapsToTrimRelativeAndSourceTime() {
        val clips = listOf(clip("trimmed", trimStart = 2_000L, trimEnd = 6_000L))

        val position = VideoLayoutMath.mergedVideoPositionAt(clips, 1_500L)

        assertEquals(0, position?.clipIndex)
        assertEquals(1_500L, position?.positionInTrimMs)
        assertEquals(3_500L, position?.sourcePositionMs)
    }

    @Test
    fun exactInteriorBoundarySelectsNextClip() {
        val clips = listOf(
            clip("first", trimStart = 1_000L, trimEnd = 4_000L),
            clip("second", trimStart = 2_000L, trimEnd = 7_000L),
        )

        val beforeBoundary = VideoLayoutMath.mergedVideoPositionAt(clips, 2_999L)
        val atBoundary = VideoLayoutMath.mergedVideoPositionAt(clips, 3_000L)

        assertEquals("first", beforeBoundary?.clip?.id)
        assertEquals(2_999L, beforeBoundary?.positionInTrimMs)
        assertEquals("second", atBoundary?.clip?.id)
        assertEquals(0L, atBoundary?.positionInTrimMs)
        assertEquals(2_000L, atBoundary?.sourcePositionMs)
    }

    @Test
    fun totalAndLaterPositionsClampToLastClipEnd() {
        val clips = listOf(
            clip("first", trimStart = 0L, trimEnd = 3_000L),
            clip("second", trimStart = 1_000L, trimEnd = 5_000L),
        )

        listOf(7_000L, 9_000L, Long.MAX_VALUE).forEach { positionMs ->
            val position = VideoLayoutMath.mergedVideoPositionAt(clips, positionMs)

            assertEquals(1, position?.clipIndex)
            assertEquals(4_000L, position?.positionInTrimMs)
            assertEquals(5_000L, position?.sourcePositionMs)
        }
    }

    @Test
    fun zeroDurationClipsAreSkippedWithoutRenumbering() {
        val clips = listOf(
            clip("zero-leading", trimStart = 1_000L, trimEnd = 1_000L),
            clip("playable", trimStart = 2_000L, trimEnd = 4_000L),
            clip("zero-trailing", trimStart = 3_000L, trimEnd = 3_000L),
        )

        val position = VideoLayoutMath.mergedVideoPositionAt(clips, 0L)

        assertEquals(1, position?.clipIndex)
        assertEquals("playable", position?.clip?.id)
        assertEquals(0L, position?.positionInTrimMs)
    }

    @Test
    fun emptyAndAllZeroTimelinesHaveNoPosition() {
        assertNull(VideoLayoutMath.mergedVideoPositionAt(emptyList(), 0L))
        assertNull(
            VideoLayoutMath.mergedVideoPositionAt(
                listOf(clip("zero", trimStart = 1_000L, trimEnd = 1_000L)),
                0L,
            ),
        )
    }

    @Test
    fun reorderChangesTimelineAndClipStartsDeterministically() {
        val first = clip("first", trimStart = 0L, trimEnd = 3_000L)
        val second = clip("second", trimStart = 1_000L, trimEnd = 6_000L)
        val reordered = listOf(second, first)

        assertEquals("second", VideoLayoutMath.mergedVideoPositionAt(reordered, 0L)?.clip?.id)
        assertEquals("first", VideoLayoutMath.mergedVideoPositionAt(reordered, 5_000L)?.clip?.id)
        assertEquals(0L, VideoLayoutMath.mergedVideoClipStartMs(reordered, 0))
        assertEquals(5_000L, VideoLayoutMath.mergedVideoClipStartMs(reordered, 1))
        assertNull(VideoLayoutMath.mergedVideoClipStartMs(reordered, 2))
    }

    private fun clip(
        id: String,
        duration: Long = 10_000L,
        trimStart: Long,
        trimEnd: Long,
    ): VideoClip =
        VideoClip(
            id = id,
            uri = "content://video/$id",
            durationMs = duration,
            trimStartMs = trimStart,
            trimEndMs = trimEnd,
            width = 1920,
            height = 1080,
            rotationDegrees = 0,
        )
}
