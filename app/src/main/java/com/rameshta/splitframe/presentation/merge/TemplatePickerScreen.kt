package com.rameshta.splitframe.presentation.merge

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rameshta.splitframe.R
import com.rameshta.splitframe.ads.EmbeddedAdPolicy
import com.rameshta.splitframe.ads.NativeAdvancedAd
import com.rameshta.splitframe.domain.LayoutMath
import com.rameshta.splitframe.domain.LayoutRecommendation
import com.rameshta.splitframe.domain.LayoutRecommendationMedia
import com.rameshta.splitframe.domain.LayoutRecommendationMediaKind
import com.rameshta.splitframe.domain.LayoutRecommendationReason
import com.rameshta.splitframe.domain.LayoutTemplate
import com.rameshta.splitframe.domain.SmartLayoutRecommendations
import com.rameshta.splitframe.domain.TemplateAspectFilter
import com.rameshta.splitframe.domain.TemplateCatalog
import com.rameshta.splitframe.domain.TemplateCollection
import com.rameshta.splitframe.domain.TemplateDiscovery
import com.rameshta.splitframe.domain.TemplateDiscoveryFilter
import com.rameshta.splitframe.domain.TemplateKind
import com.rameshta.splitframe.presentation.descriptionText
import com.rameshta.splitframe.presentation.titleText
import com.rameshta.splitframe.ui.components.StatusMessage
import com.rameshta.splitframe.ui.components.StatusTone
import com.rameshta.splitframe.ui.components.SplitFrameTopAppBar
import com.rameshta.splitframe.ui.theme.splitFrameColors
import com.rameshta.splitframe.ui.theme.splitFrameDimens
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun TemplatePickerScreen(
    state: MergeState,
    paletteSeed: Int,
    gridState: LazyGridState,
    onIntent: (MergeIntent) -> Unit,
    onOpenSingleImageTool: () -> Unit,
    onTemplateSelected: (String) -> Unit,
    showNativeAds: Boolean = false,
) {
    val discovery = state.templateDiscovery
    val project = state.project
    val selectedTemplateId = project?.template?.id
    val selectedPhotoCount = project?.assignedImages?.size ?: 0
    val favoriteTemplateIds = remember(discovery.favoriteTemplateIds) {
        discovery.favoriteTemplateIds.toSet()
    }
    val recommendationMedia = remember(
        project?.template?.cells,
        project?.assignedImages,
        state.sourceDimensions,
    ) {
        project?.template?.cells.orEmpty().mapNotNull { cell ->
            if (project?.assignedImages?.containsKey(cell.index) != true) return@mapNotNull null
            LayoutRecommendationMedia(
                kind = LayoutRecommendationMediaKind.Image,
                dimensions = state.sourceDimensions[cell.index],
            )
        }
    }
    val recommendations = remember(
        state.availableTemplates,
        recommendationMedia,
        project?.template?.aspectRatio,
        discovery.favoriteTemplateIds,
        discovery.recentTemplateIds,
    ) {
        SmartLayoutRecommendations.rank(
            templates = state.availableTemplates,
            media = recommendationMedia,
            targetCanvasAspectRatio = project?.template?.aspectRatio,
            favoriteTemplateIds = discovery.favoriteTemplateIds,
            recentTemplateIds = discovery.recentTemplateIds,
        ).take(MaxSmartRecommendations)
    }
    val recommendedTemplateIds = if (recommendationMedia.isEmpty()) {
        null
    } else {
        recommendations.map { recommendation -> recommendation.template.id }
    }
    val recommendationsById = remember(recommendations) {
        recommendations.associateBy { recommendation -> recommendation.template.id }
    }
    val visibleTemplates = remember(
        state.availableTemplates,
        discovery.filter,
        discovery.favoriteTemplateIds,
        discovery.recentTemplateIds,
        recommendedTemplateIds,
    ) {
        TemplateDiscovery.discover(
            templates = state.availableTemplates,
            favoriteTemplateIds = discovery.favoriteTemplateIds,
            recentTemplateIds = discovery.recentTemplateIds,
            filter = discovery.filter,
            recommendedTemplateIds = recommendedTemplateIds,
        )
    }
    val templatePalettes = remember(paletteSeed, state.availableTemplates) {
        randomTemplatePalettes(state.availableTemplates, paletteSeed)
    }
    val mediaCounts = remember(state.availableTemplates) {
        state.availableTemplates.map(LayoutTemplate::slotCount).distinct().sorted()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val favoriteFailureMessage = stringResource(R.string.template_favorite_update_failed)
    val editorErrorMessage = state.error?.let { stringResource(it) }
    val nativeAdPositions = remember(visibleTemplates.size, showNativeAds) {
        if (showNativeAds) {
            EmbeddedAdPolicy.nativeInsertionPositions(
                organicItemCount = visibleTemplates.size,
                afterEvery = 7,
                maximumAds = 2,
            ).toSet()
        } else {
            emptySet()
        }
    }

    LaunchedEffect(discovery.favoriteErrorVersion) {
        if (discovery.favoriteErrorTemplateId != null) {
            snackbarHostState.showSnackbar(favoriteFailureMessage)
            onIntent(MergeIntent.ClearTemplateFavoriteError)
        }
    }
    LaunchedEffect(editorErrorMessage) {
        val message = editorErrorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onIntent(MergeIntent.ClearError)
    }

    Scaffold(
        topBar = {
            SplitFrameTopAppBar(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.template_picker_subtitle),
                actions = {
                    IconButton(onClick = onOpenSingleImageTool) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = stringResource(R.string.single_image_tool),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag(TemplatePickerGridTestTag),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(
                key = "template-discovery-controls",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                TemplateDiscoveryControls(
                    filter = discovery.filter,
                    mediaCounts = mediaCounts,
                    selectedMediaCount = selectedPhotoCount,
                    isLoading = discovery.isLoading,
                    loadFailed = discovery.loadFailed,
                    onIntent = onIntent,
                )
            }
            if (visibleTemplates.isEmpty()) {
                item(
                    key = "template-discovery-empty",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    TemplateDiscoveryEmptyState(
                        filter = discovery.filter,
                        onReset = { onIntent(MergeIntent.ResetTemplateDiscovery) },
                    )
                }
            } else {
                visibleTemplates.forEachIndexed { index, template ->
                    val canApplyWithoutDroppingImages = TemplateCatalog.canApplyWithoutDroppingImages(
                        template = template,
                        imageCount = selectedPhotoCount,
                    )
                    item(key = template.id) {
                        TemplateCard(
                            template = template,
                            palette = templatePalettes.getValue(template.id),
                            selected = selectedTemplateId == template.id,
                            canApplyWithoutDroppingImages = canApplyWithoutDroppingImages,
                            favorite = template.id in favoriteTemplateIds,
                            favoriteEnabled = !discovery.isLoading &&
                                !discovery.loadFailed &&
                                template.id !in discovery.pendingFavoriteIds,
                            recommendation = recommendationsById[template.id]
                                .takeIf { discovery.filter.collection == TemplateCollection.Recommended },
                            onFavoriteClick = {
                                onIntent(MergeIntent.ToggleTemplateFavorite(template.id))
                            },
                            onClick = { onTemplateSelected(template.id) },
                        )
                    }
                    if (index + 1 in nativeAdPositions) {
                        item(
                            key = "native-ad:templates:${index + 1}",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            TemplateNativeAd()
                        }
                    }
                }
            }
        }
    }
}

internal const val TemplatePickerGridTestTag = "template-picker-grid"

@Composable
private fun TemplateNativeAd() {
    val colors = splitFrameColors()
    NativeAdvancedAd(
        containerColor = colors.adContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
        outlineColor = MaterialTheme.colorScheme.outlineVariant,
        primaryColor = MaterialTheme.colorScheme.primary,
        onPrimaryColor = MaterialTheme.colorScheme.onPrimary,
    )
}

@Composable
private fun TemplateDiscoveryControls(
    filter: TemplateDiscoveryFilter,
    mediaCounts: List<Int>,
    selectedMediaCount: Int,
    isLoading: Boolean,
    loadFailed: Boolean,
    onIntent: (MergeIntent) -> Unit,
) {
    val dimens = splitFrameDimens()
    val loadingDescription = stringResource(R.string.template_discovery_loading)
    Column(verticalArrangement = Arrangement.spacedBy(dimens.space12)) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = { query -> onIntent(MergeIntent.UpdateTemplateSearch(query)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.template_search_label)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = if (filter.query.isNotEmpty()) {
                {
                    IconButton(onClick = { onIntent(MergeIntent.UpdateTemplateSearch("")) }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.template_search_clear),
                        )
                    }
                }
            } else {
                null
            },
        )
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = loadingDescription
                    },
            )
        }
        if (loadFailed) {
            StatusMessage(
                text = stringResource(R.string.template_discovery_load_failed),
                tone = StatusTone.Warning,
            )
        }
        if (filter.collection == TemplateCollection.Recommended) {
            Text(
                text = stringResource(
                    if (selectedMediaCount > 0) {
                        R.string.template_recommendation_local_note
                    } else {
                        R.string.template_recommendation_curated_note
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FilterLabel(stringResource(R.string.template_collection_filter))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.space8)) {
            rowItems(TemplateCollection.entries, key = TemplateCollection::name) { collection ->
                FilterChip(
                    selected = filter.collection == collection,
                    onClick = { onIntent(MergeIntent.SelectTemplateCollection(collection)) },
                    label = { Text(collection.label()) },
                )
            }
        }

        FilterLabel(stringResource(R.string.template_aspect_filter))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.space8)) {
            rowItems(TemplateAspectFilter.entries, key = TemplateAspectFilter::name) { aspect ->
                FilterChip(
                    selected = filter.aspect == aspect,
                    onClick = { onIntent(MergeIntent.SelectTemplateAspect(aspect)) },
                    label = { Text(aspect.label()) },
                )
            }
        }

        FilterLabel(stringResource(R.string.template_media_count_filter))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.space8)) {
            item(key = "any-media-count") {
                FilterChip(
                    selected = filter.mediaCount == null,
                    onClick = { onIntent(MergeIntent.SelectTemplateMediaCount(null)) },
                    label = { Text(stringResource(R.string.template_media_count_any)) },
                )
            }
            rowItems(mediaCounts, key = { count -> count }) { count ->
                FilterChip(
                    selected = filter.mediaCount == count,
                    onClick = { onIntent(MergeIntent.SelectTemplateMediaCount(count)) },
                    label = { Text(stringResource(R.string.template_cell_count, count)) },
                )
            }
        }

        TextButton(
            onClick = { onIntent(MergeIntent.ResetTemplateDiscovery) },
            enabled = filter.hasActiveFilters,
        ) {
            Text(stringResource(R.string.template_filters_reset))
        }
    }
}

