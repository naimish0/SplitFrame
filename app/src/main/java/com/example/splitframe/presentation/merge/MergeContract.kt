package com.example.splitframe.presentation.merge

import com.example.splitframe.domain.ExportResolution
import com.example.splitframe.domain.ExportResult
import com.example.splitframe.domain.ImageDimensions
import com.example.splitframe.domain.ImageSource
import com.example.splitframe.domain.ImageTransform
import com.example.splitframe.domain.LayoutTemplate
import com.example.splitframe.domain.MergeProject

sealed interface MergeIntent {
    data class SelectTemplate(val templateId: String) : MergeIntent
    data class AssignImage(val cellIndex: Int, val source: ImageSource) : MergeIntent
    data class AssignImages(val sources: List<ImageSource>) : MergeIntent
    data class RemoveImage(val cellIndex: Int) : MergeIntent
    data class ReplaceImage(val cellIndex: Int, val source: ImageSource) : MergeIntent
    data class ReorderImages(val fromIndex: Int, val toIndex: Int) : MergeIntent
    data class SwapCells(val a: Int, val b: Int) : MergeIntent
    data class UpdateImageTransform(
        val cellIndex: Int,
        val transform: ImageTransform,
        val trackUndo: Boolean = true,
    ) : MergeIntent
    data class ResetImageTransform(val cellIndex: Int) : MergeIntent
    data class UpdateSpacing(val dp: Float) : MergeIntent
    data class UpdateCornerRadius(val dp: Float) : MergeIntent
    data class UpdateBackgroundColor(val argb: ULong) : MergeIntent
    data class UpdateBeforeAfterSlider(val position: Float) : MergeIntent
    data class SelectExportResolution(val resolution: ExportResolution) : MergeIntent
    data object AutoArrange : MergeIntent
    data object UndoEdit : MergeIntent
    data object RedoEdit : MergeIntent
    data object Export : MergeIntent
    data object Reset : MergeIntent
    data object ClearError : MergeIntent
    data object DismissExportResult : MergeIntent
}

sealed interface MergeAction {
    data class SelectTemplate(val templateId: String) : MergeAction
    data class AssignImage(val cellIndex: Int, val source: ImageSource) : MergeAction
    data class AssignImages(val sources: List<ImageSource>) : MergeAction
    data class RemoveImage(val cellIndex: Int) : MergeAction
    data class ReplaceImage(val cellIndex: Int, val source: ImageSource) : MergeAction
    data class ReorderImages(val fromIndex: Int, val toIndex: Int) : MergeAction
    data class SwapCells(val a: Int, val b: Int) : MergeAction
    data class UpdateImageTransform(
        val cellIndex: Int,
        val transform: ImageTransform,
        val trackUndo: Boolean = true,
    ) : MergeAction
    data class ResetImageTransform(val cellIndex: Int) : MergeAction
    data class UpdateSpacing(val dp: Float) : MergeAction
    data class UpdateCornerRadius(val dp: Float) : MergeAction
    data class UpdateBackgroundColor(val argb: ULong) : MergeAction
    data class UpdateBeforeAfterSlider(val position: Float) : MergeAction
    data class SelectExportResolution(val resolution: ExportResolution) : MergeAction
    data object AutoArrange : MergeAction
    data object UndoEdit : MergeAction
    data object RedoEdit : MergeAction
    data object Export : MergeAction
    data object Reset : MergeAction
    data object ClearError : MergeAction
    data object DismissExportResult : MergeAction
}

sealed interface MergeResultEvent {
    data class ProjectChanged(val project: MergeProject) : MergeResultEvent
    data class ImageDimensionsLoaded(val cellIndex: Int, val dimensions: ImageDimensions) : MergeResultEvent
    data class ExportStarted(val progress: Float = 0f) : MergeResultEvent
    data class ExportFinished(val result: ExportResult) : MergeResultEvent
    data class Failed(val messageRes: Int) : MergeResultEvent
    data object ErrorCleared : MergeResultEvent
    data object ExportResultDismissed : MergeResultEvent
}

data class MergeState(
    val availableTemplates: List<LayoutTemplate> = emptyList(),
    val project: MergeProject? = null,
    val sourceDimensions: Map<Int, ImageDimensions> = emptyMap(),
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportResult: ExportResult? = null,
    val error: Int? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)
