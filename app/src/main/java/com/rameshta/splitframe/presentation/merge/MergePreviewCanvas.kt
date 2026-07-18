package com.rameshta.splitframe.presentation.merge

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.rameshta.splitframe.R
import com.rameshta.splitframe.domain.CollageGradient
import com.rameshta.splitframe.domain.ImageDimensions
import com.rameshta.splitframe.domain.ImageTransform
import com.rameshta.splitframe.domain.LayoutMath
import com.rameshta.splitframe.domain.MergeProject
import com.rameshta.splitframe.domain.RectPx
import com.rameshta.splitframe.domain.TemplateKind
import com.rameshta.splitframe.presentation.coilModel
import com.rameshta.splitframe.ui.theme.splitFrameColors
import kotlin.math.roundToInt

@Composable
fun MergePreviewCanvas(
    project: MergeProject,
    modifier: Modifier = Modifier,
    sourceDimensions: Map<Int, ImageDimensions> = emptyMap(),
    selectedCellIndex: Int? = null,
    onCellTap: (Int) -> Unit,
    onImageTransformChanged: (Int, ImageTransform, Boolean) -> Unit = { _, _, _ -> },
) {
    val painters = project.template.cells.associate { cell ->
        val source = project.assignedImages[cell.index]
        val model = source?.coilModel()
        cell.index to rememberAsyncImagePainter(
            model = model,
            contentScale = ContentScale.Crop,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
        )
    }
    val animatedTransforms = project.template.cells.associate { cell ->
        val target = project.imageTransforms[cell.index] ?: ImageTransform.Default
        cell.index to ImageTransform(
            zoom = animateFloatAsState(
                targetValue = target.zoom,
                animationSpec = tween(durationMillis = 120),
                label = "cell${cell.index}Zoom",
            ).value,
            panX = animateFloatAsState(
                targetValue = target.panX,
                animationSpec = tween(durationMillis = 120),
                label = "cell${cell.index}PanX",
            ).value,
            panY = animateFloatAsState(
                targetValue = target.panY,
                animationSpec = tween(durationMillis = 120),
                label = "cell${cell.index}PanY",
            ).value,
        )
    }

    val border = project.borderColor.toComposeColor()
    val splitFrameColors = splitFrameColors()
    val dividerColor = MaterialTheme.colorScheme.surface
    val emptyFill = MaterialTheme.colorScheme.surfaceContainerHighest
    val emptyText = MaterialTheme.colorScheme.onSurfaceVariant
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(project, selectedCellIndex) {
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        val cellIndex = selectedCellIndex ?: return@detectTransformGestures
                        if (!project.assignedImages.containsKey(cellIndex)) return@detectTransformGestures
                        val frame = selectedFrameForGesture(
                            project = project,
                            cellIndex = cellIndex,
                            widthPx = size.width.toFloat(),
                            heightPx = size.height.toFloat(),
                            spacingPx = project.spacingDp.dp.toPx(),
                        ) ?: return@detectTransformGestures
                        if (!frame.contains(centroid.x, centroid.y)) return@detectTransformGestures
                        val current = project.imageTransforms[cellIndex] ?: ImageTransform.Default
                        val next = LayoutMath.transformAfterGesture(
                            sourceDimensions = sourceDimensions[cellIndex],
                            destinationWidthPx = frame.width,
                            destinationHeightPx = frame.height,
                            current = current,
                            panXpx = pan.x,
                            panYpx = pan.y,
                            zoomChange = zoomChange,
                        )
                        if (next != current) {
                            onImageTransformChanged(cellIndex, next, false)
                        }
                    }
                }
                .pointerInput(project, selectedCellIndex) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val cellIndex = selectedCellIndex ?: return@detectTapGestures
                            val frame = selectedFrameForGesture(
                                project = project,
                                cellIndex = cellIndex,
                                widthPx = size.width.toFloat(),
                                heightPx = size.height.toFloat(),
                                spacingPx = project.spacingDp.dp.toPx(),
                            ) ?: return@detectTapGestures
                            if (frame.contains(offset.x, offset.y) && project.assignedImages.containsKey(cellIndex)) {
                                val current = project.imageTransforms[cellIndex] ?: ImageTransform.Default
                                val next = LayoutMath.transformAfterDoubleTap(
                                    sourceDimensions = sourceDimensions[cellIndex],
                                    destinationWidthPx = frame.width,
                                    destinationHeightPx = frame.height,
                                    current = current,
                                    tapXInFramePx = offset.x - frame.left,
                                    tapYInFramePx = offset.y - frame.top,
                                )
                                onImageTransformChanged(cellIndex, next, true)
                            }
                        },
                        onTap = { offset ->
                            if (project.template.kind == TemplateKind.BeforeAfter) {
                                val dividerX = size.width * project.beforeAfterSlider.coerceIn(0.05f, 0.95f)
                                onCellTap(if (offset.x <= dividerX) 0 else 1)
                            } else {
                                val hitCell = project.template.cells.firstOrNull { cell ->
                                    val frame = LayoutMath.cellFrame(
                                        cell,
                                        size.width.toFloat(),
                                        size.height.toFloat(),
                                        project.spacingDp.dp.toPx(),
                                    )
                                    frame.contains(offset.x, offset.y)
                                }
                                hitCell?.let { onCellTap(it.index) }
                            }
                        },
                    )
                },
        ) {
            drawRect(project.backgroundGradient.toBrush(size))
            val cornerRadiusPx = project.cornerRadiusDp.dp.toPx()
            val spacingPx = project.spacingDp.dp.toPx()
            val borderWidthPx = project.borderWidthDp.dp.toPx()
            val selectedStrokeWidth = 3.dp.toPx()

            if (project.template.kind == TemplateKind.BeforeAfter) {
                val frame = RectPx(0f, 0f, size.width, size.height)
                val path = roundedPath(frame, cornerRadiusPx)
                clipPath(path) {
                    drawRect(emptyFill)
                    val dividerX = size.width * project.beforeAfterSlider.coerceIn(0.05f, 0.95f)
                    clipRect(right = dividerX) {
                        drawPainterInFrame(
                            painter = painters[0],
                            frame = frame,
                            dimensions = sourceDimensions[0],
                            transform = animatedTransforms[0] ?: ImageTransform.Default,
                        )
                    }
                    clipRect(left = dividerX) {
                        drawPainterInFrame(
                            painter = painters[1],
                            frame = frame,
                            dimensions = sourceDimensions[1],
                            transform = animatedTransforms[1] ?: ImageTransform.Default,
                        )
                    }
                    drawLine(
                        color = dividerColor,
                        start = Offset(dividerX, 0f),
                        end = Offset(dividerX, size.height),
                        strokeWidth = 4.dp.toPx(),
                    )
                }
                selectedCellIndex?.let { index ->
                    val selectedFrame = if (index == 0) {
                        RectPx(0f, 0f, size.width * project.beforeAfterSlider, size.height)
                    } else {
                        RectPx(size.width * project.beforeAfterSlider, 0f, size.width, size.height)
                    }
                    drawRoundRect(
                        color = splitFrameColors.selectedCell,
                        topLeft = Offset(selectedFrame.left, selectedFrame.top),
                        size = Size(selectedFrame.width, selectedFrame.height),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(selectedStrokeWidth),
                    )
                }
                drawRoundRect(
                    color = border,
                    topLeft = Offset(frame.left, frame.top),
                    size = Size(frame.width, frame.height),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(borderWidthPx),
                )
            } else {
                project.template.cells.forEach { cell ->
                    val frame = LayoutMath.cellFrame(cell, size.width, size.height, spacingPx)
                    val path = roundedPath(frame, cornerRadiusPx)
                    clipPath(path) {
                        drawRect(
                            color = emptyFill,
                            topLeft = Offset(frame.left, frame.top),
                            size = Size(frame.width, frame.height),
                        )
                        drawPainterInFrame(
                            painter = painters[cell.index],
                            frame = frame,
                            dimensions = sourceDimensions[cell.index],
                            transform = animatedTransforms[cell.index] ?: ImageTransform.Default,
                        )
                    }
                    if (borderWidthPx > 0f) {
                        drawRoundRect(
                            color = border,
                            topLeft = Offset(frame.left, frame.top),
                            size = Size(frame.width, frame.height),
                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(borderWidthPx),
                        )
                    }
                    if (selectedCellIndex == cell.index) {
                        drawRoundRect(
                            color = splitFrameColors.selectedCell,
                            topLeft = Offset(frame.left, frame.top),
                            size = Size(frame.width, frame.height),
                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(selectedStrokeWidth),
                        )
                    }
                }
            }
        }

        if (canvasSize.width > 0 && canvasSize.height > 0 && project.template.kind != TemplateKind.BeforeAfter) {
            project.template.cells
                .filter { project.assignedImages[it.index] == null }
                .forEach { cell ->
                    val frame = with(density) {
                        LayoutMath.cellFrame(
                            cell,
                            canvasSize.width.toFloat(),
                            canvasSize.height.toFloat(),
                            project.spacingDp.dp.toPx(),
                        )
                    }
                    EmptyCellLabel(
                        frame = frame,
                        canvasSize = canvasSize,
                        textColor = emptyText,
                        onClick = { onCellTap(cell.index) },
                    )
                }
        }
    }
}

