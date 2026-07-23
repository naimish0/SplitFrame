package com.rameshta.splitframe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartLayoutRecommendationsTest {
    private val templates = TemplateRepository().templates()

    @Test
    fun twoPortraitImagesPreferPortraitCellsOverWideCells() {
        val ranked = rank(
            media = listOf(image(600, 1_000), image(600, 1_000)),
        )

        assertBefore(ranked, TemplateIds.SIDE_BY_SIDE, TemplateIds.TOP_BOTTOM)
        val sideBySide = ranked.first { it.template.id == TemplateIds.SIDE_BY_SIDE }
        assertTrue(sideBySide.reasons.contains(LayoutRecommendationReason.ExactMediaCount))
        assertTrue(sideBySide.reasons.contains(LayoutRecommendationReason.FitsMediaShapes))
        assertNotNull(sideBySide.score.averageCropRetentionBasisPoints)
    }

    @Test
    fun threeLandscapeImagesPreferWideCellsOverNarrowColumns() {
        val ranked = rank(
            media = List(3) { image(1_600, 900) },
        )

        assertBefore(ranked, TemplateIds.TRIPTYCH_HORIZONTAL, TemplateIds.TRIPTYCH_VERTICAL)
    }

    @Test
    fun changingSelectedPhotoShapesChangesTheRecommendedOrder() {
        val portraitFirst = rank(
            media = listOf(image(600, 1_000), image(600, 1_000)),
        ).first().template.id
        val landscapeFirst = rank(
            media = listOf(image(1_600, 900), image(1_600, 900)),
        ).first().template.id

        assertNotEquals(portraitFirst, landscapeFirst)
    }

    @Test
    fun mixedOrientationsRespectTheCurrentMediaToCellOrder() {
        val matching = template(
            id = "matching",
            cells = listOf(
                NormalizedRect(0f, 0f, 0.4f, 1f),
                NormalizedRect(0.4f, 0f, 0.6f, 0.4f),
            ),
        )
        val reversed = template(
            id = "reversed",
            cells = listOf(
                NormalizedRect(0f, 0f, 0.6f, 0.4f),
                NormalizedRect(0.6f, 0f, 0.4f, 1f),
            ),
        )

        val ranked = SmartLayoutRecommendations.rank(
            templates = listOf(reversed, matching),
            media = listOf(image(400, 1_000), image(1_500, 1_000)),
        )

        assertEquals("matching", ranked.first().template.id)
    }

    @Test
    fun squareImagesPreferTheSquareGrid() {
        val ranked = rank(
            media = List(4) { image(1_000, 1_000) },
        )

        assertEquals(TemplateIds.GRID_2X2, ranked.first().template.id)
        assertEquals(10_000, ranked.first().score.averageCropRetentionBasisPoints)
    }

    @Test
    fun mediaCountMismatchIsExcludedWithoutHidingItFromTheCatalog() {
        val ranked = rank(
            media = listOf(image(800, 1_200), image(800, 1_200)),
        )

        assertTrue(ranked.isNotEmpty())
        assertTrue(ranked.all { it.template.slotCount == 2 })
        assertTrue(ranked.none { it.template.id == TemplateIds.GRID_2X2 })
        assertTrue(templates.any { it.id == TemplateIds.GRID_2X2 })
    }

    @Test
    fun favoritesAndRecentOrderBreakGeometryTies() {
        val first = template("first", equalHalfCells())
        val second = template("second", equalHalfCells())
        val third = template("third", equalHalfCells())
        val media = listOf(image(500, 1_000), image(500, 1_000))

        val favoriteRanked = SmartLayoutRecommendations.rank(
            templates = listOf(first, second, third),
            media = media,
            favoriteTemplateIds = setOf("second"),
        )
        val recentRanked = SmartLayoutRecommendations.rank(
            templates = listOf(first, second, third),
            media = media,
            recentTemplateIds = listOf("third", "first", "third"),
        )

        assertEquals("second", favoriteRanked.first().template.id)
        assertEquals(listOf("third", "first", "second"), recentRanked.map { it.template.id })
        assertTrue(favoriteRanked.first().reasons.contains(LayoutRecommendationReason.Favorite))
        assertTrue(recentRanked.first().reasons.contains(LayoutRecommendationReason.RecentlyUsed))
    }

    @Test
    fun targetCanvasRatioRanksUnknownGeometryWithoutInventingPhotoFit() {
        val square = template("square", equalHalfCells(), aspectRatio = 1f)
        val portrait = template("portrait", equalHalfCells(), aspectRatio = 4f / 5f)
        val unknownMedia = List(2) { LayoutRecommendationMedia(LayoutRecommendationMediaKind.Image) }

        val ranked = SmartLayoutRecommendations.rank(
            templates = listOf(square, portrait),
            media = unknownMedia,
            targetCanvasAspectRatio = 4f / 5f,
        )

        assertEquals("portrait", ranked.first().template.id)
        assertEquals(null, ranked.first().score.averageCropRetentionBasisPoints)
        assertTrue(ranked.first().reasons.contains(LayoutRecommendationReason.MatchesTargetCanvas))
        assertTrue(ranked.first().reasons.none { it == LayoutRecommendationReason.FitsMediaShapes })
    }

    @Test
    fun partialDimensionsKeepCountCompatibilityButDoNotClaimShapeFit() {
        val ranked = SmartLayoutRecommendations.rank(
            templates = templates,
            media = listOf(
                image(600, 1_000),
                LayoutRecommendationMedia(LayoutRecommendationMediaKind.Image),
            ),
        )

        assertTrue(ranked.isNotEmpty())
        assertTrue(ranked.all { it.template.slotCount == 2 })
        assertTrue(ranked.all { it.score.averageCropRetentionBasisPoints == null })
        assertTrue(ranked.all { LayoutRecommendationReason.FitsMediaShapes !in it.reasons })
    }

    @Test
    fun unsupportedMixedImageAndVideoSelectionProducesNoPhotoRecommendations() {
        val mixedMedia = listOf(
            image(1_000, 1_000),
            LayoutRecommendationMedia(
                kind = LayoutRecommendationMediaKind.Video,
                dimensions = ImageDimensions(1_920, 1_080),
            ),
        )

        val unsupported = SmartLayoutRecommendations.rank(
            templates = templates,
            media = mixedMedia,
        )
        val explicitlySupported = SmartLayoutRecommendations.rank(
            templates = templates,
            media = mixedMedia,
            mediaSupport = LayoutRecommendationMediaSupport(
                supportedKinds = LayoutRecommendationMediaKind.entries.toSet(),
                supportsMixedSelections = true,
            ),
        )

        assertTrue(unsupported.isEmpty())
        assertTrue(explicitlySupported.isNotEmpty())
        assertTrue(
            explicitlySupported.first().reasons.contains(
                LayoutRecommendationReason.MixedMediaCompatible,
            ),
        )
    }

    @Test
    fun rankingIsStableForIdenticalInput() {
        val media = listOf(image(900, 1_200), image(1_200, 900), image(1_000, 1_000))
        val expected = rank(media).map { it.template.id }

        repeat(20) {
            assertEquals(expected, rank(media).map { recommendation -> recommendation.template.id })
        }
    }

    private fun rank(
        media: List<LayoutRecommendationMedia>,
    ): List<LayoutRecommendation> =
        SmartLayoutRecommendations.rank(
            templates = templates,
            media = media,
        )

    private fun image(width: Int, height: Int): LayoutRecommendationMedia =
        LayoutRecommendationMedia(
            kind = LayoutRecommendationMediaKind.Image,
            dimensions = ImageDimensions(width, height),
        )

    private fun assertBefore(
        ranked: List<LayoutRecommendation>,
        firstId: String,
        secondId: String,
    ) {
        val ids = ranked.map { it.template.id }
        assertTrue("Missing $firstId from $ids", firstId in ids)
        assertTrue("Missing $secondId from $ids", secondId in ids)
        assertTrue("Expected $firstId before $secondId in $ids", ids.indexOf(firstId) < ids.indexOf(secondId))
    }

    private fun equalHalfCells(): List<NormalizedRect> =
        listOf(
            NormalizedRect(0f, 0f, 0.5f, 1f),
            NormalizedRect(0.5f, 0f, 0.5f, 1f),
        )

    private fun template(
        id: String,
        cells: List<NormalizedRect>,
        aspectRatio: Float = 1f,
    ): LayoutTemplate =
        LayoutTemplate(
            id = id,
            name = id,
            cells = cells.mapIndexed { index, rect -> LayoutCell(rect, index) },
            defaultSpacingDp = 0f,
            defaultCornerRadiusDp = 0f,
            aspectRatio = aspectRatio,
        )
}
