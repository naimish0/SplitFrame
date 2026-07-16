package com.example.splitframe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateRepositoryTest {
    private val templates = TemplateRepository().templates()

    @Test
    fun templatesHaveUniqueIds() {
        assertEquals(templates.size, templates.map { it.id }.toSet().size)
    }

    @Test
    fun productionTemplateCatalogHasExpandedChoices() {
        assertTrue(templates.size >= 29)
    }

    @Test
    fun templateCellsStayInsideNormalizedBounds() {
        templates.flatMap { it.cells }.forEach { cell ->
            assertTrue(cell.rect.x >= 0f)
            assertTrue(cell.rect.y >= 0f)
            assertTrue(cell.rect.width > 0f)
            assertTrue(cell.rect.height > 0f)
            assertTrue(cell.rect.right <= 1f)
            assertTrue(cell.rect.bottom <= 1f)
        }
    }

    @Test
    fun templatesCoverEverySupportedPhotoCount() {
        (2..9).forEach { count ->
            assertTrue("Missing template for $count photos", templates.any { it.cells.size == count })
        }
    }

    @Test
    fun templateCellIndicesAreStableAndUnique() {
        templates.forEach { template ->
            val indices = template.cells.map { it.index }
            assertEquals(template.id, indices.size, indices.toSet().size)
            assertEquals(template.id, (0 until template.cells.size).toList(), indices.sorted())
        }
    }
}
