package com.rameshta.splitframe.presentation.merge

import android.graphics.Bitmap
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.ExportResult
import com.rameshta.splitframe.domain.CollageBackgroundStyle
import com.rameshta.splitframe.domain.CollageBorderStyle
import com.rameshta.splitframe.domain.CollageTextLayer
import com.rameshta.splitframe.domain.CropShape
import com.rameshta.splitframe.domain.ImageDimensions
import com.rameshta.splitframe.domain.ImageSource
import com.rameshta.splitframe.domain.ImageTransform
import com.rameshta.splitframe.domain.LayoutTemplate
import com.rameshta.splitframe.domain.MergeProject
import com.rameshta.splitframe.domain.TemplateAspectFilter
import com.rameshta.splitframe.domain.TemplateCollection
import com.rameshta.splitframe.domain.TemplateDiscoveryFilter

sealed interface MergeIntent {
    data class SelectTemplate(val templateId: String) : MergeIntent
    data class UpdateTemplateSearch(val query: String) : MergeIntent
    data class SelectTemplateCollection(val collection: TemplateCollection) : MergeIntent
    data class SelectTemplateAspect(val aspect: TemplateAspectFilter) : MergeIntent
    data class SelectTemplateMediaCount(val mediaCount: Int?) : MergeIntent
    data class ToggleTemplateFavorite(val templateId: String) : MergeIntent
    data object ResetTemplateDiscovery : MergeIntent
    data object ClearTemplateFavoriteError : MergeIntent
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
    data class UpdateBackgroundStyle(val style: CollageBackgroundStyle) : MergeIntent
    data class UpdateBorderWidth(val dp: Float) : MergeIntent
    data class UpdateBorderStyle(val style: CollageBorderStyle) : MergeIntent
    data class UpdateCropShape(val cellIndex: Int, val shape: CropShape) : MergeIntent
    data class AddTextLayer(val initialText: String) : MergeIntent
    data class UpdateTextLayer(val layer: CollageTextLayer) : MergeIntent
    data class DuplicateTextLayer(val layerId: String) : MergeIntent
    data class DeleteTextLayer(val layerId: String) : MergeIntent
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
    data class UpdateTemplateSearch(val query: String) : MergeAction
    data class SelectTemplateCollection(val collection: TemplateCollection) : MergeAction
    data class SelectTemplateAspect(val aspect: TemplateAspectFilter) : MergeAction
    data class SelectTemplateMediaCount(val mediaCount: Int?) : MergeAction
    data class ToggleTemplateFavorite(val templateId: String) : MergeAction
    data object ResetTemplateDiscovery : MergeAction
    data object ClearTemplateFavoriteError : MergeAction
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
    data class UpdateBackgroundStyle(val style: CollageBackgroundStyle) : MergeAction
    data class UpdateBorderWidth(val dp: Float) : MergeAction
    data class UpdateBorderStyle(val style: CollageBorderStyle) : MergeAction
    data class UpdateCropShape(val cellIndex: Int, val shape: CropShape) : MergeAction
    data class AddTextLayer(val initialText: String) : MergeAction
    data class UpdateTextLayer(val layer: CollageTextLayer) : MergeAction
    data class DuplicateTextLayer(val layerId: String) : MergeAction
    data class DeleteTextLayer(val layerId: String) : MergeAction
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
    val unreadableSourceCells: Set<Int> = emptySet(),
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportResult: ExportResult? = null,
    val error: Int? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val templateDiscovery: TemplateDiscoveryState = TemplateDiscoveryState(),
    val blurredBackground: Bitmap? = null,
)

data class TemplateDiscoveryState(
    val filter: TemplateDiscoveryFilter = TemplateDiscoveryFilter(),
    val favoriteTemplateIds: List<String> = emptyList(),
    val recentTemplateIds: List<String> = emptyList(),
    val pendingFavoriteIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val favoriteErrorTemplateId: String? = null,
    val favoriteErrorVersion: Long = 0L,
)
