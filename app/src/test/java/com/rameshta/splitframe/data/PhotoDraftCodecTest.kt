package com.rameshta.splitframe.data

import com.rameshta.splitframe.domain.CollageBackgroundKind
import com.rameshta.splitframe.domain.CollageBackgroundStyle
import com.rameshta.splitframe.domain.CollageBorderKind
import com.rameshta.splitframe.domain.CollageBorderStyle
import com.rameshta.splitframe.domain.CollageTextFont
import com.rameshta.splitframe.domain.CollageTextLayer
import com.rameshta.splitframe.domain.CropShape
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.ImageSource
import com.rameshta.splitframe.domain.ImageTransform
import com.rameshta.splitframe.domain.MergeProject
import com.rameshta.splitframe.domain.TemplateRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class PhotoDraftCodecTest {
    private val templates = TemplateRepository().templates()
    private val template = templates.first { it.slotCount == 2 }

    @Test
    fun `round trip preserves stable creative and media state`() {
        val project = MergeProject(
            id = ProjectId,
            template = template,
            assignedImages = mapOf(
                0 to ImageSource.LocalUri("content://photos/one"),
                1 to ImageSource.LocalUri("content://photos/two"),
            ),
            imageTransforms = mapOf(1 to ImageTransform(2f, -0.2f, 0.4f)),
            spacingDp = 12f,
            cornerRadiusDp = 18f,
            backgroundColor = 0xFF102030uL,
            borderColor = 0xFFFFFFFFuL,
            borderWidthDp = 4f,
            exportResolution = ExportResolution.QHD_1440,
            backgroundStyle = CollageBackgroundStyle(
                kind = CollageBackgroundKind.MediaBlur,
                blurSourceCellIndex = 1,
                blurRadius = 20,
            ),
            borderStyle = CollageBorderStyle(kind = CollageBorderKind.Dashed),
            cropShapes = mapOf(0 to CropShape.Heart, 1 to CropShape.Star),
            textLayers = listOf(
                CollageTextLayer(
                    id = "title",
                    text = "नमस्ते 👋\nمرحبا",
                    font = CollageTextFont.Serif,
                    rotationDegrees = 27f,
                    centerX = 0.2f,
                    centerY = 0.8f,
                ),
            ),
        )

        val decoded = requireNotNull(PhotoDraftCodec.decode(PhotoDraftCodec.encode(project), templates))

        assertEquals(project, decoded)
    }

    @Test
    fun `partial larger layout keeps every selected image across restoration`() {
        val largerTemplate = templates.first { it.slotCount == 4 }
        val project = baseProject().copy(
            template = largerTemplate,
            assignedImages = largerTemplate.cells.take(3).associate { cell ->
                cell.index to ImageSource.LocalUri("content://photos/${cell.index}")
            },
        )

        val decoded = requireNotNull(PhotoDraftCodec.decode(PhotoDraftCodec.encode(project), templates))

        assertEquals(project, decoded)
        assertEquals(3, decoded.assignedImages.size)
    }

    @Test
    fun `removed persisted font falls back safely`() {
        val project = baseProject().copy(
            textLayers = listOf(CollageTextLayer(id = "title", text = "Hello")),
        )
        val encoded = PhotoDraftCodec.encode(project).replace("SansSerif", "RemovedFont")

        val decoded = requireNotNull(PhotoDraftCodec.decode(encoded, templates))

        assertEquals(CollageTextFont.SansSerif, decoded.textLayers.single().font)
    }

    @Test
    fun `unknown version template and nonfinite scalar are rejected`() {
        val encoded = PhotoDraftCodec.encode(baseProject())
        assertNull(PhotoDraftCodec.decode(encoded.replace("SPLITFRAME_PHOTO_DRAFT|1", "SPLITFRAME_PHOTO_DRAFT|2"), templates))
        val encodedTemplateId = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(template.id.toByteArray(StandardCharsets.UTF_8))
        val removedTemplateId = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("removed-template".toByteArray(StandardCharsets.UTF_8))
        assertNull(PhotoDraftCodec.decode(encoded.replace(encodedTemplateId, removedTemplateId), templates))
        assertNull(PhotoDraftCodec.decode(encoded.replace("project|", "project|").replace("|0.0|0.0|", "|NaN|0.0|"), templates))
    }

    @Test
    fun `malformed creative or media records reject the draft instead of truncating it`() {
        val encoded = PhotoDraftCodec.encode(baseProject()) +
            "image|999|bad|NaN|NaN|NaN\nshape|999|Heart\ntext|bad\n"

        assertNull(PhotoDraftCodec.decode(encoded, templates))
    }

    private fun baseProject(): MergeProject = MergeProject(
        id = ProjectId,
        template = template,
        assignedImages = emptyMap(),
        spacingDp = 0f,
        cornerRadiusDp = 0f,
        backgroundColor = 0xFFFFFFFFuL,
        borderColor = 0x00000000uL,
        borderWidthDp = 0f,
    )

    private companion object {
        const val ProjectId = "77777777-7777-4777-8777-777777777777"
    }
}
