package com.example.splitframe.presentation.single

import com.example.splitframe.domain.ImageSource
import com.example.splitframe.domain.SingleImageOutputFormat
import com.example.splitframe.domain.SingleImagePlanResult
import com.example.splitframe.domain.SingleImageResizePreset
import com.example.splitframe.domain.SingleImageResizeRequest
import com.example.splitframe.export.SingleImageProcessResult

data class SingleImageState(
    val source: ImageSource.LocalUri? = null,
    val request: SingleImageResizeRequest = SingleImageResizeRequest(),
    val planResult: SingleImagePlanResult? = null,
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val result: SingleImageProcessResult.Success? = null,
    val error: String? = null,
)

sealed interface SingleImageIntent {
    data class SelectImage(val source: ImageSource.LocalUri) : SingleImageIntent
    data class SelectPreset(val preset: SingleImageResizePreset) : SingleImageIntent
    data class SelectOutputFormat(val format: SingleImageOutputFormat) : SingleImageIntent
    data class UpdateJpegQuality(val quality: Int) : SingleImageIntent
    data class UpdateCustomWidth(val widthPx: Int?) : SingleImageIntent
    data class UpdateCustomHeight(val heightPx: Int?) : SingleImageIntent
    data class SetAspectRatioLocked(val locked: Boolean) : SingleImageIntent
    data object Process : SingleImageIntent
    data object Cancel : SingleImageIntent
    data object ClearError : SingleImageIntent
    data object ClearResult : SingleImageIntent
}
