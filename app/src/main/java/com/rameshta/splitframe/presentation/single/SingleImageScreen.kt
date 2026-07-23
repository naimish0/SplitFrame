package com.rameshta.splitframe.presentation.single

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rameshta.splitframe.R
import com.rameshta.splitframe.ads.ExternalUiReason
import com.rameshta.splitframe.ads.LocalExternalUiLauncher
import com.rameshta.splitframe.domain.ExportCanvasRule
import com.rameshta.splitframe.domain.ExportContentMode
import com.rameshta.splitframe.domain.ExportPresetCatalog
import com.rameshta.splitframe.domain.ExportPresetDefinition
import com.rameshta.splitframe.domain.ImageSource
import com.rameshta.splitframe.domain.SingleImageOutputFormat
import com.rameshta.splitframe.domain.SingleImagePlanResult
import com.rameshta.splitframe.domain.SingleImageResizePreset
import com.rameshta.splitframe.domain.SingleImageResizeWarning
import com.rameshta.splitframe.presentation.coilModel
import com.rameshta.splitframe.ui.components.PrimaryActionButton
import com.rameshta.splitframe.ui.components.SecondaryActionButton
import com.rameshta.splitframe.ui.components.SplitFrameTopAppBar
import com.rameshta.splitframe.ui.components.StatusMessage
import com.rameshta.splitframe.ui.components.StatusTone
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SingleImageScreen(
    state: SingleImageState,
    onIntent: (SingleImageIntent) -> Unit,
    onBack: () -> Unit,
    onUseInCollage: (ImageSource.LocalUri) -> Unit,
) {
    val context = LocalContext.current
    val externalUiLauncher = LocalExternalUiLauncher.current
    var isPickerOpen by rememberSaveable { mutableStateOf(false) }
    val savedMessage = stringResource(R.string.single_image_saved_to_gallery)
    LaunchedEffect(state.result?.savedUri) {
        if (state.result != null) {
            Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
        }
    }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        isPickerOpen = false
        if (uri != null) {
            context.persistUriAccessIfSupported(uri)
            onIntent(SingleImageIntent.SelectImage(ImageSource.LocalUri(uri.toString())))
        }
    }
    fun launchPicker() {
        if (isPickerOpen || state.isProcessing) return
        isPickerOpen = true
        externalUiLauncher.launch(ExternalUiReason.MediaPicker) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    Scaffold(
        topBar = {
            SplitFrameTopAppBar(
                title = stringResource(R.string.single_image_title),
                subtitle = stringResource(R.string.single_image_subtitle),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryActionButton(
                    text = stringResource(R.string.select_photo),
                    onClick = ::launchPicker,
                    icon = Icons.Default.AddPhotoAlternate,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isProcessing,
                )
                state.result?.let { result ->
                    SecondaryActionButton(
                        text = stringResource(R.string.share),
                        onClick = {
                            externalUiLauncher.launch(ExternalUiReason.ShareSheet) {
                                context.shareImage(result.savedUri)
                            }
                        },
                        icon = Icons.Default.Share,
                        enabled = !state.isProcessing,
                    )
                }
            }

            state.error?.let {
                StatusMessage(text = it, tone = StatusTone.Error)
            }
            state.persistenceWarning?.let {
                StatusMessage(text = it, tone = StatusTone.Warning)
            }

            if (state.source == null) {
                StatusMessage(
                    text = stringResource(R.string.single_image_empty),
                    tone = StatusTone.Info,
                    icon = Icons.Default.AddPhotoAlternate,
                )
                return@Column
            }

            if (state.isPlanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.checking_output_size))
            }

            SingleImagePreview(state)
            SingleImageControls(state = state, onIntent = onIntent)
            SingleImagePlanSummary(state)

            if (state.isProcessing) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(
                                if (state.isCancelling) R.string.cancelling_image else R.string.processing_image,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryActionButton(
                            text = stringResource(R.string.cancel),
                            onClick = { onIntent(SingleImageIntent.Cancel) },
                            icon = Icons.Default.Close,
                            enabled = !state.isCancelling,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryActionButton(
                    text = stringResource(R.string.save),
                    onClick = { onIntent(SingleImageIntent.Process) },
                    enabled = !state.isProcessing && !state.isPlanning && state.planResult is SingleImagePlanResult.Valid,
                    icon = Icons.Default.Save,
                    modifier = Modifier.weight(1f),
                )
                state.result?.let { result ->
                    SecondaryActionButton(
                        text = stringResource(R.string.use_in_collage),
                        onClick = { onUseInCollage(result.source) },
                        enabled = !state.isProcessing,
                        icon = Icons.Default.Save,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

        }
    }
}

@Composable
private fun SingleImagePreview(state: SingleImageState) {
    val source = state.source ?: return
    val plan = (state.planResult as? SingleImagePlanResult.Valid)?.plan
        ?: state.previewPlan?.takeIf { state.isPlanning }
        ?: return
    val result = state.result
    val aspectRatio = plan.canvasAspectRatio
    val contentModeLabel = state.request.contentMode.label()
    val previewDescription = stringResource(
        R.string.single_image_preview_description,
        plan.outputDimensions.widthPx,
        plan.outputDimensions.heightPx,
        contentModeLabel,
    )
    var comparisonPosition by rememberSaveable(result?.savedUri) {
        mutableStateOf(0.5f)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            val availableAspect = maxWidth.value / maxHeight.value
            val canvasModifier = if (aspectRatio >= availableAspect) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
            } else {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(aspectRatio)
            }
            Box(
                modifier = canvasModifier
                    .testTag(SingleImagePreviewCanvasTag)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (state.request.outputFormat == SingleImageOutputFormat.Jpeg) {
                            Color.White
                        } else {
                            Color.Transparent
                        },
                    )
                    .clipToBounds(),
            ) {
                AsyncImage(
                    model = source.coilModel(),
                    contentDescription = previewDescription,
                    contentScale = when (state.request.contentMode) {
                        ExportContentMode.Fit -> ContentScale.Fit
                        ExportContentMode.Fill -> ContentScale.Crop
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (result != null) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds(),
                    ) {
                        val comparisonCanvasWidth = maxWidth
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(comparisonPosition)
                                .clipToBounds(),
                        ) {
                            AsyncImage(
                                model = result.source.coilModel(),
                                contentDescription = null,
                                contentScale = when (state.request.contentMode) {
                                    ExportContentMode.Fit -> ContentScale.Fit
                                    ExportContentMode.Fill -> ContentScale.Crop
                                },
                                modifier = Modifier
                                    .requiredWidth(comparisonCanvasWidth)
                                    .fillMaxHeight(),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = comparisonCanvasWidth * comparisonPosition)
                                .fillMaxHeight()
                                .requiredWidth(2.dp)
                                .background(MaterialTheme.colorScheme.onSurface),
                        )
                    }
                }
            }
        }
        if (result != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.comparison_resized), modifier = Modifier.weight(1f))
                Text(stringResource(R.string.comparison_original))
            }
            val comparisonLabel = stringResource(R.string.comparison_slider_description)
            Slider(
                value = comparisonPosition,
                onValueChange = { comparisonPosition = it },
                valueRange = 0f..1f,
                modifier = Modifier.semantics { contentDescription = comparisonLabel },
            )
        }
    }
}

