package com.rameshta.splitframe.presentation.video

import androidx.annotation.StringRes
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.ExportResult
import com.rameshta.splitframe.domain.ImageTransform
import com.rameshta.splitframe.domain.MediaDurationMode
import com.rameshta.splitframe.domain.MediaSource
import com.rameshta.splitframe.domain.VideoCanvasAspectRatio
import com.rameshta.splitframe.domain.VideoClip
import com.rameshta.splitframe.domain.VideoFitMode
import com.rameshta.splitframe.domain.VideoMergeProject

sealed interface VideoMergeIntent {
    data class SelectMedia(val uris: List<String>) : VideoMergeIntent
    data class AssignMedia(val cellIndex: Int, val uri: String) : VideoMergeIntent
    data class ReplaceMedia(val cellIndex: Int, val uri: String) : VideoMergeIntent
    data class SelectVideos(val uris: List<String>) : VideoMergeIntent
    data class AssignVideo(val cellIndex: Int, val uri: String) : VideoMergeIntent
    data class ReplaceVideo(val cellIndex: Int, val uri: String) : VideoMergeIntent
    data class RemoveVideo(val cellIndex: Int) : VideoMergeIntent
    data class SelectClip(val cellIndex: Int) : VideoMergeIntent
    data object SwapVideos : VideoMergeIntent
    data class SwapCells(val firstCellIndex: Int, val secondCellIndex: Int) : VideoMergeIntent
    data object AutoArrange : VideoMergeIntent
    data class ReorderMedia(val fromCellIndex: Int, val toCellIndex: Int) : VideoMergeIntent
    data class UpdateTrim(val cellIndex: Int, val startMs: Long, val endMs: Long) : VideoMergeIntent
    data class UpdateVideoTransform(
        val cellIndex: Int,
        val transform: ImageTransform,
        val trackUndo: Boolean = true,
    ) : VideoMergeIntent
    data class ResetVideoTransform(val cellIndex: Int) : VideoMergeIntent
    data class SelectCanvasAspectRatio(val aspectRatio: VideoCanvasAspectRatio) : VideoMergeIntent
    data class SelectPrimaryAudio(val mediaId: String?) : VideoMergeIntent
    data class SelectDurationMode(val mode: MediaDurationMode) : VideoMergeIntent
    data class SelectExportResolution(val resolution: ExportResolution) : VideoMergeIntent
    data class SelectFitMode(val cellIndex: Int, val fitMode: VideoFitMode) : VideoMergeIntent
    data object Play : VideoMergeIntent
    data object Pause : VideoMergeIntent
    data class SeekTo(val positionMs: Long) : VideoMergeIntent
    data object RetryPreview : VideoMergeIntent
    data object StartExport : VideoMergeIntent
    data object CancelExport : VideoMergeIntent
    data object RetryExport : VideoMergeIntent
    data object DismissError : VideoMergeIntent
    data object ResetProject : VideoMergeIntent
    data object UndoEdit : VideoMergeIntent
    data object RedoEdit : VideoMergeIntent
    data object DismissExportResult : VideoMergeIntent
}

sealed interface VideoMergeAction {
    data class SelectMedia(val uris: List<String>) : VideoMergeAction
    data class AssignMedia(val cellIndex: Int, val uri: String) : VideoMergeAction
    data class ReplaceMedia(val cellIndex: Int, val uri: String) : VideoMergeAction
    data class RemoveMedia(val cellIndex: Int) : VideoMergeAction
    data class SelectClip(val cellIndex: Int) : VideoMergeAction
    data object SwapVideos : VideoMergeAction
    data class SwapCells(val firstCellIndex: Int, val secondCellIndex: Int) : VideoMergeAction
    data object AutoArrange : VideoMergeAction
    data class ReorderMedia(val fromCellIndex: Int, val toCellIndex: Int) : VideoMergeAction
    data class UpdateTrim(val cellIndex: Int, val startMs: Long, val endMs: Long) : VideoMergeAction
    data class UpdateVideoTransform(
        val cellIndex: Int,
        val transform: ImageTransform,
        val trackUndo: Boolean = true,
    ) : VideoMergeAction
    data class ResetVideoTransform(val cellIndex: Int) : VideoMergeAction
    data class SelectCanvasAspectRatio(val aspectRatio: VideoCanvasAspectRatio) : VideoMergeAction
    data class SelectPrimaryAudio(val mediaId: String?) : VideoMergeAction
    data class SelectDurationMode(val mode: MediaDurationMode) : VideoMergeAction
    data class SelectExportResolution(val resolution: ExportResolution) : VideoMergeAction
    data class SelectFitMode(val cellIndex: Int, val fitMode: VideoFitMode) : VideoMergeAction
    data object Play : VideoMergeAction
    data object Pause : VideoMergeAction
    data class SeekTo(val positionMs: Long) : VideoMergeAction
    data object RetryPreview : VideoMergeAction
    data object StartExport : VideoMergeAction
    data object CancelExport : VideoMergeAction
    data object RetryExport : VideoMergeAction
    data object DismissError : VideoMergeAction
    data object ResetProject : VideoMergeAction
    data object UndoEdit : VideoMergeAction
    data object RedoEdit : VideoMergeAction
    data object DismissExportResult : VideoMergeAction
}

sealed interface VideoMergeResultEvent {
    data class ProjectChanged(val project: VideoMergeProject) : VideoMergeResultEvent
    data class SelectedClipChanged(val cellIndex: Int) : VideoMergeResultEvent
    data object MediaSelectionStarted : VideoMergeResultEvent
    data object MetadataReadingStarted : VideoMergeResultEvent
    data object PreviewPreparing : VideoMergeResultEvent
    data object PreviewReady : VideoMergeResultEvent
    data object Buffering : VideoMergeResultEvent
    data object Playing : VideoMergeResultEvent
    data object Paused : VideoMergeResultEvent
    data class Seeked(val positionMs: Long) : VideoMergeResultEvent
    data class ExportQueued(val workId: String?) : VideoMergeResultEvent
    data class ExportProgressChanged(val workId: String?, val progress: Float) : VideoMergeResultEvent
    data class ExportFinished(val workId: String?, val result: ExportResult) : VideoMergeResultEvent
    data class ExportCancelled(val workId: String?) : VideoMergeResultEvent
    data class Failed(@StringRes val messageRes: Int) : VideoMergeResultEvent
    data object ErrorCleared : VideoMergeResultEvent
    data object ExportResultDismissed : VideoMergeResultEvent
}

enum class VideoEditorStatus {
    Initial,
    SelectingMedia,
    ReadingMetadata,
    PreparingPreview,
    PreviewReady,
    Buffering,
    Playing,
    Paused,
    ExportQueued,
    Exporting,
    ExportCompleted,
    ExportFailed,
    ExportCancelled,
    ProjectUnavailable,
    RecoverableMediaError,
    UnsupportedMedia,
    InsufficientStorage,
}

data class VideoMergeState(
    val project: VideoMergeProject? = null,
    val isProjectPersisted: Boolean = false,
    val selectedClipIndex: Int = 0,
    val status: VideoEditorStatus = VideoEditorStatus.Initial,
    val playbackPositionMs: Long = 0L,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportWorkId: String? = null,
    val exportResult: ExportResult? = null,
    @StringRes val error: Int? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    val selectedMedia: MediaSource? get() = project?.mediaByCell?.get(selectedClipIndex)
    val selectedClip: VideoClip? get() = (selectedMedia as? MediaSource.Video)?.clip
}
