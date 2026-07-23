package com.rameshta.splitframe.domain

import java.util.Locale
import kotlin.math.abs

enum class TemplateCollection {
    All,
    Recommended,
    Favorites,
    Recent,
    Grid,
    Magazine,
    Mosaic,
}

enum class TemplateAspectFilter {
    Any,
    Square,
    Portrait,
    Landscape,
    Widescreen,
}

data class TemplateDiscoveryFilter(
    val query: String = "",
    val collection: TemplateCollection = TemplateCollection.All,
    val mediaCount: Int? = null,
    val aspect: TemplateAspectFilter = TemplateAspectFilter.Any,
) {
    val hasActiveFilters: Boolean
        get() = query.isNotBlank() ||
            collection != TemplateCollection.All ||
            mediaCount != null ||
            aspect != TemplateAspectFilter.Any
}

object TemplateDiscovery {
    fun discover(
        templates: List<LayoutTemplate>,
        favoriteTemplateIds: List<String>,
        recentTemplateIds: List<String>,
        filter: TemplateDiscoveryFilter,
        recommendedTemplateIds: List<String>? = null,
    ): List<LayoutTemplate> {
        val templatesById = templates.associateBy(LayoutTemplate::id)
        val source = when (filter.collection) {
            TemplateCollection.All -> templates
            TemplateCollection.Recommended -> recommendedTemplateIds
                ?.mapNotNull(templatesById::get)
                ?: templates.filter { it.metadata.category == TemplateCategory.Recommended }
            TemplateCollection.Favorites -> favoriteTemplateIds.mapNotNull(templatesById::get)
            TemplateCollection.Recent -> recentTemplateIds.mapNotNull(templatesById::get)
            TemplateCollection.Grid -> templates.filter { it.metadata.category == TemplateCategory.Grid }
            TemplateCollection.Magazine -> templates.filter { it.metadata.category == TemplateCategory.Magazine }
            TemplateCollection.Mosaic -> templates.filter { it.metadata.category == TemplateCategory.Mosaic }
        }

        return source
            .asSequence()
            .distinctBy(LayoutTemplate::id)
            .filter { template -> filter.mediaCount == null || template.slotCount == filter.mediaCount }
            .filter { template -> template.matches(filter.aspect) }
            .filter { template -> template.matchesQuery(filter.query) }
            .toList()
    }

    fun orientationOf(template: LayoutTemplate): TemplateOrientation =
        when {
            template.aspectRatio.closeTo(1f) -> TemplateOrientation.Square
            template.aspectRatio < 1f -> TemplateOrientation.Portrait
            else -> TemplateOrientation.Landscape
        }

    fun ratioLabel(template: LayoutTemplate): String =
        when {
            template.aspectRatio.closeTo(1f) -> "1:1"
            template.aspectRatio.closeTo(4f / 5f) -> "4:5"
            template.aspectRatio.closeTo(5f / 4f) -> "5:4"
            template.aspectRatio.closeTo(16f / 9f) -> "16:9"
            else -> template.aspectRatio.toString()
        }

    private fun LayoutTemplate.matches(aspect: TemplateAspectFilter): Boolean =
        when (aspect) {
            TemplateAspectFilter.Any -> true
            TemplateAspectFilter.Square -> orientationOf(this) == TemplateOrientation.Square
            TemplateAspectFilter.Portrait -> orientationOf(this) == TemplateOrientation.Portrait
            TemplateAspectFilter.Landscape -> orientationOf(this) == TemplateOrientation.Landscape
            TemplateAspectFilter.Widescreen -> aspectRatio.closeTo(16f / 9f)
        }

    private fun LayoutTemplate.matchesQuery(rawQuery: String): Boolean {
        val queryTokens = rawQuery.normalizedSearchText()
            .split(' ')
            .filter { token -> token.isNotBlank() && token !in IgnoredQueryTokens }
        if (queryTokens.isEmpty()) return true

        val orientation = orientationOf(this).name.lowercase(Locale.ROOT)
        val searchableText = listOfNotNull(
            id,
            name,
            metadata.category.name,
            metadata.previewAsset,
            kind.name,
            orientation,
            ratioLabel(this),
            "$slotCount photo",
            "$slotCount photos",
            "$slotCount image",
            "$slotCount images",
        ).joinToString(" ").normalizedSearchText()
        return queryTokens.all(searchableText::contains)
    }

    private fun String.normalizedSearchText(): String =
        lowercase(Locale.ROOT)
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Whitespace, " ")
            .trim()

    private fun Float.closeTo(other: Float): Boolean = abs(this - other) < AspectTolerance

    private val IgnoredQueryTokens = setOf("a", "an", "and", "layout", "layouts", "the")
    private val Whitespace = Regex("\\s+")
    private const val AspectTolerance = 0.01f
}
