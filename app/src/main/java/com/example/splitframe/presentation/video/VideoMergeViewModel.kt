package com.example.splitframe.presentation.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.splitframe.R
import com.example.splitframe.data.VideoProjectStore
import com.example.splitframe.data.local.VideoExportWorkEntity
import com.example.splitframe.domain.ExportResolution
import com.example.splitframe.domain.ExportResult
import com.example.splitframe.domain.ImageTransform
import com.example.splitframe.domain.LayoutTemplate
import com.example.splitframe.domain.MediaDurationMode
import com.example.splitframe.domain.MediaSource
import com.example.splitframe.domain.VideoCanvasAspectRatio
import com.example.splitframe.domain.VideoClip
import com.example.splitframe.domain.VideoFitMode
import com.example.splitframe.domain.VideoLayoutMath
import com.example.splitframe.domain.VideoMergeProject
import com.example.splitframe.domain.withFitMode
import com.example.splitframe.domain.withTransform
import com.example.splitframe.export.MediaMetadataFailure
import com.example.splitframe.export.MixedMediaMetadataReader
import com.example.splitframe.export.MixedMediaMetadataResult
import com.example.splitframe.export.VideoExportWorker
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoMergeViewModel(
    private val videoProjectStore: VideoProjectStore,
    private val mixedMediaMetadataReader: MixedMediaMetadataReader,
    private val workManager: WorkManager,
) : ViewModel() {
    private val undoStack = ArrayDeque<VideoMergeProject>()
    private val redoStack = ArrayDeque<VideoMergeProject>()
    private var metadataJob: Job? = null
    private var exportObserveJob: Job? = null
    private val _state = MutableStateFlow(VideoMergeState())
    val state: StateFlow<VideoMergeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val project = videoProjectStore.getOrCreate(null).asVideoMergeProject()
            _state.update {
                it.copy(
                    project = project,
                    selectedClipIndex = project.selectedCellIndex ?: 0,
                    status = VideoEditorStatus.Initial,
                )
            }
            videoProjectStore.save(project)
            observeExportWork(project.id)
        }
    }

    fun process(intent: VideoMergeIntent) {
        handle(intent.toAction())
    }

    private fun handle(action: VideoMergeAction) {
        when (action) {
            is VideoMergeAction.SelectMedia -> selectMedia(action.uris)
            is VideoMergeAction.AssignMedia -> readAndAssign(listOf(action.cellIndex to action.uri), replace = false)
            is VideoMergeAction.ReplaceMedia -> readAndAssign(listOf(action.cellIndex to action.uri), replace = true)
            is VideoMergeAction.RemoveMedia -> removeMedia(action.cellIndex)
            is VideoMergeAction.SelectClip -> selectCell(action.cellIndex)
            VideoMergeAction.SwapVideos -> swapCells(0, 1)
            is VideoMergeAction.SwapCells -> swapCells(action.firstCellIndex, action.secondCellIndex)
            VideoMergeAction.AutoArrange -> autoArrange()
            is VideoMergeAction.ReorderMedia -> swapCells(action.fromCellIndex, action.toCellIndex)
            is VideoMergeAction.UpdateTrim -> updateTrim(action.cellIndex, action.startMs, action.endMs)
            is VideoMergeAction.UpdateVideoTransform -> updateTransform(action.cellIndex, action.transform, action.trackUndo)
            is VideoMergeAction.ResetVideoTransform -> updateTransform(action.cellIndex, ImageTransform.Default, trackUndo = true)
            is VideoMergeAction.SelectCanvasAspectRatio -> updateProject { project ->
                val videos = project.orderedVideoMedia()
                val template = VideoLayoutMath.sequenceTemplateFor(videos.size, action.aspectRatio)
                project.copy(
                    canvasAspectRatio = action.aspectRatio,
                    template = template,
                    mediaByCell = videos.toCellMap(template),
                )
            }
            is VideoMergeAction.SelectPrimaryAudio -> updateProject { project ->
                project.copy(primaryAudioMediaId = availableAudioMediaId(project, action.mediaId))
            }
            is VideoMergeAction.SelectDurationMode -> updateProject { it.copy(durationMode = action.mode) }
            is VideoMergeAction.SelectExportResolution -> updateProject { it.copy(exportResolution = action.resolution) }
            is VideoMergeAction.SelectFitMode -> updateMedia(action.cellIndex) { it.withFitMode(action.fitMode) }
            VideoMergeAction.Play -> reduce(VideoMergeResultEvent.Playing)
            VideoMergeAction.Pause -> reduce(VideoMergeResultEvent.Paused)
            is VideoMergeAction.SeekTo -> reduce(VideoMergeResultEvent.Seeked(action.positionMs))
            VideoMergeAction.RetryPreview -> reduce(VideoMergeResultEvent.PreviewPreparing)
            VideoMergeAction.StartExport -> startExport()
            VideoMergeAction.CancelExport -> cancelExport()
            VideoMergeAction.RetryExport -> startExport()
            VideoMergeAction.DismissError -> reduce(VideoMergeResultEvent.ErrorCleared)
            VideoMergeAction.ResetProject -> resetProject()
            VideoMergeAction.UndoEdit -> undoEdit()
            VideoMergeAction.RedoEdit -> redoEdit()
            VideoMergeAction.DismissExportResult -> reduce(VideoMergeResultEvent.ExportResultDismissed)
        }
    }

    private fun selectMedia(uris: List<String>) {
        if (uris.isEmpty()) return
        val current = _state.value.project ?: return
        val existingUris = current.mediaByCell.values.map { it.uri }.toSet()
        val distinct = uris.distinct().filterNot { it in existingUris }
        if (distinct.size < uris.size) {
            reduce(VideoMergeResultEvent.Failed(R.string.media_duplicate_skipped))
        }
        readAndAppend(distinct)
    }

    private fun readAndAppend(uris: List<String>) {
        if (uris.isEmpty()) return
        metadataJob?.cancel()
        metadataJob = viewModelScope.launch {
            reduce(VideoMergeResultEvent.MetadataReadingStarted)
            val current = _state.value.project ?: return@launch
            val results = readMetadata(uris)
            val validMedia = results.mapNotNull { (_, result) ->
                ((result as? MixedMediaMetadataResult.Valid)?.media as? MediaSource.Video)
            }
            val containsNonVideo = results.any { (_, result) ->
                result is MixedMediaMetadataResult.Valid && result.media !is MediaSource.Video
            }
            val firstFailure = results.firstNotNullOfOrNull { (_, result) ->
                (result as? MixedMediaMetadataResult.Unsupported)?.failure
            }
            if (validMedia.isNotEmpty()) {
                val ordered = current.orderedVideoMedia() + validMedia
                val targetTemplate = VideoLayoutMath.sequenceTemplateFor(ordered.size, current.canvasAspectRatio)
                val updated = current.copy(
                    template = targetTemplate,
                    mediaByCell = ordered.toCellMap(targetTemplate),
                    selectedCellIndex = targetTemplate.cells.firstOrNull()?.index ?: 0,
                    spacingDp = VideoLayoutMath.EdgeToEdgeSpacingDp,
                ).withAvailableAudio()
                commitProjectChange(updated)
                reduce(VideoMergeResultEvent.PreviewPreparing)
            }
            if (containsNonVideo) {
                reduce(VideoMergeResultEvent.Failed(R.string.video_only_required))
            }
            firstFailure?.let { reduce(VideoMergeResultEvent.Failed(it.toMessageRes())) }
        }
    }

    private fun readAndAssign(assignments: List<Pair<Int, String>>, replace: Boolean) {
        if (assignments.isEmpty()) return
        metadataJob?.cancel()
        metadataJob = viewModelScope.launch {
            reduce(VideoMergeResultEvent.MetadataReadingStarted)
            val current = _state.value.project ?: return@launch
            val replacementCells = assignments.map { it.first }.toSet()
            val existingUris = current.mediaByCell
                .filterKeys { key -> key !in replacementCells || !replace }
                .values
                .map { it.uri }
                .toSet()
            val results = readMetadata(assignments.map { it.second })
            var updated = current
            var firstFailure: MediaMetadataFailure? = null
            assignments.zip(results).forEach { (assignment, metadata) ->
                val (cell, uri) = assignment
                if (cell !in updated.template.cells.map { it.index }) return@forEach
                if (uri in existingUris) {
                    firstFailure = firstFailure ?: MediaMetadataFailure.Unreadable
                    return@forEach
                }
                when (val result = metadata.second) {
                    is MixedMediaMetadataResult.Valid -> {
                        val media = result.media as? MediaSource.Video
                        if (media == null) {
                            firstFailure = firstFailure ?: MediaMetadataFailure.MissingVideoTrack
                        } else {
                            updated = updated.copy(mediaByCell = updated.mediaByCell + (cell to media))
                        }
                    }
                    is MixedMediaMetadataResult.Unsupported -> firstFailure = firstFailure ?: result.failure
                }
            }
            if (updated != current) {
                commitProjectChange(updated.withAvailableAudio())
                reduce(VideoMergeResultEvent.PreviewPreparing)
            }
            firstFailure?.let { reduce(VideoMergeResultEvent.Failed(it.toMessageRes())) }
        }
    }

    private suspend fun readMetadata(uris: List<String>): List<Pair<String, MixedMediaMetadataResult>> =
        withContext(Dispatchers.IO) {
            uris.map { uri -> uri to mixedMediaMetadataReader.read(uri) }
        }

    private fun removeMedia(cellIndex: Int) {
        val project = _state.value.project ?: return
        if (!project.mediaByCell.containsKey(cellIndex)) return
        val updatedVideos = project.orderedVideoMedia()
            .filterIndexed { index, _ -> index != project.orderedCellIndexPosition(cellIndex) }
        val template = VideoLayoutMath.sequenceTemplateFor(updatedVideos.size, project.canvasAspectRatio)
        val selected = updatedVideos.indices.toList()
            .closestTo(cellIndex)
            .takeIf { updatedVideos.isNotEmpty() }
            ?: 0
        commitProjectChange(
            project.copy(
                template = template,
                mediaByCell = updatedVideos.toCellMap(template),
                selectedCellIndex = selected,
            )
                .withAvailableAudio(),
        )
        reduce(VideoMergeResultEvent.SelectedClipChanged(selected))
    }

    private fun selectCell(cellIndex: Int) {
        val project = _state.value.project ?: return
        val safeCell = project.template.cells.map { it.index }.closestTo(cellIndex)
        val updated = project.copy(
            selectedCellIndex = safeCell,
            spacingDp = VideoLayoutMath.EdgeToEdgeSpacingDp,
        )
        reduce(VideoMergeResultEvent.SelectedClipChanged(safeCell))
        viewModelScope.launch { videoProjectStore.save(updated) }
        _state.update { it.copy(project = updated) }
    }

    private fun swapCells(firstCellIndex: Int, secondCellIndex: Int) {
        val project = _state.value.project ?: return
        if (firstCellIndex == secondCellIndex) return
        val first = project.mediaByCell[firstCellIndex]
        val second = project.mediaByCell[secondCellIndex]
        if (first == null && second == null) return
        val updatedMedia = project.mediaByCell.toMutableMap().apply {
            if (second == null) remove(firstCellIndex) else put(firstCellIndex, second)
            if (first == null) remove(secondCellIndex) else put(secondCellIndex, first)
        }
        commitProjectChange(project.copy(mediaByCell = updatedMedia).withAvailableAudio())
    }

    private fun autoArrange() {
        val project = _state.value.project ?: return
        val ordered = project.orderedVideoMedia()
        val template = VideoLayoutMath.sequenceTemplateFor(ordered.size, project.canvasAspectRatio)
        commitProjectChange(
            project.copy(
                template = template,
                mediaByCell = ordered.toCellMap(template),
                selectedCellIndex = project.selectedCellIndex?.coerceIn(0, (ordered.size - 1).coerceAtLeast(0)),
            ).withAvailableAudio(),
        )
    }

    private fun updateTrim(cellIndex: Int, startMs: Long, endMs: Long) {
        updateVideoClip(cellIndex) { clip ->
            val (start, end) = VideoLayoutMath.normalizeTrim(clip.durationMs, startMs, endMs)
            clip.copy(trimStartMs = start, trimEndMs = end)
        }
        reduce(VideoMergeResultEvent.Seeked(startMs))
    }

    private fun updateTransform(cellIndex: Int, transform: ImageTransform, trackUndo: Boolean) {
        updateMedia(cellIndex, trackUndo) { it.withTransform(transform) }
    }

    private fun updateVideoClip(
        cellIndex: Int,
        trackUndo: Boolean = true,
        transform: (VideoClip) -> VideoClip,
    ) {
        updateMedia(cellIndex, trackUndo) { media ->
            if (media is MediaSource.Video) {
                media.copy(clip = transform(media.clip))
            } else {
                media
            }
        }
    }

    private fun updateMedia(
        cellIndex: Int,
        trackUndo: Boolean = true,
        transform: (MediaSource) -> MediaSource,
    ) {
        val project = _state.value.project ?: return
        val media = project.mediaByCell[cellIndex] ?: return
        commitProjectChange(project.copy(mediaByCell = project.mediaByCell + (cellIndex to transform(media))).withAvailableAudio(), trackUndo)
    }

    private fun updateProject(transform: (VideoMergeProject) -> VideoMergeProject) {
        val project = _state.value.project ?: return
        commitProjectChange(transform(project).withAvailableAudio())
    }

    private fun commitProjectChange(project: VideoMergeProject, trackUndo: Boolean = true) {
        val edgeToEdgeProject = project.copy(spacingDp = VideoLayoutMath.EdgeToEdgeSpacingDp)
        val current = _state.value.project
        if (trackUndo && current != null && current != edgeToEdgeProject) {
            undoStack.addLast(current)
            while (undoStack.size > MaxUndoDepth) undoStack.removeFirst()
            redoStack.clear()
        }
        reduce(VideoMergeResultEvent.ProjectChanged(edgeToEdgeProject))
        viewModelScope.launch {
            videoProjectStore.save(edgeToEdgeProject)
        }
    }

    private fun undoEdit() {
        val current = _state.value.project ?: return
        val previous = undoStack.removeLastOrNull()?.copy(spacingDp = VideoLayoutMath.EdgeToEdgeSpacingDp) ?: return
        redoStack.addLast(current.copy(spacingDp = VideoLayoutMath.EdgeToEdgeSpacingDp))
        reduce(VideoMergeResultEvent.ProjectChanged(previous))
        viewModelScope.launch { videoProjectStore.save(previous) }
    }

    private fun redoEdit() {
        val current = _state.value.project ?: return
        val next = redoStack.removeLastOrNull()?.copy(spacingDp = VideoLayoutMath.EdgeToEdgeSpacingDp) ?: return
        undoStack.addLast(current.copy(spacingDp = VideoLayoutMath.EdgeToEdgeSpacingDp))
        reduce(VideoMergeResultEvent.ProjectChanged(next))
        viewModelScope.launch { videoProjectStore.save(next) }
    }

    private fun resetProject() {
        val current = _state.value.project ?: return
        val reset = VideoMergeProject(id = current.id)
        undoStack.clear()
        redoStack.clear()
        reduce(VideoMergeResultEvent.ProjectChanged(reset))
        viewModelScope.launch { videoProjectStore.reset(current.id) }
    }

    private fun startExport() {
        val project = _state.value.project ?: return
        if (_state.value.isExporting) return
        if (!project.isComplete) {
            reduce(VideoMergeResultEvent.Failed(R.string.video_missing_clips))
            return
        }
        if (project.clips.values.any { !it.hasValidTrim }) {
            reduce(VideoMergeResultEvent.Failed(R.string.video_invalid_trim))
            return
        }
        viewModelScope.launch {
            videoProjectStore.save(project.withAvailableAudio())
            val workName = VideoExportWorker.uniqueWorkName(project.id)
            val request = OneTimeWorkRequestBuilder<VideoExportWorker>()
                .setId(UUID.randomUUID())
                .setInputData(workDataOf(VideoExportWorker.ProjectIdKey to project.id))
                .build()
            videoProjectStore.setExportWork(
                VideoExportWorkEntity(
                    projectId = project.id,
                    workId = request.id.toString(),
                    state = VideoExportWorker.StateQueued,
                    progress = 0f,
                    outputUri = null,
                    errorMessage = null,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
            workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
            reduce(VideoMergeResultEvent.ExportQueued(request.id.toString()))
        }
    }

    private fun cancelExport() {
        val project = _state.value.project ?: return
        workManager.cancelUniqueWork(VideoExportWorker.uniqueWorkName(project.id))
        viewModelScope.launch {
            videoProjectStore.setExportWork(
                VideoExportWorkEntity(
                    projectId = project.id,
                    workId = _state.value.exportWorkId,
                    state = VideoExportWorker.StateCancelled,
                    progress = _state.value.exportProgress,
                    outputUri = null,
                    errorMessage = null,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
        reduce(VideoMergeResultEvent.ExportCancelled)
    }

    private fun observeExportWork(projectId: String) {
        exportObserveJob?.cancel()
        exportObserveJob = viewModelScope.launch {
            videoProjectStore.observeExportWork(projectId).collect { work ->
                when (work?.state) {
                    VideoExportWorker.StateQueued -> reduce(VideoMergeResultEvent.ExportQueued(work.workId))
                    VideoExportWorker.StateRunning -> reduce(VideoMergeResultEvent.ExportProgressChanged(work.progress))
                    VideoExportWorker.StateSucceeded -> reduce(VideoMergeResultEvent.ExportFinished(ExportResult.Success(work.outputUri.orEmpty())))
                    VideoExportWorker.StateFailed -> reduce(
                        VideoMergeResultEvent.ExportFinished(
                            ExportResult.Failure(work.errorMessage ?: "Video export failed."),
                        ),
                    )
                    VideoExportWorker.StateCancelled -> reduce(VideoMergeResultEvent.ExportCancelled)
                }
            }
        }
    }

    private fun reduce(result: VideoMergeResultEvent) {
        _state.update { state ->
            when (result) {
                is VideoMergeResultEvent.ProjectChanged -> state.copy(
                    project = result.project,
                    selectedClipIndex = result.project.selectedCellIndex ?: state.selectedClipIndex,
                    error = null,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
                )
                is VideoMergeResultEvent.SelectedClipChanged -> state.copy(selectedClipIndex = result.cellIndex)
                VideoMergeResultEvent.MediaSelectionStarted -> state.copy(status = VideoEditorStatus.SelectingMedia)
                VideoMergeResultEvent.MetadataReadingStarted -> state.copy(status = VideoEditorStatus.ReadingMetadata, error = null)
                VideoMergeResultEvent.PreviewPreparing -> state.copy(status = VideoEditorStatus.PreparingPreview)
                VideoMergeResultEvent.PreviewReady -> state.copy(status = VideoEditorStatus.PreviewReady)
                VideoMergeResultEvent.Buffering -> state.copy(status = VideoEditorStatus.Buffering)
                VideoMergeResultEvent.Playing -> state.copy(status = VideoEditorStatus.Playing)
                VideoMergeResultEvent.Paused -> state.copy(status = VideoEditorStatus.Paused)
                is VideoMergeResultEvent.Seeked -> state.copy(playbackPositionMs = result.positionMs)
                is VideoMergeResultEvent.ExportQueued -> state.copy(
                    status = VideoEditorStatus.ExportQueued,
                    isExporting = true,
                    exportProgress = 0f,
                    exportWorkId = result.workId,
                    exportResult = null,
                    error = null,
                )
                is VideoMergeResultEvent.ExportProgressChanged -> state.copy(
                    status = VideoEditorStatus.Exporting,
                    isExporting = true,
                    exportProgress = result.progress.coerceIn(0f, 1f),
                )
                is VideoMergeResultEvent.ExportFinished -> state.copy(
                    status = if (result.result is ExportResult.Success) {
                        VideoEditorStatus.ExportCompleted
                    } else {
                        VideoEditorStatus.ExportFailed
                    },
                    isExporting = false,
                    exportProgress = if (result.result is ExportResult.Success) 1f else state.exportProgress,
                    exportResult = result.result,
                )
                VideoMergeResultEvent.ExportCancelled -> state.copy(
                    status = VideoEditorStatus.ExportCancelled,
                    isExporting = false,
                    exportProgress = 0f,
                )
                is VideoMergeResultEvent.Failed -> state.copy(
                    status = VideoEditorStatus.RecoverableMediaError,
                    error = result.messageRes,
                )
                VideoMergeResultEvent.ErrorCleared -> state.copy(error = null)
                VideoMergeResultEvent.ExportResultDismissed -> state.copy(exportResult = null)
            }
        }
    }

    private fun VideoMergeProject.withAvailableAudio(): VideoMergeProject =
        copy(primaryAudioMediaId = availableAudioMediaId(this, primaryAudioMediaId))

    private fun VideoMergeProject.asVideoMergeProject(): VideoMergeProject {
        val videos = orderedVideoMedia()
        val targetTemplate = VideoLayoutMath.sequenceTemplateFor(videos.size, canvasAspectRatio)
        return copy(
            template = targetTemplate,
            mediaByCell = videos.toCellMap(targetTemplate),
            selectedCellIndex = targetTemplate.cells.firstOrNull()?.index ?: 0,
            spacingDp = VideoLayoutMath.EdgeToEdgeSpacingDp,
            cornerRadiusDp = 0f,
        ).withAvailableAudio()
    }

    private fun VideoMergeProject.orderedVideoMedia(): List<MediaSource.Video> {
        val templateOrdered = template.cells.mapNotNull { cell -> mediaByCell[cell.index] as? MediaSource.Video }
        return templateOrdered.ifEmpty {
            mediaByCell.entries
                .sortedBy { it.key }
                .mapNotNull { (_, media) -> media as? MediaSource.Video }
        }
    }

    private fun VideoMergeProject.orderedCellIndexPosition(cellIndex: Int): Int {
        val templateIndexes = template.cells.map { it.index }
        val position = templateIndexes.indexOf(cellIndex)
        if (position >= 0) return position
        return mediaByCell.keys.sorted().indexOf(cellIndex).takeIf { it >= 0 } ?: cellIndex
    }

    private fun availableAudioMediaId(project: VideoMergeProject, requestedMediaId: String?): String? {
        if (requestedMediaId == null) return null
        val requested = project.mediaByCell.values
            .filterIsInstance<MediaSource.Video>()
            .firstOrNull { it.id == requestedMediaId }
            ?.clip
        if (requested?.hasAudio == true) return requestedMediaId
        return project.mediaByCell.values
            .filterIsInstance<MediaSource.Video>()
            .firstOrNull { it.clip.hasAudio }
            ?.id
    }

    private fun MediaMetadataFailure.toMessageRes(): Int =
        when (this) {
            MediaMetadataFailure.UnsupportedFormat -> R.string.video_unsupported_format
            MediaMetadataFailure.Unreadable -> R.string.video_unreadable
            MediaMetadataFailure.MissingVideoTrack -> R.string.video_missing_track
            MediaMetadataFailure.UnsupportedDrmOrCodec -> R.string.video_unsupported_codec
            MediaMetadataFailure.InvalidDuration -> R.string.video_invalid_duration
        }

    private fun List<MediaSource>.toCellMap(template: LayoutTemplate): Map<Int, MediaSource> =
        take(template.slotCount).mapIndexed { index, media -> template.cells[index].index to media }.toMap()

    private fun List<Int>.closestTo(requested: Int): Int =
        if (isEmpty()) {
            requested
        } else {
            minBy { kotlin.math.abs(it - requested) }
        }

    private fun VideoMergeIntent.toAction(): VideoMergeAction =
        when (this) {
            is VideoMergeIntent.SelectMedia -> VideoMergeAction.SelectMedia(uris)
            is VideoMergeIntent.AssignMedia -> VideoMergeAction.AssignMedia(cellIndex, uri)
            is VideoMergeIntent.ReplaceMedia -> VideoMergeAction.ReplaceMedia(cellIndex, uri)
            is VideoMergeIntent.SelectVideos -> VideoMergeAction.SelectMedia(uris)
            is VideoMergeIntent.AssignVideo -> VideoMergeAction.AssignMedia(cellIndex, uri)
            is VideoMergeIntent.ReplaceVideo -> VideoMergeAction.ReplaceMedia(cellIndex, uri)
            is VideoMergeIntent.RemoveVideo -> VideoMergeAction.RemoveMedia(cellIndex)
            is VideoMergeIntent.SelectClip -> VideoMergeAction.SelectClip(cellIndex)
            VideoMergeIntent.SwapVideos -> VideoMergeAction.SwapVideos
            is VideoMergeIntent.SwapCells -> VideoMergeAction.SwapCells(firstCellIndex, secondCellIndex)
            VideoMergeIntent.AutoArrange -> VideoMergeAction.AutoArrange
            is VideoMergeIntent.ReorderMedia -> VideoMergeAction.ReorderMedia(fromCellIndex, toCellIndex)
            is VideoMergeIntent.UpdateTrim -> VideoMergeAction.UpdateTrim(cellIndex, startMs, endMs)
            is VideoMergeIntent.UpdateVideoTransform -> VideoMergeAction.UpdateVideoTransform(cellIndex, transform, trackUndo)
            is VideoMergeIntent.ResetVideoTransform -> VideoMergeAction.ResetVideoTransform(cellIndex)
            is VideoMergeIntent.SelectCanvasAspectRatio -> VideoMergeAction.SelectCanvasAspectRatio(aspectRatio)
            is VideoMergeIntent.SelectPrimaryAudio -> VideoMergeAction.SelectPrimaryAudio(mediaId)
            is VideoMergeIntent.SelectDurationMode -> VideoMergeAction.SelectDurationMode(mode)
            is VideoMergeIntent.SelectExportResolution -> VideoMergeAction.SelectExportResolution(resolution)
            is VideoMergeIntent.SelectFitMode -> VideoMergeAction.SelectFitMode(cellIndex, fitMode)
            VideoMergeIntent.Play -> VideoMergeAction.Play
            VideoMergeIntent.Pause -> VideoMergeAction.Pause
            is VideoMergeIntent.SeekTo -> VideoMergeAction.SeekTo(positionMs)
            VideoMergeIntent.RetryPreview -> VideoMergeAction.RetryPreview
            VideoMergeIntent.StartExport -> VideoMergeAction.StartExport
            VideoMergeIntent.CancelExport -> VideoMergeAction.CancelExport
            VideoMergeIntent.RetryExport -> VideoMergeAction.RetryExport
            VideoMergeIntent.DismissError -> VideoMergeAction.DismissError
            VideoMergeIntent.ResetProject -> VideoMergeAction.ResetProject
            VideoMergeIntent.UndoEdit -> VideoMergeAction.UndoEdit
            VideoMergeIntent.RedoEdit -> VideoMergeAction.RedoEdit
            VideoMergeIntent.DismissExportResult -> VideoMergeAction.DismissExportResult
        }

    private companion object {
        const val MaxUndoDepth = 30
    }
}
