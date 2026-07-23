package com.rameshta.splitframe.presentation.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.rameshta.splitframe.R
import com.rameshta.splitframe.data.VideoProjectReadResult
import com.rameshta.splitframe.data.VideoProjectStore
import com.rameshta.splitframe.data.local.VideoExportWorkEntity
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.ExportResult
import com.rameshta.splitframe.domain.ImageTransform
import com.rameshta.splitframe.domain.LayoutTemplate
import com.rameshta.splitframe.domain.MediaDurationMode
import com.rameshta.splitframe.domain.MediaSource
import com.rameshta.splitframe.domain.VideoCanvasAspectRatio
import com.rameshta.splitframe.domain.VideoClip
import com.rameshta.splitframe.domain.VideoFitMode
import com.rameshta.splitframe.domain.VideoLayoutMath
import com.rameshta.splitframe.domain.VideoMergeProject
import com.rameshta.splitframe.domain.TransformUndoSession
import com.rameshta.splitframe.domain.withFitMode
import com.rameshta.splitframe.domain.withTransform
import com.rameshta.splitframe.export.MediaMetadataFailure
import com.rameshta.splitframe.export.MixedMediaMetadataReader
import com.rameshta.splitframe.export.MixedMediaMetadataResult
import com.rameshta.splitframe.export.VideoExportWorker
import com.rameshta.splitframe.export.isLiveVideoExportState
import com.rameshta.splitframe.export.videoExportRecoveryDelayMillis
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class VideoProjectSessionArgs(
    val projectId: String,
    val createIfMissing: Boolean,
)

