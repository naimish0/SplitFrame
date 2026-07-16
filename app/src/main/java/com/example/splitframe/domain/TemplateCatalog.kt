package com.example.splitframe.domain

object TemplateCatalog {
    fun compatibleTemplates(
        templates: List<LayoutTemplate>,
        imageCount: Int,
        showFlexibleTemplates: Boolean = false,
    ): List<LayoutTemplate> {
        if (imageCount <= 0) return templates
        val exact = templates.filter { it.slotCount == imageCount }
        if (!showFlexibleTemplates) return exact
        return exact + templates.filter { it.slotCount > imageCount }
    }

    fun compatibleOrFallback(
        templates: List<LayoutTemplate>,
        imageCount: Int,
        fallback: (Int) -> LayoutTemplate,
        showFlexibleTemplates: Boolean = false,
    ): List<LayoutTemplate> {
        val compatible = compatibleTemplates(templates, imageCount, showFlexibleTemplates)
        return compatible.ifEmpty {
            if (imageCount in 1..CollageLimits.MaxImages) {
                listOf(fallback(imageCount))
            } else {
                emptyList()
            }
        }
    }

    fun filterTemplates(
        templates: List<LayoutTemplate>,
        imageCount: Int,
        filter: TemplateFilter,
    ): List<LayoutTemplate> =
        when (filter) {
            TemplateFilter.ALL -> templates
            TemplateFilter.SYMMETRICAL -> templates.filter { it.isSymmetricalTemplate() }
            TemplateFilter.ASYMMETRICAL -> templates.filter { it.isAsymmetricalTemplate() }
            TemplateFilter.PORTRAIT -> templates.filter { it.isPortraitTemplate() }
            TemplateFilter.LANDSCAPE -> templates.filter { it.isLandscapeTemplate() }
        }.let { compatibleTemplates(it, imageCount) }

    fun bySlotCount(templates: List<LayoutTemplate>): Map<Int, List<LayoutTemplate>> =
        templates.groupBy { it.slotCount }

    private fun LayoutTemplate.isSymmetricalTemplate(): Boolean {
        if (slotCount == 1) return true
        if (metadata.category in setOf(TemplateCategory.Grid, TemplateCategory.Symmetrical)) return true
        val first = cells.firstOrNull()?.rect ?: return false
        return cells.all { cell ->
            cell.rect.width.closeTo(first.width) && cell.rect.height.closeTo(first.height)
        }
    }

    private fun LayoutTemplate.isAsymmetricalTemplate(): Boolean =
        metadata.category in setOf(TemplateCategory.Magazine, TemplateCategory.Mosaic, TemplateCategory.Asymmetrical) ||
            !isSymmetricalTemplate()

    private fun LayoutTemplate.isPortraitTemplate(): Boolean =
        aspectRatio < SquareAspectRatio

    private fun LayoutTemplate.isLandscapeTemplate(): Boolean =
        aspectRatio > SquareAspectRatio

    private fun Float.closeTo(other: Float): Boolean =
        kotlin.math.abs(this - other) < 0.001f

    private const val SquareAspectRatio = 1f
}
