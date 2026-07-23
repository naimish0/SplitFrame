package com.rameshta.splitframe.presentation.single

import com.rameshta.splitframe.domain.ImageSource
import com.rameshta.splitframe.domain.ExportContentMode
import com.rameshta.splitframe.domain.SingleImageOutputFormat
import com.rameshta.splitframe.domain.SingleImagePlanResult
import com.rameshta.splitframe.domain.SingleImageResizePreset
import com.rameshta.splitframe.domain.SingleImageResizePlan
import com.rameshta.splitframe.domain.SingleImageResizeRequest
import com.rameshta.splitframe.domain.TargetSizeUnit
import com.rameshta.splitframe.domain.ImageMetadataPolicy
import com.rameshta.splitframe.domain.SavedResizePreset
import com.rameshta.splitframe.export.SingleImageProcessResult

data class SingleImageState(
    val source: ImageSource.LocalUri? = null,
    val sourceDimensions: com.rameshta.splitframe.domain.ImageDimensions? = null,
    val batchSources: List<ImageSource.LocalUri> = emptyList(),
    val request: SingleImageResizeRequest = SingleImageResizeRequest(),
    val planResult: SingleImagePlanResult? = null,
    val previewPlan: SingleImageResizePlan? = null,
    val isPlanning: Boolean = false,
    val isProcessing: Boolean = false,
    val isCancelling: Boolean = false,
    val progress: Float = 0f,
    val result: SingleImageProcessResult.Success? = null,
    val error: String? = null,
    val persistenceWarning: String? = null,
    val savedPresets: List<SavedResizePreset> = emptyList(),
    val batchSummary: BatchExportSummary? = null,
)

data class BatchExportSummary(
    val completed: Int,
    val total: Int,
    val failures: Int,
    val savedUris: List<String>,
)

sealed interface SingleImageIntent {
    data class SelectImage(val source: ImageSource.LocalUri) : SingleImageIntent
    data class SelectBatchImages(val sources: List<ImageSource.LocalUri>) : SingleImageIntent
    data class SelectPreset(val preset: SingleImageResizePreset) : SingleImageIntent
    data class SelectOutputFormat(val format: SingleImageOutputFormat) : SingleImageIntent
    data class UpdateEncodingQuality(val quality: Int) : SingleImageIntent
    data class UpdateResizePercent(val percent: Int?) : SingleImageIntent
    data class UpdateTargetSizeValue(val value: Int?) : SingleImageIntent
    data class SelectTargetSizeUnit(val unit: TargetSizeUnit) : SingleImageIntent
    data class SelectMetadataPolicy(val policy: ImageMetadataPolicy) : SingleImageIntent
    data class SaveCurrentPreset(val name: String) : SingleImageIntent
    data class ApplySavedPreset(val name: String) : SingleImageIntent
    data class DeleteSavedPreset(val name: String) : SingleImageIntent
    data class UpdateCustomWidth(val widthPx: Int?) : SingleImageIntent
    data class UpdateCustomHeight(val heightPx: Int?) : SingleImageIntent
    data class SetAspectRatioLocked(val locked: Boolean) : SingleImageIntent
    data class SelectContentMode(val contentMode: ExportContentMode) : SingleImageIntent
    data object Process : SingleImageIntent
    data object ProcessBatch : SingleImageIntent
    data object Cancel : SingleImageIntent
    data object ClearError : SingleImageIntent
    data object ClearResult : SingleImageIntent
}