@Composable
private fun FilterLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun TemplateCollection.label(): String =
    when (this) {
        TemplateCollection.All -> stringResource(R.string.filter_all)
        TemplateCollection.Recommended -> stringResource(R.string.sort_recommended)
        TemplateCollection.Favorites -> stringResource(R.string.home_favorite_layouts)
        TemplateCollection.Recent -> stringResource(R.string.home_recent_layouts)
        TemplateCollection.Grid -> stringResource(R.string.template_group_grid)
        TemplateCollection.Magazine -> stringResource(R.string.template_group_magazine)
        TemplateCollection.Mosaic -> stringResource(R.string.template_group_mosaic)
    }

@Composable
private fun TemplateAspectFilter.label(): String =
    when (this) {
        TemplateAspectFilter.Any -> stringResource(R.string.template_aspect_any)
        TemplateAspectFilter.Square -> stringResource(R.string.template_aspect_square)
        TemplateAspectFilter.Portrait -> stringResource(R.string.template_aspect_portrait)
        TemplateAspectFilter.Landscape -> stringResource(R.string.template_aspect_landscape)
        TemplateAspectFilter.Widescreen -> stringResource(R.string.template_aspect_widescreen)
    }

@Composable
private fun LayoutRecommendation.summary(): String {
    val labels = mutableListOf<String>()
    for (reason in reasons.take(MaxDisplayedRecommendationReasons)) {
        labels += when (reason) {
            LayoutRecommendationReason.ExactMediaCount -> stringResource(
                R.string.template_recommendation_exact_count,
                template.slotCount,
            )
            LayoutRecommendationReason.FitsMediaShapes ->
                stringResource(R.string.template_recommendation_shapes)
            LayoutRecommendationReason.MatchesOrientations ->
                stringResource(R.string.template_recommendation_orientations)
            LayoutRecommendationReason.MatchesAggregateAspect ->
                stringResource(R.string.template_recommendation_aggregate)
            LayoutRecommendationReason.MatchesTargetCanvas ->
                stringResource(R.string.template_recommendation_canvas)
            LayoutRecommendationReason.Favorite ->
                stringResource(R.string.template_recommendation_favorite)
            LayoutRecommendationReason.RecentlyUsed ->
                stringResource(R.string.template_recommendation_recent)
            LayoutRecommendationReason.MixedMediaCompatible ->
                stringResource(R.string.template_recommendation_mixed_media)
        }
    }
    return labels.joinToString(separator = " • ")
}

