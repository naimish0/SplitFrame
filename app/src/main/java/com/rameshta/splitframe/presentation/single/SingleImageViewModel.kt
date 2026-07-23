package com.rameshta.splitframe.presentation.single

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.rameshta.splitframe.data.DeviceWallpaperDimensionsProvider
import com.rameshta.splitframe.data.ProjectStore
import com.rameshta.splitframe.domain.ImageDimensions
import com.rameshta.splitframe.domain.ImageMetadataPolicy
import com.rameshta.splitframe.domain.ExportContentMode
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.SingleImageExportSettings
import com.rameshta.splitframe.domain.SingleImagePlanError
import com.rameshta.splitframe.domain.SingleImageOutputFormat
import com.rameshta.splitframe.domain.SingleImageOutputMetadata
import com.rameshta.splitframe.domain.SingleImagePlanResult
import com.rameshta.splitframe.domain.SingleImageResizePreset
import com.rameshta.splitframe.domain.SingleImageResizeRequest
import com.rameshta.splitframe.domain.SavedResizePreset
import com.rameshta.splitframe.domain.matches
import com.rameshta.splitframe.export.SingleImageProcessResult
import com.rameshta.splitframe.export.SingleImageProcessingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class SingleImageViewModel(
    private val repository: SingleImageProcessingRepository,
    private val projectStore: ProjectStore,
    private val wallpaperDimensionsProvider: DeviceWallpaperDimensionsProvider,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val restoredSource = savedStateHandle.get<String>(SourceSavedStateKey)
        ?.takeIf { it.length in 2..MaxSavedUriLength && ':' in it }
        ?.let { com.rameshta.splitframe.domain.ImageSource.LocalUri(it) }
    private val restoredBatchSources = savedStateHandle.get<ArrayList<String>>(BatchSourcesSavedStateKey)
        .orEmpty()
        .take(MaxBatchImages)
        .mapNotNull { uri ->
            uri.takeIf { it.length in 2..MaxSavedUriLength && ':' in it }
                ?.let { com.rameshta.splitframe.domain.ImageSource.LocalUri(it) }
        }
    private var pendingRestoredResult = restoredResultSnapshot()
    private val _state = MutableStateFlow(
        SingleImageState(
            source = restoredSource ?: restoredBatchSources.firstOrNull(),
            batchSources = restoredBatchSources,
        ),
    )
    val state: StateFlow<SingleImageState> = _state.asStateFlow()
    private var processJob: Job? = null
    private var planJob: Job? = null
    private var settingsSaveJob: Job? = null
    private var requestRevision = 0L
    private var planGeneration = 0L
    @Volatile
    private var processGeneration = 0L
    private var settingsSaveGeneration = 0L
    private var resolvedWallpaperDimensions: ImageDimensions? = null

    init {
        restoreExportSettings()
        loadSavedPresets()
        loadWallpaperDimensions()
        if (_state.value.source != null) refreshPlan()
    }

    fun process(intent: SingleImageIntent) {
        if (_state.value.isProcessing && intent != SingleImageIntent.Cancel) return
        when (intent) {
            is SingleImageIntent.SelectImage -> {
                planJob?.cancel()
                savedStateHandle[SourceSavedStateKey] = intent.source.uri
                savedStateHandle.remove<ArrayList<String>>(BatchSourcesSavedStateKey)
                clearSavedResult()
                _state.update {
                    it.copy(
                        source = intent.source,
                        sourceDimensions = null,
                        batchSources = emptyList(),
                        batchSummary = null,
                        planResult = null,
                        previewPlan = null,
                        isPlanning = true,
                        result = null,
                        error = null,
                    )
                }
                refreshPlan()
            }
            is SingleImageIntent.SelectBatchImages -> selectBatchImages(intent.sources)
            is SingleImageIntent.SelectPreset -> updateRequest { it.copy(preset = intent.preset) }
            is SingleImageIntent.SelectOutputFormat -> updateRequest { it.copy(outputFormat = intent.format) }
            is SingleImageIntent.UpdateEncodingQuality -> updateRequest(debouncePersistence = true) {
                it.copy(encodingQuality = intent.quality.coerceIn(60, 100))
            }
            is SingleImageIntent.UpdateResizePercent -> updateRequest(debouncePersistence = true) {
                it.copy(resizePercent = intent.percent ?: 0)
            }
            is SingleImageIntent.UpdateTargetSizeValue -> updateRequest(debouncePersistence = true) {
                it.copy(targetSizeValue = intent.value)
            }
            is SingleImageIntent.SelectTargetSizeUnit -> updateRequest {
                it.copy(targetSizeUnit = intent.unit)
            }
            is SingleImageIntent.SelectMetadataPolicy -> updateRequest {
                it.copy(metadataPolicy = intent.policy)
            }
            is SingleImageIntent.SaveCurrentPreset -> saveCurrentPreset(intent.name)
            is SingleImageIntent.ApplySavedPreset -> applySavedPreset(intent.name)
            is SingleImageIntent.DeleteSavedPreset -> deleteSavedPreset(intent.name)
            is SingleImageIntent.UpdateCustomWidth -> updateRequest(debouncePersistence = true) {
                it.copy(
                    customWidthPx = intent.widthPx,
                    customHeightPx = if (it.lockAspectRatio) null else it.customHeightPx,
                )
            }
            is SingleImageIntent.UpdateCustomHeight -> updateRequest(debouncePersistence = true) {
                it.copy(
                    customWidthPx = if (it.lockAspectRatio) null else it.customWidthPx,
                    customHeightPx = intent.heightPx,
                )
            }
            is SingleImageIntent.SetAspectRatioLocked -> updateRequest {
                it.copy(
                    lockAspectRatio = intent.locked,
                    customHeightPx = if (intent.locked && it.customWidthPx != null) null else it.customHeightPx,
                )
            }
            is SingleImageIntent.SelectContentMode -> updateRequest { it.copy(contentMode = intent.contentMode) }
            SingleImageIntent.Process -> processImage()
            SingleImageIntent.ProcessBatch -> processBatch()
            SingleImageIntent.Cancel -> cancelProcessing()
            SingleImageIntent.ClearError -> _state.update { it.copy(error = null) }
            SingleImageIntent.ClearResult -> {
                clearSavedResult()
                _state.update { it.copy(result = null) }
            }
        }
    }

    private fun selectBatchImages(sources: List<com.rameshta.splitframe.domain.ImageSource.LocalUri>) {
        val selected = sources.distinctBy { it.uri }.take(MaxBatchImages)
        if (selected.isEmpty()) return
        planJob?.cancel()
        val first = selected.first()
        savedStateHandle[SourceSavedStateKey] = first.uri
        savedStateHandle[BatchSourcesSavedStateKey] = ArrayList(selected.map { it.uri })
        clearSavedResult()
        _state.update {
            it.copy(
                source = first,
                sourceDimensions = null,
                batchSources = selected,
                batchSummary = null,
                planResult = null,
                previewPlan = null,
                isPlanning = true,
                result = null,
                error = null,
            )
        }
        refreshPlan()
    }

    private fun loadSavedPresets() {
        viewModelScope.launch {
            val presets = try {
                withContext(Dispatchers.IO) { projectStore.getSavedResizePresets() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _state.update { it.copy(persistenceWarning = "Saved presets could not be loaded.") }
                return@launch
            }
            _state.update { it.copy(savedPresets = presets) }
        }
    }

    private fun saveCurrentPreset(rawName: String) {
        val name = rawName.trim()
        if (name.length !in 1..40) {
            _state.update { it.copy(error = "Preset names must contain 1 to 40 characters.") }
            return
        }
        val current = _state.value
        val updated = (
            current.savedPresets.filterNot { it.name.equals(name, ignoreCase = true) } +
                SavedResizePreset(name, SingleImageExportSettings.from(current.request))
            ).takeLast(12)
        persistSavedPresets(updated)
    }

    private fun applySavedPreset(name: String) {
        val saved = _state.value.savedPresets.firstOrNull { it.name == name } ?: return
        requestRevision += 1L
        clearSavedResult()
        _state.update { current ->
            current.copy(
                request = saved.settings.toRequest(current.request.deviceWallpaperDimensions),
                result = null,
                error = null,
            )
        }
        persistExportSettings(_state.value.request, debouncePersistence = false)
        refreshPlan()
    }

    private fun deleteSavedPreset(name: String) {
        persistSavedPresets(_state.value.savedPresets.filterNot { it.name == name })
    }

    private fun persistSavedPresets(presets: List<SavedResizePreset>) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { projectStore.setSavedResizePresets(presets) }
                _state.update { it.copy(savedPresets = presets, persistenceWarning = null) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _state.update { it.copy(persistenceWarning = "Saved presets could not be updated.") }
            }
        }
    }

    private fun updateRequest(
        debouncePersistence: Boolean = false,
        transform: (SingleImageResizeRequest) -> SingleImageResizeRequest,
    ) {
        requestRevision += 1L
        clearSavedResult()
        val updatedRequest = transform(_state.value.request)
        _state.update {
            it.copy(
                request = updatedRequest,
                planResult = null,
                isPlanning = it.source != null,
                result = null,
                error = null,
            )
        }
        persistExportSettings(updatedRequest, debouncePersistence)
        refreshPlan()
    }

    private fun refreshPlan() {
        planGeneration += 1L
        val generation = planGeneration
        planJob?.cancel()
        val source = _state.value.source ?: run {
            _state.update { it.copy(planResult = null, isPlanning = false) }
            return
        }
        val request = _state.value.request
        _state.update { current ->
            if (current.source == source && current.request == request) {
                current.copy(planResult = null, isPlanning = true)
            } else {
                current
            }
        }
        planJob = viewModelScope.launch {
            try {
                val restoredAtInspection = pendingRestoredResult
                val inspection = withContext(Dispatchers.IO) {
                    val sourceDimensions = repository.dimensions(source)
                    val plan = sourceDimensions?.let { dimensions ->
                        com.rameshta.splitframe.domain.SingleImageResizePlanner.plan(dimensions, request)
                    } ?: SingleImagePlanResult.Invalid(SingleImagePlanError.InvalidDimensions)
                    PlanInspection(
                        plan = plan,
                        sourceDimensions = sourceDimensions,
                        restoredOutputReadable = restoredAtInspection?.let { restored ->
                            repository.isReadable(
                                com.rameshta.splitframe.domain.ImageSource.LocalUri(restored.savedUri),
                            )
                        },
                    )
                }
                val plan = inspection.plan
                if (inspection.restoredOutputReadable == false && pendingRestoredResult === restoredAtInspection) {
                    clearSavedResult()
                }
                _state.update { current ->
                    if (
                        planGeneration == generation &&
                        current.source == source &&
                        current.request == request
                    ) {
                        current.copy(
                            planResult = plan,
                            sourceDimensions = inspection.sourceDimensions,
                            previewPlan = (plan as? SingleImagePlanResult.Valid)?.plan ?: current.previewPlan,
                            isPlanning = false,
                            result = current.result ?: if (inspection.restoredOutputReadable == false) {
                                null
                            } else {
                                (plan as? SingleImagePlanResult.Valid)?.plan?.let(::restoredProcessResultFor)
                            },
                            error = plan.userErrorOrNull(),
                        )
                    } else {
                        current
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _state.update { current ->
                    if (planGeneration == generation && current.source == source && current.request == request) {
                        current.copy(
                            planResult = null,
                            isPlanning = false,
                            error = "Could not inspect this image. Choose it again or try another photo.",
                        )
                    } else {
                        current
                    }
                }
            } finally {
                if (planGeneration == generation) planJob = null
            }
        }
    }

    private fun restoreExportSettings() {
        val revisionAtStart = requestRevision
        viewModelScope.launch {
            val restored = try {
                withContext(Dispatchers.IO) { projectStore.getSingleImageExportSettings() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _state.update {
                    it.copy(persistenceWarning = "Saved export settings could not be restored. Defaults are in use.")
                }
                null
            } ?: return@launch
            if (requestRevision != revisionAtStart || _state.value.isProcessing) return@launch
            _state.update { current ->
                current.copy(
                    request = restored.toRequest(current.request.deviceWallpaperDimensions),
                    result = null,
                    error = null,
                )
            }
            refreshPlan()
        }
    }

    private fun loadWallpaperDimensions() {
        viewModelScope.launch {
            resolvedWallpaperDimensions = try {
                withContext(Dispatchers.IO) { wallpaperDimensionsProvider.dimensions() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            applyResolvedWallpaperDimensions()
        }
    }

    private fun applyResolvedWallpaperDimensions() {
        val current = _state.value
        if (current.isProcessing || current.request.deviceWallpaperDimensions == resolvedWallpaperDimensions) return
        val affectsOutput = current.request.preset == SingleImageResizePreset.DeviceWallpaper
        _state.update {
            it.copy(
                request = it.request.copy(deviceWallpaperDimensions = resolvedWallpaperDimensions),
                planResult = if (affectsOutput) null else it.planResult,
                isPlanning = affectsOutput && it.source != null,
                result = if (affectsOutput) null else it.result,
            )
        }
        if (affectsOutput && _state.value.source != null) refreshPlan()
    }

    private fun persistExportSettings(
        request: SingleImageResizeRequest,
        debouncePersistence: Boolean,
    ) {
        val settings = SingleImageExportSettings.from(request)
        settingsSaveGeneration += 1L
        val generation = settingsSaveGeneration
        settingsSaveJob?.cancel()
        settingsSaveJob = viewModelScope.launch {
            if (debouncePersistence) delay(SettingsSaveDebounceMillis)
            try {
                withContext(Dispatchers.IO) { projectStore.setSingleImageExportSettings(settings) }
                _state.update { current ->
                    if (
                        settingsSaveGeneration == generation &&
                        SingleImageExportSettings.from(current.request) == settings
                    ) {
                        current.copy(persistenceWarning = null)
                    } else {
                        current
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _state.update { current ->
                    if (
                        settingsSaveGeneration == generation &&
                        SingleImageExportSettings.from(current.request) == settings
                    ) {
                        current.copy(
                            persistenceWarning =
                                "Export settings could not be remembered. You can still export this image.",
                        )
                    } else {
                        current
                    }
                }
            } finally {
                if (settingsSaveGeneration == generation) settingsSaveJob = null
            }
        }
    }

    private fun processImage() {
        val source = _state.value.source ?: return
        if (_state.value.isProcessing || processJob?.isActive == true) return
        if (_state.value.isPlanning) {
            _state.update { it.copy(error = "Please wait while the output size is checked.") }
            return
        }
        val plan = _state.value.planResult
        if (plan !is SingleImagePlanResult.Valid) {
            _state.update { it.copy(error = plan.userErrorOrNull() ?: "Choose a supported output size first.") }
            return
        }
        val request = _state.value.request
        _state.update {
            it.copy(
                isProcessing = true,
                isCancelling = false,
                progress = 0f,
                result = null,
                error = null,
            )
        }
        processGeneration += 1L
        val generation = processGeneration
        processJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    repository.process(source, request) { progress ->
                        _state.update { current ->
                            if (
                                processGeneration == generation &&
                                current.isProcessing &&
                                current.source == source &&
                                current.request == request
                            ) {
                                current.copy(progress = progress.coerceIn(0f, 1f))
                            } else {
                                current
                            }
                        }
                    }
                }
                if (
                    result is SingleImageProcessResult.Success &&
                    processGeneration == generation &&
                    _state.value.source == source &&
                    _state.value.request == request
                ) {
                    persistResult(result)
                    recordResizeExport(result)
                }
                _state.update { state ->
                    if (
                        processGeneration != generation ||
                        state.source != source ||
                        state.request != request
                    ) {
                        state
                    } else {
                        when (result) {
                            is SingleImageProcessResult.Success -> {
                                state.copy(
                                    isProcessing = false,
                                    isCancelling = false,
                                    progress = 1f,
                                    result = result,
                                    error = null,
                                )
                            }
                            is SingleImageProcessResult.Failure -> state.copy(
                                isProcessing = false,
                                isCancelling = false,
                                error = result.reason,
                            )
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _state.update { state ->
                    if (processGeneration == generation && state.source == source && state.request == request) {
                        state.copy(
                            isProcessing = false,
                            isCancelling = false,
                            error = throwable.message ?: "Image export failed. Try again.",
                        )
                    } else {
                        state
                    }
                }
            } finally {
                if (processGeneration == generation) {
                    _state.update { state ->
                        if (state.isProcessing && state.source == source && state.request == request) {
                            val wasCancelling = state.isCancelling
                            state.copy(
                                isProcessing = false,
                                isCancelling = false,
                                progress = if (wasCancelling) 0f else state.progress,
                                error = if (wasCancelling) "Processing cancelled." else state.error,
                            )
                        } else {
                            state
                        }
                    }
                    processJob = null
                    applyResolvedWallpaperDimensions()
                }
            }
        }
    }

    private fun processBatch() {
        val sources = _state.value.batchSources.take(MaxBatchImages)
        if (sources.isEmpty()) {
            _state.update { it.copy(error = "Select photos for the batch first.") }
            return
        }
        if (_state.value.isProcessing || processJob?.isActive == true) return
        val request = _state.value.request
        _state.update {
            it.copy(
                isProcessing = true,
                isCancelling = false,
                progress = 0f,
                result = null,
                batchSummary = null,
                error = null,
            )
        }
        processGeneration += 1L
        val generation = processGeneration
        processJob = viewModelScope.launch {
            val savedUris = mutableListOf<String>()
            var failures = 0
            var lastSuccess: SingleImageProcessResult.Success? = null
            try {
                sources.forEachIndexed { index, source ->
                    val result = withContext(Dispatchers.Default) {
                        repository.process(source, request) { itemProgress ->
                            val overall = (index + itemProgress.coerceIn(0f, 1f)) / sources.size
                            _state.update { current ->
                                if (processGeneration == generation && current.isProcessing) {
                                    current.copy(progress = overall)
                                } else {
                                    current
                                }
                            }
                        }
                    }
                    when (result) {
                        is SingleImageProcessResult.Success -> {
                            savedUris += result.savedUri
                            lastSuccess = result
                            recordResizeExport(result)
                        }
                        is SingleImageProcessResult.Failure -> failures += 1
                    }
                    _state.update { current ->
                        if (processGeneration == generation && current.isProcessing) {
                            current.copy(
                                progress = (index + 1f) / sources.size,
                                batchSummary = BatchExportSummary(
                                    completed = index + 1,
                                    total = sources.size,
                                    failures = failures,
                                    savedUris = savedUris.toList(),
                                ),
                            )
                        } else {
                            current
                        }
                    }
                }
                lastSuccess?.let(::persistResult)
                _state.update { current ->
                    if (processGeneration == generation) {
                        current.copy(
                            isProcessing = false,
                            isCancelling = false,
                            progress = 1f,
                            result = lastSuccess,
                            error = when {
                                failures == sources.size -> "No images were exported. Check the selected files and settings."
                                failures > 0 -> "$failures of ${sources.size} images could not be exported."
                                else -> null
                            },
                        )
                    } else {
                        current
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _state.update { current ->
                    if (processGeneration == generation) {
                        current.copy(error = throwable.message ?: "Batch export failed.")
                    } else {
                        current
                    }
                }
            } finally {
                if (processGeneration == generation) {
                    _state.update { current ->
                        if (current.isProcessing) {
                            val wasCancelling = current.isCancelling
                            current.copy(
                                isProcessing = false,
                                isCancelling = false,
                                error = if (wasCancelling) "Batch export cancelled." else current.error,
                            )
                        } else {
                            current
                        }
                    }
                    processJob = null
                    applyResolvedWallpaperDimensions()
                }
            }
        }
    }

    private suspend fun recordResizeExport(result: SingleImageProcessResult.Success) {
        try {
            withContext(Dispatchers.IO) {
                projectStore.addExportHistory(
                    id = UUID.randomUUID().toString(),
                    templateId = ResizeHistoryTemplateId,
                    savedUri = result.savedUri,
                    resolution = ExportResolution.ORIGINAL,
                    createdAtMillis = System.currentTimeMillis(),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            _state.update {
                it.copy(persistenceWarning = "Image saved, but recent export history could not be updated.")
            }
        }
    }

    private fun cancelProcessing() {
        if (!_state.value.isProcessing || _state.value.isCancelling) return
        processJob?.cancel()
        _state.update {
            it.copy(
                isCancelling = true,
                error = "Stopping the export safely…",
            )
        }
    }

    private fun SingleImagePlanResult?.userErrorOrNull(): String? =
        when (this) {
            is SingleImagePlanResult.Invalid -> when (reason) {
                SingleImagePlanError.InvalidDimensions -> "This image could not be read."
                SingleImagePlanError.InvalidOutputDimensions ->
                    "Enter a positive width and height for the custom canvas."
                SingleImagePlanError.DeviceWallpaperUnavailable ->
                    "Wallpaper dimensions are unavailable. Choose another preset or Custom."
                SingleImagePlanError.OutputTooLarge ->
                    "Use dimensions up to 8192 px per edge and 24 megapixels total."
            }
            else -> null
        }

    private fun restoredResultSnapshot(): RestoredResultSnapshot? {
        val savedUri = savedStateHandle.get<String>(ResultUriSavedStateKey)
            ?.takeIf { it.length in 2..MaxSavedUriLength && ':' in it }
            ?: return null
        val originalWidth = savedStateHandle.get<Int>(ResultOriginalWidthKey) ?: return null
        val originalHeight = savedStateHandle.get<Int>(ResultOriginalHeightKey) ?: return null
        val outputWidth = savedStateHandle.get<Int>(ResultOutputWidthKey) ?: return null
        val outputHeight = savedStateHandle.get<Int>(ResultOutputHeightKey) ?: return null
        if (originalWidth <= 0 || originalHeight <= 0 || outputWidth <= 0 || outputHeight <= 0) return null
        val format = savedStateHandle.get<String>(ResultFormatKey)
            ?.let { name -> SingleImageOutputFormat.entries.firstOrNull { it.name == name } }
            ?: return null
        val contentMode = savedStateHandle.get<String>(ResultContentModeKey)
            ?.let { name -> ExportContentMode.entries.firstOrNull { it.name == name } }
            ?: return null
        return RestoredResultSnapshot(
            savedUri = savedUri,
            metadata = SingleImageOutputMetadata(
                originalDimensions = ImageDimensions(originalWidth, originalHeight),
                outputDimensions = ImageDimensions(outputWidth, outputHeight),
                originalBytes = savedStateHandle.get<Long>(ResultOriginalBytesKey)?.takeIf { it >= 0L },
                outputBytes = savedStateHandle.get<Long>(ResultOutputBytesKey)?.takeIf { it >= 0L },
                outputFormat = format,
                encodingQuality = savedStateHandle.get<Int>(ResultQualityKey)?.takeIf { it >= 0 },
                contentMode = contentMode,
                metadataPolicy = savedStateHandle.get<String>(ResultMetadataPolicyKey)
                    ?.let { name -> ImageMetadataPolicy.entries.firstOrNull { it.name == name } }
                    ?: ImageMetadataPolicy.RemoveMetadata,
            ),
        )
    }

    private fun restoredProcessResultFor(plan: com.rameshta.splitframe.domain.SingleImageResizePlan): SingleImageProcessResult.Success? {
        val restored = pendingRestoredResult ?: return null
        val request = _state.value.request
        if (!restored.metadata.matches(plan, request)) {
            return null
        }
        return SingleImageProcessResult.Success(
            source = com.rameshta.splitframe.domain.ImageSource.LocalUri(restored.savedUri),
            savedUri = restored.savedUri,
            plan = plan,
            metadata = restored.metadata,
        )
    }

    private fun persistResult(result: SingleImageProcessResult.Success) {
        savedStateHandle[ResultUriSavedStateKey] = result.savedUri
        savedStateHandle[ResultOriginalWidthKey] = result.metadata.originalDimensions.widthPx
        savedStateHandle[ResultOriginalHeightKey] = result.metadata.originalDimensions.heightPx
        savedStateHandle[ResultOutputWidthKey] = result.metadata.outputDimensions.widthPx
        savedStateHandle[ResultOutputHeightKey] = result.metadata.outputDimensions.heightPx
        savedStateHandle[ResultOriginalBytesKey] = result.metadata.originalBytes ?: -1L
        savedStateHandle[ResultOutputBytesKey] = result.metadata.outputBytes ?: -1L
        savedStateHandle[ResultFormatKey] = result.metadata.outputFormat.name
        savedStateHandle[ResultQualityKey] = result.metadata.encodingQuality ?: -1
        savedStateHandle[ResultContentModeKey] = result.metadata.contentMode.name
        savedStateHandle[ResultMetadataPolicyKey] = result.metadata.metadataPolicy.name
    }

    private fun clearSavedResult() {
        pendingRestoredResult = null
        listOf(
            ResultUriSavedStateKey,
            ResultOriginalWidthKey,
            ResultOriginalHeightKey,
            ResultOutputWidthKey,
            ResultOutputHeightKey,
            ResultOriginalBytesKey,
            ResultOutputBytesKey,
            ResultFormatKey,
            ResultQualityKey,
            ResultContentModeKey,
            ResultMetadataPolicyKey,
        ).forEach { key -> savedStateHandle.remove<Any?>(key) }
    }

    private data class RestoredResultSnapshot(
        val savedUri: String,
        val metadata: SingleImageOutputMetadata,
    )

    private data class PlanInspection(
        val plan: SingleImagePlanResult,
        val sourceDimensions: ImageDimensions?,
        val restoredOutputReadable: Boolean?,
    )

    private companion object {
        const val SettingsSaveDebounceMillis = 150L
        const val MaxSavedUriLength = 4_096
        const val MaxBatchImages = 20
        const val ResizeHistoryTemplateId = "single_image_resize"
        const val SourceSavedStateKey = "single_image_source_v1"
        const val BatchSourcesSavedStateKey = "single_image_batch_sources_v1"
        const val ResultUriSavedStateKey = "single_image_result_uri_v1"
        const val ResultOriginalWidthKey = "single_image_result_original_width_v1"
        const val ResultOriginalHeightKey = "single_image_result_original_height_v1"
        const val ResultOutputWidthKey = "single_image_result_output_width_v1"
        const val ResultOutputHeightKey = "single_image_result_output_height_v1"
        const val ResultOriginalBytesKey = "single_image_result_original_bytes_v1"
        const val ResultOutputBytesKey = "single_image_result_output_bytes_v1"
        const val ResultFormatKey = "single_image_result_format_v1"
        const val ResultQualityKey = "single_image_result_quality_v1"
        const val ResultContentModeKey = "single_image_result_content_mode_v1"
        const val ResultMetadataPolicyKey = "single_image_result_metadata_policy_v1"
    }
}