class VideoMergeViewModel(
    private val videoProjectStore: VideoProjectStore,
    private val mixedMediaMetadataReader: MixedMediaMetadataReader,
    private val workManager: WorkManager,
    private val sessionArgs: VideoProjectSessionArgs,
) : ViewModel() {
    private val undoStack = ArrayDeque<VideoMergeProject>()
    private val redoStack = ArrayDeque<VideoMergeProject>()
    private var metadataJob: Job? = null
    private var exportObserveJob: Job? = null
    private var exportEnqueueJob: Job? = null
    private var transformUndoExpiryJob: Job? = null
    private val transformUndoSession = TransformUndoSession()
    private val persistenceMutex = Mutex()
    private val _state = MutableStateFlow(VideoMergeState())
    val state: StateFlow<VideoMergeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existingProject = videoProjectStore.inspect(sessionArgs.projectId)
            val openedProject = videoProjectStore.openProject(
                projectId = sessionArgs.projectId,
                createIfMissing = sessionArgs.createIfMissing,
            )
            if (openedProject == null) {
                _state.update { it.copy(status = VideoEditorStatus.ProjectUnavailable) }
                return@launch
            }
            val project = openedProject.asVideoMergeProject()
            _state.update {
                it.copy(
                    project = project,
                    isProjectPersisted = existingProject is VideoProjectReadResult.Ready,
                    selectedClipIndex = project.selectedCellIndex ?: 0,
                    status = VideoEditorStatus.Initial,
                )
            }
            observeExportWork(project.id)
            reconcileRestoredExportWork(project.id)
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
            VideoMergeAction.Play -> playPreview()
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
        persist(updated)
        _state.update { it.copy(project = updated) }
        val orderedClipIndex = project.orderedVideoCellIndexes().indexOf(safeCell)
        VideoLayoutMath.mergedVideoClipStartMs(project.orderedClips, orderedClipIndex)?.let { startMs ->
            if (!_state.value.isExporting) reduce(VideoMergeResultEvent.Paused)
            reduce(VideoMergeResultEvent.Seeked(startMs))
        }
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
        val updatedProject = _state.value.project ?: return
        val orderedClipIndex = updatedProject.orderedVideoCellIndexes().indexOf(cellIndex)
        VideoLayoutMath.mergedVideoClipStartMs(updatedProject.orderedClips, orderedClipIndex)?.let { clipStartMs ->
            if (!_state.value.isExporting) reduce(VideoMergeResultEvent.Paused)
            reduce(VideoMergeResultEvent.Seeked(clipStartMs))
        }
    }

    private fun updateTransform(cellIndex: Int, transform: ImageTransform, trackUndo: Boolean) {
        val shouldTrackUndo = transformUndoSession.onTransform(
            cellIndex = cellIndex,
            gestureFinished = trackUndo,
        )
        transformUndoExpiryJob?.cancel()
        transformUndoExpiryJob = if (trackUndo) {
            null
        } else {
            viewModelScope.launch {
                delay(TransformGestureIdleMillis)
                transformUndoSession.expire(cellIndex)
            }
        }
        updateMedia(cellIndex, shouldTrackUndo) { it.withTransform(transform) }
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
        persist(edgeToEdgeProject)
    }

    private fun undoEdit() {
        val current = _state.value.project ?: return
        val previous = undoStack.removeLastOrNull()?.copy(spacingDp = VideoLayoutMath.EdgeToEdgeSpacingDp) ?: return
        redoStack.addLast(current.copy(spacingDp = VideoLayoutMath.EdgeToEdgeSpacingDp))
        reduce(VideoMergeResultEvent.ProjectChanged(previous))
        persist(previous)
    }

    private fun redoEdit() {
        val current = _state.value.project ?: return
        val next = redoStack.removeLastOrNull()?.copy(spacingDp = VideoLayoutMath.EdgeToEdgeSpacingDp) ?: return
        undoStack.addLast(current.copy(spacingDp = VideoLayoutMath.EdgeToEdgeSpacingDp))
        reduce(VideoMergeResultEvent.ProjectChanged(next))
        persist(next)
    }

    private fun resetProject() {
        val state = _state.value
        val current = state.project ?: return
        val reset = VideoMergeProject(id = current.id)
        undoStack.clear()
        redoStack.clear()
        reduce(VideoMergeResultEvent.ProjectChanged(reset))
        if (!state.isProjectPersisted) return
        viewModelScope.launch {
            persistenceMutex.withLock { videoProjectStore.reset(current.id) }
        }
    }

    private fun playPreview() {
        val state = _state.value
        val project = state.project ?: return
        if (state.isExporting) return
        val durationMs = VideoLayoutMath.outputDurationForMergedVideos(project.orderedClips)
        if (durationMs <= 0L) return
        if (state.playbackPositionMs >= durationMs) {
            reduce(VideoMergeResultEvent.Seeked(0L))
        }
        reduce(VideoMergeResultEvent.Playing)
    }

    private fun startExport() {
        val project = _state.value.project ?: return
        if (_state.value.isExporting || exportEnqueueJob?.isActive == true) return
        if (!project.isComplete) {
            reduce(VideoMergeResultEvent.Failed(R.string.video_missing_clips))
            return
        }
        if (project.clips.values.any { !it.hasValidTrim }) {
            reduce(VideoMergeResultEvent.Failed(R.string.video_invalid_trim))
            return
        }
        val enqueueJob = viewModelScope.launch {
            var queuedWorkId: String? = null
            try {
                val saved = persistenceMutex.withLock {
                    videoProjectStore.save(project.withAvailableAudio())
                }
                if (!saved) {
                    reduce(VideoMergeResultEvent.Failed(R.string.video_export_queue_failed))
                    return@launch
                }
                markProjectPersisted(project.id)
                val workName = VideoExportWorker.uniqueWorkName(project.id)
                val request = OneTimeWorkRequestBuilder<VideoExportWorker>()
                    .setId(UUID.randomUUID())
                    .setInputData(workDataOf(VideoExportWorker.ProjectIdKey to project.id))
                    .build()
                queuedWorkId = request.id.toString()
                videoProjectStore.setExportWork(
                    VideoExportWorkEntity(
                        projectId = project.id,
                        workId = queuedWorkId,
                        state = VideoExportWorker.StateQueued,
                        progress = 0f,
                        outputUri = null,
                        errorMessage = null,
                        updatedAtMillis = System.currentTimeMillis(),
                    ),
                )
                val operation = workManager.enqueueUniqueWork(
                    workName,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
                withContext(Dispatchers.IO) { operation.result.get() }
                reduce(VideoMergeResultEvent.ExportQueued(queuedWorkId))
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                queuedWorkId?.let { workId ->
                    videoProjectStore.updateExportWorkIfCurrent(
                        VideoExportWorkEntity(
                            projectId = project.id,
                            workId = workId,
                            state = VideoExportWorker.StateFailed,
                            progress = 0f,
                            outputUri = null,
                            errorMessage = ExportSchedulingFailure,
                            updatedAtMillis = System.currentTimeMillis(),
                        ),
                        expectedStates = listOf(VideoExportWorker.StateQueued),
                    )
                }
                reduce(VideoMergeResultEvent.Failed(R.string.video_export_queue_failed))
            }
        }
        exportEnqueueJob = enqueueJob
        enqueueJob.invokeOnCompletion {
            if (exportEnqueueJob === enqueueJob) exportEnqueueJob = null
        }
    }

    private fun cancelExport() {
        val project = _state.value.project ?: return
        val workId = _state.value.exportWorkId ?: return
        val workUuid = runCatching { UUID.fromString(workId) }.getOrNull() ?: return
        viewModelScope.launch {
            val cancelled = videoProjectStore.updateExportWorkIfCurrent(
                VideoExportWorkEntity(
                    projectId = project.id,
                    workId = workId,
                    state = VideoExportWorker.StateCancelled,
                    progress = _state.value.exportProgress,
                    outputUri = null,
                    errorMessage = null,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
                expectedStates = listOf(VideoExportWorker.StateQueued, VideoExportWorker.StateRunning),
            )
            if (cancelled) {
                workManager.cancelWorkById(workUuid)
                reduce(VideoMergeResultEvent.ExportCancelled(workId))
            }
        }
    }

    private fun observeExportWork(projectId: String) {
        exportObserveJob?.cancel()
        exportObserveJob = viewModelScope.launch {
            videoProjectStore.observeExportWork(projectId).collect { work ->
                work?.toVideoMergeResultEvent()?.let(::reduce)
            }
        }
    }

    private suspend fun reconcileRestoredExportWork(projectId: String) {
        var stored = videoProjectStore.getExportWork(projectId) ?: return
        if (stored.state !in listOf(VideoExportWorker.StateQueued, VideoExportWorker.StateRunning)) return
        val recoveryDelay = videoExportRecoveryDelayMillis(
            updatedAtMillis = stored.updatedAtMillis,
            nowMillis = System.currentTimeMillis(),
            graceMillis = ExportRecoveryGraceMillis,
        )
        if (recoveryDelay > 0L) {
            delay(recoveryDelay)
            val latest = videoProjectStore.getExportWork(projectId) ?: return
            if (
                latest.workId != stored.workId ||
                latest.state !in listOf(VideoExportWorker.StateQueued, VideoExportWorker.StateRunning)
            ) {
                return
            }
            stored = latest
        }
        val workId = stored.workId ?: return
        val uuid = runCatching { UUID.fromString(workId) }.getOrNull()
        val workInfo = if (uuid == null) {
            null
        } else {
            try {
                withContext(Dispatchers.IO) { workManager.getWorkInfoById(uuid).get() }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // A transient WorkManager lookup failure must not invalidate durable state.
                return
            }
        }
        if (workInfo?.state.isLiveVideoExportState()) return
        val recoveredState = if (workInfo?.state == WorkInfo.State.CANCELLED) {
            VideoExportWorker.StateCancelled
        } else {
            VideoExportWorker.StateFailed
        }
        videoProjectStore.updateExportWorkIfCurrent(
            stored.copy(
                state = recoveredState,
                progress = 0f,
                outputUri = null,
                errorMessage = if (recoveredState == VideoExportWorker.StateFailed) {
                    ExportRecoveryFailure
                } else {
                    null
                },
                updatedAtMillis = System.currentTimeMillis(),
            ),
            expectedStates = listOf(VideoExportWorker.StateQueued, VideoExportWorker.StateRunning),
        )
    }

    private fun persist(project: VideoMergeProject) {
        if (project.mediaByCell.isEmpty() && !_state.value.isProjectPersisted) return
        viewModelScope.launch {
            val saved = persistenceMutex.withLock { videoProjectStore.save(project) }
            if (saved) markProjectPersisted(project.id)
        }
    }

    private fun markProjectPersisted(projectId: String) {
        _state.update { state ->
            if (state.project?.id == projectId) {
                state.copy(isProjectPersisted = true)
            } else {
                state
            }
        }
    }

    private fun reduce(result: VideoMergeResultEvent) {
        _state.update { state ->
            when (result) {
                is VideoMergeResultEvent.ProjectChanged -> state.copy(
                    project = result.project,
                    selectedClipIndex = result.project.selectedCellIndex ?: state.selectedClipIndex,
                    playbackPositionMs = state.playbackPositionMs.coerceIn(
                        0L,
                        VideoLayoutMath.outputDurationForMergedVideos(result.project.orderedClips),
                    ),
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
                is VideoMergeResultEvent.Seeked -> state.copy(
                    playbackPositionMs = result.positionMs.coerceIn(
                        0L,
                        state.project?.let { VideoLayoutMath.outputDurationForMergedVideos(it.orderedClips) } ?: 0L,
                    ),
                )
                is VideoMergeResultEvent.ExportQueued -> state.copy(
                    status = VideoEditorStatus.ExportQueued,
                    isExporting = true,
                    exportProgress = 0f,
                    exportWorkId = result.workId,
                    exportResult = null,
                    error = null,
                )
                is VideoMergeResultEvent.ExportProgressChanged -> if (state.acceptsExportWork(result.workId)) {
                    state.copy(
                        status = VideoEditorStatus.Exporting,
                        isExporting = true,
                        exportWorkId = result.workId ?: state.exportWorkId,
                        exportProgress = result.progress.coerceIn(0f, 1f),
                    )
                } else {
                    state
                }
                is VideoMergeResultEvent.ExportFinished -> if (state.acceptsExportWork(result.workId)) {
                    state.copy(
                        status = if (result.result is ExportResult.Success) {
                            VideoEditorStatus.ExportCompleted
                        } else {
                            VideoEditorStatus.ExportFailed
                        },
                        isExporting = false,
                        exportWorkId = result.workId ?: state.exportWorkId,
                        exportProgress = if (result.result is ExportResult.Success) 1f else state.exportProgress,
                        exportResult = result.result,
                    )
                } else {
                    state
                }
                is VideoMergeResultEvent.ExportCancelled -> if (state.acceptsExportWork(result.workId)) {
                    state.copy(
                        status = VideoEditorStatus.ExportCancelled,
                        isExporting = false,
                        exportWorkId = result.workId ?: state.exportWorkId,
                        exportProgress = 0f,
                    )
                } else {
                    state
                }
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
            selectedCellIndex = selectedCellIndex?.takeIf { selected ->
                targetTemplate.cells.any { it.index == selected }
            } ?: targetTemplate.cells.firstOrNull()?.index ?: 0,
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

    private fun VideoMergeProject.orderedVideoCellIndexes(): List<Int> {
        val templateIndexes = template.cells.map { it.index }
        val templateMediaIndexes = templateIndexes.filter { mediaByCell[it] is MediaSource.Video }
        val extraMediaIndexes = mediaByCell
            .filterKeys { it !in templateIndexes }
            .filterValues { it is MediaSource.Video }
            .keys
            .sorted()
        return templateMediaIndexes + extraMediaIndexes
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
        const val TransformGestureIdleMillis = 250L
        const val ExportRecoveryGraceMillis = 5_000L
        const val ExportSchedulingFailure = "Could not schedule video export. Try again."
        const val ExportRecoveryFailure = "The previous video export could not be recovered. Export again."
    }
}

internal fun VideoExportWorkEntity.toVideoMergeResultEvent(): VideoMergeResultEvent? =
    when (state) {
        VideoExportWorker.StateQueued -> VideoMergeResultEvent.ExportQueued(workId)
        VideoExportWorker.StateRunning -> VideoMergeResultEvent.ExportProgressChanged(workId, progress)
        VideoExportWorker.StateSucceeded -> VideoMergeResultEvent.ExportFinished(
            workId = workId,
            result = outputUri
                ?.takeIf { it.isNotBlank() }
                ?.let(ExportResult::Success)
                ?: ExportResult.Failure("Saved video is unavailable."),
        )
        VideoExportWorker.StateFailed -> VideoMergeResultEvent.ExportFinished(
            workId = workId,
            result = ExportResult.Failure(errorMessage?.takeIf { it.isNotBlank() } ?: "Video export failed."),
        )
        VideoExportWorker.StateCancelled -> VideoMergeResultEvent.ExportCancelled(workId)
        else -> null
    }

internal fun VideoMergeState.acceptsExportWork(workId: String?): Boolean =
    exportWorkId == null || exportWorkId == workId