@Composable
private fun TemplateDiscoveryEmptyState(
    filter: TemplateDiscoveryFilter,
    onReset: () -> Unit,
) {
    val message = when {
        filter.collection == TemplateCollection.Favorites &&
            filter.query.isBlank() &&
            filter.mediaCount == null &&
            filter.aspect == TemplateAspectFilter.Any -> {
            stringResource(R.string.template_favorites_empty)
        }
        filter.collection == TemplateCollection.Recent &&
            filter.query.isBlank() &&
            filter.mediaCount == null &&
            filter.aspect == TemplateAspectFilter.Any -> {
            stringResource(R.string.template_recent_empty)
        }
        else -> stringResource(R.string.template_search_empty)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(splitFrameDimens().space12),
    ) {
        StatusMessage(
            text = message,
            tone = StatusTone.Info,
            icon = Icons.Default.ViewModule,
        )
        if (filter.hasActiveFilters) {
            TextButton(onClick = onReset) {
                Text(stringResource(R.string.template_filters_reset))
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: LayoutTemplate,
    palette: TemplatePalette,
    selected: Boolean,
    canApplyWithoutDroppingImages: Boolean,
    favorite: Boolean,
    favoriteEnabled: Boolean,
    recommendation: LayoutRecommendation?,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = splitFrameColors()
    val dimens = splitFrameDimens()
    val title = template.titleText()
    val description = template.descriptionText()
    val recommendationSummary = recommendation?.summary()
    val baseSelectionDescription = if (canApplyWithoutDroppingImages) {
        stringResource(R.string.template_open_accessibility, title)
    } else {
        stringResource(R.string.template_unavailable_accessibility, title, template.cells.size)
    }
    val selectionDescription = recommendationSummary?.let { summary ->
        stringResource(R.string.template_recommendation_accessibility, baseSelectionDescription, summary)
    } ?: baseSelectionDescription
    val favoriteStateDescription = stringResource(
        if (favorite) R.string.template_favorite_state_on else R.string.template_favorite_state_off,
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.selected = selected
                role = Role.Button
                contentDescription = selectionDescription
            }
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) colors.selectedCell else MaterialTheme.colorScheme.outlineVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 6.dp else 1.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.space12),
                verticalArrangement = Arrangement.spacedBy(dimens.space8),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(118.dp),
                ) {
                    TemplateThumbnail(template = template, palette = palette)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.space8),
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.template_selected),
                            tint = colors.selectedCell,
                            modifier = Modifier.size(dimens.iconSmall),
                        )
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                recommendationSummary?.let { summary ->
                    Text(
                        text = stringResource(R.string.template_recommendation_why, summary),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = stringResource(R.string.template_cell_count, template.cells.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) colors.selectedCell else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!canApplyWithoutDroppingImages) {
                    Text(
                        text = stringResource(R.string.template_unavailable_count, template.cells.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            val favoriteActionDescription = if (favorite) {
                stringResource(R.string.template_favorite_remove, title)
            } else {
                stringResource(R.string.template_favorite_add, title)
            }
            IconToggleButton(
                checked = favorite,
                onCheckedChange = { onFavoriteClick() },
                enabled = favoriteEnabled,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(dimens.space4)
                    .size(dimens.touchTarget)
                    .semantics {
                        stateDescription = favoriteStateDescription
                    },
            ) {
                Icon(
                    imageVector = if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = favoriteActionDescription,
                )
            }
        }
    }
}

@Composable
private fun TemplateThumbnail(
    template: LayoutTemplate,
    palette: TemplatePalette,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRoundRect(
            color = palette.background,
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
        )
        drawRoundRect(
            color = palette.accent,
            topLeft = Offset(size.width * 0.06f, size.height * 0.08f),
            size = Size(size.width * 0.88f, size.height * 0.84f),
            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
            alpha = 0.18f,
        )
        if (template.kind == TemplateKind.BeforeAfter) {
            val radius = 14.dp.toPx()
            drawRoundRect(
                color = palette.fills[0],
                topLeft = Offset.Zero,
                size = Size(size.width / 2f, size.height),
                cornerRadius = CornerRadius(radius, radius),
            )
            drawRoundRect(
                color = palette.fills[1],
                topLeft = Offset(size.width / 2f, 0f),
                size = Size(size.width / 2f, size.height),
                cornerRadius = CornerRadius(radius, radius),
            )
            drawLine(
                outline,
                Offset(size.width / 2f, 0f),
                Offset(size.width / 2f, size.height),
                strokeWidth = 3.dp.toPx(),
            )
        } else {
            template.cells.forEach { cell ->
                val frame = LayoutMath.cellFrame(cell, size.width, size.height, 6.dp.toPx())
                drawRoundRect(
                    color = palette.fills[cell.index % palette.fills.size],
                    topLeft = Offset(frame.left, frame.top),
                    size = Size(frame.width, frame.height),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                )
            }
        }
    }
}