private fun selectedFrameForGesture(
    project: MergeProject,
    cellIndex: Int,
    widthPx: Float,
    heightPx: Float,
    spacingPx: Float,
): RectPx? {
    if (project.template.kind == TemplateKind.BeforeAfter) {
        return RectPx(0f, 0f, widthPx, heightPx)
    }
    val cell = project.template.cells.firstOrNull { it.index == cellIndex } ?: return null
    return LayoutMath.cellFrame(cell, widthPx, heightPx, spacingPx)
}

@Composable
private fun EmptyCellLabel(
    frame: RectPx,
    canvasSize: IntSize,
    textColor: Color,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val description = stringResource(R.string.add_photo_to_edit)
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    frame.left.roundToInt().coerceIn(0, canvasSize.width),
                    frame.top.roundToInt().coerceIn(0, canvasSize.height),
                )
            }
            .size(
                width = with(density) { frame.width.toDp() },
                height = with(density) { frame.height.toDp() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .semantics {
                    contentDescription = description
                    role = Role.Button
                }
                .clickable(onClick = onClick),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            shape = RoundedCornerShape(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 96.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.roundedPath(
    frame: RectPx,
    cornerRadiusPx: Float,
): Path =
    Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(frame.left, frame.top, frame.right, frame.bottom),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
            ),
        )
    }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPainterInFrame(
    painter: androidx.compose.ui.graphics.painter.Painter?,
    frame: RectPx,
    dimensions: ImageDimensions?,
    transform: ImageTransform,
) {
    if (painter == null) return
    if (dimensions == null) {
        withTransform({
            translate(frame.left, frame.top)
        }) {
            with(painter) {
                draw(Size(frame.width, frame.height))
            }
        }
        return
    }

    val source = LayoutMath.cropToFillSourceRect(
        sourceWidthPx = dimensions.widthPx.toFloat(),
        sourceHeightPx = dimensions.heightPx.toFloat(),
        destinationWidthPx = frame.width,
        destinationHeightPx = frame.height,
        transform = transform,
    )
    val scale = frame.width / source.width.coerceAtLeast(1f)
    val drawWidth = dimensions.widthPx * scale
    val drawHeight = dimensions.heightPx * scale
    withTransform({
        translate(
            left = frame.left - source.left * scale,
            top = frame.top - source.top * scale,
        )
    }) {
        with(painter) {
            draw(Size(drawWidth, drawHeight))
        }
    }
}

private fun ULong.toComposeColor(): Color = Color(toLong().toInt())

private fun CollageGradient.toBrush(size: Size): Brush =
    Brush.linearGradient(
        colors = listOf(startColor.toComposeColor(), centerColor.toComposeColor(), endColor.toComposeColor()),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