@Composable
private fun SingleImageControls(
    state: SingleImageState,
    onIntent: (SingleImageIntent) -> Unit,
) {
    val request = state.request
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.resize_preset), style = MaterialTheme.typography.labelLarge)
        PresetOptions(
            title = stringResource(R.string.export_presets_social_common),
            definitions = ExportPresetCatalog.socialAndCommon,
            state = state,
            onIntent = onIntent,
        )
        PresetOptions(
            title = stringResource(R.string.export_presets_resize),
            definitions = ExportPresetCatalog.resize,
            state = state,
            onIntent = onIntent,
        )
        if (request.preset == SingleImageResizePreset.Custom) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DimensionField(
                    label = stringResource(R.string.width_px),
                    value = request.customWidthPx,
                    onValueChange = { onIntent(SingleImageIntent.UpdateCustomWidth(it)) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isProcessing,
                )
                DimensionField(
                    label = stringResource(R.string.height_px),
                    value = request.customHeightPx,
                    onValueChange = { onIntent(SingleImageIntent.UpdateCustomHeight(it)) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isProcessing,
                )
            }
            val lockAspectLabel = stringResource(R.string.lock_aspect_ratio)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lockAspectLabel, modifier = Modifier.weight(1f))
                Switch(
                    checked = request.lockAspectRatio,
                    onCheckedChange = { onIntent(SingleImageIntent.SetAspectRatioLocked(it)) },
                    enabled = !state.isProcessing,
                    modifier = Modifier.semantics { contentDescription = lockAspectLabel },
                )
            }
            if (!request.lockAspectRatio) {
                StatusMessage(
                    text = stringResource(R.string.aspect_ratio_warning),
                    tone = StatusTone.Info,
                )
            }
        }

        Text(stringResource(R.string.content_placement), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportContentMode.entries.forEach { contentMode ->
                FilterChip(
                    selected = request.contentMode == contentMode,
                    onClick = { onIntent(SingleImageIntent.SelectContentMode(contentMode)) },
                    label = { Text(contentMode.label()) },
                    enabled = !state.isProcessing,
                )
            }
        }
        val geometry = (state.planResult as? SingleImagePlanResult.Valid)?.plan?.canvasGeometry
        when {
            request.contentMode == ExportContentMode.Fill && geometry?.cropsContent == true -> StatusMessage(
                text = stringResource(R.string.fill_crop_warning),
                tone = StatusTone.Warning,
            )
            request.contentMode == ExportContentMode.Fit && geometry?.addsPadding == true -> StatusMessage(
                text = stringResource(R.string.fit_padding_notice),
                tone = StatusTone.Info,
            )
        }

        Text(stringResource(R.string.output_format), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SingleImageOutputFormat.entries.forEach { format ->
                FilterChip(
                    selected = request.outputFormat == format,
                    onClick = { onIntent(SingleImageIntent.SelectOutputFormat(format)) },
                    label = { Text(format.label()) },
                    enabled = !state.isProcessing,
                )
            }
        }
        if (request.outputFormat != SingleImageOutputFormat.Png) {
            val qualityLabel = stringResource(R.string.encoding_quality, request.encodingQuality)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(qualityLabel, style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = request.encodingQuality.toFloat(),
                    onValueChange = { onIntent(SingleImageIntent.UpdateEncodingQuality(it.toInt())) },
                    valueRange = 60f..100f,
                    enabled = !state.isProcessing,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = qualityLabel },
                )
            }
        }
    }
}