private data class TemplatePalette(
    val background: Color,
    val accent: Color,
    val fills: List<Color>,
)

private fun randomTemplatePalettes(
    templates: List<LayoutTemplate>,
    seed: Int,
): Map<String, TemplatePalette> {
    val random = Random(seed)
    return templates.associate { template ->
        val fillCount = maxOf(6, template.cells.size)
        val baseHue = random.nextFloat() * 360f
        val fills = List(fillCount) { index ->
            randomColor(
                random = random,
                hue = baseHue + random.nextFloat() * 360f + index * random.nextFloatIn(18f, 72f),
                saturationRange = 0.22f..0.42f,
                valueRange = 0.94f..1f,
            )
        }.shuffled(random)

        template.id to TemplatePalette(
            background = randomColor(
                random = random,
                saturationRange = 0.06f..0.18f,
                valueRange = 0.97f..1f,
            ),
            accent = randomColor(
                random = random,
                saturationRange = 0.28f..0.48f,
                valueRange = 0.92f..1f,
            ),
            fills = fills,
        )
    }
}

private fun randomColor(
    random: Random,
    hue: Float = random.nextFloat() * 360f,
    saturationRange: ClosedFloatingPointRange<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
): Color =
    hsvColor(
        hue = hue,
        saturation = random.nextFloatIn(saturationRange.start, saturationRange.endInclusive),
        value = random.nextFloatIn(valueRange.start, valueRange.endInclusive),
    )

