package com.rameshta.splitframe.presentation.video

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil.compose.AsyncImage
import com.rameshta.splitframe.R
import com.rameshta.splitframe.ads.ExternalUiReason
import com.rameshta.splitframe.ads.LocalExternalUiLauncher
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.ImageDimensions
import com.rameshta.splitframe.domain.ImageTransform
import com.rameshta.splitframe.domain.LayoutMath
import com.rameshta.splitframe.domain.MergedVideoTimelinePosition
import com.rameshta.splitframe.domain.MediaSource
import com.rameshta.splitframe.domain.MixedMediaLimits
import com.rameshta.splitframe.domain.VideoCanvasAspectRatio
import com.rameshta.splitframe.domain.VideoClip
import com.rameshta.splitframe.domain.VideoLayoutMath
import com.rameshta.splitframe.domain.VideoMergeProject
import com.rameshta.splitframe.domain.VideoTransition
import com.rameshta.splitframe.presentation.localizedRuntimeMessage
import com.rameshta.splitframe.presentation.labelText
import com.rameshta.splitframe.ui.components.PrimaryActionButton
import com.rameshta.splitframe.ui.components.SecondaryActionButton
import com.rameshta.splitframe.ui.components.SplitFrameSection
import com.rameshta.splitframe.ui.components.SplitFrameTopAppBar
import com.rameshta.splitframe.ui.components.StatusMessage
import com.rameshta.splitframe.ui.components.StatusTone
import com.rameshta.splitframe.ui.theme.splitFrameColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    state: VideoMergeState,
    onIntent: (VideoMergeIntent) -> Unit,
    onBack: () -> Unit,
) {
    val project = state.project ?: return
    val context = LocalContext.current
    val externalUiLauncher = LocalExternalUiLauncher.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingSinglePickCellIndex by remember { mutableStateOf<Int?>(null) }
    val multiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MixedMediaLimits.MaxItems),
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach(context::persistMediaUriAccessIfSupported)
            onIntent(VideoMergeIntent.SelectMedia(uris.map { it.toString() }))
        }
    }
    val singlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val targetCell = pendingSinglePickCellIndex ?: state.selectedClipIndex
        pendingSinglePickCellIndex = null
        if (uri != null) {
            context.persistMediaUriAccessIfSupported(uri)
            onIntent(VideoMergeIntent.ReplaceMedia(targetCell, uri.toString()))
        }
    }
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            context.persistMediaUriAccessIfSupported(uri)
            onIntent(VideoMergeIntent.SelectUserAudio(uri.toString()))
        }
    }
    val pickUserAudio = {
        if (!state.isExporting) {
            externalUiLauncher.launch(ExternalUiReason.MediaPicker) {
                audioPicker.launch(arrayOf("audio/*"))
            }
        }
    }
    val pickMixed = {
        if (!state.isExporting) {
            externalUiLauncher.launch(ExternalUiReason.MediaPicker) {
                multiPicker.launch(
                    PickVisualMediaRequest(
                        mediaType = ActivityResultContracts.PickVisualMedia.VideoOnly,
                        isOrderedSelection = true,
                    ),
                )
            }
        }
    }
    fun pickMediaForCell(cellIndex: Int) {
        if (state.isExporting) return
        val isTemplateCell = project.template.cells.any { it.index == cellIndex }
        if (!isTemplateCell) return
        pendingSinglePickCellIndex = cellIndex
        onIntent(VideoMergeIntent.SelectClip(cellIndex))
        externalUiLauncher.launch(ExternalUiReason.MediaPicker) {
            singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
    }
    val replaceSelected = {
        pickMediaForCell(state.selectedClipIndex)
    }

    val errorMessage = state.error?.let { stringResource(it) }
    LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onIntent(VideoMergeIntent.DismissError)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SplitFrameTopAppBar(
                title = stringResource(R.string.video_editor_title),
                subtitle = stringResource(R.string.mode_video_split),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onIntent(VideoMergeIntent.UndoEdit) },
                        enabled = state.canUndo && !state.isExporting,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.undo))
                    }
                    IconButton(
                        onClick = { onIntent(VideoMergeIntent.RedoEdit) },
                        enabled = state.canRedo && !state.isExporting,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.redo))
                    }
                    IconButton(
                        onClick = { onIntent(VideoMergeIntent.ResetProject) },
                        enabled = !state.isExporting,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reset))
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            val wide = maxWidth >= 720.dp && maxWidth > maxHeight
            if (wide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FixedPreviewPane(
                        state = state,
                        onIntent = onIntent,
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight(),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 520.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        EditorControls(
                            state = state,
                            onIntent = onIntent,
                            onPickMedia = pickMixed,
                            onPickCellMedia = ::pickMediaForCell,
                            onReplaceSelected = replaceSelected,
                            onPickUserAudio = pickUserAudio,
                            modifier = Modifier.weight(1f),
                        )
                        PersistentExportAction(state = state, onIntent = onIntent)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FixedPreviewPane(
                        state = state,
                        onIntent = onIntent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 380.dp),
                    )
                    EditorControls(
                        state = state,
                        onIntent = onIntent,
                        onPickMedia = pickMixed,
                        onPickCellMedia = ::pickMediaForCell,
                        onReplaceSelected = replaceSelected,
                        onPickUserAudio = pickUserAudio,
                        modifier = Modifier.weight(1f),
                    )
                    PersistentExportAction(state = state, onIntent = onIntent)
                }
            }
        }
    }
}

