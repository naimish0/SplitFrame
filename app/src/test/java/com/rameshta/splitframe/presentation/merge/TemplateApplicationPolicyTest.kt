package com.rameshta.splitframe.presentation.merge

import com.rameshta.splitframe.domain.ImageSource
import com.rameshta.splitframe.domain.LayoutCell
import com.rameshta.splitframe.domain.LayoutTemplate
import com.rameshta.splitframe.domain.NormalizedRect
import com.rameshta.splitframe.domain.TemplateCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateApplicationPolicyTest {
    @Test
    fun exactAndLargerLayoutsAcceptAllSelectedImagesInOrder() {
        val sources = (1..3).map { ImageSource.LocalUri("content://splitframe/$it") }
        val exact = template("exact", 3)
        val larger = template("larger", 4)

        assertTrue(TemplateCatalog.canApplyWithoutDroppingImages(exact, sources.size))
        assertTrue(TemplateCatalog.canApplyWithoutDroppingImages(larger, sources.size))
        assertEquals(
            mapOf(0 to sources[0], 1 to sources[1], 2 to sources[2]),
            assignSourcesToTemplateCells(larger, sources),
        )
    }

    @Test
    fun smallerLayoutIsRejectedWithoutTruncatingSelectedImages() {
        val sources = (1..3).map { ImageSource.LocalUri("content://splitframe/$it") }
        val smaller = template("smaller", 2)

        assertFalse(TemplateCatalog.canApplyWithoutDroppingImages(smaller, sources.size))
        assertNull(assignSourcesToTemplateCells(smaller, sources))
    }

    private fun template(id: String, cellCount: Int) = LayoutTemplate(
        id = id,
        name = id,
        cells = (0 until cellCount).map { index ->
            LayoutCell(
                rect = NormalizedRect(
                    x = index / cellCount.toFloat(),
                    y = 0f,
                    width = 1f / cellCount,
                    height = 1f,
                ),
                index = index,
            )
        },
        defaultSpacingDp = 0f,
        defaultCornerRadiusDp = 0f,
    )
}
