package com.example.splitframe.presentation.merge

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.splitframe.R
import com.example.splitframe.ads.BannerAd
import com.example.splitframe.domain.LayoutMath
import com.example.splitframe.domain.LayoutTemplate
import com.example.splitframe.domain.TemplateKind
import com.example.splitframe.presentation.descriptionRes
import com.example.splitframe.presentation.titleRes
import com.example.splitframe.ui.components.AdContainer
import com.example.splitframe.ui.components.StatusMessage
import com.example.splitframe.ui.components.StatusTone
import com.example.splitframe.ui.components.SplitFrameTopAppBar
import com.example.splitframe.ui.theme.splitFrameColors
import com.example.splitframe.ui.theme.splitFrameDimens
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun TemplatePickerScreen(
    state: MergeState,
    paletteSeed: Int,
    onTemplateSelected: (String) -> Unit,
) {
    val selectedTemplateId = state.project?.template?.id
    val selectedPhotoCount = state.project?.assignedImages?.size ?: 0
    val templatePalettes = remember(paletteSeed, state.availableTemplates) {
        randomTemplatePalettes(state.availableTemplates, paletteSeed)
    }
    Scaffold(
        topBar = {
            SplitFrameTopAppBar(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.template_picker_subtitle),
            )
        },
        bottomBar = {
            AdContainer {
                BannerAd(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                )
            }
        },
    ) { padding ->
        if (state.availableTemplates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                StatusMessage(
                    text = stringResource(R.string.templates_empty_message),
                    tone = StatusTone.Info,
                    icon = Icons.Default.ViewModule,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 168.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.availableTemplates, key = { it.id }) { template ->
                    val enabled = selectedPhotoCount == 0 || template.cells.size == selectedPhotoCount
                    TemplateCard(
                        template = template,
                        palette = templatePalettes.getValue(template.id),
                        selected = selectedTemplateId == template.id,
                        enabled = enabled,
                        onClick = { onTemplateSelected(template.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: LayoutTemplate,
    palette: TemplatePalette,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = splitFrameColors()
    val dimens = splitFrameDimens()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.62f)
            .semantics { this.selected = selected }
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) colors.selectedCell else MaterialTheme.colorScheme.outlineVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 6.dp else 1.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(dimens.space12),
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
                    text = stringResource(template.titleRes()),
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
                text = stringResource(template.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.template_cell_count, template.cells.size),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) colors.selectedCell else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!enabled) {
                Text(
                    text = stringResource(R.string.template_unavailable_count, template.cells.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
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
            drawLine(outline, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 3.dp.toPx())
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
