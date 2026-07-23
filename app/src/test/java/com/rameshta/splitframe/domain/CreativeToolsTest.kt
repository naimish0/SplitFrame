package com.rameshta.splitframe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreativeToolsTest {
    @Test
    fun `text metrics scale from the same reference canvas`() {
        val layer = CollageTextLayer(
            id = "title",
            text = "Hello\n世界",
            fontSize = 36f,
            outlineWidth = 2f,
            centerX = 0.25f,
            centerY = 0.75f,
            scale = 1.5f,
        )
        val preview = CollageTextMath.metrics(layer, 360f, 720f)
        val export = CollageTextMath.metrics(layer, 1080f, 2160f)

        assertEquals(preview.centerXPx * 3f, export.centerXPx, Tolerance)
        assertEquals(preview.centerYPx * 3f, export.centerYPx, Tolerance)
        assertEquals(preview.textSizePx * 3f, export.textSizePx, Tolerance)
        assertEquals(preview.outlineWidthPx * 3f, export.outlineWidthPx, Tolerance)
    }

    @Test
    fun `invalid text transformations normalize safely while preserving unicode`() {
        val normalized = CollageTextLayer(
            id = "unicode",
            text = "नमस्ते 👋\nمرحبا",
            fontSize = Float.NaN,
            opacity = 2f,
            centerX = -1f,
            centerY = Float.POSITIVE_INFINITY,
            scale = 10f,
        ).normalized()

        assertEquals("नमस्ते 👋\nمرحبا", normalized.text)
        assertEquals(32f, normalized.fontSize, Tolerance)
        assertEquals(1f, normalized.opacity, Tolerance)
        assertEquals(0f, normalized.centerX, Tolerance)
        assertEquals(0.5f, normalized.centerY, Tolerance)
        assertEquals(4f, normalized.scale, Tolerance)
    }

    @Test
    fun `all normalized crop paths remain within unit bounds and close`() {
        CropShape.entries.forEach { shape ->
            val commands = CropShapePaths.commands(shape)
            assertTrue(commands.last() == NormalizedPathCommand.Close)
            commands.flatMap(::coordinates).forEach { coordinate ->
                assertTrue("$shape coordinate $coordinate", coordinate in 0f..1f)
            }
        }
    }

    @Test
    fun `auto arrange preserves layout media set and deterministically matches shapes`() {
        val template = LayoutTemplate(
            id = "two",
            name = "Two",
            cells = listOf(
                LayoutCell(NormalizedRect(0f, 0f, 0.3f, 1f), 0),
                LayoutCell(NormalizedRect(0.3f, 0f, 0.7f, 1f), 1),
            ),
            defaultSpacingDp = 0f,
            defaultCornerRadiusDp = 0f,
            aspectRatio = 1f,
        )
        val dimensions = mapOf(
            0 to ImageDimensions(1600, 900),
            1 to ImageDimensions(900, 1600),
        )

        val first = AutoArrangeMath.assignments(template, listOf(0, 1), dimensions)
        val second = AutoArrangeMath.assignments(template, listOf(0, 1), dimensions)

        assertEquals(first, second)
        assertEquals(setOf(0, 1), first.keys)
        assertEquals(setOf(0, 1), first.values.toSet())
        assertEquals(1, first[0])
        assertEquals(0, first[1])
    }

    @Test
    fun `auto arrange handles partial media without duplicates`() {
        val template = TemplateRepository().templates().first { it.slotCount == 4 }
        val result = AutoArrangeMath.assignments(
            template = template,
            occupiedCellIndices = listOf(0, 2),
            dimensionsByCell = emptyMap(),
        )

        assertEquals(2, result.size)
        assertEquals(setOf(0, 2), result.values.toSet())
        assertEquals(result.keys.size, result.keys.toSet().size)
    }

    @Test
    fun `background and border settings clamp expensive values`() {
        assertEquals(
            32,
            CollageBackgroundStyle(blurRadius = 500).normalized().blurRadius,
        )
        val border = CollageBorderStyle(dashLengthDp = Float.NaN, gapLengthDp = -10f).normalized()
        assertEquals(10f, border.dashLengthDp, Tolerance)
        assertEquals(1f, border.gapLengthDp, Tolerance)
    }

    private fun coordinates(command: NormalizedPathCommand): List<Float> = when (command) {
        is NormalizedPathCommand.MoveTo -> listOf(command.x, command.y)
        is NormalizedPathCommand.LineTo -> listOf(command.x, command.y)
        is NormalizedPathCommand.CubicTo -> listOf(
            command.control1X,
            command.control1Y,
            command.control2X,
            command.control2Y,
            command.x,
            command.y,
        )
        NormalizedPathCommand.Close -> emptyList()
    }

    private companion object {
        const val Tolerance = 0.0001f
    }
}
