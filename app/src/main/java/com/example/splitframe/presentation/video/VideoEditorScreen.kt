package com.example.splitframe.presentation.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil.compose.AsyncImage
import com.example.splitframe.R
import com.example.splitframe.domain.ExportResolution
import com.example.splitframe.domain.ImageDimensions
import com.example.splitframe.domain.ImageTransform
import com.example.splitframe.domain.LayoutMath
import com.example.splitframe.domain.LayoutTemplate
import com.example.splitframe.domain.MediaDurationMode
import com.example.splitframe.domain.MediaSource
import com.example.splitframe.domain.MixedMediaLimits
import com.example.splitframe.domain.MixedMediaTemplateCatalog
import com.example.splitframe.domain.VideoCanvasAspectRatio
import com.example.splitframe.domain.VideoClip
import com.example.splitframe.domain.VideoFitMode
import com.example.splitframe.domain.VideoLayoutMath
import com.example.splitframe.domain.VideoMergeProject
import com.example.splitframe.ui.components.PrimaryActionButton
import com.example.splitframe.ui.components.SecondaryActionButton
import com.example.splitframe.ui.components.SplitFrameSection
import com.example.splitframe.ui.components.SplitFrameTopAppBar
import com.example.splitframe.ui.components.StatusMessage
import com.example.splitframe.ui.components.StatusTone
import com.example.splitframe.ui.theme.splitFrameColors
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
    val snackbarHostState = remember { SnackbarHostState() }
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
        if (uri != null) {
            context.persistMediaUriAccessIfSupported(uri)
            onIntent(VideoMergeIntent.ReplaceMedia(state.selectedClipIndex, uri.toString()))
        }
    }
    val pickMixed = {
        multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
    val replaceSelected = {
        singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
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
                            onReplaceSelected = replaceSelected,
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
                        onReplaceSelected = replaceSelected,
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                if (state.status == VideoEditorStatus.Playing) {
                    onIntent(VideoMergeIntent.Pause)
                } else {
                    onIntent(VideoMergeIntent.Play)
                }
            },
            enabled = project.mediaByCell.values.any { it is MediaSource.Video },
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
            text = state.playbackPositionMs.formatDuration(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (project.mediaByCell.values.filterIsInstance<MediaSource.Video>().size > MixedMediaLimits.MaxLivePreviewVideos) {
            StatusMessage(
                text = stringResource(R.string.mixed_media_live_preview_limited),
                tone = StatusTone.Info,
            )
        }
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
    val liveVideos = remember(project.mediaByCell, project.template.id) {
        project.template.cells
            .mapNotNull { cell -> (project.mediaByCell[cell.index] as? MediaSource.Video)?.let { cell.index to it.clip } }
            .take(MixedMediaLimits.MaxLivePreviewVideos)
    }
    val liveVideoIds = liveVideos.map { it.second.id }.toSet()
    val players = remember { mutableStateMapOf<String, ExoPlayer>() }
    val currentStatus by rememberUpdatedState(state.status)

    DisposableEffect(liveVideoIds) {
        liveVideos.forEach { (_, clip) ->
            players.getOrPut(clip.id) { ExoPlayer.Builder(context).build() }
        }
        val removed = players.keys - liveVideoIds
        removed.forEach { key -> players.remove(key)?.release() }
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            players.values.forEach { it.release() }
            players.clear()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                players.values.forEach { it.pause() }
                onIntent(VideoMergeIntent.Pause)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    liveVideos.forEach { (_, clip) ->
        VideoPreviewPlayerEffect(players[clip.id], clip)
    }
    LaunchedEffect(project.primaryAudioMediaId, liveVideoIds) {
        liveVideos.forEach { (_, clip) ->
            players[clip.id]?.volume = if (clip.id == project.primaryAudioMediaId) 1f else 0f
        }
    }
    LaunchedEffect(state.playbackPositionMs, liveVideoIds, state.status) {
        if (state.status != VideoEditorStatus.Playing) {
            liveVideos.forEach { (_, clip) ->
                seekPreview(players[clip.id], clip, state.playbackPositionMs)
            }
        }
    }
    LaunchedEffect(project.id, liveVideoIds, state.status, project.durationMode) {
        var timelineStart = SystemClock.elapsedRealtime() - state.playbackPositionMs
        while (currentStatus == VideoEditorStatus.Playing) {
            val outputDurationMs = VideoLayoutMath.outputDurationForMedia(project.mediaByCell, project.durationMode)
            val timelinePositionMs = (SystemClock.elapsedRealtime() - timelineStart).coerceAtLeast(0L)
            if (outputDurationMs > 0L && timelinePositionMs >= outputDurationMs) {
                players.values.forEach { it.pause() }
                liveVideos.forEach { (_, clip) -> seekPreview(players[clip.id], clip, 0L) }
                onIntent(VideoMergeIntent.SeekTo(0L))
                timelineStart = SystemClock.elapsedRealtime()
                delay(80)
                if (currentStatus == VideoEditorStatus.Playing) {
                    players.values.forEach { it.playWhenReady = true }
                }
            } else {
                liveVideos.forEach { (_, clip) ->
                    val target = if (project.durationMode == MediaDurationMode.LOOP_SHORTER) {
                        VideoLayoutMath.loopedPositionMs(clip, timelinePositionMs) - clip.trimStartMs
                    } else {
                        timelinePositionMs.coerceIn(0L, clip.trimmedDurationMs)
                    }
                    val player = players[clip.id] ?: return@forEach
                    if (kotlin.math.abs(player.currentPosition - target) > 240L && player.playbackState != Player.STATE_IDLE) {
                        player.seekTo(target)
                    }
                    player.playWhenReady = true
                }
                onIntent(VideoMergeIntent.SeekTo(timelinePositionMs))
                delay(250)
            }
        }
        players.values.forEach { it.pause() }
    }

    Surface(
        color = project.backgroundColor.toComposeColor(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        modifier = modifier,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(project.spacingDp.dp),
        ) {
            project.template.cells.forEach { cell ->
                val media = project.mediaByCell[cell.index]
                val selected = state.selectedClipIndex == cell.index
                val player = (media as? MediaSource.Video)?.let { players[it.id] }
                MixedMediaCell(
                    media = media,
                    player = player,
                    selected = selected,
                    livePreview = player != null,
                    modifier = Modifier
                        .offset(x = maxWidth * cell.rect.x, y = maxHeight * cell.rect.y)
                        .width(maxWidth * cell.rect.width)
                        .height(maxHeight * cell.rect.height)
                        .padding((project.spacingDp / 2f).dp),
                    shape = RoundedCornerShape(project.cornerRadiusDp.dp),
                    onSelect = { onIntent(VideoMergeIntent.SelectClip(cell.index)) },
                    onTransform = { onIntent(VideoMergeIntent.UpdateVideoTransform(cell.index, it, trackUndo = false)) },
                    onTransformCommit = { onIntent(VideoMergeIntent.UpdateVideoTransform(cell.index, it, trackUndo = true)) },
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun MixedMediaCell(
    media: MediaSource?,
    player: ExoPlayer?,
    selected: Boolean,
    livePreview: Boolean,
    modifier: Modifier,
    shape: RoundedCornerShape,
    onSelect: () -> Unit,
    onTransform: (ImageTransform) -> Unit,
    onTransformCommit: (ImageTransform) -> Unit,
) {
    val colors = splitFrameColors()
    val borderColor = if (selected) colors.selectedCell else MaterialTheme.colorScheme.outlineVariant
    val emptyDescription = stringResource(R.string.video_empty_cell_description)
    val cellDescription = stringResource(R.string.video_cell_description)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black)
            .border(if (selected) 3.dp else 1.dp, borderColor, shape)
            .semantics {
                this.selected = selected
                contentDescription = if (media == null) emptyDescription else cellDescription
            }
            .clickable(onClick = onSelect)
            .pointerInput(media?.id, selected, media?.transform) {
                if (!selected || media == null) return@pointerInput
                var latest = media.transform
                detectDragGestures(
                    onDragEnd = { onTransformCommit(latest) },
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
                        onTransform(latest)
                    },
                )
            }
            .pointerInput(media?.id, selected, media?.transform) {
                if (!selected || media == null) return@pointerInput
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val transform = LayoutMath.transformAfterDoubleTap(
                            sourceDimensions = ImageDimensions(media.width, media.height),
                            destinationWidthPx = size.width.toFloat(),
                            destinationHeightPx = size.height.toFloat(),
                            current = media.transform,
                            tapXInFramePx = offset.x,
                            tapYInFramePx = offset.y,
                        )
                        onTransformCommit(transform)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when (media) {
            null -> EmptyCell()
            is MediaSource.Image -> ImageCell(media)
            is MediaSource.Video -> {
                if (player != null && livePreview) {
                    ContentFrame(
                        player = player,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = media.transform.zoom
                                scaleY = media.transform.zoom
                                translationX = media.transform.panX * size.width * 0.2f
                                translationY = media.transform.panY * size.height * 0.2f
                            },
                        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                        contentScale = if (media.fitMode == VideoFitMode.FIT) ContentScale.Fit else ContentScale.Crop,
                        keepContentOnReset = true,
                    )
                } else {
                    VideoPosterCell(media)
                }
                MediaBadge(stringResource(R.string.mixed_media_video_label), Modifier.align(Alignment.TopStart))
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
private fun ImageCell(media: MediaSource.Image) {
    AsyncImage(
        model = Uri.parse(media.enhancedPath ?: media.uri),
        contentDescription = null,
        contentScale = if (media.fitMode == VideoFitMode.FIT) ContentScale.Fit else ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = media.transform.zoom
                scaleY = media.transform.zoom
                translationX = media.transform.panX * size.width * 0.2f
                translationY = media.transform.panY * size.height * 0.2f
            },
    )
    MediaBadge(stringResource(R.string.mixed_media_image_label), Modifier.padding(6.dp))
}

@Composable
private fun VideoPosterCell(media: MediaSource.Video) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text(
            text = media.clip.trimmedDurationMs.formatDuration(),
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
private fun VideoPreviewPlayerEffect(player: ExoPlayer?, clip: VideoClip) {
    LaunchedEffect(player, clip.uri, clip.trimStartMs, clip.trimEndMs) {
        if (player == null) return@LaunchedEffect
        player.setMediaItem(clip.toPreviewMediaItem())
        player.prepare()
        player.seekTo(0L)
    }
}

@Composable
private fun EditorControls(
    state: VideoMergeState,
    onIntent: (VideoMergeIntent) -> Unit,
    onPickMedia: () -> Unit,
    onReplaceSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MediaSelectionSection(state, onPickMedia)
        TemplateSelector(state, onIntent)
        MediaThumbnailStrip(state, onIntent)
        VideoTools(state, onIntent, onReplaceSelected, onPickMedia)
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
    SplitFrameSection(
        title = stringResource(R.string.video_template_title),
        supportingText = stringResource(R.string.mixed_media_count, project.mediaCount, MixedMediaLimits.MaxItems),
    ) {
        PrimaryActionButton(
            text = stringResource(R.string.video_pick_two),
            icon = Icons.Default.VideoLibrary,
            onClick = onPickMedia,
            enabled = !state.isExporting && project.mediaCount < MixedMediaLimits.MaxItems,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateSelector(
    state: VideoMergeState,
    onIntent: (VideoMergeIntent) -> Unit,
) {
    val project = state.project ?: return
    val count = project.mediaCount.coerceAtLeast(MixedMediaLimits.MinItems)
    val templates = MixedMediaTemplateCatalog.compatibleTemplates(count)
    SplitFrameSection(title = stringResource(R.string.layout_controls)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            templates.take(12).forEach { template ->
                TemplateChip(
                    template = template,
                    selected = project.template.id == template.id,
                    enabled = template.slotCount >= project.mediaCount && !state.isExporting,
                    onClick = { onIntent(VideoMergeIntent.SelectTemplate(template.id)) },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoCanvasAspectRatio.entries.forEach { ratio ->
                FilterChip(
                    selected = project.canvasAspectRatio == ratio,
                    onClick = { onIntent(VideoMergeIntent.SelectCanvasAspectRatio(ratio)) },
                    label = { Text(ratio.label) },
                )
            }
        }
    }
}

@Composable
private fun TemplateChip(
    template: LayoutTemplate,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = { Text(template.name.templateDisplayName()) },
    )
}

@Composable
private fun MediaThumbnailStrip(
    state: VideoMergeState,
    onIntent: (VideoMergeIntent) -> Unit,
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
            Card(
                modifier = Modifier
                    .width(82.dp)
                    .height(64.dp)
                    .semantics { selected = state.selectedClipIndex == cell.index }
                    .clickable { onIntent(VideoMergeIntent.SelectClip(cell.index)) },
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
                enabled = project.mediaCount < MixedMediaLimits.MaxItems && !state.isExporting,
            )
        }
        AudioControls(project = project, onIntent = onIntent)
        DurationAndResolutionControls(project = project, onIntent = onIntent)
        SelectedMediaControls(state = state, onIntent = onIntent)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioControls(
    project: VideoMergeProject,
    onIntent: (VideoMergeIntent) -> Unit,
) {
    val videos = project.template.cells.mapNotNull { cell ->
        (project.mediaByCell[cell.index] as? MediaSource.Video)?.let { cell.index to it }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.video_audio_source), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioChip(
                text = stringResource(R.string.video_audio_none),
                selected = project.primaryAudioMediaId == null,
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
        Text(text = stringResource(R.string.video_duration_mode), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = project.durationMode == MediaDurationMode.LOOP_SHORTER,
                onClick = { onIntent(VideoMergeIntent.SelectDurationMode(MediaDurationMode.LOOP_SHORTER)) },
                label = { Text(stringResource(R.string.video_duration_loop)) },
            )
            FilterChip(
                selected = project.durationMode == MediaDurationMode.FREEZE_SHORTER,
                onClick = { onIntent(VideoMergeIntent.SelectDurationMode(MediaDurationMode.FREEZE_SHORTER)) },
                label = { Text(stringResource(R.string.video_duration_longest)) },
            )
            FilterChip(
                selected = project.durationMode == MediaDurationMode.STOP_AT_SHORTEST,
                onClick = { onIntent(VideoMergeIntent.SelectDurationMode(MediaDurationMode.STOP_AT_SHORTEST)) },
                label = { Text(stringResource(R.string.video_duration_shortest)) },
            )
        }
        Text(text = stringResource(R.string.export_resolution), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportResolution.entries.forEach { resolution ->
                FilterChip(
                    selected = project.exportResolution == resolution,
                    onClick = { onIntent(VideoMergeIntent.SelectExportResolution(resolution)) },
                    label = { Text(resolution.label) },
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
            FilterChip(
                selected = media.fitMode == VideoFitMode.FILL,
                onClick = { onIntent(VideoMergeIntent.SelectFitMode(cellIndex, VideoFitMode.FILL)) },
                label = { Text(stringResource(R.string.video_fit_fill)) },
            )
            FilterChip(
                selected = media.fitMode == VideoFitMode.FIT,
                onClick = { onIntent(VideoMergeIntent.SelectFitMode(cellIndex, VideoFitMode.FIT)) },
                label = { Text(stringResource(R.string.video_fit_fit)) },
            )
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
    val outputSize = VideoLayoutMath.outputSizeForMedia(project.canvasAspectRatio, project.exportResolution, project.mediaByCell)
    val outputDurationMs = VideoLayoutMath.outputDurationForMedia(project.mediaByCell, project.durationMode)
    val estimateBytes = VideoLayoutMath.estimateMp4Bytes(outputSize, outputDurationMs)
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
                is com.example.splitframe.domain.ExportResult.Success ->
                    StatusMessage(text = stringResource(R.string.video_export_success), tone = StatusTone.Success)
                is com.example.splitframe.domain.ExportResult.Failure ->
                    StatusMessage(text = stringResource(R.string.video_export_failure, result.reason), tone = StatusTone.Error)
                null -> Unit
            }
        }
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
            enabled = project.isComplete && project.hasVideo,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun VideoClip.toPreviewMediaItem(): MediaItem =
    MediaItem.Builder()
        .setUri(Uri.parse(uri))
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(trimStartMs)
                .setEndPositionMs(trimEndMs)
                .build(),
        )
        .build()

private fun seekPreview(player: ExoPlayer?, clip: VideoClip, projectPositionMs: Long) {
    if (player == null) return
    val target = VideoLayoutMath.loopedPositionMs(clip, projectPositionMs) - clip.trimStartMs
    player.seekTo(target.coerceIn(0L, clip.trimmedDurationMs))
}

private fun Context.persistMediaUriAccessIfSupported(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun ULong.toComposeColor(): Color = Color(toLong().toInt())

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

private fun Float.percent(): String = "${(this * 100).roundToInt()}%"

private fun String.templateDisplayName(): String =
    replace('_', ' ')
        .split(' ')
        .joinToString(" ") { token -> token.replaceFirstChar { it.titlecase() } }