@Composable
private fun PresetOptions(
    title: String,
    definitions: List<ExportPresetDefinition>,
    state: SingleImageState,
    onIntent: (SingleImageIntent) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        definitions.forEach { definition ->
            FilterChip(
                selected = state.request.preset == definition.id,
                onClick = { onIntent(SingleImageIntent.SelectPreset(definition.id)) },
                label = { Text(definition.optionLabel(state.request)) },
                enabled = !state.isProcessing,
            )
        }
    }
}

@Composable
private fun SingleImagePlanSummary(state: SingleImageState) {
    when (val result = state.planResult) {
        is SingleImagePlanResult.Valid -> {
            val plan = result.plan
            val summaryWarnings = plan.warnings.filterNot { it == SingleImageResizeWarning.ContentCropped }
            Text(
                text = stringResource(
                    if (plan.originalDimensions == plan.outputDimensions) {
                        R.string.single_image_operation_compress
                    } else {
                        R.string.single_image_operation_resize
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            val completed = state.result
            StatusMessage(
                text = if (completed == null) {
                    stringResource(
                        R.string.single_image_dimensions,
                        plan.originalDimensions.widthPx,
                        plan.originalDimensions.heightPx,
                        plan.outputDimensions.widthPx,
                        plan.outputDimensions.heightPx,
                        formatBytes(plan.estimatedBytes),
                    )
                } else {
                    actualResultSummary(completed)
                },
                tone = if (plan.warnings.isEmpty()) StatusTone.Info else StatusTone.Warning,
            )
            summaryWarnings.forEach { warning ->
                val warningText = when (warning) {
                    SingleImageResizeWarning.ContentCropped -> return@forEach
                    SingleImageResizeWarning.LargeOutput -> stringResource(R.string.large_output_warning)
                    SingleImageResizeWarning.WouldDownscale -> stringResource(R.string.downscale_warning)
                }
                StatusMessage(text = warningText, tone = StatusTone.Warning)
            }
        }
        is SingleImagePlanResult.Invalid -> StatusMessage(
            text = stringResource(R.string.invalid_output_size),
            tone = StatusTone.Error,
        )
        null -> Unit
    }
}

@Composable
private fun actualResultSummary(result: com.rameshta.splitframe.export.SingleImageProcessResult.Success): String {
    val metadata = result.metadata
    val originalSize = metadata.originalBytes?.let { formatBytes(it) }
        ?: stringResource(R.string.file_size_unavailable)
    val outputSize = metadata.outputBytes?.let { formatBytes(it) }
        ?: stringResource(R.string.file_size_unavailable)
    val format = metadata.outputFormat.label()
    val encoding = metadata.encodingQuality?.let { quality ->
        stringResource(R.string.single_image_actual_format_quality, format, quality)
    } ?: format
    val comparison = metadata.comparison
    val change = when {
        comparison.bytesSaved == null || comparison.percentageReduction == null ->
            stringResource(R.string.single_image_reduction_unavailable)
        comparison.reduced -> stringResource(
            R.string.single_image_reduction,
            formatBytes(comparison.bytesSaved),
            comparison.percentageReduction,
        )
        else -> stringResource(
            R.string.single_image_increase,
            formatBytes(-comparison.bytesSaved),
            -comparison.percentageReduction,
        )
    }
    return stringResource(
        R.string.single_image_actual_summary,
        metadata.originalDimensions.widthPx,
        metadata.originalDimensions.heightPx,
        metadata.outputDimensions.widthPx,
        metadata.outputDimensions.heightPx,
        originalSize,
        outputSize,
        change,
        encoding,
    )
}

@Composable
private fun DimensionField(
    label: String,
    value: Int?,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { text -> onValueChange(text.filter(Char::isDigit).toIntOrNull()) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
private fun SingleImageResizePreset.label(): String =
    when (this) {
        SingleImageResizePreset.InstagramSquarePost -> stringResource(R.string.preset_instagram_square)
        SingleImageResizePreset.InstagramPortraitPost -> stringResource(R.string.preset_instagram_portrait)
        SingleImageResizePreset.InstagramStoryReel -> stringResource(R.string.preset_instagram_story_reel)
        SingleImageResizePreset.WhatsAppStatus -> stringResource(R.string.preset_whatsapp_status)
        SingleImageResizePreset.YouTubeThumbnail -> stringResource(R.string.preset_youtube_thumbnail)
        SingleImageResizePreset.PinterestPin -> stringResource(R.string.preset_pinterest_pin)
        SingleImageResizePreset.DeviceWallpaper -> stringResource(R.string.preset_device_wallpaper)
        SingleImageResizePreset.Custom -> stringResource(R.string.preset_custom)
        SingleImageResizePreset.LongEdge1080 -> stringResource(R.string.preset_1080p)
        SingleImageResizePreset.LongEdge2K -> stringResource(R.string.preset_2k)
        SingleImageResizePreset.LongEdge4K -> stringResource(R.string.preset_4k)
        SingleImageResizePreset.Scale2x -> stringResource(R.string.preset_2x)
        SingleImageResizePreset.Scale4x -> stringResource(R.string.preset_4x)
    }

@Composable
private fun ExportPresetDefinition.optionLabel(request: com.rameshta.splitframe.domain.SingleImageResizeRequest): String {
    val dimensions = when (val rule = canvasRule) {
        is ExportCanvasRule.Fixed -> rule.dimensions
        ExportCanvasRule.DeviceWallpaper -> request.deviceWallpaperDimensions
        ExportCanvasRule.Custom -> if (request.customWidthPx != null && request.customHeightPx != null) {
            com.rameshta.splitframe.domain.ImageDimensions(request.customWidthPx, request.customHeightPx)
        } else {
            null
        }
        is ExportCanvasRule.OriginalLongEdge,
        is ExportCanvasRule.OriginalScale -> null
    }
    return dimensions?.let {
        stringResource(R.string.preset_with_dimensions, id.label(), it.widthPx, it.heightPx)
    } ?: id.label()
}

@Composable
private fun ExportContentMode.label(): String =
    when (this) {
        ExportContentMode.Fit -> stringResource(R.string.content_mode_fit)
        ExportContentMode.Fill -> stringResource(R.string.content_mode_fill)
    }

@Composable
private fun SingleImageOutputFormat.label(): String =
    when (this) {
        SingleImageOutputFormat.Jpeg -> stringResource(R.string.format_jpeg)
        SingleImageOutputFormat.Png -> stringResource(R.string.format_png)
        SingleImageOutputFormat.Webp -> stringResource(R.string.format_webp)
    }

@Composable
private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024f * 1024f)
    return if (mb < 1f) {
        stringResource(R.string.file_size_kb, max(1, (bytes / 1024L).toInt()))
    } else {
        stringResource(R.string.file_size_mb, mb)
    }
}

private fun Context.shareImage(savedUri: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(savedUri))
        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.share)))
}

internal const val SingleImagePreviewCanvasTag = "single-image-preview-canvas"

private fun Context.persistUriAccessIfSupported(uri: Uri) {
    try {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
    } catch (_: IllegalArgumentException) {
    }
}
