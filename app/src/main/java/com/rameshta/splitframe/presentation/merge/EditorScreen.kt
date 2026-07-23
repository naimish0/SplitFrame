package com.rameshta.splitframe.presentation.merge

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import coil.compose.AsyncImage
import com.rameshta.splitframe.R
import com.rameshta.splitframe.ads.ExternalUiReason
import com.rameshta.splitframe.ads.LocalExternalUiLauncher
import com.rameshta.splitframe.domain.CollageLimits
import com.rameshta.splitframe.domain.CollageBackgroundKind
import com.rameshta.splitframe.domain.CollageBackgroundStyle
import com.rameshta.splitframe.domain.CollageBorderKind
import com.rameshta.splitframe.domain.CollagePattern
import com.rameshta.splitframe.domain.CollageTextFont
import com.rameshta.splitframe.domain.CollageTextLayer
import com.rameshta.splitframe.domain.CropShape
import com.rameshta.splitframe.domain.ImageSource
import com.rameshta.splitframe.domain.ImageTransform
import com.rameshta.splitframe.domain.TemplateKind
import com.rameshta.splitframe.presentation.coilModel
import com.rameshta.splitframe.presentation.titleText
import com.rameshta.splitframe.ui.components.PrimaryActionButton
import com.rameshta.splitframe.ui.components.SecondaryActionButton
import com.rameshta.splitframe.ui.components.SplitFrameSection
import com.rameshta.splitframe.ui.components.SplitFrameTopAppBar
import com.rameshta.splitframe.ui.components.StatusMessage
import com.rameshta.splitframe.ui.components.StatusTone
import com.rameshta.splitframe.ui.theme.EditorBackgroundSwatches
import com.rameshta.splitframe.ui.theme.splitFrameColors
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: MergeState,
    onIntent: (MergeIntent) -> Unit,
    onBack: () -> Unit,
    onRequestExport: (() -> Unit) -> Unit = { export -> export() },
    onExportForShare: () -> Unit = {},
) {
    val project = state.project ?: return
    val context = LocalContext.current
    val externalUiLauncher = LocalExternalUiLauncher.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedCell by rememberSaveable(project.template.id) { mutableIntStateOf(0) }
    var showExportSheet by rememberSaveable { mutableStateOf(false) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    var isPickerOpen by rememberSaveable { mutableStateOf(false) }
    val selectionLimitReached = project.assignedImages.size >= CollageLimits.MaxImages
    val canExportImages = project.isReadyForImageExport &&
        state.unreadableSourceCells.isEmpty() &&
        !state.isExporting

    val singlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        isPickerOpen = false
        if (uri != null) {
            context.persistUriAccessIfSupported(uri)
            onIntent(MergeIntent.AssignImage(selectedCell, ImageSource.LocalUri(uri.toString())))
        }
    }
    val multiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = CollageLimits.MaxImages),
    ) { uris: List<Uri> ->
        isPickerOpen = false
        if (uris.isNotEmpty()) {
            uris.forEach(context::persistUriAccessIfSupported)
            onIntent(MergeIntent.AssignImages(uris.map { ImageSource.LocalUri(it.toString()) }))
        }
    }

    fun launchSinglePicker() {
        if (isPickerOpen) return
        if (selectionLimitReached && project.assignedImages[selectedCell] == null) return
        isPickerOpen = true
        externalUiLauncher.launch(ExternalUiReason.MediaPicker) {
            singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    fun launchMultiPicker() {
        if (isPickerOpen) return
        if (selectionLimitReached) return
        isPickerOpen = true
        externalUiLauncher.launch(ExternalUiReason.MediaPicker) {
            multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    val errorMessage = state.error?.let { stringResource(it) }
    LaunchedEffect(errorMessage, showExportSheet) {
        val message = errorMessage ?: return@LaunchedEffect
        if (showExportSheet) return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onIntent(MergeIntent.ClearError)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SplitFrameTopAppBar(
                title = stringResource(R.string.editor_title),
                subtitle = project.template.titleText(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onIntent(MergeIntent.UndoEdit) },
                        enabled = state.canUndo && !state.isExporting,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.undo))
                    }
                    IconButton(
                        onClick = { onIntent(MergeIntent.RedoEdit) },
                        enabled = state.canRedo && !state.isExporting,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.redo))
                    }
                    IconButton(
                        onClick = {
                            if (project.hasEdits()) showResetConfirm = true else onIntent(MergeIntent.Reset)
                        },
                        enabled = !state.isExporting,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reset))
                    }
                    IconButton(onClick = { showExportSheet = true }, enabled = canExportImages) {
                        Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.export))
                    }
                },
            )
        },
    ) { padding ->
        LaunchedEffect(project.template.cells.size, selectedCell) {
            val maxCell = project.template.cells.lastIndex.coerceAtLeast(0)
            if (selectedCell > maxCell) selectedCell = maxCell
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BoxWithConstraints {
                val wide = maxWidth >= 720.dp
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        EditorCanvasSection(
                            state = state,
                            selectedCell = selectedCell,
                            onAddPhotos = ::launchMultiPicker,
                            onCellSelected = { cell ->
                                selectedCell = cell
                                if (project.assignedImages[cell] == null && !selectionLimitReached) launchSinglePicker()
                            },
                            onImageTransformChanged = { cell, transform, trackUndo ->
                                onIntent(MergeIntent.UpdateImageTransform(cell, transform, trackUndo))
                            },
                            modifier = Modifier.weight(1.25f),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ThumbnailStrip(
                                state = state,
                                selectedCell = selectedCell,
                                onCellSelected = { selectedCell = it },
                                onReorder = { from, to -> onIntent(MergeIntent.ReorderImages(from, to)) },
                            )
                            SelectedCellPanel(
                                state = state,
                                selectedCell = selectedCell,
                                onPickPhoto = ::launchSinglePicker,
                                onAddPhotos = ::launchMultiPicker,
                                onIntent = onIntent,
                            )
                        EditorTools(
                                state = state,
                                selectedCell = selectedCell,
                                onExport = { showExportSheet = true },
                                canExport = canExportImages,
                                onIntent = onIntent,
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        EditorCanvasSection(
                            state = state,
                            selectedCell = selectedCell,
                            onAddPhotos = ::launchMultiPicker,
                            onCellSelected = { cell ->
                                selectedCell = cell
                                if (project.assignedImages[cell] == null && !selectionLimitReached) launchSinglePicker()
                            },
                            onImageTransformChanged = { cell, transform, trackUndo ->
                                onIntent(MergeIntent.UpdateImageTransform(cell, transform, trackUndo))
                            },
                        )
                        ThumbnailStrip(
                            state = state,
                            selectedCell = selectedCell,
                            onCellSelected = { selectedCell = it },
                            onReorder = { from, to -> onIntent(MergeIntent.ReorderImages(from, to)) },
                        )
                        SelectedCellPanel(
                            state = state,
                            selectedCell = selectedCell,
                            onPickPhoto = ::launchSinglePicker,
                            onAddPhotos = ::launchMultiPicker,
                            onIntent = onIntent,
                        )
	                        EditorTools(
                            state = state,
                            selectedCell = selectedCell,
                            onExport = { showExportSheet = true },
                            canExport = canExportImages,
                            onIntent = onIntent,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.reset_project_title)) },
            text = { Text(stringResource(R.string.reset_project_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onIntent(MergeIntent.Reset)
                    },
                ) {
                    Text(stringResource(R.string.confirm_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showExportSheet) {
        ModalBottomSheet(onDismissRequest = { showExportSheet = false }) {
            ExportSheet(
                state = state,
                onIntent = onIntent,
                onClose = { showExportSheet = false },
                onRequestExport = onRequestExport,
                onExportForShare = onExportForShare,
            )
        }
    }
}

@Composable
private fun EditorCanvasSection(
    state: MergeState,
    selectedCell: Int,
    onAddPhotos: () -> Unit,
    onCellSelected: (Int) -> Unit,
    onImageTransformChanged: (Int, ImageTransform, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val project = state.project ?: return
    val colors = splitFrameColors()
    val selectionLimitReached = project.assignedImages.size >= CollageLimits.MaxImages
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.editorCanvas,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.selected_cell_indicator, selectedCell + 1),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.selectedCell,
                )
                Text(
                    text = stringResource(R.string.photo_count, project.assignedImages.size, CollageLimits.MaxImages),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SecondaryActionButton(
                    text = stringResource(R.string.add_photos),
                    onClick = onAddPhotos,
                    enabled = !selectionLimitReached && !state.isExporting,
                    icon = Icons.Default.AddPhotoAlternate,
                )
            }
            if (selectionLimitReached) {
                StatusMessage(
                    text = stringResource(R.string.photo_limit_reached),
                    tone = StatusTone.Info,
                )
            }
            if (state.unreadableSourceCells.isNotEmpty()) {
                StatusMessage(
                    text = stringResource(R.string.photo_missing_media_repair),
                    tone = StatusTone.Warning,
                )
            }
            MergePreviewCanvas(
                project = project,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(project.template.aspectRatio),
                sourceDimensions = state.sourceDimensions,
                selectedCellIndex = selectedCell,
                blurredBackground = state.blurredBackground,
                onCellTap = onCellSelected,
                onImageTransformChanged = onImageTransformChanged,
            )
        }
    }
}

@Composable
private fun ThumbnailStrip(
    state: MergeState,
    selectedCell: Int,
    onCellSelected: (Int) -> Unit,
    onReorder: (Int, Int) -> Unit,
) {
    val project = state.project ?: return
    if (project.assignedImages.isEmpty()) return
    val cells = project.template.cells.filter { project.assignedImages[it.index] != null }
    val scrollState = rememberScrollState()
    val itemStepPx = with(LocalDensity.current) { 82.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    var draggingIndex by remember(cells.map { it.index }) { mutableStateOf<Int?>(null) }
    var dragTargetIndex by remember(cells.map { it.index }) { mutableStateOf<Int?>(null) }
    SplitFrameSection(
        title = stringResource(R.string.thumbnail_strip),
        supportingText = stringResource(R.string.photo_count, project.assignedImages.size, CollageLimits.MaxImages),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            cells.forEachIndexed { orderIndex, cell ->
                val source = project.assignedImages[cell.index] ?: return@forEachIndexed
                val isSelected = selectedCell == cell.index
                val isDragging = draggingIndex == orderIndex
                val isDropTarget = dragTargetIndex == orderIndex && draggingIndex != orderIndex
                val liftScale by animateFloatAsState(
                    targetValue = if (isDragging) 1.08f else 1f,
                    label = "thumbnail-lift-$orderIndex",
                )
                val thumbnailDescription = stringResource(R.string.thumbnail_content_description, orderIndex + 1, cells.size)
                val movePrevious = stringResource(R.string.move_previous)
                val moveNext = stringResource(R.string.move_next)
                Surface(
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            scaleX = liftScale
                            scaleY = liftScale
                            alpha = if (isDragging) 0.88f else 1f
                        }
                        .semantics {
                            selected = isSelected
                            contentDescription = thumbnailDescription
                            customActions = buildList {
                                if (orderIndex > 0) {
                                    add(
                                        CustomAccessibilityAction(movePrevious) {
                                            onReorder(orderIndex, orderIndex - 1)
                                            true
                                        },
                                    )
                                }
                                if (orderIndex < cells.lastIndex) {
                                    add(
                                        CustomAccessibilityAction(moveNext) {
                                            onReorder(orderIndex, orderIndex + 1)
                                            true
                                        },
                                    )
                                }
                            }
                        }
                        .pointerInput(cells.size, orderIndex) {
                            var dragX = 0f
                            detectDragGestures(
                                onDragStart = {
                                    dragX = 0f
                                    draggingIndex = orderIndex
                                    dragTargetIndex = orderIndex
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragX += dragAmount.x
                                    val target = (orderIndex + (dragX / itemStepPx).roundToInt())
                                        .coerceIn(cells.indices)
                                    if (target != dragTargetIndex) {
                                        dragTargetIndex = target
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onDragEnd = {
                                    val target = dragTargetIndex
                                    if (target != null && target != orderIndex) {
                                        onReorder(orderIndex, target)
                                        onCellSelected(cells[target].index)
                                    }
                                    draggingIndex = null
                                    dragTargetIndex = null
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    dragTargetIndex = null
                                },
                            )
                        }
                        .clickable { onCellSelected(cell.index) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected || isDropTarget) 3.dp else 1.dp,
                        color = if (isSelected || isDropTarget) {
                            splitFrameColors().selectedCell
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                    tonalElevation = if (isSelected || isDragging) 6.dp else 1.dp,
                ) {
                    Box {
                        AsyncImage(
                            model = source.coilModel(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = stringResource(R.string.thumbnail_position, orderIndex + 1, cells.size),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedCellPanel(
    state: MergeState,
    selectedCell: Int,
    onPickPhoto: () -> Unit,
    onAddPhotos: () -> Unit,
    onIntent: (MergeIntent) -> Unit,
) {
    val project = state.project ?: return
    val source = project.assignedImages[selectedCell]
    val transform = project.imageTransforms[selectedCell] ?: ImageTransform.Default
    val selectionLimitReached = project.assignedImages.size >= CollageLimits.MaxImages
    var showRemoveConfirm by rememberSaveable(selectedCell) { mutableStateOf(false) }
    SplitFrameSection(
        title = stringResource(R.string.cell_number, selectedCell + 1),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryActionButton(
                    text = stringResource(if (source == null) R.string.select_photo else R.string.replace_photo),
                    onClick = onPickPhoto,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isExporting && (source != null || !selectionLimitReached),
                    icon = Icons.Default.AddPhotoAlternate,
                )
                if (source != null) {
                    SecondaryActionButton(
                        text = stringResource(R.string.remove_photo),
                        onClick = {
                            if (transform != ImageTransform.Default) {
                                showRemoveConfirm = true
                            } else {
                                onIntent(MergeIntent.RemoveImage(selectedCell))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isExporting,
                        icon = Icons.Default.Delete,
                    )
                }
            }
            SecondaryActionButton(
                text = stringResource(R.string.add_multiple_photos),
                onClick = onAddPhotos,
                enabled = !selectionLimitReached && !state.isExporting,
                icon = Icons.Default.AddPhotoAlternate,
                modifier = Modifier.fillMaxWidth(),
            )
            if (selectionLimitReached) {
                StatusMessage(
                    text = stringResource(R.string.photo_limit_reached),
                    tone = StatusTone.Info,
                )
            }

            if (source != null) {
                StatusMessage(
                    text = stringResource(R.string.crop_gesture_help),
                    tone = StatusTone.Info,
                )
                LabeledSlider(
                    label = stringResource(R.string.zoom),
                    value = transform.zoom,
                    valueText = stringResource(R.string.zoom_value, transform.zoom),
                    valueRange = ImageTransform.MIN_ZOOM..ImageTransform.MAX_ZOOM,
                    onValueChange = {
                        onIntent(MergeIntent.UpdateImageTransform(selectedCell, transform.copy(zoom = it)))
                    },
                )
                SecondaryActionButton(
                    text = stringResource(R.string.reset_crop),
                    onClick = {
                        onIntent(MergeIntent.UpdateImageTransform(selectedCell, ImageTransform.Default))
                    },
                    enabled = transform != ImageTransform.Default,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Refresh,
                )
            }
        }
    }
    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text(stringResource(R.string.remove_photo_title)) },
            text = { Text(stringResource(R.string.remove_photo_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveConfirm = false
                        onIntent(MergeIntent.RemoveImage(selectedCell))
                    },
                ) {
                    Text(stringResource(R.string.remove_photo))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorTools(
    state: MergeState,
    selectedCell: Int,
    onExport: () -> Unit,
    canExport: Boolean,
    onIntent: (MergeIntent) -> Unit,
) {
    val project = state.project ?: return
    SplitFrameSection(
        title = stringResource(R.string.layout_controls),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SecondaryActionButton(
                text = stringResource(R.string.auto_arrange),
                onClick = { onIntent(MergeIntent.AutoArrange) },
                enabled = project.assignedImages.isNotEmpty() && !state.isExporting,
                modifier = Modifier.fillMaxWidth(),
            )
            if (project.template.kind != TemplateKind.BeforeAfter) {
                ShapeCropTool(
                    selectedShape = project.cropShapes[selectedCell] ?: CropShape.Rectangle,
                    onShapeSelected = { shape ->
                        onIntent(MergeIntent.UpdateCropShape(selectedCell, shape))
                    },
                )
            }
            LabeledSlider(
                label = stringResource(R.string.spacing),
                value = project.spacingDp,
                valueRange = 0f..36f,
                onValueChange = { onIntent(MergeIntent.UpdateSpacing(it)) },
            )
            LabeledSlider(
                label = stringResource(R.string.corner_radius),
                value = project.cornerRadiusDp,
                valueRange = 0f..64f,
                onValueChange = { onIntent(MergeIntent.UpdateCornerRadius(it)) },
            )
            if (project.template.kind == TemplateKind.BeforeAfter) {
                LabeledSlider(
                    label = stringResource(R.string.before_after_slider),
                    value = project.beforeAfterSlider,
                    valueRange = 0.05f..0.95f,
                    onValueChange = { onIntent(MergeIntent.UpdateBeforeAfterSlider(it)) },
                )
            }
            BackgroundTool(project.backgroundStyle, selectedCell, onIntent)
            BorderTool(project.borderStyle, project.borderWidthDp, onIntent)
            TextTool(project.textLayers, onIntent)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryActionButton(
                    text = stringResource(R.string.swap_previous),
                    onClick = { onIntent(MergeIntent.SwapCells(selectedCell, selectedCell - 1)) },
                    enabled = selectedCell > 0,
                    modifier = Modifier.weight(1f),
                )
                SecondaryActionButton(
                    text = stringResource(R.string.swap_next),
                    onClick = { onIntent(MergeIntent.SwapCells(selectedCell, selectedCell + 1)) },
                    enabled = selectedCell < project.template.cells.lastIndex,
                    modifier = Modifier.weight(1f),
                )
            }
            PrimaryActionButton(
                text = stringResource(R.string.export),
                onClick = onExport,
                enabled = canExport,
                icon = Icons.Default.FileDownload,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ShapeCropTool(
    selectedShape: CropShape,
    onShapeSelected: (CropShape) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.shape_crop), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CropShape.entries.forEach { shape ->
                FilterChip(
                    selected = selectedShape == shape,
                    onClick = { onShapeSelected(shape) },
                    label = { Text(shape.label()) },
                )
            }
        }
    }
}

@Composable
private fun BackgroundTool(
    style: CollageBackgroundStyle,
    selectedCell: Int,
    onIntent: (MergeIntent) -> Unit,
) {
    val safe = style.normalized()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.background), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CollageBackgroundKind.entries.forEach { kind ->
                FilterChip(
                    selected = safe.kind == kind,
                    onClick = {
                        val updated = when (kind) {
                            CollageBackgroundKind.AdaptiveLinear -> safe.copy(kind = kind)
                            CollageBackgroundKind.Solid -> safe.copy(kind = kind)
                            CollageBackgroundKind.LinearGradient,
                            CollageBackgroundKind.RadialGradient,
                            -> BackgroundPalettes.first().applyTo(safe).copy(kind = kind)
                            CollageBackgroundKind.MediaBlur -> safe.copy(
                                kind = kind,
                                blurSourceCellIndex = selectedCell,
                            )
                            CollageBackgroundKind.Pattern -> safe.copy(
                                kind = kind,
                                pattern = CollagePattern.Dots,
                            )
                        }
                        onIntent(MergeIntent.UpdateBackgroundStyle(updated))
                    },
                    label = { Text(kind.label()) },
                )
            }
        }
        when (safe.kind) {
            CollageBackgroundKind.Solid -> ColorPaletteRow(
                selectedColor = safe.primaryColor,
                onSelected = { color -> onIntent(MergeIntent.UpdateBackgroundColor(color)) },
            )
            CollageBackgroundKind.LinearGradient,
            CollageBackgroundKind.RadialGradient,
            -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BackgroundPalettes.forEach { palette ->
                    FilterChip(
                        selected = palette.matches(safe),
                        onClick = {
                            onIntent(
                                MergeIntent.UpdateBackgroundStyle(
                                    palette.applyTo(safe).copy(kind = safe.kind),
                                ),
                            )
                        },
                        label = { Text(stringResource(palette.labelRes)) },
                    )
                }
            }
            CollageBackgroundKind.MediaBlur -> LabeledSlider(
                label = stringResource(R.string.blur_radius),
                value = safe.blurRadius.toFloat(),
                valueRange = 1f..32f,
                onValueChange = { radius ->
                    onIntent(
                        MergeIntent.UpdateBackgroundStyle(
                            safe.copy(blurRadius = radius.roundToInt()),
                        ),
                    )
                },
            )
            CollageBackgroundKind.Pattern -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CollagePattern.entries.forEach { pattern ->
                    FilterChip(
                        selected = safe.pattern == pattern,
                        onClick = {
                            onIntent(MergeIntent.UpdateBackgroundStyle(safe.copy(pattern = pattern)))
                        },
                        label = { Text(pattern.label()) },
                    )
                }
            }
            CollageBackgroundKind.AdaptiveLinear -> Unit
        }
    }
}

@Composable
private fun BorderTool(
    style: com.rameshta.splitframe.domain.CollageBorderStyle,
    widthDp: Float,
    onIntent: (MergeIntent) -> Unit,
) {
    val safe = style.normalized()
    val selectedKind = if (safe.kind == CollageBorderKind.LegacySolid) {
        CollageBorderKind.Solid
    } else {
        safe.kind
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.border), style = MaterialTheme.typography.labelLarge)
        LabeledSlider(
            label = stringResource(R.string.border_width),
            value = widthDp,
            valueRange = 0f..12f,
            onValueChange = { onIntent(MergeIntent.UpdateBorderWidth(it)) },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                CollageBorderKind.Solid,
                CollageBorderKind.LinearGradient,
                CollageBorderKind.Dashed,
            ).forEach { kind ->
                FilterChip(
                    selected = selectedKind == kind,
                    onClick = {
                        onIntent(
                            MergeIntent.UpdateBorderStyle(
                                safe.copy(
                                    kind = kind,
                                    secondaryColor = if (kind == CollageBorderKind.LinearGradient) {
                                        BorderGradientSecondColor
                                    } else {
                                        safe.secondaryColor
                                    },
                                ),
                            ),
                        )
                    },
                    label = { Text(kind.label()) },
                )
            }
        }
        ColorPaletteRow(
            selectedColor = safe.primaryColor,
            onSelected = { color ->
                onIntent(MergeIntent.UpdateBorderStyle(safe.copy(primaryColor = color)))
            },
        )
        if (selectedKind == CollageBorderKind.Dashed) {
            LabeledSlider(
                label = stringResource(R.string.dash_length),
                value = safe.dashLengthDp,
                valueRange = 1f..48f,
                onValueChange = {
                    onIntent(MergeIntent.UpdateBorderStyle(safe.copy(dashLengthDp = it)))
                },
            )
            LabeledSlider(
                label = stringResource(R.string.dash_gap),
                value = safe.gapLengthDp,
                valueRange = 1f..48f,
                onValueChange = {
                    onIntent(MergeIntent.UpdateBorderStyle(safe.copy(gapLengthDp = it)))
                },
            )
        }
    }
}

@Composable
private fun TextTool(
    layers: List<CollageTextLayer>,
    onIntent: (MergeIntent) -> Unit,
) {
    var selectedLayerId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(layers.map(CollageTextLayer::id)) {
        if (layers.none { it.id == selectedLayerId }) {
            selectedLayerId = layers.lastOrNull()?.id
        }
    }
    val selectedLayer = layers.firstOrNull { it.id == selectedLayerId }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.text_tool), style = MaterialTheme.typography.labelLarge)
        SecondaryActionButton(
            text = stringResource(R.string.add_text),
            onClick = { onIntent(MergeIntent.AddTextLayer) },
            icon = Icons.Default.Add,
            modifier = Modifier.fillMaxWidth(),
        )
        if (layers.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                layers.forEachIndexed { index, layer ->
                    FilterChip(
                        selected = layer.id == selectedLayerId,
                        onClick = { selectedLayerId = layer.id },
                        label = {
                            Text(
                                layer.text.lineSequence().firstOrNull()?.take(18)?.takeIf(String::isNotBlank)
                                    ?: stringResource(R.string.text_layer_number, index + 1),
                            )
                        },
                    )
                }
            }
        }
        selectedLayer?.let { layer ->
            OutlinedTextField(
                value = layer.text,
                onValueChange = { onIntent(MergeIntent.UpdateTextLayer(layer.copy(text = it))) },
                label = { Text(stringResource(R.string.text_content)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 6,
            )
            Text(stringResource(R.string.font), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CollageTextFont.entries.forEach { font ->
                    FilterChip(
                        selected = layer.font == font,
                        onClick = { onIntent(MergeIntent.UpdateTextLayer(layer.copy(font = font))) },
                        label = { Text(font.label()) },
                    )
                }
            }
            Text(stringResource(R.string.text_color), style = MaterialTheme.typography.labelMedium)
            ColorPaletteRow(layer.color) { color ->
                onIntent(MergeIntent.UpdateTextLayer(layer.copy(color = color)))
            }
            LabeledSlider(
                label = stringResource(R.string.font_size),
                value = layer.fontSize,
                valueRange = 8f..144f,
                onValueChange = { onIntent(MergeIntent.UpdateTextLayer(layer.copy(fontSize = it))) },
            )
            Text(stringResource(R.string.outline_color), style = MaterialTheme.typography.labelMedium)
            ColorPaletteRow(layer.outlineColor) { color ->
                onIntent(MergeIntent.UpdateTextLayer(layer.copy(outlineColor = color)))
            }
            LabeledSlider(
                label = stringResource(R.string.outline_width),
                value = layer.outlineWidth,
                valueRange = 0f..12f,
                onValueChange = { onIntent(MergeIntent.UpdateTextLayer(layer.copy(outlineWidth = it))) },
            )
            Text(stringResource(R.string.shadow_color), style = MaterialTheme.typography.labelMedium)
            ColorPaletteRow(layer.shadowColor) { color ->
                onIntent(MergeIntent.UpdateTextLayer(layer.copy(shadowColor = color)))
            }
            LabeledSlider(
                label = stringResource(R.string.shadow_radius),
                value = layer.shadowRadius,
                valueRange = 0f..24f,
                onValueChange = { onIntent(MergeIntent.UpdateTextLayer(layer.copy(shadowRadius = it))) },
            )
            LabeledSlider(
                label = stringResource(R.string.shadow_offset_x),
                value = layer.shadowOffsetX,
                valueRange = -32f..32f,
                onValueChange = { onIntent(MergeIntent.UpdateTextLayer(layer.copy(shadowOffsetX = it))) },
            )
            LabeledSlider(
                label = stringResource(R.string.shadow_offset_y),
                value = layer.shadowOffsetY,
                valueRange = -32f..32f,
                onValueChange = { onIntent(MergeIntent.UpdateTextLayer(layer.copy(shadowOffsetY = it))) },
            )
            LabeledSlider(
                label = stringResource(R.string.opacity),
                value = layer.opacity,
                valueText = "${(layer.opacity * 100f).roundToInt()}%",
                valueRange = 0f..1f,
                onValueChange = { onIntent(MergeIntent.UpdateTextLayer(layer.copy(opacity = it))) },
            )
            LabeledSlider(
                label = stringResource(R.string.rotation),
                value = layer.rotationDegrees,
                valueRange = -180f..180f,
                onValueChange = { onIntent(MergeIntent.UpdateTextLayer(layer.copy(rotationDegrees = it))) },
            )
            LabeledSlider(
                label = stringResource(R.string.position_x),
                value = layer.centerX,
                valueText = "${(layer.centerX * 100f).roundToInt()}%",
                valueRange = 0f..1f,
                onValueChange = { onIntent(MergeIntent.UpdateTextLayer(layer.copy(centerX = it))) },
            )
            LabeledSlider(
                label = stringResource(R.string.position_y),
                value = layer.centerY,
                valueText = "${(layer.centerY * 100f).roundToInt()}%",
                valueRange = 0f..1f,
                onValueChange = { onIntent(MergeIntent.UpdateTextLayer(layer.copy(centerY = it))) },
            )
            LabeledSlider(
                label = stringResource(R.string.scale),
                value = layer.scale,
                valueText = "${(layer.scale * 100f).roundToInt()}%",
                valueRange = 0.25f..4f,
                onValueChange = { onIntent(MergeIntent.UpdateTextLayer(layer.copy(scale = it))) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryActionButton(
                    text = stringResource(R.string.duplicate),
                    onClick = { onIntent(MergeIntent.DuplicateTextLayer(layer.id)) },
                    icon = Icons.Default.ContentCopy,
                    modifier = Modifier.weight(1f),
                )
                SecondaryActionButton(
                    text = stringResource(R.string.delete),
                    onClick = { onIntent(MergeIntent.DeleteTextLayer(layer.id)) },
                    icon = Icons.Default.Delete,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ColorPaletteRow(
    selectedColor: ULong,
    onSelected: (ULong) -> Unit,
) {
    val descriptions = listOf(
        R.string.select_background_white,
        R.string.select_background_black,
        R.string.select_background_gray,
        R.string.select_background_green,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EditorBackgroundSwatches.forEachIndexed { index, swatch ->
            ColorSwatch(
                argb = swatch.argb,
                color = swatch.color,
                description = stringResource(descriptions[index]),
                selected = selectedColor == swatch.argb,
                onClick = { onSelected(swatch.argb) },
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueText: String = value.toInt().toString(),
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

@Composable
private fun ColorSwatch(
    argb: ULong,
    color: Color,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .padding(6.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                CircleShape,
            )
            .semantics {
                contentDescription = description
                this.selected = selected
            }
            .clickable(onClick = onClick),
    )
}

private data class CreativePalette(
    val labelRes: Int,
    val primary: ULong,
    val secondary: ULong,
    val tertiary: ULong,
) {
    fun applyTo(style: CollageBackgroundStyle): CollageBackgroundStyle = style.copy(
        primaryColor = primary,
        secondaryColor = secondary,
        tertiaryColor = tertiary,
    )

    fun matches(style: CollageBackgroundStyle): Boolean =
        style.primaryColor == primary &&
            style.secondaryColor == secondary &&
            style.tertiaryColor == tertiary
}

private val BackgroundPalettes = listOf(
    CreativePalette(R.string.background_palette_ocean, 0xFF083D3DuL, 0xFF2A9D8FuL, 0xFFE9F5F2uL),
    CreativePalette(R.string.background_palette_sunset, 0xFF5B2A86uL, 0xFFE76F51uL, 0xFFF4A261uL),
    CreativePalette(R.string.background_palette_mono, 0xFF111827uL, 0xFF6B7280uL, 0xFFF3F4F6uL),
)

private const val BorderGradientSecondColor: ULong = 0xFFFFFFFFuL

@Composable
private fun CollageBackgroundKind.label(): String = when (this) {
    CollageBackgroundKind.AdaptiveLinear -> stringResource(R.string.background_adaptive)
    CollageBackgroundKind.Solid -> stringResource(R.string.background_solid)
    CollageBackgroundKind.LinearGradient -> stringResource(R.string.background_linear_gradient)
    CollageBackgroundKind.RadialGradient -> stringResource(R.string.background_radial_gradient)
    CollageBackgroundKind.MediaBlur -> stringResource(R.string.background_media_blur)
    CollageBackgroundKind.Pattern -> stringResource(R.string.background_pattern)
}

@Composable
private fun CollagePattern.label(): String = when (this) {
    CollagePattern.Dots -> stringResource(R.string.pattern_dots)
    CollagePattern.DiagonalStripes -> stringResource(R.string.pattern_stripes)
}

@Composable
private fun CollageBorderKind.label(): String = when (this) {
    CollageBorderKind.LegacySolid,
    CollageBorderKind.Solid,
    -> stringResource(R.string.border_solid)
    CollageBorderKind.LinearGradient -> stringResource(R.string.border_gradient)
    CollageBorderKind.Dashed -> stringResource(R.string.border_dashed)
}

@Composable
private fun CropShape.label(): String = when (this) {
    CropShape.Rectangle -> stringResource(R.string.shape_rectangle)
    CropShape.Circle -> stringResource(R.string.shape_circle)
    CropShape.Heart -> stringResource(R.string.shape_heart)
    CropShape.Hexagon -> stringResource(R.string.shape_hexagon)
    CropShape.Star -> stringResource(R.string.shape_star)
}

@Composable
private fun CollageTextFont.label(): String = when (this) {
    CollageTextFont.SansSerif -> stringResource(R.string.font_sans_serif)
    CollageTextFont.Serif -> stringResource(R.string.font_serif)
    CollageTextFont.Monospace -> stringResource(R.string.font_monospace)
}

private fun com.rameshta.splitframe.domain.MergeProject.hasEdits(): Boolean =
    assignedImages.isNotEmpty() ||
        imageTransforms.isNotEmpty() ||
        spacingDp != template.defaultSpacingDp ||
        cornerRadiusDp != template.defaultCornerRadiusDp ||
        beforeAfterSlider != 0.5f ||
        backgroundColor != 0xFFFFFFFFuL ||
        backgroundStyle.kind != CollageBackgroundKind.AdaptiveLinear ||
        borderWidthDp > 0f ||
        borderStyle.kind != CollageBorderKind.LegacySolid ||
        cropShapes.isNotEmpty() ||
        textLayers.isNotEmpty()

private fun android.content.Context.persistUriAccessIfSupported(uri: Uri) {
    try {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
        // Android Photo Picker grants access without broad media storage permission.
    } catch (_: IllegalArgumentException) {
        // The provider did not offer a persistable grant.
    }
}
