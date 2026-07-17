package com.example.splitframe.presentation.merge

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.splitframe.R
import com.example.splitframe.domain.CollageLimits
import com.example.splitframe.domain.ImageSource
import com.example.splitframe.domain.ImageTransform
import com.example.splitframe.domain.TemplateKind
import com.example.splitframe.presentation.coilModel
import com.example.splitframe.presentation.titleText
import com.example.splitframe.ui.components.PrimaryActionButton
import com.example.splitframe.ui.components.SecondaryActionButton
import com.example.splitframe.ui.components.SplitFrameSection
import com.example.splitframe.ui.components.SplitFrameTopAppBar
import com.example.splitframe.ui.components.StatusMessage
import com.example.splitframe.ui.components.StatusTone
import com.example.splitframe.ui.theme.EditorBackgroundSwatches
import com.example.splitframe.ui.theme.splitFrameColors
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: MergeState,
    onIntent: (MergeIntent) -> Unit,
    onBack: () -> Unit,
    onShowInterstitialAd: (() -> Unit) -> Unit = { action -> action() },
    onShowInterstitialBeforeExport: (() -> Unit) -> Unit = { action -> action() },
) {
    val project = state.project ?: return
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedCell by rememberSaveable(project.template.id) { mutableIntStateOf(0) }
    var showExportSheet by rememberSaveable { mutableStateOf(false) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    var isPickerOpen by rememberSaveable { mutableStateOf(false) }
    val selectionLimitReached = project.assignedImages.size >= CollageLimits.MaxImages
    val canExportImages = project.isReadyForImageExport && !state.isExporting

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
        singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun launchMultiPicker() {
        if (isPickerOpen) return
        if (selectionLimitReached) return
        isPickerOpen = true
        multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
                onShowInterstitialAd = onShowInterstitialAd,
                onShowInterstitialBeforeExport = onShowInterstitialBeforeExport,
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
            MergePreviewCanvas(
                project = project,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(project.template.aspectRatio),
                sourceDimensions = state.sourceDimensions,
                selectedCellIndex = selectedCell,
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
    val dragThresholdPx = with(LocalDensity.current) { 42.dp.toPx() }
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
                val thumbnailDescription = stringResource(R.string.thumbnail_content_description, orderIndex + 1, cells.size)
                var dragX = 0f
                Surface(
                    modifier = Modifier
                        .size(72.dp)
                        .semantics {
                            selected = isSelected
                            contentDescription = thumbnailDescription
                        }
                        .pointerInput(cells.size, orderIndex) {
                            detectDragGestures(
                                onDragStart = { dragX = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragX += dragAmount.x
                                },
                                onDragEnd = {
                                    if (abs(dragX) > dragThresholdPx) {
                                        val targetIndex = if (dragX > 0f) orderIndex + 1 else orderIndex - 1
                                        onReorder(orderIndex, targetIndex.coerceIn(cells.indices))
                                    }
                                },
                            )
                        }
                        .clickable { onCellSelected(cell.index) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) splitFrameColors().selectedCell else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    tonalElevation = if (isSelected) 4.dp else 1.dp,
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.background), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val descriptions = listOf(
                        R.string.select_background_white,
                        R.string.select_background_black,
                        R.string.select_background_gray,
                        R.string.select_background_green,
                    )
                    EditorBackgroundSwatches.forEachIndexed { index, swatch ->
                        ColorSwatch(
                            argb = swatch.argb,
                            color = swatch.color,
                            description = stringResource(descriptions[index]),
                            onIntent = onIntent,
                        )
                    }
                }
            }
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
    onIntent: (MergeIntent) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .padding(6.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .semantics { contentDescription = description }
            .clickable { onIntent(MergeIntent.UpdateBackgroundColor(argb)) },
    )
}

private fun com.example.splitframe.domain.MergeProject.hasEdits(): Boolean =
    assignedImages.isNotEmpty() ||
        imageTransforms.isNotEmpty() ||
        spacingDp != template.defaultSpacingDp ||
        cornerRadiusDp != template.defaultCornerRadiusDp ||
        beforeAfterSlider != 0.5f ||
        backgroundColor != 0xFFFFFFFFuL

private fun android.content.Context.persistUriAccessIfSupported(uri: Uri) {
    try {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
        // Android Photo Picker grants access without broad media storage permission.
    } catch (_: IllegalArgumentException) {
        // The provider did not offer a persistable grant.
    }
}