private fun hsvColor(
    hue: Float,
    saturation: Float,
    value: Float,
): Color {
    val normalizedHue = ((hue % 360f) + 360f) % 360f
    val chroma = value * saturation
    val secondary = chroma * (1f - abs((normalizedHue / 60f) % 2f - 1f))
    val match = value - chroma
    val (redPrime, greenPrime, bluePrime) = when {
        normalizedHue < 60f -> Triple(chroma, secondary, 0f)
        normalizedHue < 120f -> Triple(secondary, chroma, 0f)
        normalizedHue < 180f -> Triple(0f, chroma, secondary)
        normalizedHue < 240f -> Triple(0f, secondary, chroma)
        normalizedHue < 300f -> Triple(secondary, 0f, chroma)
        else -> Triple(chroma, 0f, secondary)
    }
    return Color(
        red = ((redPrime + match) * 255f).roundToInt().coerceIn(0, 255),
        green = ((greenPrime + match) * 255f).roundToInt().coerceIn(0, 255),
        blue = ((bluePrime + match) * 255f).roundToInt().coerceIn(0, 255),
        alpha = 255,
    )
}

private fun Random.nextFloatIn(start: Float, endInclusive: Float): Float =
    start + nextFloat() * (endInclusive - start)

private const val MaxSmartRecommendations = 12
private const val MaxDisplayedRecommendationReasons = 4
