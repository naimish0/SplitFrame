package com.rameshta.splitframe.domain

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt

enum class LayoutRecommendationMediaKind {
    Image,
    Video,
}

data class LayoutRecommendationMedia(
    val kind: LayoutRecommendationMediaKind,
    val dimensions: ImageDimensions? = null,
)

data class LayoutRecommendationMediaSupport(
    val supportedKinds: Set<LayoutRecommendationMediaKind>,
    val supportsMixedSelections: Boolean,
) {
    fun supports(media: List<LayoutRecommendationMedia>): Boolean {
        val selectedKinds = media.mapTo(mutableSetOf(), LayoutRecommendationMedia::kind)
        return selectedKinds.all(supportedKinds::contains) &&
            (supportsMixedSelections || selectedKinds.size <= 1)
    }

    companion object {
        val ImagesOnly = LayoutRecommendationMediaSupport(
            supportedKinds = setOf(LayoutRecommendationMediaKind.Image),
            supportsMixedSelections = false,
        )
    }
}

enum class LayoutRecommendationReason {
    ExactMediaCount,
    FitsMediaShapes,
    MatchesOrientations,
    MatchesAggregateAspect,
    MatchesTargetCanvas,
    Favorite,
    RecentlyUsed,
    MixedMediaCompatible,
}

data class LayoutRecommendationScore(
    val averageCropRetentionBasisPoints: Int?,
    val worstCropRetentionBasisPoints: Int?,
    val orientationMatchBasisPoints: Int?,
    val aggregateAspectMatchBasisPoints: Int?,
    val targetCanvasMatchBasisPoints: Int?,
    val favorite: Boolean,
    val recentRank: Int?,
    val catalogOrder: Int,
)

data class LayoutRecommendation(
    val template: LayoutTemplate,
    val score: LayoutRecommendationScore,
    val reasons: List<LayoutRecommendationReason>,
)

object SmartLayoutRecommendations {
    fun rank(
        templates: List<LayoutTemplate>,
        media: List<LayoutRecommendationMedia>,
        targetCanvasAspectRatio: Float? = null,
        favoriteTemplateIds: Collection<String> = emptySet(),
        recentTemplateIds: List<String> = emptyList(),
        mediaSupport: LayoutRecommendationMediaSupport = LayoutRecommendationMediaSupport.ImagesOnly,
    ): List<LayoutRecommendation> {
        if (media.isEmpty() || !mediaSupport.supports(media)) return emptyList()

        val favoriteIds = favoriteTemplateIds.toSet()
        val recentRanks = buildMap {
            recentTemplateIds.forEach { templateId ->
                if (templateId !in this) put(templateId, size)
            }
        }
        val mixedSelection = media.map(LayoutRecommendationMedia::kind).distinct().size > 1

        return templates
            .withIndex()
            .distinctBy { (_, template) -> template.id }
            .asSequence()
            .filter { (_, template) ->
                template.kind == TemplateKind.Standard &&
                    template.slotCount == media.size &&
                    template.hasValidRecommendationGeometry()
            }
            .map { (catalogOrder, template) ->
                val geometry = template.geometryScore(media)
                val targetCanvasMatch = targetCanvasAspectRatio
                    ?.takeIf { it.isUsableAspectRatio() }
                    ?.let { target -> ratioFitBasisPoints(template.aspectRatio, target) }
                val favorite = template.id in favoriteIds
                val recentRank = recentRanks[template.id]
                LayoutRecommendation(
                    template = template,
                    score = LayoutRecommendationScore(
                        averageCropRetentionBasisPoints = geometry?.averageCropRetentionBasisPoints,
                        worstCropRetentionBasisPoints = geometry?.worstCropRetentionBasisPoints,
                        orientationMatchBasisPoints = geometry?.orientationMatchBasisPoints,
                        aggregateAspectMatchBasisPoints = geometry?.aggregateAspectMatchBasisPoints,
                        targetCanvasMatchBasisPoints = targetCanvasMatch,
                        favorite = favorite,
                        recentRank = recentRank,
                        catalogOrder = catalogOrder,
                    ),
                    reasons = buildList {
                        add(LayoutRecommendationReason.ExactMediaCount)
                        if (geometry?.averageCropRetentionBasisPoints.orMissing() >= StrongShapeFitBasisPoints) {
                            add(LayoutRecommendationReason.FitsMediaShapes)
                        }
                        if (geometry?.orientationMatchBasisPoints.orMissing() >= StrongOrientationFitBasisPoints) {
                            add(LayoutRecommendationReason.MatchesOrientations)
                        }
                        if (favorite) add(LayoutRecommendationReason.Favorite)
                        if (recentRank != null) add(LayoutRecommendationReason.RecentlyUsed)
                        if (geometry?.aggregateAspectMatchBasisPoints.orMissing() >= StrongAggregateFitBasisPoints) {
                            add(LayoutRecommendationReason.MatchesAggregateAspect)
                        }
                        if (targetCanvasMatch.orMissing() >= StrongTargetFitBasisPoints) {
                            add(LayoutRecommendationReason.MatchesTargetCanvas)
                        }
                        if (mixedSelection) add(LayoutRecommendationReason.MixedMediaCompatible)
                    },
                )
            }
            .sortedWith(RecommendationComparator)
            .toList()
    }

