package com.example.splitframe.presentation.single

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitframe.domain.SingleImagePlanError
import com.example.splitframe.domain.SingleImagePlanResult
import com.example.splitframe.domain.SingleImageResizeRequest
import com.example.splitframe.export.SingleImageProcessingRepository
import com.example.splitframe.export.SingleImageProcessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SingleImageViewModel(
    private val repository: SingleImageProcessingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SingleImageState())
    val state: StateFlow<SingleImageState> = _state.asStateFlow()
    private var processJob: Job? = null

    fun process(intent: SingleImageIntent) {
        when (intent) {
            is SingleImageIntent.SelectImage -> {
                _state.update {
                    it.copy(
                        source = intent.source,
                        result = null,
                        error = null,
                    )
                }
                refreshPlan()
            }
            is SingleImageIntent.SelectPreset -> updateRequest { it.copy(preset = intent.preset) }
            is SingleImageIntent.SelectOutputFormat -> updateRequest { it.copy(outputFormat = intent.format) }
            is SingleImageIntent.UpdateJpegQuality -> updateRequest { it.copy(jpegQuality = intent.quality.coerceIn(60, 100)) }
            is SingleImageIntent.UpdateCustomWidth -> updateRequest { it.copy(customWidthPx = intent.widthPx) }
            is SingleImageIntent.UpdateCustomHeight -> updateRequest { it.copy(customHeightPx = intent.heightPx) }
            is SingleImageIntent.SetAspectRatioLocked -> updateRequest { it.copy(lockAspectRatio = intent.locked) }
            SingleImageIntent.Process -> processImage()
            SingleImageIntent.Cancel -> cancelProcessing()
            SingleImageIntent.ClearError -> _state.update { it.copy(error = null) }
            SingleImageIntent.ClearResult -> _state.update { it.copy(result = null) }
        }
    }

    private fun updateRequest(transform: (SingleImageResizeRequest) -> SingleImageResizeRequest) {
        _state.update { it.copy(request = transform(it.request), result = null, error = null) }
        refreshPlan()
    }

    private fun refreshPlan() {
        val source = _state.value.source ?: return
        val request = _state.value.request
        viewModelScope.launch {
            val plan = withContext(Dispatchers.IO) {
                repository.plan(source, request)
            }
            _state.update { it.copy(planResult = plan, error = plan.userErrorOrNull()) }
        }
    }

    private fun processImage() {
        val source = _state.value.source ?: return
        if (_state.value.isProcessing || processJob?.isActive == true) return
        val plan = _state.value.planResult
        if (plan !is SingleImagePlanResult.Valid) {
            _state.update { it.copy(error = plan.userErrorOrNull() ?: "Choose a supported output size first.") }
            return
        }
        val request = _state.value.request
        _state.update { it.copy(isProcessing = true, progress = 0f, result = null, error = null) }
        processJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                repository.process(source, request) { progress ->
                    _state.update { it.copy(progress = progress.coerceIn(0f, 1f)) }
                }
            }
            _state.update { state ->
                when (result) {
                    is SingleImageProcessResult.Success -> state.copy(
                        isProcessing = false,
                        progress = 1f,
                        result = result,
                        error = null,
                    )
                    is SingleImageProcessResult.Failure -> state.copy(
                        isProcessing = false,
                        error = result.reason,
                    )
                }
            }
            processJob = null
        }
    }

    private fun cancelProcessing() {
        processJob?.cancel()
        processJob = null
        _state.update {
            it.copy(
                isProcessing = false,
                progress = 0f,
                error = "Processing cancelled.",
            )
        }
    }

    private fun SingleImagePlanResult?.userErrorOrNull(): String? =
        when (this) {
            is SingleImagePlanResult.Invalid -> when (reason) {
                SingleImagePlanError.InvalidDimensions -> "This image could not be read."
                SingleImagePlanError.OutputTooLarge -> "The requested output size is too large for this device."
            }
            else -> null
        }
}
