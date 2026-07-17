package com.example.splitframe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MixedMediaModelTest {
    @Test
    fun templateCatalogHasExactTemplatesForTwoThroughNineItems() {
        (2..9).forEach { count ->
            val templates = MixedMediaTemplateCatalog.compatibleTemplates(count)

            assertTrue("Expected templates for $count items", templates.isNotEmpty())
            assertTrue(templates.all { it.slotCount == count })
        }
    }

    @Test
    fun projectCompletionRequiresEveryTemplateCellToBeFilled() {
        val template = MixedMediaTemplateCatalog.defaultForCount(3)
        val incomplete = VideoMergeProject(
            id = "mixed",
            template = template,
            mediaByCell = mapOf(
                template.cells[0].index to image("a"),
                template.cells[1].index to video("b", duration = 5_000L),
            ),
        )
        val complete = incomplete.copy(
            mediaByCell = incomplete.mediaByCell + (template.cells[2].index to image("c")),
        )

        assertFalse(incomplete.isComplete)
        assertTrue(complete.isComplete)
    }

    @Test
    fun mixedProjectExposesOnlyVideoClipsInClipMap() {
        val template = MixedMediaTemplateCatalog.defaultForCount(2)
        val project = VideoMergeProject(
            id = "mixed",
            template = template,
            mediaByCell = mapOf(
                0 to image("image"),
                1 to video("video", duration = 7_000L),
            ),
        )

        assertEquals(setOf(1), project.clips.keys)
        assertTrue(project.hasVideo)
    }

    @Test
    fun loopShorterUsesLongestTrimmedVideoDuration() {
        val media = mapOf(
            0 to video("short", duration = 3_000L),
            1 to image("still"),
            2 to video("long", duration = 8_000L),
        )

        assertEquals(8_000L, VideoLayoutMath.outputDurationForMedia(media, MediaDurationMode.LOOP_SHORTER))
        assertEquals(3_000L, VideoLayoutMath.outputDurationForMedia(media, MediaDurationMode.STOP_AT_SHORTEST))
    }

    @Test
    fun loopPositionMapsProjectTimeIntoTrimmedClipRange() {
        val clip = clip(id = "clip", duration = 10_000L, trimStart = 2_000L, trimEnd = 5_000L)

        assertEquals(2_000L, VideoLayoutMath.loopedPositionMs(clip, 0L))
        assertEquals(4_500L, VideoLayoutMath.loopedPositionMs(clip, 2_500L))
        assertEquals(2_500L, VideoLayoutMath.loopedPositionMs(clip, 3_500L))
    }

    @Test
    fun primaryAudioClipResolvesByMediaId() {
        val audio = video("audio", duration = 5_000L, hasAudio = true)
        val silent = video("silent", duration = 5_000L, hasAudio = false)
        val project = VideoMergeProject(
            id = "audio",
            mediaByCell = mapOf(0 to audio, 1 to silent),
            primaryAudioMediaId = audio.id,
        )

        assertNotNull(project.primaryAudioClip)
        assertEquals(audio.id, project.primaryAudioClip?.id)
        assertEquals(null, project.copy(primaryAudioMediaId = silent.id).primaryAudioClip)
    }

    private fun image(id: String): MediaSource.Image =
        MediaSource.Image(
            id = id,
            uri = "content://image/$id",
            width = 1200,
            height = 800,
        )

    private fun video(
        id: String,
        duration: Long,
        hasAudio: Boolean = false,
    ): MediaSource.Video =
        MediaSource.Video(clip(id = id, duration = duration, hasAudio = hasAudio))

    private fun clip(
        id: String,
        duration: Long,
        trimStart: Long = 0L,
        trimEnd: Long = duration,
        hasAudio: Boolean = false,
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
            hasAudio = hasAudio,
        )
}
