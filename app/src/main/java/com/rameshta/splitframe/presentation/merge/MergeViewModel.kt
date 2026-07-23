package com.rameshta.splitframe.presentation.merge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.rameshta.splitframe.R
import com.rameshta.splitframe.data.PhotoDraftCodec
import com.rameshta.splitframe.data.ProjectStore
import com.rameshta.splitframe.domain.CollageGradient
import com.rameshta.splitframe.domain.AutoArrangeMath
import com.rameshta.splitframe.domain.CollageBackgroundKind
import com.rameshta.splitframe.domain.CollageBackgroundStyle
import com.rameshta.splitframe.domain.CollageLimits
import com.rameshta.splitframe.domain.CollageTextLayer
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.ExportResult
import com.rameshta.splitframe.domain.ImageSource
import com.rameshta.splitframe.domain.ImageTransform
import com.rameshta.splitframe.domain.LayoutTemplate
import com.rameshta.splitframe.domain.MergeProject
import com.rameshta.splitframe.domain.TemplateCatalog
import com.rameshta.splitframe.domain.TemplateDiscoveryFilter
import com.rameshta.splitframe.domain.TemplateRepository
import com.rameshta.splitframe.domain.TransformUndoSession
import com.rameshta.splitframe.export.ImageExportRepository
import com.rameshta.splitframe.export.ImageSourceReader
import com.rameshta.splitframe.export.ImageValidationResult
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MergeViewModel(
    private val templateRepository: TemplateRepository,
    private val projectStore: ProjectStore,
    private val imageSourceReader: ImageSourceReader,
    private val imageExportRepository: ImageExportRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val templates = templateRepository.templates()
    private val savedStateDraft = savedStateHandle.get<String>(DraftSavedStateKey)
    private val restoredStateDraft = savedStateDraft
        ?.let { PhotoDraftCodec.decode(it, templates) }
    private val undoStack = ArrayDeque<ProjectSnapshot>()
    private val redoStack = ArrayDeque<ProjectSnapshot>()
    private val adaptiveBackgroundGenerator = AdaptiveCollageBackgroundGenerator(imageSourceReader)
    private var backgroundGeneration = 0
    private var exportJob: Job? = null
    private var draftPersistenceJob: Job? = null
    private var transformUndoExpiryJob: Job? = null
    private val transformUndoSession = TransformUndoSession()
    private val recentLayoutUseTracker = RecentLayoutUseTracker()
    private var hasCommittedProjectChange = false
    private var corruptDraftBlocked = savedStateDraft != null && restoredStateDraft == null
    private var restoreValidationGeneration = 0L
    private var persistedFavoriteTemplateIds: List<String> = emptyList()
    private var persistedRecentTemplateIds: List<String> = emptyList()
    private val pendingFavoriteTargets = linkedMapOf<String, Boolean>()
    private val _state = MutableStateFlow(
        MergeState(
            availableTemplates = templates,
            project = restoredStateDraft ?: createProject(templates.first(), ExportResolution.FHD_1080),
            error = R.string.photo_draft_corrupt.takeIf { corruptDraftBlocked },
        ),
    )
    val state: StateFlow<MergeState> = _state.asStateFlow()

    init {
        if (restoredStateDraft != null) {
            restoreRuntimeState(restoredStateDraft)
        } else {
            viewModelScope.launch {
                val persistedEncoded = projectStore.getActivePhotoDraft()
                val persistedDraft = persistedEncoded?.let { PhotoDraftCodec.decode(it, templates) }
                if (!hasCommittedProjectChange && persistedDraft != null) {
                    _state.update { current -> current.copy(project = persistedDraft) }
                    savedStateHandle[DraftSavedStateKey] = PhotoDraftCodec.encode(persistedDraft)
                    restoreRuntimeState(persistedDraft)
                } else if (!hasCommittedProjectChange && persistedEncoded != null) {
                    corruptDraftBlocked = true
                    _state.update { current -> current.copy(error = R.string.photo_draft_corrupt) }
                } else if (!hasCommittedProjectChange) {
                    val lastResolution = projectStore.getLastResolution()
                    _state.update { current ->
                        current.copy(project = current.project?.copy(exportResolution = lastResolution))
                    }
                }
            }
        }
        viewModelScope.launch {
            try {
                combine(
                    projectStore.observeFavoriteTemplates(),
                    projectStore.observeRecentLayouts(),
                ) { favoriteIds, recentIds -> favoriteIds to recentIds }
                    .collect { (favoriteIds, recentIds) ->
                        persistedFavoriteTemplateIds = favoriteIds.distinct()
                        persistedRecentTemplateIds = recentIds.distinct()
                        pendingFavoriteTargets.entries.removeAll { (templateId, desiredFavorite) ->
                            persistedFavoriteTemplateIds.contains(templateId) == desiredFavorite
                        }
                        publishTemplateDiscovery(isLoading = false, loadFailed = false)
                    }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                publishTemplateDiscovery(isLoading = false, loadFailed = true)
            }
        }
    }

    fun process(intent: MergeIntent) {
        handle(intent.toAction())
    }

    fun selectTemplateForEditing(templateId: String): Boolean {
        if (corruptDraftBlocked) {
            _state.update { it.copy(error = R.string.photo_draft_corrupt) }
            return false
        }
        return selectTemplate(templateId)
    }

    private fun handle(action: MergeAction) {
        if (
            corruptDraftBlocked &&
            action != MergeAction.Reset &&
            action != MergeAction.ClearError
        ) {
            _state.update { it.copy(error = R.string.photo_draft_corrupt) }
            return
        }
        when (action) {
            is MergeAction.SelectTemplate -> selectTemplate(action.templateId)
            is MergeAction.UpdateTemplateSearch -> updateTemplateFilter { it.copy(query = action.query) }
            is MergeAction.SelectTemplateCollection -> updateTemplateFilter {
                it.copy(collection = action.collection)
            }
            is MergeAction.SelectTemplateAspect -> updateTemplateFilter { it.copy(aspect = action.aspect) }
            is MergeAction.SelectTemplateMediaCount -> updateTemplateFilter {
                it.copy(mediaCount = action.mediaCount)
            }
            is MergeAction.ToggleTemplateFavorite -> toggleTemplateFavorite(action.templateId)
            MergeAction.ResetTemplateDiscovery -> updateTemplateFilter { TemplateDiscoveryFilter() }
            MergeAction.ClearTemplateFavoriteError -> clearTemplateFavoriteError()
            is MergeAction.AssignImage -> assignImage(action.cellIndex, action.source)
            is MergeAction.AssignImages -> assignImages(action.sources)
            is MergeAction.RemoveImage -> removeImage(action.cellIndex)
            is MergeAction.ReplaceImage -> assignImage(action.cellIndex, action.source)
            is MergeAction.ReorderImages -> reorderImages(action.fromIndex, action.toIndex)
            is MergeAction.SwapCells -> swapCells(action.a, action.b)
            is MergeAction.UpdateImageTransform -> updateImageTransform(action.cellIndex, action.transform, action.trackUndo)
            is MergeAction.ResetImageTransform -> updateImageTransform(action.cellIndex, ImageTransform.Default, trackUndo = true)
            is MergeAction.UpdateSpacing -> updateProject { it.copy(spacingDp = action.dp.coerceIn(0f, 36f)) }
            is MergeAction.UpdateCornerRadius -> updateProject { it.copy(cornerRadiusDp = action.dp.coerceIn(0f, 64f)) }
            is MergeAction.UpdateBackgroundColor -> updateBackgroundStyle(
                (_state.value.project?.backgroundStyle ?: CollageBackgroundStyle()).copy(
                    kind = CollageBackgroundKind.Solid,
                    primaryColor = action.argb,
                    secondaryColor = action.argb,
                    tertiaryColor = action.argb,
                ),
            )
            is MergeAction.UpdateBackgroundStyle -> updateBackgroundStyle(action.style)
            is MergeAction.UpdateBorderWidth -> updateProject {
                it.copy(borderWidthDp = action.dp.takeIf(Float::isFinite)?.coerceIn(0f, 12f) ?: 0f)
            }
            is MergeAction.UpdateBorderStyle -> updateProject {
                it.copy(
                    borderStyle = action.style.normalized(),
                    borderWidthDp = if (it.borderWidthDp <= 0f) 3f else it.borderWidthDp,
                )
            }
            is MergeAction.UpdateCropShape -> updateCropShape(action.cellIndex, action.shape)
            MergeAction.AddTextLayer -> addTextLayer()
            is MergeAction.UpdateTextLayer -> updateTextLayer(action.layer)
            is MergeAction.DuplicateTextLayer -> duplicateTextLayer(action.layerId)
            is MergeAction.DeleteTextLayer -> deleteTextLayer(action.layerId)
            is MergeAction.UpdateBeforeAfterSlider -> updateProject {
                it.copy(beforeAfterSlider = action.position.coerceIn(0.05f, 0.95f))
            }
            is MergeAction.SelectExportResolution -> selectResolution(action.resolution)
            MergeAction.AutoArrange -> autoArrange()
            MergeAction.UndoEdit -> undoEdit()
            MergeAction.RedoEdit -> redoEdit()
            MergeAction.Export -> export()
            MergeAction.Reset -> reset()
            MergeAction.ClearError -> reduce(MergeResultEvent.ErrorCleared)
            MergeAction.DismissExportResult -> reduce(MergeResultEvent.ExportResultDismissed)
        }
    }

    private fun selectTemplate(templateId: String): Boolean {
        val currentProject = _state.value.project ?: return false
        val template = templates.firstOrNull { it.id == templateId } ?: return false
        val orderedSources = currentProject.orderedSources()
        if (!TemplateCatalog.canApplyWithoutDroppingImages(template, orderedSources.size)) {
            reduce(MergeResultEvent.Failed(R.string.layout_requires_photo_count))
            return false
        }

        val updated = projectWithOrderedSources(
            base = createProject(template, currentProject.exportResolution).copy(
                backgroundColor = currentProject.backgroundColor,
                backgroundGradient = currentProject.backgroundGradient,
                borderColor = currentProject.borderColor,
                borderWidthDp = currentProject.borderWidthDp,
                backgroundStyle = currentProject.backgroundStyle,
                borderStyle = currentProject.borderStyle,
                cropShapes = currentProject.cropShapes,
                textLayers = currentProject.textLayers,
            ),
            sources = orderedSources,
            transformsByKey = currentProject.transformsBySourceKey(),
        )?.copy(
            backgroundColor = currentProject.backgroundColor,
            backgroundGradient = currentProject.backgroundGradient,
            borderColor = currentProject.borderColor,
            borderWidthDp = currentProject.borderWidthDp,
            backgroundStyle = currentProject.backgroundStyle,
            borderStyle = currentProject.borderStyle,
            cropShapes = currentProject.cropShapes,
            textLayers = currentProject.textLayers,
        ) ?: return false
        commitProjectChange(
            project = updated,
            sourceDimensions = remapDimensions(
                updated,
                currentProject.dimensionsBySourceKey(_state.value.sourceDimensions),
            ),
            layoutUseOrigin = LayoutUseOrigin.Selection,
        )
        return true
    }

    private fun updateTemplateFilter(transform: (TemplateDiscoveryFilter) -> TemplateDiscoveryFilter) {
        _state.update { state ->
            state.copy(
                templateDiscovery = state.templateDiscovery.copy(
                    filter = transform(state.templateDiscovery.filter),
                ),
            )
        }
    }

    private fun toggleTemplateFavorite(templateId: String) {
        if (templates.none { it.id == templateId } || pendingFavoriteTargets.containsKey(templateId)) return
        val wasFavorite = _state.value.templateDiscovery.favoriteTemplateIds.contains(templateId)
        val desiredFavorite = !wasFavorite
        pendingFavoriteTargets[templateId] = desiredFavorite
        publishTemplateDiscovery()
        viewModelScope.launch {
            try {
                projectStore.setTemplateFavorite(templateId, desiredFavorite)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                pendingFavoriteTargets.remove(templateId)
                _state.update { state ->
                    state.copy(
                        templateDiscovery = state.templateDiscovery.copy(
                            favoriteTemplateIds = favoriteIdsWithOptimisticChanges(
                                persistedFavoriteTemplateIds,
                                pendingFavoriteTargets,
                            ),
                            pendingFavoriteIds = pendingFavoriteTargets.keys.toSet(),
                            favoriteErrorTemplateId = templateId,
                            favoriteErrorVersion = state.templateDiscovery.favoriteErrorVersion + 1L,
                        ),
                    )
                }
            }
        }
    }

    private fun clearTemplateFavoriteError() {
        _state.update { state ->
            state.copy(
                templateDiscovery = state.templateDiscovery.copy(favoriteErrorTemplateId = null),
            )
        }
    }

    private fun publishTemplateDiscovery(
        isLoading: Boolean = _state.value.templateDiscovery.isLoading,
        loadFailed: Boolean = _state.value.templateDiscovery.loadFailed,
    ) {
        _state.update { state ->
            state.copy(
                templateDiscovery = state.templateDiscovery.copy(
                    favoriteTemplateIds = favoriteIdsWithOptimisticChanges(
                        persistedFavoriteTemplateIds,
                        pendingFavoriteTargets,
                    ),
                    recentTemplateIds = persistedRecentTemplateIds,
                    pendingFavoriteIds = pendingFavoriteTargets.keys.toSet(),
                    isLoading = isLoading,
                    loadFailed = loadFailed,
                ),
            )
        }
    }

    private fun assignImage(cellIndex: Int, source: ImageSource) {
        val project = _state.value.project ?: return
        if (project.template.cells.none { it.index == cellIndex }) return
        if (!project.assignedImages.containsKey(cellIndex) && project.assignedImages.size >= CollageLimits.MaxImages) {
            reduce(MergeResultEvent.Failed(R.string.image_max_count_reached))
            return
        }
        viewModelScope.launch {
            when (val validation = withContext(Dispatchers.IO) { imageSourceReader.validate(source) }) {
                is ImageValidationResult.Valid -> {
                    val updated = project.copy(
                        assignedImages = project.assignedImages + (cellIndex to source),
                        imageTransforms = project.imageTransforms - cellIndex,
                    )
                    commitProjectChange(
                        project = updated,
                        sourceDimensions = _state.value.sourceDimensions - cellIndex + (cellIndex to validation.dimensions),
                        layoutUseOrigin = LayoutUseOrigin.UserMutation,
                    )
                    _state.update { state ->
                        state.copy(unreadableSourceCells = state.unreadableSourceCells - cellIndex)
                    }
                }
                ImageValidationResult.UnsupportedFormat -> reduce(MergeResultEvent.Failed(R.string.image_unsupported_format))
                ImageValidationResult.Unreadable -> reduce(MergeResultEvent.Failed(R.string.image_unreadable))
            }
        }
    }

    private fun assignImages(sources: List<ImageSource>) {
        if (sources.isEmpty()) return
        viewModelScope.launch {
            val initialProject = _state.value.project ?: return@launch
            val existingKeys = initialProject.orderedSources().map { it.sourceKey() }.toSet()
            val distinctIncomingSources = sources.distinctBy { it.sourceKey() }
            val uniqueSources = distinctIncomingSources.filterNot { it.sourceKey() in existingKeys }
            val duplicateSkipped = distinctIncomingSources.size < sources.size || distinctIncomingSources.any { it.sourceKey() in existingKeys }
            val remainingSlots = (CollageLimits.MaxImages - initialProject.assignedImages.size).coerceAtLeast(0)
            val cappedSources = uniqueSources.take(remainingSlots)
            val maxSkipped = uniqueSources.size > cappedSources.size

            val validations = withContext(Dispatchers.IO) {
                cappedSources.map { source -> source to imageSourceReader.validate(source) }
            }
            val validSources = validations.mapNotNull { (source, result) ->
                (result as? ImageValidationResult.Valid)?.let { source to it.dimensions }
            }
            val failure = validations.firstOrNull { (_, result) -> result !is ImageValidationResult.Valid }?.second

            if (validSources.isNotEmpty()) {
                appendValidImages(validSources)
            }
            when {
                failure is ImageValidationResult.UnsupportedFormat -> reduce(MergeResultEvent.Failed(R.string.image_unsupported_format))
                failure is ImageValidationResult.Unreadable -> reduce(MergeResultEvent.Failed(R.string.image_unreadable))
                maxSkipped -> reduce(MergeResultEvent.Failed(R.string.image_max_count_reached))
                duplicateSkipped -> reduce(MergeResultEvent.Failed(R.string.image_duplicate_skipped))
            }
        }
    }

    private fun removeImage(cellIndex: Int) {
        val project = _state.value.project ?: return
        if (!project.assignedImages.containsKey(cellIndex)) return
        val remainingSources = project.template.cells
            .filterNot { it.index == cellIndex }
            .mapNotNull { project.assignedImages[it.index] }
        val transformsByKey = project.transformsBySourceKey()
        val dimensionsByKey = project.dimensionsBySourceKey(_state.value.sourceDimensions)
        val updated = projectWithOrderedSources(
            base = project,
            sources = remainingSources,
            transformsByKey = transformsByKey,
        ) ?: return
        commitProjectChange(
            project = updated,
            sourceDimensions = remapDimensions(updated, dimensionsByKey),
            layoutUseOrigin = LayoutUseOrigin.UserMutation,
        )
    }

    private fun updateImageTransform(cellIndex: Int, transform: ImageTransform, trackUndo: Boolean) {
        val project = _state.value.project ?: return
        if (!project.assignedImages.containsKey(cellIndex)) return
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
        commitProjectChange(
            project = project.copy(imageTransforms = project.imageTransforms + (cellIndex to transform.normalized())),
            trackUndo = shouldTrackUndo,
            layoutUseOrigin = LayoutUseOrigin.UserMutation,
        )
    }

    private fun reorderImages(fromIndex: Int, toIndex: Int) {
        val project = _state.value.project ?: return
        val sources = project.orderedSources().toMutableList()
        if (fromIndex !in sources.indices || sources.isEmpty()) return
        val boundedTo = toIndex.coerceIn(sources.indices)
        if (fromIndex == boundedTo) return
        val moved = sources.removeAt(fromIndex)
        sources.add(boundedTo, moved)
        val updated = projectWithOrderedSources(project, sources, project.transformsBySourceKey()) ?: return
        commitProjectChange(
            project = updated,
            sourceDimensions = remapDimensions(
                updated,
                project.dimensionsBySourceKey(_state.value.sourceDimensions),
            ),
            layoutUseOrigin = LayoutUseOrigin.UserMutation,
        )
    }

    private fun swapCells(a: Int, b: Int) {
        val project = _state.value.project ?: return
        val validCells = project.template.cells.map { it.index }.toSet()
        if (a == b || a !in validCells || b !in validCells) return
        val images = project.assignedImages.toMutableMap()
        val first = images[a]
        val second = images[b]
        if (second == null) {
            images.remove(a)
        } else {
            images[a] = second
        }
        if (first == null) {
            images.remove(b)
        } else {
            images[b] = first
        }

        val dimensions = _state.value.sourceDimensions.toMutableMap()
        val firstDimensions = dimensions[a]
        val secondDimensions = dimensions[b]
        if (secondDimensions == null) dimensions.remove(a) else dimensions[a] = secondDimensions
        if (firstDimensions == null) dimensions.remove(b) else dimensions[b] = firstDimensions

        val transforms = project.imageTransforms.toMutableMap()
        val firstTransform = transforms[a]
        val secondTransform = transforms[b]
        if (secondTransform == null) transforms.remove(a) else transforms[a] = secondTransform
        if (firstTransform == null) transforms.remove(b) else transforms[b] = firstTransform

        commitProjectChange(
            project = project.copy(assignedImages = images, imageTransforms = transforms),
            sourceDimensions = dimensions,
            layoutUseOrigin = LayoutUseOrigin.UserMutation,
        )
    }

    private fun autoArrange() {
        val project = _state.value.project ?: return
        val assignments = AutoArrangeMath.assignments(
            template = project.template,
            occupiedCellIndices = project.template.cells.mapNotNull { cell ->
                cell.index.takeIf(project.assignedImages::containsKey)
            },
            dimensionsByCell = _state.value.sourceDimensions,
        )
        if (assignments.isEmpty()) return
        val images = assignments.mapValues { (_, oldCell) -> project.assignedImages.getValue(oldCell) }
        val transforms = assignments.mapNotNull { (targetCell, oldCell) ->
            project.imageTransforms[oldCell]?.let { targetCell to it }
        }.toMap()
        val dimensions = assignments.mapNotNull { (targetCell, oldCell) ->
            _state.value.sourceDimensions[oldCell]?.let { targetCell to it }
        }.toMap()
        // Crop transforms follow media; shape masks stay attached to layout cells.
        commitProjectChange(
            project.copy(assignedImages = images, imageTransforms = transforms),
            dimensions,
            layoutUseOrigin = LayoutUseOrigin.UserMutation,
        )
    }

    private fun updateBackgroundStyle(style: CollageBackgroundStyle) {
        val safe = style.normalized()
        updateProject { project ->
            project.copy(
                backgroundStyle = safe,
                backgroundColor = safe.primaryColor,
                backgroundGradient = when (safe.kind) {
                    CollageBackgroundKind.Solid -> CollageGradient.solid(safe.primaryColor)
                    CollageBackgroundKind.LinearGradient,
                    CollageBackgroundKind.RadialGradient,
                    CollageBackgroundKind.Pattern,
                    -> CollageGradient(
                        safe.primaryColor,
                        safe.secondaryColor,
                        safe.tertiaryColor,
                    )
                    CollageBackgroundKind.AdaptiveLinear,
                    CollageBackgroundKind.MediaBlur,
                    -> project.backgroundGradient
                },
            )
        }
        _state.value.project?.let(::refreshDerivedBackground)
    }

    private fun updateCropShape(cellIndex: Int, shape: com.rameshta.splitframe.domain.CropShape) {
        val project = _state.value.project ?: return
        if (project.template.cells.none { it.index == cellIndex }) return
        updateProject {
            val updated = if (shape == com.rameshta.splitframe.domain.CropShape.Rectangle) {
                it.cropShapes - cellIndex
            } else {
                it.cropShapes + (cellIndex to shape)
            }
            it.copy(cropShapes = updated)
        }
    }

    private fun addTextLayer() {
        val project = _state.value.project ?: return
        if (project.textLayers.size >= MaxTextLayers) return
        val offset = project.textLayers.size.coerceAtMost(6) * 0.035f
        updateProject {
            it.copy(
                textLayers = it.textLayers + CollageTextLayer(
                    id = UUID.randomUUID().toString(),
                    text = "Text",
                    centerX = (0.5f + offset).coerceAtMost(0.8f),
                    centerY = (0.5f + offset).coerceAtMost(0.8f),
                ),
            )
        }
    }

    private fun updateTextLayer(layer: CollageTextLayer) {
        val project = _state.value.project ?: return
        if (project.textLayers.none { it.id == layer.id }) return
        val safe = layer.normalized()
        updateProject { current ->
            current.copy(textLayers = current.textLayers.map { if (it.id == safe.id) safe else it })
        }
    }

    private fun duplicateTextLayer(layerId: String) {
        val project = _state.value.project ?: return
        if (project.textLayers.size >= MaxTextLayers) return
        val source = project.textLayers.firstOrNull { it.id == layerId } ?: return
        val duplicate = source.copy(
            id = UUID.randomUUID().toString(),
            centerX = (source.centerX + 0.05f).coerceAtMost(1f),
            centerY = (source.centerY + 0.05f).coerceAtMost(1f),
        )
        updateProject { it.copy(textLayers = it.textLayers + duplicate) }
    }

    private fun deleteTextLayer(layerId: String) {
        updateProject { project ->
            project.copy(textLayers = project.textLayers.filterNot { it.id == layerId })
        }
    }

    private fun selectResolution(resolution: ExportResolution) {
        updateProject(countsAsLayoutUse = false) { it.copy(exportResolution = resolution) }
        viewModelScope.launch {
            projectStore.setLastResolution(resolution)
        }
    }

    private fun export() {
        if (_state.value.isExporting || exportJob?.isActive == true) return
        val project = _state.value.project ?: return
        if (!project.isReadyForImageExport || _state.value.unreadableSourceCells.isNotEmpty()) {
            reduce(
                MergeResultEvent.Failed(
                    if (_state.value.unreadableSourceCells.isNotEmpty()) {
                        R.string.image_unreadable
                    } else {
                        R.string.missing_images
                    },
                ),
            )
            return
        }
        reduce(MergeResultEvent.ExportStarted())
        exportJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    imageExportRepository.export(project)
                }
                var exportHistoryFailed = false
                if (result is ExportResult.Success) {
                    recordRecentLayoutUse(LayoutUseOrigin.ExportSuccess, project, project)
                    try {
                        projectStore.addExportHistory(
                            id = UUID.randomUUID().toString(),
                            templateId = project.template.id,
                            savedUri = result.savedUri,
                            resolution = project.exportResolution,
                            createdAtMillis = System.currentTimeMillis(),
                        )
                    } catch (_: Throwable) {
                        // MediaStore already committed the image; history is secondary metadata.
                        exportHistoryFailed = true
                    }
                }
                reduce(MergeResultEvent.ExportFinished(result))
                if (exportHistoryFailed) {
                    reduce(MergeResultEvent.Failed(R.string.export_history_update_failed))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                reduce(ExportResult.Failure(throwable.message ?: "Unknown export error.").let(MergeResultEvent::ExportFinished))
            } finally {
                exportJob = null
            }
        }
    }

    private fun reset() {
        val current = _state.value.project ?: return
        corruptDraftBlocked = false
        savedStateHandle.remove<String>(DraftSavedStateKey)
        commitProjectChange(createProject(current.template, current.exportResolution), emptyMap())
    }

    private fun loadDimensions(cellIndex: Int, source: ImageSource) {
        viewModelScope.launch {
            val dimensions = withContext(Dispatchers.IO) {
                imageSourceReader.dimensions(source)
            } ?: return@launch
            reduce(MergeResultEvent.ImageDimensionsLoaded(cellIndex, dimensions))
        }
    }

    private fun updateProject(
        countsAsLayoutUse: Boolean = true,
        transform: (MergeProject) -> MergeProject,
    ) {
        val project = _state.value.project ?: return
        commitProjectChange(
            project = transform(project),
            layoutUseOrigin = if (countsAsLayoutUse) {
                LayoutUseOrigin.UserMutation
            } else {
                LayoutUseOrigin.Internal
            },
        )
    }

    private fun appendValidImages(validSources: List<Pair<ImageSource, com.rameshta.splitframe.domain.ImageDimensions>>) {
        val project = _state.value.project ?: return
        val existingSources = project.orderedSources()
        val allSources = existingSources + validSources.map { it.first }
        val targetTemplate = if (
            TemplateCatalog.canApplyWithoutDroppingImages(project.template, allSources.size)
        ) {
            project.template
        } else {
            templateForImageCount(allSources.size)
        }
        if (targetTemplate == null) {
            reduce(MergeResultEvent.Failed(R.string.layout_requires_photo_count))
            return
        }
        val dimensionsByKey = project.dimensionsBySourceKey(_state.value.sourceDimensions).toMutableMap()
        validSources.forEach { (source, dimensions) ->
            dimensionsByKey[source.sourceKey()] = dimensions
        }
        val retainsCurrentTemplate = targetTemplate.id == project.template.id
        val updated = projectWithOrderedSources(
            base = project.copy(
                template = targetTemplate,
                spacingDp = if (retainsCurrentTemplate) project.spacingDp else targetTemplate.defaultSpacingDp,
                cornerRadiusDp = if (retainsCurrentTemplate) {
                    project.cornerRadiusDp
                } else {
                    targetTemplate.defaultCornerRadiusDp
                },
            ),
            sources = allSources,
            transformsByKey = project.transformsBySourceKey(),
        ) ?: run {
            reduce(MergeResultEvent.Failed(R.string.layout_requires_photo_count))
            return
        }
        commitProjectChange(
            project = updated,
            sourceDimensions = remapDimensions(updated, dimensionsByKey),
            layoutUseOrigin = LayoutUseOrigin.UserMutation,
        )
    }

    private fun commitProjectChange(
        project: MergeProject,
        sourceDimensions: Map<Int, com.rameshta.splitframe.domain.ImageDimensions> = _state.value.sourceDimensions,
        trackUndo: Boolean = true,
        layoutUseOrigin: LayoutUseOrigin = LayoutUseOrigin.Internal,
    ) {
        hasCommittedProjectChange = true
        restoreValidationGeneration += 1L
        val current = _state.value.project
        val unreadableSourceKeys = current?.assignedImages
            ?.filterKeys(_state.value.unreadableSourceCells::contains)
            ?.values
            ?.map { it.sourceKey() }
            ?.toSet()
            .orEmpty()
        val remappedUnreadableCells = project.assignedImages.mapNotNull { (cellIndex, source) ->
            cellIndex.takeIf { source.sourceKey() in unreadableSourceKeys }
        }.toSet()
        val sourceKeysChanged = current?.orderedSourceKeys() != project.orderedSourceKeys()
        if (trackUndo && current != null && current != project) {
            undoStack.addLast(
                ProjectSnapshot(
                    current,
                    _state.value.sourceDimensions,
                    _state.value.unreadableSourceCells,
                ),
            )
            while (undoStack.size > MaxUndoDepth) undoStack.removeFirst()
            redoStack.clear()
        }
        _state.update {
            it.copy(
                project = project,
                sourceDimensions = sourceDimensions,
                unreadableSourceCells = remappedUnreadableCells,
                error = null,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
        scheduleDraftPersistence(project)
        recordRecentLayoutUse(layoutUseOrigin, current, project)
        if (sourceKeysChanged) refreshDerivedBackground(project)
    }

    private fun refreshDerivedBackground(project: MergeProject) {
        when (project.backgroundStyle.kind) {
            CollageBackgroundKind.AdaptiveLinear -> scheduleAdaptiveBackground(project)
            CollageBackgroundKind.MediaBlur -> scheduleBlurredBackground(project)
            else -> {
                backgroundGeneration++
                _state.update { it.copy(blurredBackground = null) }
            }
        }
    }

    private fun scheduleAdaptiveBackground(project: MergeProject) {
        val sources = project.orderedSources()
        val generation = ++backgroundGeneration
        if (sources.isEmpty()) {
            _state.update { state ->
                val current = state.project ?: return@update state
                if (current.id != project.id) {
                    state
                } else {
                    state.copy(
                        project = current.copy(backgroundGradient = CollageGradient.Neutral),
                        blurredBackground = null,
                    )
                }
            }
            return
        }

        viewModelScope.launch {
            val gradient = adaptiveBackgroundGenerator.generate(sources)
            _state.update { state ->
                val current = state.project ?: return@update state
                if (generation != backgroundGeneration || current.id != project.id || current.orderedSourceKeys() != project.orderedSourceKeys()) {
                    state
                } else {
                    state.copy(
                        project = current.copy(
                            backgroundGradient = gradient,
                            backgroundStyle = current.backgroundStyle.copy(
                                primaryColor = gradient.startColor,
                                secondaryColor = gradient.centerColor,
                                tertiaryColor = gradient.endColor,
                            ),
                        ),
                        blurredBackground = null,
                    )
                }
            }
        }
    }

    private fun scheduleBlurredBackground(project: MergeProject) {
        val selectedCell: Int = project.backgroundStyle.blurSourceCellIndex
            ?.takeIf(project.assignedImages::containsKey)
            ?: project.template.cells.firstOrNull { project.assignedImages.containsKey(it.index) }?.index
            ?: run {
                backgroundGeneration++
                _state.update { it.copy(blurredBackground = null) }
                return
            }
        val source = project.assignedImages[selectedCell]
        val generation = ++backgroundGeneration
        if (source == null) {
            _state.update { it.copy(blurredBackground = null) }
            return
        }
        viewModelScope.launch {
            val blurred = adaptiveBackgroundGenerator.generateBlurred(
                source = source,
                radius = project.backgroundStyle.blurRadius,
            )
            _state.update { state ->
                val current = state.project ?: return@update state
                if (
                    generation != backgroundGeneration ||
                    current.id != project.id ||
                    current.backgroundStyle.kind != CollageBackgroundKind.MediaBlur ||
                    current.assignedImages[selectedCell] != source
                ) {
                    state
                } else {
                    state.copy(blurredBackground = blurred)
                }
            }
        }
    }

    private fun undoEdit() {
        val current = _state.value.project ?: return
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(
            ProjectSnapshot(current, _state.value.sourceDimensions, _state.value.unreadableSourceCells),
        )
        _state.update {
            it.copy(
                project = previous.project,
                sourceDimensions = previous.sourceDimensions,
                unreadableSourceCells = previous.unreadableSourceCells,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                error = null,
            )
        }
        hasCommittedProjectChange = true
        scheduleDraftPersistence(previous.project)
        recordRecentLayoutUse(LayoutUseOrigin.History, current, previous.project)
        if (
            current.orderedSourceKeys() != previous.project.orderedSourceKeys() ||
            current.backgroundStyle != previous.project.backgroundStyle
        ) {
            refreshDerivedBackground(previous.project)
        }
    }

    private fun redoEdit() {
        val current = _state.value.project ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(
            ProjectSnapshot(current, _state.value.sourceDimensions, _state.value.unreadableSourceCells),
        )
        _state.update {
            it.copy(
                project = next.project,
                sourceDimensions = next.sourceDimensions,
                unreadableSourceCells = next.unreadableSourceCells,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                error = null,
            )
        }
        hasCommittedProjectChange = true
        scheduleDraftPersistence(next.project)
        recordRecentLayoutUse(LayoutUseOrigin.History, current, next.project)
        if (
            current.orderedSourceKeys() != next.project.orderedSourceKeys() ||
            current.backgroundStyle != next.project.backgroundStyle
        ) {
            refreshDerivedBackground(next.project)
        }
    }

    private fun templateForImageCount(count: Int): LayoutTemplate? =
        if (count == 0) {
            _state.value.project?.template ?: templates.firstOrNull()
        } else {
            TemplateCatalog.compatibleOrFallback(
                templates = templates.filter { it.kind == com.rameshta.splitframe.domain.TemplateKind.Standard },
                imageCount = count,
                fallback = templateRepository::fallbackGridTemplate,
            ).firstOrNull()
        }

    private fun projectWithOrderedSources(
        base: MergeProject,
        sources: List<ImageSource>,
        transformsByKey: Map<String, ImageTransform>,
    ): MergeProject? {
        val assignedImages = assignSourcesToTemplateCells(base.template, sources) ?: return null
        val transforms = sources.mapIndexedNotNull { index, source ->
            transformsByKey[source.sourceKey()]?.let { base.template.cells[index].index to it }
        }.toMap()
        return base.copy(
            assignedImages = assignedImages,
            imageTransforms = transforms,
            cropShapes = base.cropShapes.filterKeys { cellIndex ->
                base.template.cells.any { it.index == cellIndex }
            },
            )
        }

    private fun recordRecentLayoutUse(
        origin: LayoutUseOrigin,
        before: MergeProject?,
        after: MergeProject,
    ) {
        val templateId = recentLayoutUseTracker.templateIdToRecord(origin, before, after) ?: return
        viewModelScope.launch {
            try {
                projectStore.recordRecentLayout(templateId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Recent discovery is best-effort and must never block editing.
            }
        }
    }

    private fun remapDimensions(
        project: MergeProject,
        dimensionsByKey: Map<String, com.rameshta.splitframe.domain.ImageDimensions>,
    ): Map<Int, com.rameshta.splitframe.domain.ImageDimensions> =
        project.assignedImages.mapNotNull { (cellIndex, source) ->
            dimensionsByKey[source.sourceKey()]?.let { cellIndex to it }
        }.toMap()

    private fun reduce(result: MergeResultEvent) {
        _state.update { state ->
            when (result) {
                is MergeResultEvent.ProjectChanged -> state.copy(
                    project = result.project,
                    error = null,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
                )
                is MergeResultEvent.ImageDimensionsLoaded -> state.copy(
                    sourceDimensions = state.sourceDimensions + (result.cellIndex to result.dimensions),
                )
                is MergeResultEvent.ExportStarted -> state.copy(
                    isExporting = true,
                    exportProgress = result.progress,
                    exportResult = null,
                    error = null,
                )
                is MergeResultEvent.ExportFinished -> state.copy(
                    isExporting = false,
                    exportProgress = 1f,
                    exportResult = result.result,
                    error = null,
                )
                is MergeResultEvent.Failed -> state.copy(error = result.messageRes)
                MergeResultEvent.ErrorCleared -> state.copy(error = null)
                MergeResultEvent.ExportResultDismissed -> state.copy(exportResult = null)
            }
        }
    }

    private fun MergeIntent.toAction(): MergeAction =
        when (this) {
            is MergeIntent.SelectTemplate -> MergeAction.SelectTemplate(templateId)
            is MergeIntent.UpdateTemplateSearch -> MergeAction.UpdateTemplateSearch(query)
            is MergeIntent.SelectTemplateCollection -> MergeAction.SelectTemplateCollection(collection)
            is MergeIntent.SelectTemplateAspect -> MergeAction.SelectTemplateAspect(aspect)
            is MergeIntent.SelectTemplateMediaCount -> MergeAction.SelectTemplateMediaCount(mediaCount)
            is MergeIntent.ToggleTemplateFavorite -> MergeAction.ToggleTemplateFavorite(templateId)
            MergeIntent.ResetTemplateDiscovery -> MergeAction.ResetTemplateDiscovery
            MergeIntent.ClearTemplateFavoriteError -> MergeAction.ClearTemplateFavoriteError
            is MergeIntent.AssignImage -> MergeAction.AssignImage(cellIndex, source)
            is MergeIntent.AssignImages -> MergeAction.AssignImages(sources)
            is MergeIntent.RemoveImage -> MergeAction.RemoveImage(cellIndex)
            is MergeIntent.ReplaceImage -> MergeAction.ReplaceImage(cellIndex, source)
            is MergeIntent.ReorderImages -> MergeAction.ReorderImages(fromIndex, toIndex)
            is MergeIntent.SwapCells -> MergeAction.SwapCells(a, b)
            is MergeIntent.UpdateImageTransform -> MergeAction.UpdateImageTransform(cellIndex, transform, trackUndo)
            is MergeIntent.ResetImageTransform -> MergeAction.ResetImageTransform(cellIndex)
            is MergeIntent.UpdateSpacing -> MergeAction.UpdateSpacing(dp)
            is MergeIntent.UpdateCornerRadius -> MergeAction.UpdateCornerRadius(dp)
            is MergeIntent.UpdateBackgroundColor -> MergeAction.UpdateBackgroundColor(argb)
            is MergeIntent.UpdateBackgroundStyle -> MergeAction.UpdateBackgroundStyle(style)
            is MergeIntent.UpdateBorderWidth -> MergeAction.UpdateBorderWidth(dp)
            is MergeIntent.UpdateBorderStyle -> MergeAction.UpdateBorderStyle(style)
            is MergeIntent.UpdateCropShape -> MergeAction.UpdateCropShape(cellIndex, shape)
            MergeIntent.AddTextLayer -> MergeAction.AddTextLayer
            is MergeIntent.UpdateTextLayer -> MergeAction.UpdateTextLayer(layer)
            is MergeIntent.DuplicateTextLayer -> MergeAction.DuplicateTextLayer(layerId)
            is MergeIntent.DeleteTextLayer -> MergeAction.DeleteTextLayer(layerId)
            is MergeIntent.UpdateBeforeAfterSlider -> MergeAction.UpdateBeforeAfterSlider(position)
            is MergeIntent.SelectExportResolution -> MergeAction.SelectExportResolution(resolution)
            MergeIntent.AutoArrange -> MergeAction.AutoArrange
            MergeIntent.UndoEdit -> MergeAction.UndoEdit
            MergeIntent.RedoEdit -> MergeAction.RedoEdit
            MergeIntent.Export -> MergeAction.Export
            MergeIntent.Reset -> MergeAction.Reset
            MergeIntent.ClearError -> MergeAction.ClearError
            MergeIntent.DismissExportResult -> MergeAction.DismissExportResult
        }

    private fun createProject(template: LayoutTemplate, resolution: ExportResolution): MergeProject =
        MergeProject(
            id = UUID.randomUUID().toString(),
            template = template,
            assignedImages = emptyMap(),
            spacingDp = template.defaultSpacingDp,
            cornerRadiusDp = template.defaultCornerRadiusDp,
            backgroundColor = 0xFFFFFFFFu,
            backgroundGradient = CollageGradient.Neutral,
            borderColor = 0x00000000u,
            borderWidthDp = 0f,
            exportResolution = resolution,
        )

    private fun scheduleDraftPersistence(project: MergeProject) {
        val encoded = PhotoDraftCodec.encode(project)
        savedStateHandle[DraftSavedStateKey] = encoded
        draftPersistenceJob?.cancel()
        draftPersistenceJob = viewModelScope.launch {
            delay(DraftPersistenceDebounceMillis)
            projectStore.setActivePhotoDraft(encoded)
        }
    }

    private fun restoreRuntimeState(project: MergeProject) {
        val generation = ++restoreValidationGeneration
        val sourceKeys = project.orderedSourceKeys()
        refreshDerivedBackground(project)
        viewModelScope.launch {
            val validations = withContext(Dispatchers.IO) {
                project.assignedImages.map { (cellIndex, source) ->
                    cellIndex to imageSourceReader.validate(source)
                }
            }
            val dimensions = validations.mapNotNull { (cellIndex, result) ->
                (result as? ImageValidationResult.Valid)?.let { cellIndex to it.dimensions }
            }.toMap()
            val hasMissingMedia = validations.any { (_, result) -> result !is ImageValidationResult.Valid }
            val unreadableCells = validations.mapNotNull { (cellIndex, result) ->
                cellIndex.takeIf { result !is ImageValidationResult.Valid }
            }.toSet()
            _state.update { current ->
                if (
                    generation != restoreValidationGeneration ||
                    current.project?.id != project.id ||
                    current.project?.orderedSourceKeys() != sourceKeys
                ) {
                    current
                } else {
                    current.copy(
                        sourceDimensions = dimensions,
                        unreadableSourceCells = unreadableCells,
                        error = if (hasMissingMedia) R.string.image_unreadable else current.error,
                    )
                }
            }
        }
    }

    private fun MergeProject.orderedSources(): List<ImageSource> =
        template.cells.mapNotNull { assignedImages[it.index] }

    private fun MergeProject.orderedSourceKeys(): List<String> =
        orderedSources().map { it.sourceKey() }

    private fun MergeProject.transformsBySourceKey(): Map<String, ImageTransform> =
        template.cells.mapNotNull { cell ->
            val source = assignedImages[cell.index] ?: return@mapNotNull null
            val transform = imageTransforms[cell.index] ?: return@mapNotNull null
            source.sourceKey() to transform
        }.toMap()

    private fun MergeProject.dimensionsBySourceKey(
        dimensions: Map<Int, com.rameshta.splitframe.domain.ImageDimensions>,
    ): Map<String, com.rameshta.splitframe.domain.ImageDimensions> =
        template.cells.mapNotNull { cell ->
            val source = assignedImages[cell.index] ?: return@mapNotNull null
            val dimension = dimensions[cell.index] ?: return@mapNotNull null
            source.sourceKey() to dimension
        }.toMap()

    private fun ImageSource.sourceKey(): String =
        when (this) {
            is ImageSource.LocalUri -> uri
        }

    private data class ProjectSnapshot(
        val project: MergeProject,
        val sourceDimensions: Map<Int, com.rameshta.splitframe.domain.ImageDimensions>,
        val unreadableSourceCells: Set<Int>,
    )

    private companion object {
        const val MaxUndoDepth = 30
        const val TransformGestureIdleMillis = 250L
        const val MaxTextLayers = 20
        const val DraftSavedStateKey = "active_photo_draft_v1"
        const val DraftPersistenceDebounceMillis = 250L
    }

    override fun onCleared() {
        adaptiveBackgroundGenerator.clear()
        super.onCleared()
    }
}

internal fun favoriteIdsWithOptimisticChanges(
    persistedIds: List<String>,
    pendingTargets: Map<String, Boolean>,
): List<String> {
    val result = persistedIds.distinct().toMutableList()
    pendingTargets.forEach { (templateId, desiredFavorite) ->
        result.remove(templateId)
        if (desiredFavorite) result.add(0, templateId)
    }
    return result
}

internal fun assignSourcesToTemplateCells(
    template: LayoutTemplate,
    sources: List<ImageSource>,
): Map<Int, ImageSource>? {
    if (!TemplateCatalog.canApplyWithoutDroppingImages(template, sources.size)) return null
    return sources.mapIndexed { index, source -> template.cells[index].index to source }.toMap()
}

internal enum class LayoutUseOrigin {
    Selection,
    UserMutation,
    History,
    ExportSuccess,
    Internal,
}

internal class RecentLayoutUseTracker {
    private var lastRecordedKey: Pair<String, String>? = null

    fun templateIdToRecord(
        origin: LayoutUseOrigin,
        before: MergeProject?,
        after: MergeProject,
    ): String? {
        val qualifies = when (origin) {
            LayoutUseOrigin.UserMutation -> before != after && after.assignedImages.isNotEmpty()
            LayoutUseOrigin.ExportSuccess -> after.isReadyForImageExport
            LayoutUseOrigin.Selection,
            LayoutUseOrigin.History,
            LayoutUseOrigin.Internal,
            -> false
        }
        if (!qualifies) return null

        val key = after.id to after.template.id
        if (key == lastRecordedKey) return null
        lastRecordedKey = key
        return after.template.id
    }
}
