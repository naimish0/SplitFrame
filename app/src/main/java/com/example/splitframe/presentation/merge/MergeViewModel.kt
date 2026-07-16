package com.example.splitframe.presentation.merge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitframe.R
import com.example.splitframe.data.ProjectStore
import com.example.splitframe.domain.CollageGradient
import com.example.splitframe.domain.CollageLimits
import com.example.splitframe.domain.ExportResolution
import com.example.splitframe.domain.ExportResult
import com.example.splitframe.domain.ImageSource
import com.example.splitframe.domain.ImageTransform
import com.example.splitframe.domain.LayoutTemplate
import com.example.splitframe.domain.MergeProject
import com.example.splitframe.domain.TemplateRepository
import com.example.splitframe.export.ImageExportRepository
import com.example.splitframe.export.ImageSourceReader
import com.example.splitframe.export.ImageValidationResult
import com.example.splitframe.ml.SuperResolutionProcessor
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MergeViewModel(
    private val templateRepository: TemplateRepository,
    private val projectStore: ProjectStore,
    private val imageSourceReader: ImageSourceReader,
    private val imageExportRepository: ImageExportRepository,
    private val superResolutionProcessor: SuperResolutionProcessor,
) : ViewModel() {
    private val templates = templateRepository.templates()
    private val undoStack = ArrayDeque<ProjectSnapshot>()
    private val redoStack = ArrayDeque<ProjectSnapshot>()
    private val adaptiveBackgroundGenerator = AdaptiveCollageBackgroundGenerator(imageSourceReader)
    private var backgroundGeneration = 0
    private var exportJob: Job? = null
    private val _state = MutableStateFlow(
        MergeState(
            availableTemplates = templates,
            project = createProject(templates.first(), ExportResolution.FHD_1080),
        ),
    )
    val state: StateFlow<MergeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val lastResolution = projectStore.getLastResolution()
            _state.update { current ->
                current.copy(project = current.project?.copy(exportResolution = lastResolution))
            }
        }
    }

    fun process(intent: MergeIntent) {
        handle(intent.toAction())
    }

    private fun handle(action: MergeAction) {
        when (action) {
            is MergeAction.SelectTemplate -> selectTemplate(action.templateId)
            is MergeAction.AssignImage -> assignImage(action.cellIndex, action.source)
            is MergeAction.AssignImages -> assignImages(action.sources)
            is MergeAction.RemoveImage -> removeImage(action.cellIndex)
            is MergeAction.ReplaceImage -> assignImage(action.cellIndex, action.source)
            is MergeAction.EnhanceImage -> enhanceImage(action.cellIndex)
            is MergeAction.ResetEnhancement -> resetEnhancement(action.cellIndex)
            is MergeAction.ReorderImages -> reorderImages(action.fromIndex, action.toIndex)
            is MergeAction.SwapCells -> swapCells(action.a, action.b)
            is MergeAction.UpdateImageTransform -> updateImageTransform(action.cellIndex, action.transform, action.trackUndo)
            is MergeAction.ResetImageTransform -> updateImageTransform(action.cellIndex, ImageTransform.Default, trackUndo = true)
            is MergeAction.UpdateSpacing -> updateProject { it.copy(spacingDp = action.dp.coerceIn(0f, 36f)) }
            is MergeAction.UpdateCornerRadius -> updateProject { it.copy(cornerRadiusDp = action.dp.coerceIn(0f, 64f)) }
            is MergeAction.UpdateBackgroundColor -> updateProject {
                it.copy(
                    backgroundColor = action.argb,
                    backgroundGradient = CollageGradient.solid(action.argb),
                )
            }
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

    private fun selectTemplate(templateId: String) {
        val currentProject = _state.value.project ?: return
        val template = templates.firstOrNull { it.id == templateId } ?: return
        val orderedSources = currentProject.orderedSources()
        if (orderedSources.isNotEmpty() && orderedSources.size != template.cells.size) {
            reduce(MergeResultEvent.Failed(R.string.layout_requires_photo_count))
            return
        }

        val updated = projectWithOrderedSources(
            base = createProject(template, currentProject.exportResolution).copy(
                backgroundColor = currentProject.backgroundColor,
                backgroundGradient = currentProject.backgroundGradient,
                borderColor = currentProject.borderColor,
                borderWidthDp = currentProject.borderWidthDp,
            ),
            sources = orderedSources,
            transformsByKey = currentProject.transformsBySourceKey(),
        ).copy(
            backgroundColor = currentProject.backgroundColor,
            backgroundGradient = currentProject.backgroundGradient,
            borderColor = currentProject.borderColor,
            borderWidthDp = currentProject.borderWidthDp,
        )
        commitProjectChange(updated, remapDimensions(updated, currentProject.dimensionsBySourceKey(_state.value.sourceDimensions)))
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
                    )
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
        val targetTemplate = templateForImageCount(remainingSources.size) ?: project.template
        val updated = projectWithOrderedSources(
            base = project.copy(template = targetTemplate),
            sources = remainingSources,
            transformsByKey = transformsByKey,
        )
        commitProjectChange(updated, remapDimensions(updated, dimensionsByKey))
    }

    private fun updateImageTransform(cellIndex: Int, transform: ImageTransform, trackUndo: Boolean) {
        val project = _state.value.project ?: return
        if (!project.assignedImages.containsKey(cellIndex)) return
        commitProjectChange(
            project = project.copy(imageTransforms = project.imageTransforms + (cellIndex to transform.normalized())),
            trackUndo = trackUndo,
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
        val updated = projectWithOrderedSources(project, sources, project.transformsBySourceKey())
        commitProjectChange(updated, remapDimensions(updated, project.dimensionsBySourceKey(_state.value.sourceDimensions)))
    }

    private fun enhanceImage(cellIndex: Int) {
        if (_state.value.isEnhancing) return
        val source = _state.value.project?.assignedImages?.get(cellIndex)
        if (source == null) {
            reduce(MergeResultEvent.Failed(R.string.no_photo_selected))
            return
        }
        viewModelScope.launch {
            reduce(MergeResultEvent.EnhancementStarted(cellIndex))
            val enhanced = withContext(Dispatchers.Default) {
                superResolutionProcessor.enhance(source)
            }
            if (enhanced == null) {
                reduce(MergeResultEvent.EnhancementStopped)
                reduce(MergeResultEvent.Failed(R.string.enhance_failed))
                return@launch
            }
            val dimensions = withContext(Dispatchers.IO) {
                imageSourceReader.dimensions(enhanced)
            }
            reduce(MergeResultEvent.EnhancementFinished(cellIndex, enhanced, dimensions))
        }
    }

    private fun resetEnhancement(cellIndex: Int) {
        val enhanced = _state.value.project?.assignedImages?.get(cellIndex) as? ImageSource.Enhanced ?: return
        val original = ImageSource.LocalUri(enhanced.originalUri)
        viewModelScope.launch {
            when (val validation = withContext(Dispatchers.IO) { imageSourceReader.validate(original) }) {
                is ImageValidationResult.Valid -> {
                    val project = _state.value.project ?: return@launch
                    commitProjectChange(
                        project = project.copy(
                            assignedImages = project.assignedImages + (cellIndex to original),
                        ),
                        sourceDimensions = _state.value.sourceDimensions + (cellIndex to validation.dimensions),
                    )
                }
                ImageValidationResult.UnsupportedFormat -> reduce(MergeResultEvent.Failed(R.string.image_unsupported_format))
                ImageValidationResult.Unreadable -> reduce(MergeResultEvent.Failed(R.string.image_unreadable))
            }
        }
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

        commitProjectChange(project.copy(assignedImages = images, imageTransforms = transforms), dimensions)
    }

    private fun autoArrange() {
        val project = _state.value.project ?: return
        val sources = project.orderedSources()
        if (sources.isEmpty()) return
        val targetTemplate = templateForImageCount(sources.size) ?: run {
            reduce(MergeResultEvent.Failed(R.string.layout_requires_photo_count))
            return
        }
        val updated = projectWithOrderedSources(
            base = project.copy(
                template = targetTemplate,
                spacingDp = targetTemplate.defaultSpacingDp,
                cornerRadiusDp = targetTemplate.defaultCornerRadiusDp,
            ),
            sources = sources,
            transformsByKey = project.transformsBySourceKey(),
        )
        commitProjectChange(updated, remapDimensions(updated, project.dimensionsBySourceKey(_state.value.sourceDimensions)))
    }

    private fun selectResolution(resolution: ExportResolution) {
        updateProject { it.copy(exportResolution = resolution) }
        viewModelScope.launch {
            projectStore.setLastResolution(resolution)
        }
    }

    private fun export() {
        if (_state.value.isExporting || exportJob?.isActive == true) return
        val project = _state.value.project ?: return
        val requiredCells = project.template.cells.map { it.index }.toSet()
        if (!project.assignedImages.keys.containsAll(requiredCells)) {
            reduce(MergeResultEvent.Failed(R.string.missing_images))
            return
        }
        reduce(MergeResultEvent.ExportStarted())
        exportJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    imageExportRepository.export(project)
                }
                if (result is ExportResult.Success) {
                    projectStore.addExportHistory(
                        id = UUID.randomUUID().toString(),
                        templateId = project.template.id,
                        savedUri = result.savedUri,
                        resolution = project.exportResolution,
                        createdAtMillis = System.currentTimeMillis(),
                    )
                }
                reduce(MergeResultEvent.ExportFinished(result))
            } catch (throwable: Throwable) {
                reduce(ExportResult.Failure(throwable.message ?: "Unknown export error.").let(MergeResultEvent::ExportFinished))
            } finally {
                exportJob = null
            }
        }
    }

    private fun reset() {
        val current = _state.value.project ?: return
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

    private fun updateProject(transform: (MergeProject) -> MergeProject) {
        val project = _state.value.project ?: return
        commitProjectChange(transform(project))
    }

    private fun appendValidImages(validSources: List<Pair<ImageSource, com.example.splitframe.domain.ImageDimensions>>) {
        val project = _state.value.project ?: return
        val existingSources = project.orderedSources()
        val allSources = existingSources + validSources.map { it.first }
        val targetTemplate = if (project.template.cells.size == allSources.size) {
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
        val updated = projectWithOrderedSources(
            base = project.copy(
                template = targetTemplate,
                spacingDp = if (project.template.cells.size == allSources.size) project.spacingDp else targetTemplate.defaultSpacingDp,
                cornerRadiusDp = if (project.template.cells.size == allSources.size) {
                    project.cornerRadiusDp
                } else {
                    targetTemplate.defaultCornerRadiusDp
                },
            ),
            sources = allSources,
            transformsByKey = project.transformsBySourceKey(),
        )
        commitProjectChange(updated, remapDimensions(updated, dimensionsByKey))
    }

    private fun commitProjectChange(
        project: MergeProject,
        sourceDimensions: Map<Int, com.example.splitframe.domain.ImageDimensions> = _state.value.sourceDimensions,
        trackUndo: Boolean = true,
    ) {
        val current = _state.value.project
        val sourceKeysChanged = current?.orderedSourceKeys() != project.orderedSourceKeys()
        if (trackUndo && current != null && current != project) {
            undoStack.addLast(ProjectSnapshot(current, _state.value.sourceDimensions))
            while (undoStack.size > MaxUndoDepth) undoStack.removeFirst()
            redoStack.clear()
        }
        _state.update {
            it.copy(
                project = project,
                sourceDimensions = sourceDimensions,
                error = null,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
        if (sourceKeysChanged) {
            scheduleAdaptiveBackground(project)
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
                    state.copy(project = current.copy(backgroundGradient = CollageGradient.Neutral))
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
                    state.copy(project = current.copy(backgroundGradient = gradient))
                }
            }
        }
    }

    private fun undoEdit() {
        val current = _state.value.project ?: return
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(ProjectSnapshot(current, _state.value.sourceDimensions))
        _state.update {
            it.copy(
                project = previous.project,
                sourceDimensions = previous.sourceDimensions,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                error = null,
            )
        }
        if (current.orderedSourceKeys() != previous.project.orderedSourceKeys()) {
            scheduleAdaptiveBackground(previous.project)
        }
    }

    private fun redoEdit() {
        val current = _state.value.project ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(ProjectSnapshot(current, _state.value.sourceDimensions))
        _state.update {
            it.copy(
                project = next.project,
                sourceDimensions = next.sourceDimensions,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                error = null,
            )
        }
        if (current.orderedSourceKeys() != next.project.orderedSourceKeys()) {
            scheduleAdaptiveBackground(next.project)
        }
    }

    private fun templateForImageCount(count: Int): LayoutTemplate? =
        when (count) {
            0 -> _state.value.project?.template ?: templates.firstOrNull()
            7 -> templates.firstOrNull { it.id == com.example.splitframe.domain.TemplateIds.ADAPTIVE_GRID_7 }
            8 -> templates.firstOrNull { it.id == com.example.splitframe.domain.TemplateIds.ADAPTIVE_GRID_8 }
            9 -> templates.firstOrNull { it.id == com.example.splitframe.domain.TemplateIds.ADAPTIVE_GRID_9 }
            else -> templates.firstOrNull { it.cells.size == count && it.kind != com.example.splitframe.domain.TemplateKind.BeforeAfter }
        }

    private fun projectWithOrderedSources(
        base: MergeProject,
        sources: List<ImageSource>,
        transformsByKey: Map<String, ImageTransform>,
    ): MergeProject {
        val assignedImages = sources.mapIndexed { index, source ->
            base.template.cells[index].index to source
        }.toMap()
        val transforms = sources.mapIndexedNotNull { index, source ->
            transformsByKey[source.sourceKey()]?.let { base.template.cells[index].index to it }
        }.toMap()
        return base.copy(
            assignedImages = assignedImages,
            imageTransforms = transforms,
        )
    }

    private fun remapDimensions(
        project: MergeProject,
        dimensionsByKey: Map<String, com.example.splitframe.domain.ImageDimensions>,
    ): Map<Int, com.example.splitframe.domain.ImageDimensions> =
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
                is MergeResultEvent.EnhancementStarted -> state.copy(
                    isEnhancing = true,
                    enhancingCellIndex = result.cellIndex,
                    error = null,
                )
                is MergeResultEvent.EnhancementFinished -> {
                    val project = state.project
                    state.copy(
                        project = project?.copy(
                            assignedImages = project.assignedImages + (result.cellIndex to result.source),
                        ),
                        sourceDimensions = if (result.dimensions != null) {
                            state.sourceDimensions + (result.cellIndex to result.dimensions)
                        } else {
                            state.sourceDimensions
                        },
                        isEnhancing = false,
                        enhancingCellIndex = null,
                        error = null,
                    )
                }
                MergeResultEvent.EnhancementStopped -> state.copy(
                    isEnhancing = false,
                    enhancingCellIndex = null,
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
            is MergeIntent.AssignImage -> MergeAction.AssignImage(cellIndex, source)
            is MergeIntent.AssignImages -> MergeAction.AssignImages(sources)
            is MergeIntent.RemoveImage -> MergeAction.RemoveImage(cellIndex)
            is MergeIntent.ReplaceImage -> MergeAction.ReplaceImage(cellIndex, source)
            is MergeIntent.EnhanceImage -> MergeAction.EnhanceImage(cellIndex)
            is MergeIntent.ResetEnhancement -> MergeAction.ResetEnhancement(cellIndex)
            is MergeIntent.ReorderImages -> MergeAction.ReorderImages(fromIndex, toIndex)
            is MergeIntent.SwapCells -> MergeAction.SwapCells(a, b)
            is MergeIntent.UpdateImageTransform -> MergeAction.UpdateImageTransform(cellIndex, transform, trackUndo)
            is MergeIntent.ResetImageTransform -> MergeAction.ResetImageTransform(cellIndex)
            is MergeIntent.UpdateSpacing -> MergeAction.UpdateSpacing(dp)
            is MergeIntent.UpdateCornerRadius -> MergeAction.UpdateCornerRadius(dp)
            is MergeIntent.UpdateBackgroundColor -> MergeAction.UpdateBackgroundColor(argb)
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
        dimensions: Map<Int, com.example.splitframe.domain.ImageDimensions>,
    ): Map<String, com.example.splitframe.domain.ImageDimensions> =
        template.cells.mapNotNull { cell ->
            val source = assignedImages[cell.index] ?: return@mapNotNull null
            val dimension = dimensions[cell.index] ?: return@mapNotNull null
            source.sourceKey() to dimension
        }.toMap()

    private fun ImageSource.sourceKey(): String =
        when (this) {
            is ImageSource.Enhanced -> originalUri
            is ImageSource.LocalUri -> uri
        }

    private data class ProjectSnapshot(
        val project: MergeProject,
        val sourceDimensions: Map<Int, com.example.splitframe.domain.ImageDimensions>,
    )

    private companion object {
        const val MaxUndoDepth = 30
    }
}