    private fun LayoutTemplate.geometryScore(
        media: List<LayoutRecommendationMedia>,
    ): GeometryScore? {
        val samples = media.mapIndexed { index, item ->
            val dimensions = item.dimensions?.takeIf { it.widthPx > 0 && it.heightPx > 0 }
                ?: return null
            val sourceRatio = dimensions.widthPx.toFloat() / dimensions.heightPx.toFloat()
            val cell = cells[index]
            val cellRatio = aspectRatio * cell.rect.width / cell.rect.height
            if (!sourceRatio.isUsableAspectRatio() || !cellRatio.isUsableAspectRatio()) {
                return null
            }
            GeometrySample(
                sourceRatio = sourceRatio,
                cellRatio = cellRatio,
                cellArea = (cell.rect.width * cell.rect.height).toDouble(),
            )
        }
        val totalArea = samples.sumOf(GeometrySample::cellArea)
        if (!totalArea.isFinite() || totalArea <= 0.0) return null
        val weightedRetention = samples.sumOf { sample ->
            ratioFit(sample.sourceRatio, sample.cellRatio) * sample.cellArea
        } / totalArea
        val worstRetention = samples.minOf { sample -> ratioFit(sample.sourceRatio, sample.cellRatio) }
        val orientationMatches = samples.count { sample ->
            orientationOf(sample.sourceRatio) == orientationOf(sample.cellRatio)
        }
        val aggregateSourceRatio = geometricMean(samples.map(GeometrySample::sourceRatio))
        val aggregateCellRatio = geometricMean(samples.map(GeometrySample::cellRatio))

        return GeometryScore(
            averageCropRetentionBasisPoints = weightedRetention.toBasisPoints(),
            worstCropRetentionBasisPoints = worstRetention.toBasisPoints(),
            orientationMatchBasisPoints =
                (orientationMatches.toDouble() / samples.size.toDouble()).toBasisPoints(),
            aggregateAspectMatchBasisPoints = ratioFitBasisPoints(aggregateSourceRatio, aggregateCellRatio),
        )
    }

    private fun LayoutTemplate.hasValidRecommendationGeometry(): Boolean =
        aspectRatio.isUsableAspectRatio() && cells.all { cell ->
            cell.rect.width.isFinite() &&
                cell.rect.height.isFinite() &&
                cell.rect.width > 0f &&
                cell.rect.height > 0f &&
                (aspectRatio * cell.rect.width / cell.rect.height).isUsableAspectRatio()
        }

    private fun ratioFitBasisPoints(first: Float, second: Float): Int =
        ratioFit(first, second).toBasisPoints()

    private fun ratioFit(first: Float, second: Float): Double =
        min(first / second, second / first).toDouble().coerceIn(0.0, 1.0)

    private fun geometricMean(values: List<Float>): Float =
        exp(values.map { value -> ln(value.toDouble()) }.average()).toFloat()

    private fun orientationOf(aspectRatio: Float): TemplateOrientation =
        when {
            aspectRatio in SquareLowerBound..SquareUpperBound -> TemplateOrientation.Square
            aspectRatio < 1f -> TemplateOrientation.Portrait
            else -> TemplateOrientation.Landscape
        }

    private fun Float.isUsableAspectRatio(): Boolean = isFinite() && this > 0f

    private fun Double.toBasisPoints(): Int =
        (coerceIn(0.0, 1.0) * MaxBasisPoints).roundToInt()

    private fun Int?.orMissing(): Int = this ?: MissingScore

    private data class GeometrySample(
        val sourceRatio: Float,
        val cellRatio: Float,
        val cellArea: Double,
    )

    private data class GeometryScore(
        val averageCropRetentionBasisPoints: Int,
        val worstCropRetentionBasisPoints: Int,
        val orientationMatchBasisPoints: Int,
        val aggregateAspectMatchBasisPoints: Int,
    )

    private val RecommendationComparator =
        compareByDescending<LayoutRecommendation> { it.score.averageCropRetentionBasisPoints.orMissing() }
            .thenByDescending { it.score.worstCropRetentionBasisPoints.orMissing() }
            .thenByDescending { it.score.orientationMatchBasisPoints.orMissing() }
            .thenByDescending { it.score.aggregateAspectMatchBasisPoints.orMissing() }
            .thenByDescending { it.score.targetCanvasMatchBasisPoints.orMissing() }
            .thenByDescending { it.score.favorite }
            .thenBy { it.score.recentRank ?: Int.MAX_VALUE }
            .thenBy { it.score.catalogOrder }
            .thenBy { it.template.id }

    private const val MaxBasisPoints = 10_000
    private const val MissingScore = -1
    private const val StrongShapeFitBasisPoints = 7_500
    private const val StrongOrientationFitBasisPoints = 7_500
    private const val StrongAggregateFitBasisPoints = 8_500
    private const val StrongTargetFitBasisPoints = 9_500
    private const val SquareLowerBound = 0.9f
    private const val SquareUpperBound = 1.1f
}