@Composable
private fun FixedPreviewPane(
    state: VideoMergeState,
    onIntent: (VideoMergeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VideoPreviewCanvas(
            state = state,
            onIntent = onIntent,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(state.project?.canvasAspectRatio?.ratio ?: 16f / 9f),
        )
        PlaybackControls(state = state, onIntent = onIntent)
    }
}

@Composable
private fun PlaybackControls(
    state: VideoMergeState,
    onIntent: (VideoMergeIntent) -> Unit,
) {
    val project = state.project ?: return
    val durationMs = VideoLayoutMath.outputDurationForMergedVideos(project.orderedClips)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    if (state.status == VideoEditorStatus.Playing) {
                        onIntent(VideoMergeIntent.Pause)
                    } else {
                        onIntent(VideoMergeIntent.Play)
                    }
                },
                enabled = durationMs > 0L && !state.isExporting,
            ) {
                Icon(
                    imageVector = if (state.status == VideoEditorStatus.Playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.status == VideoEditorStatus.Playing) {
                        stringResource(R.string.video_pause)
                    } else {
                        stringResource(R.string.video_play)
                    },
                )
            }
            Text(
                text = stringResource(
                    R.string.video_playback_position,
                    state.playbackPositionMs.formatDuration(),
                    durationMs.formatDuration(),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = state.playbackPositionMs.coerceIn(0L, durationMs).toFloat(),
            onValueChange = { value ->
                onIntent(VideoMergeIntent.Pause)
                onIntent(VideoMergeIntent.SeekTo(value.toLong()))
            },
            enabled = durationMs > 0L && !state.isExporting,
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPreviewCanvas(
    state: VideoMergeState,
    onIntent: (VideoMergeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val project = state.project ?: return
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clips = project.orderedClips
    val playlistKey = remember(clips) {
        clips.map { clip -> PreviewClipKey(clip.id, clip.uri, clip.trimStartMs, clip.trimEndMs) }
    }
    val player = remember(context.applicationContext) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 1f
        }
    }
    val currentOnIntent by rememberUpdatedState(onIntent)
    val currentStatus by rememberUpdatedState(state.status)
    val currentPlaybackPositionMs by rememberUpdatedState(state.playbackPositionMs)
    val timelinePosition = VideoLayoutMath.mergedVideoPositionAt(clips, state.playbackPositionMs)
    val activeCellIndex = timelinePosition?.clip?.id?.let { activeClipId ->
        project.mediaByCell.entries.firstOrNull { (_, media) ->
            media is MediaSource.Video && media.clip.id == activeClipId
        }?.key
    }
    val activeMedia = activeCellIndex?.let { project.mediaByCell[it] as? MediaSource.Video }
    val currentActiveMedia by rememberUpdatedState(activeMedia)
    val previewDescription = timelinePosition?.let { position ->
        stringResource(R.string.video_sequence_preview, position.clipIndex + 1, clips.size)
    } ?: stringResource(R.string.video_empty_sequence_preview)

    DisposableEffect(player) {
        onDispose { player.release() }
    }
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
                if (currentStatus == VideoEditorStatus.Playing) {
                    currentOnIntent(VideoMergeIntent.Pause)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player, playlistKey) {
        player.pause()
        player.setMediaItems(clips.map(VideoClip::toPreviewMediaItem))
        if (clips.isNotEmpty()) {
            player.prepare()
            VideoLayoutMath.mergedVideoPositionAt(clips, currentPlaybackPositionMs)?.let { position ->
                player.seekTo(position.clipIndex, position.playerPositionMs())
            }
            if (currentStatus == VideoEditorStatus.Playing) player.play()
        }
    }

    LaunchedEffect(player, state.playbackPositionMs, state.status, playlistKey) {
        if (state.status != VideoEditorStatus.Playing) {
            player.pause()
            VideoLayoutMath.mergedVideoPositionAt(clips, state.playbackPositionMs)?.let { position ->
                val targetPositionMs = position.playerPositionMs()
                if (
                    player.currentMediaItemIndex != position.clipIndex ||
                    kotlin.math.abs(player.currentPosition - targetPositionMs) > PreviewSeekToleranceMs
                ) {
                    player.seekTo(position.clipIndex, targetPositionMs)
                }
            }
        }
    }

    LaunchedEffect(player, state.status, playlistKey) {
        if (state.status != VideoEditorStatus.Playing || clips.isEmpty()) return@LaunchedEffect
        val durationMs = VideoLayoutMath.outputDurationForMergedVideos(clips)
        if (durationMs <= 0L) {
            currentOnIntent(VideoMergeIntent.Pause)
            return@LaunchedEffect
        }
        VideoLayoutMath.mergedVideoPositionAt(clips, currentPlaybackPositionMs)?.let { position ->
            if (
                player.currentMediaItemIndex != position.clipIndex ||
                kotlin.math.abs(player.currentPosition - position.playerPositionMs()) > PreviewSeekToleranceMs
            ) {
                player.seekTo(position.clipIndex, position.playerPositionMs())
            }
        }
        player.play()
        while (currentStatus == VideoEditorStatus.Playing) {
            if (player.playbackState == Player.STATE_ENDED) {
                player.pause()
                VideoLayoutMath.mergedVideoPositionAt(clips, durationMs)?.let { terminal ->
                    player.seekTo(terminal.clipIndex, terminal.playerPositionMs())
                }
                currentOnIntent(VideoMergeIntent.SeekTo(durationMs))
                currentOnIntent(VideoMergeIntent.Pause)
                break
            }
            val itemIndex = player.currentMediaItemIndex.coerceIn(0, clips.lastIndex)
            val itemStartMs = VideoLayoutMath.mergedVideoClipStartMs(clips, itemIndex) ?: 0L
            val projectPositionMs = (itemStartMs + player.currentPosition).coerceIn(0L, durationMs)
            currentOnIntent(VideoMergeIntent.SeekTo(projectPositionMs))
            delay(PreviewProgressPollMs)
        }
        player.pause()
    }

    Surface(
        color = Color.Black,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = previewDescription }
                .pointerInput(activeMedia?.id, activeCellIndex) {
                    val media = currentActiveMedia ?: return@pointerInput
                    val cellIndex = activeCellIndex ?: return@pointerInput
                    var latest = media.transform
                    detectDragGestures(
                        onDragEnd = {
                            currentOnIntent(VideoMergeIntent.UpdateVideoTransform(cellIndex, latest, trackUndo = true))
                        },
                        onDragCancel = {
                            currentOnIntent(VideoMergeIntent.UpdateVideoTransform(cellIndex, latest, trackUndo = true))
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            latest = LayoutMath.transformAfterGesture(
                                sourceDimensions = ImageDimensions(media.width, media.height),
                                destinationWidthPx = size.width.toFloat(),
                                destinationHeightPx = size.height.toFloat(),
                                current = latest,
                                panXpx = dragAmount.x,
                                panYpx = dragAmount.y,
                                zoomChange = 1f,
                            )
                            currentOnIntent(VideoMergeIntent.UpdateVideoTransform(cellIndex, latest, trackUndo = false))
                        },
                    )
                }
                .pointerInput(activeMedia?.id, activeCellIndex) {
                    if (currentActiveMedia == null) return@pointerInput
                    val cellIndex = activeCellIndex ?: return@pointerInput
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val latestMedia = currentActiveMedia ?: return@detectTapGestures
                            val transform = LayoutMath.transformAfterDoubleTap(
                                sourceDimensions = ImageDimensions(latestMedia.width, latestMedia.height),
                                destinationWidthPx = size.width.toFloat(),
                                destinationHeightPx = size.height.toFloat(),
                                current = latestMedia.transform,
                                tapXInFramePx = offset.x,
                                tapYInFramePx = offset.y,
                            )
                            currentOnIntent(VideoMergeIntent.UpdateVideoTransform(cellIndex, transform, trackUndo = true))
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (activeMedia == null) {
                EmptyCell()
            } else {
                ContentFrame(
                    player = player,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val transform = activeMedia.transform.normalized()
                            val crop = LayoutMath.cropToFillSourceRect(
                                sourceWidthPx = activeMedia.width.toFloat(),
                                sourceHeightPx = activeMedia.height.toFloat(),
                                destinationWidthPx = size.width,
                                destinationHeightPx = size.height,
                                transform = transform,
                            )
                            scaleX = transform.zoom
                            scaleY = transform.zoom
                            translationX = -(crop.centerX - activeMedia.width / 2f) *
                                (size.width / crop.width.coerceAtLeast(1f))
                            translationY = -(crop.centerY - activeMedia.height / 2f) *
                                (size.height / crop.height.coerceAtLeast(1f))
                        },
                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                    contentScale = ContentScale.Crop,
                    keepContentOnReset = true,
                )
                MediaBadge(
                    text = stringResource(
                        R.string.video_sequence_preview,
                        (timelinePosition?.clipIndex ?: 0) + 1,
                        clips.size,
                    ),
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }
    }
}

@Composable
private fun EmptyCell() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color.White)
        Text(
            text = stringResource(R.string.video_clip_empty),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun MediaBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(6.dp),
        color = Color.Black.copy(alpha = 0.58f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun EditorControls(
    state: VideoMergeState,
    onIntent: (VideoMergeIntent) -> Unit,
    onPickMedia: () -> Unit,
    onPickCellMedia: (Int) -> Unit,
    onReplaceSelected: () -> Unit,
    onPickUserAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MediaSelectionSection(state, onPickMedia)
        MediaThumbnailStrip(state, onIntent, onPickCellMedia)
        VideoTools(state, onIntent, onReplaceSelected, onPickMedia, onPickUserAudio)
        VideoExportPanel(state)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun MediaSelectionSection(
    state: VideoMergeState,
    onPickMedia: () -> Unit,
) {
    val project = state.project ?: return
    val canPickMedia = !state.isExporting
    SplitFrameSection(
        title = stringResource(R.string.video_template_title),
        supportingText = stringResource(R.string.mixed_media_count, project.mediaCount),
        modifier = Modifier.clickable(enabled = canPickMedia, onClick = onPickMedia),
    ) {
        PrimaryActionButton(
            text = stringResource(R.string.video_pick_two),
            icon = Icons.Default.VideoLibrary,
            onClick = onPickMedia,
            enabled = canPickMedia,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.status == VideoEditorStatus.ReadingMetadata) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.video_reading_metadata),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MediaThumbnailStrip(
    state: VideoMergeState,
    onIntent: (VideoMergeIntent) -> Unit,
    onPickCellMedia: (Int) -> Unit,
) {
    val project = state.project ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        project.template.cells.forEach { cell ->
            val media = project.mediaByCell[cell.index]
            val canPickEmptyCell = media == null &&
                !state.isExporting
            Card(
                modifier = Modifier
                    .width(82.dp)
                    .height(64.dp)
                    .semantics { selected = state.selectedClipIndex == cell.index }
                    .clickable {
                        if (canPickEmptyCell) {
                            onPickCellMedia(cell.index)
                        } else {
                            onIntent(VideoMergeIntent.SelectClip(cell.index))
                        }
                    },
                border = BorderStroke(
                    width = if (state.selectedClipIndex == cell.index) 2.dp else 1.dp,
                    color = if (state.selectedClipIndex == cell.index) {
                        splitFrameColors().selectedCell
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (media) {
                        is MediaSource.Image -> AsyncImage(
                            model = Uri.parse(media.enhancedPath ?: media.uri),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        is MediaSource.Video -> Text(
                            text = media.clip.trimmedDurationMs.formatDuration(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        null -> Text(
                            text = (cell.index + 1).toString(),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoTools(
    state: VideoMergeState,
    onIntent: (VideoMergeIntent) -> Unit,
    onReplaceSelected: () -> Unit,
    onPickMedia: () -> Unit,
    onPickUserAudio: () -> Unit,
) {
    val project = state.project ?: return
    SplitFrameSection(title = stringResource(R.string.video_crop_position)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryActionButton(
                text = stringResource(R.string.video_replace),
                onClick = onReplaceSelected,
                enabled = project.mediaByCell[state.selectedClipIndex] != null && !state.isExporting,
            )
            SecondaryActionButton(
                text = stringResource(R.string.video_remove),
                onClick = { onIntent(VideoMergeIntent.RemoveVideo(state.selectedClipIndex)) },
                enabled = project.mediaByCell[state.selectedClipIndex] != null && !state.isExporting,
                icon = Icons.Default.Close,
            )
            AssistChip(
                onClick = {
                    val next = project.template.cells
                        .map { it.index }
                        .firstOrNull { it > state.selectedClipIndex }
                        ?: project.template.cells.firstOrNull()?.index
                        ?: state.selectedClipIndex
                    onIntent(VideoMergeIntent.SwapCells(state.selectedClipIndex, next))
                },
                label = { Text(stringResource(R.string.video_swap)) },
                leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                enabled = project.mediaCount >= 2,
            )
            SecondaryActionButton(
                text = stringResource(R.string.video_pick_two),
                onClick = onPickMedia,
                enabled = !state.isExporting,
            )
        }
        DurationAndResolutionControls(project = project, onIntent = onIntent)
        Text(text = stringResource(R.string.video_transition), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoTransition.entries.forEach { transition ->
                FilterChip(
                    selected = project.transition == transition,
                    onClick = { onIntent(VideoMergeIntent.SelectTransition(transition)) },
                    label = {
                        Text(
                            stringResource(
                                if (transition == VideoTransition.Cut) {
                                    R.string.video_transition_cut
                                } else {
                                    R.string.video_transition_fade
                                },
                            ),
                        )
                    },
                )
            }
        }
        SelectedMediaControls(state = state, onIntent = onIntent)
        AudioControls(project = project, onIntent = onIntent, onPickUserAudio = onPickUserAudio)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioControls(
    project: VideoMergeProject,
    onIntent: (VideoMergeIntent) -> Unit,
    onPickUserAudio: () -> Unit,
) {
    val videos = project.template.cells.mapNotNull { cell ->
        (project.mediaByCell[cell.index] as? MediaSource.Video)?.let { cell.index to it }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.video_audio_source), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioChip(
                text = stringResource(R.string.video_audio_none),
                selected = project.primaryAudioMediaId == null && project.userAudioUri == null,
                enabled = true,
                onClick = { onIntent(VideoMergeIntent.SelectPrimaryAudio(null)) },
            )
            videos.forEach { (cellIndex, media) ->
                AudioChip(
                    text = stringResource(R.string.video_audio_from_item, cellIndex + 1),
                    selected = project.primaryAudioMediaId == media.id,
                    enabled = media.clip.hasAudio,
                    onClick = { onIntent(VideoMergeIntent.SelectPrimaryAudio(media.id)) },
                )
            }
            AudioChip(
                text = stringResource(R.string.video_audio_user_owned),
                selected = project.userAudioUri != null,
                enabled = true,
                onClick = onPickUserAudio,
            )
        }
        if (project.userAudioUri != null) {
            Text(
                text = stringResource(R.string.video_audio_user_owned_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AudioChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(text) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationAndResolutionControls(
    project: VideoMergeProject,
    onIntent: (VideoMergeIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.export_resolution), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportResolution.entries.forEach { resolution ->
                FilterChip(
                    selected = project.exportResolution == resolution,
                    onClick = { onIntent(VideoMergeIntent.SelectExportResolution(resolution)) },
                    label = { Text(resolution.labelText()) },
                )
            }
        }
    }
}

@Composable
private fun SelectedMediaControls(
    state: VideoMergeState,
    onIntent: (VideoMergeIntent) -> Unit,
) {
    val media = state.selectedMedia ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.video_clip_selected, state.selectedClipIndex + 1),
            style = MaterialTheme.typography.titleSmall,
        )
        when (media) {
            is MediaSource.Image -> Text(
                text = stringResource(R.string.video_resolution_meta, media.width, media.height),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is MediaSource.Video -> {
                val clip = media.clip
                Text(
                    text = listOf(
                        stringResource(R.string.video_duration, clip.durationMs.formatDuration()),
                        stringResource(R.string.video_resolution_meta, clip.orientedWidth, clip.orientedHeight),
                        if (clip.hasAudio) stringResource(R.string.video_audio_available) else stringResource(R.string.video_audio_missing),
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TrimControls(clip = clip, cellIndex = state.selectedClipIndex, onIntent = onIntent)
            }
        }
        TransformControls(media = media, cellIndex = state.selectedClipIndex, onIntent = onIntent)
    }
}

@Composable
private fun TrimControls(
    clip: VideoClip,
    cellIndex: Int,
    onIntent: (VideoMergeIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.video_trim), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(R.string.video_trim_selected, clip.trimmedDurationMs.formatDuration()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = clip.trimStartMs.toFloat(),
            onValueChange = { value ->
                onIntent(VideoMergeIntent.UpdateTrim(cellIndex, value.toLong(), clip.trimEndMs))
            },
            valueRange = 0f..(clip.trimEndMs - VideoLayoutMath.MinTrimDurationMs).coerceAtLeast(0L).toFloat(),
        )
        Text(stringResource(R.string.video_trim_start, clip.trimStartMs.formatDuration()))
        Slider(
            value = clip.trimEndMs.toFloat(),
            onValueChange = { value ->
                onIntent(VideoMergeIntent.UpdateTrim(cellIndex, clip.trimStartMs, value.toLong()))
            },
            valueRange = (clip.trimStartMs + VideoLayoutMath.MinTrimDurationMs).toFloat()..clip.durationMs.toFloat(),
        )
        Text(stringResource(R.string.video_trim_end, clip.trimEndMs.formatDuration()))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransformControls(
    media: MediaSource,
    cellIndex: Int,
    onIntent: (VideoMergeIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.video_crop_position), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { onIntent(VideoMergeIntent.ResetVideoTransform(cellIndex)) },
                label = { Text(stringResource(R.string.reset)) },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
            )
        }
        Text(
            text = stringResource(R.string.video_zoom_value, media.transform.zoom),
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = media.transform.zoom,
            onValueChange = { zoom ->
                onIntent(VideoMergeIntent.UpdateVideoTransform(cellIndex, media.transform.copy(zoom = zoom), trackUndo = false))
            },
            onValueChangeFinished = {
                onIntent(VideoMergeIntent.UpdateVideoTransform(cellIndex, media.transform.normalized(), trackUndo = true))
            },
            valueRange = ImageTransform.MIN_ZOOM..ImageTransform.MAX_ZOOM,
        )
        Text(
            text = stringResource(R.string.video_pan_horizontal, media.transform.panX.percent()),
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = media.transform.panX,
            onValueChange = { pan ->
                onIntent(VideoMergeIntent.UpdateVideoTransform(cellIndex, media.transform.copy(panX = pan), trackUndo = false))
            },
            onValueChangeFinished = {
                onIntent(VideoMergeIntent.UpdateVideoTransform(cellIndex, media.transform.normalized(), trackUndo = true))
            },
            valueRange = -1f..1f,
        )
        Text(
            text = stringResource(R.string.video_pan_vertical, media.transform.panY.percent()),
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = media.transform.panY,
            onValueChange = { pan ->
                onIntent(VideoMergeIntent.UpdateVideoTransform(cellIndex, media.transform.copy(panY = pan), trackUndo = false))
            },
            onValueChangeFinished = {
                onIntent(VideoMergeIntent.UpdateVideoTransform(cellIndex, media.transform.normalized(), trackUndo = true))
            },
            valueRange = -1f..1f,
        )
    }
}

@Composable
private fun VideoExportPanel(state: VideoMergeState) {
    val project = state.project ?: return
    val context = LocalContext.current
    val externalUiLauncher = LocalExternalUiLauncher.current
    val shareUnavailableMessage = stringResource(R.string.video_share_unavailable)
    val outputSize = VideoLayoutMath.outputSizeForMedia(project.canvasAspectRatio, project.exportResolution, project.mediaByCell)
    val outputDurationMs = VideoLayoutMath.outputDurationForMergedVideos(project.orderedClips)
    val estimateBytes = VideoLayoutMath.estimateMp4Bytes(outputSize, project.orderedClips)
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = stringResource(R.string.video_export_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(
                    R.string.video_export_summary,
                    project.canvasAspectRatio.label,
                    outputSize.widthPx,
                    outputSize.heightPx,
                    outputDurationMs.formatDuration(),
                    estimateBytes.formatFileSize(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (project.exportResolution == ExportResolution.UHD_2160) {
                StatusMessage(text = stringResource(R.string.video_4k_warning), tone = StatusTone.Warning)
            }
            if (project.containsHdr) {
                StatusMessage(text = stringResource(R.string.video_hdr_sdr_note), tone = StatusTone.Info)
            }
            when (val result = state.exportResult) {
                is com.rameshta.splitframe.domain.ExportResult.Success -> {
                    StatusMessage(text = stringResource(R.string.video_export_success), tone = StatusTone.Success)
                    SecondaryActionButton(
                        text = stringResource(R.string.share),
                        icon = Icons.Default.Share,
                        onClick = {
                            val shareIntent = context.videoShareIntent(result.savedUri)
                            if (shareIntent == null) {
                                Toast.makeText(
                                    context,
                                    shareUnavailableMessage,
                                    Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                externalUiLauncher.launch(ExternalUiReason.ShareSheet) {
                                    context.startActivity(shareIntent)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is com.rameshta.splitframe.domain.ExportResult.Failure ->
                    StatusMessage(
                        text = stringResource(
                            R.string.video_export_failure,
                            localizedRuntimeMessage(result.reason),
                        ),
                        tone = StatusTone.Error,
                    )
                null -> Unit
            }
        }
    }
}

private fun Context.videoShareIntent(savedUri: String): Intent? {
    val sendIntent = createVideoSendIntent(savedUri) ?: return null
    if (sendIntent.resolveActivity(packageManager) == null) return null
    return Intent.createChooser(sendIntent, getString(R.string.share))
}

internal fun Context.createVideoSendIntent(savedUri: String): Intent? {
    val uri = runCatching { Uri.parse(savedUri) }
        .getOrNull()
        ?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
        ?: return null
    return Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.video_share_subject))
        clipData = ClipData.newRawUri(getString(R.string.video_share_subject), uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

@Composable
private fun PersistentExportAction(
    state: VideoMergeState,
    onIntent: (VideoMergeIntent) -> Unit,
) {
    val project = state.project ?: return
    if (state.isExporting) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(progress = { state.exportProgress }, modifier = Modifier.fillMaxWidth())
            SecondaryActionButton(
                text = stringResource(R.string.video_export_cancel),
                onClick = { onIntent(VideoMergeIntent.CancelExport) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        PrimaryActionButton(
            text = stringResource(R.string.save),
            icon = Icons.Default.FileDownload,
            onClick = { onIntent(VideoMergeIntent.StartExport) },
            enabled = project.isComplete,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun VideoClip.toPreviewMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setUri(Uri.parse(uri))
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(trimStartMs)
                .setEndPositionMs(trimEndMs)
                .build(),
        )
        .build()

private fun MergedVideoTimelinePosition.playerPositionMs(): Long =
    if (positionInTrimMs >= clip.trimmedDurationMs) {
        (clip.trimmedDurationMs - 1L).coerceAtLeast(0L)
    } else {
        positionInTrimMs
    }

private data class PreviewClipKey(
    val id: String,
    val uri: String,
    val trimStartMs: Long,
    val trimEndMs: Long,
)

private fun Context.persistMediaUriAccessIfSupported(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun Long.formatDuration(): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun Long.formatFileSize(): String =
    if (this >= 1_048_576L) {
        "%.1f MB".format(this / 1_048_576f)
    } else {
        "${(this / 1024L).coerceAtLeast(1L)} KB"
    }

private const val PreviewSeekToleranceMs = 240L
private const val PreviewProgressPollMs = 200L

private fun Float.percent(): String = "${(this * 100).roundToInt()}%"
