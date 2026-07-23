package com.rameshta.splitframe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateDiscoveryTest {
    private val templates = TemplateRepository().templates()

    @Test
    fun classificationUsesConcreteCanvasAspectAndKnownRatios() {
        val byOrientation = templates.groupingBy(TemplateDiscovery::orientationOf).eachCount()
        val byRatio = templates.groupingBy(TemplateDiscovery::ratioLabel).eachCount()

        assertEquals(33, byOrientation[TemplateOrientation.Square])
        assertEquals(52, byOrientation[TemplateOrientation.Portrait])
        assertEquals(25, byOrientation[TemplateOrientation.Landscape])
        assertEquals(33, byRatio["1:1"])
        assertEquals(52, byRatio["4:5"])
        assertEquals(22, byRatio["5:4"])
        assertEquals(3, byRatio["16:9"])
    }

    @Test
    fun searchIsCaseInsensitiveAndMatchesNamesCategoriesAndRatios() {
        val topBottom = discover(query = "TOP and bottom")
        val portraitHeroes = discover(query = "hero PORTRAIT")
        val widescreen = discover(query = "16:9")

        assertTrue(topBottom.any { it.id == TemplateIds.TOP_BOTTOM })
        assertTrue(portraitHeroes.isNotEmpty())
        assertTrue(portraitHeroes.all { it.name.contains("hero", ignoreCase = true) })
        assertEquals(3, widescreen.size)
        assertTrue(widescreen.all { TemplateDiscovery.ratioLabel(it) == "16:9" })
    }

    @Test
    fun searchAndAllFiltersComposeAsOneConjunction() {
        val target = templates.first {
            it.metadata.category == TemplateCategory.Grid &&
                it.slotCount == 6 &&
                TemplateDiscovery.orientationOf(it) == TemplateOrientation.Portrait
        }
        val nonMatchingFavorite = templates.first { it.slotCount != target.slotCount }
        val result = TemplateDiscovery.discover(
            templates = templates,
            favoriteTemplateIds = listOf(target.id, nonMatchingFavorite.id),
            recentTemplateIds = emptyList(),
            filter = TemplateDiscoveryFilter(
                query = "grid",
                collection = TemplateCollection.Favorites,
                mediaCount = 6,
                aspect = TemplateAspectFilter.Portrait,
            ),
        )

        assertEquals(listOf(target.id), result.map(LayoutTemplate::id))
    }

    @Test
    fun recentAndFavoriteCollectionsPreserveStoredOrderWithoutDuplicates() {
        val first = templates[4].id
        val second = templates[1].id
        val recent = TemplateDiscovery.discover(
            templates = templates,
            favoriteTemplateIds = emptyList(),
            recentTemplateIds = listOf(first, second, first, "removed-layout"),
            filter = TemplateDiscoveryFilter(collection = TemplateCollection.Recent),
        )
        val favorites = TemplateDiscovery.discover(
            templates = templates,
            favoriteTemplateIds = listOf(second, second, first, "removed-layout"),
            recentTemplateIds = emptyList(),
            filter = TemplateDiscoveryFilter(collection = TemplateCollection.Favorites),
        )

        assertEquals(listOf(first, second), recent.map(LayoutTemplate::id))
        assertEquals(listOf(second, first), favorites.map(LayoutTemplate::id))
    }

    @Test
    fun recommendedIsTheVerifiedMetadataCategoryNotAnInventedRanking() {
        val recommended = TemplateDiscovery.discover(
            templates = templates,
            favoriteTemplateIds = emptyList(),
            recentTemplateIds = emptyList(),
            filter = TemplateDiscoveryFilter(collection = TemplateCollection.Recommended),
        )

        assertEquals(35, recommended.size)
        assertTrue(recommended.all { it.metadata.category == TemplateCategory.Recommended })
    }

    @Test
    fun smartRecommendedOrderIsDeduplicatedWhileAllLayoutsStayAccessible() {
        val recommendedIds = listOf(
            templates[7].id,
            "removed-layout",
            templates[2].id,
            templates[7].id,
        )
        val recommended = TemplateDiscovery.discover(
            templates = templates,
            favoriteTemplateIds = emptyList(),
            recentTemplateIds = emptyList(),
            filter = TemplateDiscoveryFilter(collection = TemplateCollection.Recommended),
            recommendedTemplateIds = recommendedIds,
        )
        val all = TemplateDiscovery.discover(
            templates = templates,
            favoriteTemplateIds = emptyList(),
            recentTemplateIds = emptyList(),
            filter = TemplateDiscoveryFilter(collection = TemplateCollection.All),
            recommendedTemplateIds = recommendedIds,
        )

        assertEquals(listOf(templates[7].id, templates[2].id), recommended.map(LayoutTemplate::id))
        assertEquals(templates.map(LayoutTemplate::id), all.map(LayoutTemplate::id))
    }

    @Test
    fun emptyPersonalizedRecommendationSetDoesNotFallBackToIncompatibleLayouts() {
        val recommended = TemplateDiscovery.discover(
            templates = templates,
            favoriteTemplateIds = emptyList(),
            recentTemplateIds = emptyList(),
            filter = TemplateDiscoveryFilter(collection = TemplateCollection.Recommended),
            recommendedTemplateIds = emptyList(),
        )

        assertTrue(recommended.isEmpty())
    }

    @Test
    fun impossibleCombinationReturnsHelpfulEmptySurfaceInput() {
        val result = TemplateDiscovery.discover(
            templates = templates,
            favoriteTemplateIds = emptyList(),
            recentTemplateIds = emptyList(),
            filter = TemplateDiscoveryFilter(
                query = "not-a-real-layout",
                mediaCount = 15,
                aspect = TemplateAspectFilter.Widescreen,
            ),
        )

        assertTrue(result.isEmpty())
        assertFalse(TemplateDiscoveryFilter().hasActiveFilters)
        assertTrue(TemplateDiscoveryFilter(query = "grid").hasActiveFilters)
    }

    private fun discover(query: String): List<LayoutTemplate> =
        TemplateDiscovery.discover(
            templates = templates,
            favoriteTemplateIds = emptyList(),
            recentTemplateIds = emptyList(),
            filter = TemplateDiscoveryFilter(query = query),
        )
}
