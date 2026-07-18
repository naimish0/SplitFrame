package com.example.splitframe.presentation.merge

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.splitframe.R
import com.example.splitframe.domain.ExportResolution
import com.example.splitframe.domain.ExportResult
import com.example.splitframe.domain.LayoutMath
import com.example.splitframe.ui.components.PrimaryActionButton
import com.example.splitframe.ui.components.SecondaryActionButton
import com.example.splitframe.ui.components.StatusMessage
import com.example.splitframe.ui.components.StatusTone
import com.example.splitframe.ui.theme.splitFrameColors
import kotlin.math.max

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportSheet(
    state: MergeState,
    onIntent: (MergeIntent) -> Unit,
    onClose: () -> Unit,
    onRequestExport: (() -> Unit) -> Unit = { export -> export() },
    onExportForShare: () -> Unit = {},
) {
    val project = state.project ?: return
    val context = LocalContext.current
    val colors = splitFrameColors()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    var pendingShare by rememberSaveable { mutableStateOf(false) }
    var lastResultSnackbarKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSize = LayoutMath.outputSizeForResolution(project.template, project.exportResolution, state.sourceDimensions)
    val canExportImages = project.isReadyForImageExport && !state.isExporting
    val errorMessage = state.error?.let { stringResource(it) }
    val successMessage = stringResource(R.string.export_success)
    val resultMessage = when (val result = state.exportResult) {
        is ExportResult.Success -> successMessage
        is ExportResult.Failure -> stringResource(R.string.export_failure, result.reason)
        null -> null
    }
    val resultSnackbarKey = when (val result = state.exportResult) {
        is ExportResult.Success -> "success:${result.savedUri}"
        is ExportResult.Failure -> "failure:${result.reason}"
        null -> null
    }

    LaunchedEffect(state.exportResult, pendingShare) {
        if (pendingShare) {
            when (val result = state.exportResult) {
                is ExportResult.Success -> {
                    shareImage(context, result.savedUri)
                    pendingShare = false
                }
                is ExportResult.Failure -> pendingShare = false
                null -> Unit
            }
        }
    }

    LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        onIntent(MergeIntent.ClearError)
        snackbarHostState.showSnackbar(message)
    }

    LaunchedEffect(resultSnackbarKey) {
        val key = resultSnackbarKey ?: return@LaunchedEffect
        val message = resultMessage ?: return@LaunchedEffect
        if (lastResultSnackbarKey == key) return@LaunchedEffect
        lastResultSnackbarKey = key
        snackbarHostState.showSnackbar(message)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        val sheetMaxHeight = minOf(maxHeight, 640.dp)
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = sheetMaxHeight)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.export_title), style = MaterialTheme.typography.titleLarge)
                StatusMessage(
                    text = stringResource(
                        R.string.export_selected_resolution,
                        project.exportResolution.label,
                        selectedSize.widthPx,
                        selectedSize.heightPx,
                    ),
                    tone = StatusTone.Info,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.export_resolution), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportResolution.entries.forEach { resolution ->
                            val size = LayoutMath.outputSizeForResolution(project.template, resolution, state.sourceDimensions)
                            val estimate = formatBytes(LayoutMath.estimatedJpegSizeBytes(size))
                            FilterChip(
                                selected = project.exportResolution == resolution,
                                onClick = { onIntent(MergeIntent.SelectExportResolution(resolution)) },
                                label = {
                                    Text(
                                        stringResource(
                                            R.string.resolution_option_with_size,
                                            resolution.label,
                                            size.widthPx,
                                            size.heightPx,
                                            estimate,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }

                if (shouldWarnAboutSourceResolution(state)) {
                    StatusMessage(
                        text = stringResource(R.string.source_resolution_warning),
                        tone = StatusTone.Warning,
                    )
                }

                if (state.isExporting) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = { state.exportProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.exportProgress,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.exporting), modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }

                when (val result = state.exportResult) {
                    is ExportResult.Success -> StatusMessage(
                        text = stringResource(R.string.export_success),
                        tone = StatusTone.Success,
                        icon = Icons.Default.CheckCircle,
                    )
                    is ExportResult.Failure -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusMessage(
                            text = stringResource(R.string.export_failure, result.reason),
                            tone = StatusTone.Error,
                        )
                        SecondaryActionButton(
                            text = stringResource(R.string.retry),
                            onClick = { onRequestExport { onIntent(MergeIntent.Export) } },
                            enabled = canExportImages,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    null -> Unit
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryActionButton(
                        text = stringResource(R.string.save),
                        onClick = { onRequestExport { onIntent(MergeIntent.Export) } },
                        enabled = canExportImages,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Save,
                    )
                    SecondaryActionButton(
                        text = stringResource(R.string.share),
                        onClick = {
                            val result = state.exportResult as? ExportResult.Success
                            if (result != null) {
                                shareImage(context, result.savedUri)
                            } else {
                                onRequestExport {
                                    onExportForShare()
                                    pendingShare = true
                                    onIntent(MergeIntent.Export)
                                }
                            }
                        },
                        enabled = canExportImages,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Share,
                    )
                }
                SecondaryActionButton(
                    text = stringResource(R.string.close),
                    onClick = {
                        onIntent(MergeIntent.DismissExportResult)
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

private fun shouldWarnAboutSourceResolution(state: MergeState): Boolean {
    val project = state.project ?: return false
    val selectedLongEdge = project.exportResolution.longEdgePx
    if (selectedLongEdge <= 0 || state.sourceDimensions.isEmpty()) return false
    val smallestSourceLongEdge = state.sourceDimensions.values.minOfOrNull { it.longEdgePx } ?: return false
    return selectedLongEdge > smallestSourceLongEdge
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

private fun shareImage(context: Context, savedUri: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(savedUri))
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_subject))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
}
