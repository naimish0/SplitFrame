package com.rameshta.splitframe.domain

import org.junit.Assert.assertFalse
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
        assertTrue(templates.size >= 100)
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
        (1..CollageLimits.MaxImages).forEach { count ->
            val compatible = templates.filter { it.kind == TemplateKind.Standard && it.slotCount == count }

            assertTrue("Missing template for $count photos", compatible.isNotEmpty())
            assertTrue("Expected multiple templates for $count photos", compatible.size >= 3)
        }
    }

    @Test
    fun templateCatalogFiltersByExactSlotCountByDefault() {
        (1..CollageLimits.MaxImages).forEach { count ->
            val compatible = TemplateCatalog.compatibleTemplates(templates, imageCount = count)

            assertTrue("No compatible templates for $count photos", compatible.isNotEmpty())
            assertTrue(
                "Template with mismatched slot count returned for $count photos",
                compatible.all { it.slotCount == count },
            )
        }
    }

    @Test
    fun allFilterDisplaysEveryCompatibleTemplateForEverySupportedCount() {
        (1..CollageLimits.MaxImages).forEach { count ->
            val compatible = TemplateCatalog.compatibleTemplates(templates, imageCount = count)
            val all = TemplateCatalog.filterTemplates(templates, imageCount = count, filter = TemplateFilter.ALL)

            assertEquals("All should not exclude templates for $count photos", compatible, all)
        }
    }

    @Test
    fun stableTemplateFiltersWorkForEverySupportedCount() {
        (1..CollageLimits.MaxImages).forEach { count ->
            val compatible = TemplateCatalog.compatibleTemplates(templates, imageCount = count)

            TemplateFilter.entries.forEach { filter ->
                val filtered = TemplateCatalog.filterTemplates(templates, imageCount = count, filter = filter)
                assertTrue(
                    "Filter $filter returned incompatible slot count for $count photos",
                    filtered.all { it.slotCount == count },
                )
            }
        }
    }

    @Test
    fun portraitAndLandscapeFiltersUseCanvasOrientation() {
        val portrait = TemplateCatalog.filterTemplates(
            templates = templates,
            imageCount = 6,
            filter = TemplateFilter.PORTRAIT,
        )
        val landscape = TemplateCatalog.filterTemplates(
            templates = templates,
            imageCount = 6,
            filter = TemplateFilter.LANDSCAPE,
        )

        assertTrue("Expected portrait templates for six images", portrait.isNotEmpty())
        assertTrue("Expected landscape templates for six images", landscape.isNotEmpty())
        assertTrue("Portrait filter returned non-portrait templates", portrait.all { it.aspectRatio < 1f })
        assertTrue("Landscape filter returned non-landscape templates", landscape.all { it.aspectRatio > 1f })
        assertFalse(
            "Portrait and Landscape should not return the same templates",
            portrait.map { it.id }.toSet() == landscape.map { it.id }.toSet(),
        )
    }

    @Test
    fun templateCatalogFlexibleModeAddsOnlyLargerTemplates() {
        val count = 9

        val exact = TemplateCatalog.compatibleTemplates(templates, imageCount = count)
        val flexible = TemplateCatalog.compatibleTemplates(
            templates = templates,
            imageCount = count,
            showFlexibleTemplates = true,
        )

        assertTrue(flexible.containsAll(exact))
        assertTrue(flexible.all { it.slotCount >= count })
        assertTrue(flexible.any { it.slotCount > count })
    }

    @Test
    fun fallbackGridSupportsEverySupportedImageCount() {
        (1..CollageLimits.MaxImages).forEach { count ->
            val fallback = TemplateCatalog.compatibleOrFallback(
                templates = emptyList(),
                imageCount = count,
                fallback = TemplateRepository()::fallbackGridTemplate,
            ).single()

            assertEquals(count, fallback.slotCount)
            assertEquals((0 until count).toList(), fallback.cells.map { it.index }.sorted())
            assertEquals(TemplateCategory.Grid, fallback.metadata.category)
        }
    }

    @Test
    fun templatesDeclareCompatibilityMetadata() {
        templates.forEach { template ->
            assertTrue(template.id, template.id.isNotBlank())
            assertTrue(template.id, template.aspectRatio > 0f)
            assertTrue(template.id, template.metadata.supportedOrientations.isNotEmpty())
        }
    }

    @Test
    fun standardTemplateSlotsDoNotOverlapUnexpectedly() {
        templates.filter { it.kind == TemplateKind.Standard }.forEach { template ->
            template.cells.forEachIndexed { firstIndex, first ->
                template.cells.drop(firstIndex + 1).forEach { second ->
                    assertFalse(
                        "Unexpected overlap in ${template.id} between ${first.index} and ${second.index}",
                        overlaps(first.rect, second.rect),
                    )
                }
            }
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

    private fun overlaps(first: NormalizedRect, second: NormalizedRect): Boolean {
        val left = maxOf(first.x, second.x)
        val top = maxOf(first.y, second.y)
        val right = minOf(first.right, second.right)
        val bottom = minOf(first.bottom, second.bottom)
        return right - left > 0.0001f && bottom - top > 0.0001f
    }
}
