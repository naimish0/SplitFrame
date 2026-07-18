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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rameshta.splitframe.R
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
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
                        onClick = { context.shareImage(result.savedUri) },
                        icon = Icons.Default.Share,
                        enabled = !state.isProcessing,
                    )
                }
            }

            state.error?.let {
                StatusMessage(text = it, tone = StatusTone.Error)
            }

            if (state.source == null) {
                StatusMessage(
                    text = stringResource(R.string.single_image_empty),
                    tone = StatusTone.Info,
                    icon = Icons.Default.AddPhotoAlternate,
                )
                return@Column
            }

            SingleImagePreview(state)
            SingleImageControls(state = state, onIntent = onIntent)
            SingleImagePlanSummary(state)

            if (state.isProcessing) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.processing_image), modifier = Modifier.weight(1f))
                        SecondaryActionButton(
                            text = stringResource(R.string.cancel),
                            onClick = { onIntent(SingleImageIntent.Cancel) },
                            icon = Icons.Default.Close,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryActionButton(
                    text = stringResource(R.string.save),
                    onClick = { onIntent(SingleImageIntent.Process) },
                    enabled = !state.isProcessing && state.planResult is SingleImagePlanResult.Valid,
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
    val result = state.result
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var panX by rememberSaveable { mutableFloatStateOf(0f) }
    var panY by rememberSaveable { mutableFloatStateOf(0f) }
    val panRangePx = with(LocalDensity.current) { 72.dp.toPx() }
    val aspectRatio = (state.planResult as? SingleImagePlanResult.Valid)
        ?.plan
        ?.originalDimensions
        ?.let { it.widthPx / it.heightPx.toFloat() }
        ?.coerceIn(0.6f, 1.8f)
        ?: 1f

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clipToBounds(),
        ) {
            val transform = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoom,
                    scaleY = zoom,
                    translationX = panX * panRangePx,
                    translationY = panY * panRangePx,
                )
            AsyncImage(
                model = result?.source?.coilModel() ?: source.coilModel(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = transform,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.zoom), style = MaterialTheme.typography.labelLarge)
            Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..4f, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pan), style = MaterialTheme.typography.labelLarge)
            Slider(value = panX, onValueChange = { panX = it }, valueRange = -1f..1f, modifier = Modifier.weight(1f))
            Slider(value = panY, onValueChange = { panY = it }, valueRange = -1f..1f, modifier = Modifier.weight(1f))
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SingleImageResizePreset.entries.forEach { preset ->
                FilterChip(
                    selected = request.preset == preset,
                    onClick = { onIntent(SingleImageIntent.SelectPreset(preset)) },
                    label = { Text(preset.label()) },
                    enabled = !state.isProcessing,
                )
            }
        }
        if (request.preset == SingleImageResizePreset.Custom) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DimensionField(
                    label = stringResource(R.string.width_px),
                    value = request.customWidthPx,
                    onValueChange = { onIntent(SingleImageIntent.UpdateCustomWidth(it)) },
                    modifier = Modifier.weight(1f),
                )
                DimensionField(
                    label = stringResource(R.string.height_px),
                    value = request.customHeightPx,
                    onValueChange = { onIntent(SingleImageIntent.UpdateCustomHeight(it)) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lock_aspect_ratio), modifier = Modifier.weight(1f))
                Switch(
                    checked = request.lockAspectRatio,
                    onCheckedChange = { onIntent(SingleImageIntent.SetAspectRatioLocked(it)) },
                    enabled = !state.isProcessing,
                )
            }
            if (!request.lockAspectRatio) {
                StatusMessage(
                    text = stringResource(R.string.aspect_ratio_warning),
                    tone = StatusTone.Warning,
                )
            }
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
        if (request.outputFormat == SingleImageOutputFormat.Jpeg) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.jpeg_quality, request.jpegQuality), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = request.jpegQuality.toFloat(),
                    onValueChange = { onIntent(SingleImageIntent.UpdateJpegQuality(it.toInt())) },
                    valueRange = 60f..100f,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isProcessing,
                )
            }
        }
    }
}

@Composable
private fun SingleImagePlanSummary(state: SingleImageState) {
    when (val result = state.planResult) {
        is SingleImagePlanResult.Valid -> {
            val plan = result.plan
            val warningText = when (plan.warning) {
                SingleImageResizeWarning.AspectRatioUnlocked -> stringResource(R.string.aspect_ratio_warning)
                SingleImageResizeWarning.LargeOutput -> stringResource(R.string.large_output_warning)
                SingleImageResizeWarning.WouldDownscale -> stringResource(R.string.downscale_warning)
                null -> null
            }
            StatusMessage(
                text = stringResource(
                    R.string.single_image_dimensions,
                    plan.originalDimensions.widthPx,
                    plan.originalDimensions.heightPx,
                    plan.outputDimensions.widthPx,
                    plan.outputDimensions.heightPx,
                    formatBytes(plan.estimatedBytes),
                ),
                tone = if (warningText == null) StatusTone.Info else StatusTone.Warning,
            )
            if (warningText != null) {
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
private fun DimensionField(
    label: String,
    value: Int?,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { text -> onValueChange(text.filter(Char::isDigit).toIntOrNull()) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun SingleImageResizePreset.label(): String =
    when (this) {
        SingleImageResizePreset.LongEdge1080 -> stringResource(R.string.preset_1080p)
        SingleImageResizePreset.LongEdge2K -> stringResource(R.string.preset_2k)
        SingleImageResizePreset.LongEdge4K -> stringResource(R.string.preset_4k)
        SingleImageResizePreset.Scale2x -> stringResource(R.string.preset_2x)
        SingleImageResizePreset.Scale4x -> stringResource(R.string.preset_4x)
        SingleImageResizePreset.Custom -> stringResource(R.string.preset_custom)
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

private fun Context.persistUriAccessIfSupported(uri: Uri) {
    try {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
    } catch (_: IllegalArgumentException) {
    }
}
