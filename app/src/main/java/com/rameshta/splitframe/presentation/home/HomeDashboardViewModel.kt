package com.rameshta.splitframe.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rameshta.splitframe.data.ProjectStore
import com.rameshta.splitframe.data.RecentVideoProject
import com.rameshta.splitframe.data.RecentVideoProjectStatus
import com.rameshta.splitframe.data.RecentVideoProjectStore
import com.rameshta.splitframe.data.local.ExportHistoryEntity
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.ImageDimensions
import com.rameshta.splitframe.domain.ImageSource
import com.rameshta.splitframe.domain.LayoutTemplate
import com.rameshta.splitframe.domain.TemplateRepository
import com.rameshta.splitframe.export.ImageSourceReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Ready(
        val continueProject: RecentVideoProject? = null,
        val recentProjects: List<RecentVideoProject> = emptyList(),
        val recentPhotoExports: List<RecentPhotoExport> = emptyList(),
        val favoriteLayouts: List<LayoutTemplate> = emptyList(),
        val recentlyUsedLayouts: List<LayoutTemplate> = emptyList(),
    ) : HomeUiState {
        val hasPersonalizedContent: Boolean
            get() = continueProject != null ||
                recentProjects.isNotEmpty() ||
                recentPhotoExports.isNotEmpty() ||
                favoriteLayouts.isNotEmpty() ||
                recentlyUsedLayouts.isNotEmpty()
    }

    data object Error : HomeUiState
}

data class RecentPhotoExport(
    val id: String,
    val savedUri: String,
    val template: LayoutTemplate?,
    val resolution: ExportResolution,
    val createdAtMillis: Long,
    val dimensions: ImageDimensions? = null,
)

class HomeDashboardViewModel(
    recentVideoProjectStore: RecentVideoProjectStore,
    projectStore: ProjectStore,
    templateRepository: TemplateRepository,
    imageSourceReader: ImageSourceReader,
) : ViewModel() {
    private val templates = templateRepository.templates()
    private val recentExportsWithDimensions = projectStore.observeRecentExports()
        .map { exports ->
            withContext(Dispatchers.IO) {
                RecentExportsWithDimensions(
                    exports = exports,
                    dimensionsById = exports
                        .mapNotNull { export ->
                            imageSourceReader
                                .dimensions(ImageSource.LocalUri(export.savedUri))
                                ?.let { export.id to it }
                        }
                        .toMap(),
                )
            }
        }

    private val states: Flow<HomeUiState> = combine(
        recentVideoProjectStore.observeProjects(),
        projectStore.observeFavoriteTemplates(),
        projectStore.observeRecentLayouts(),
        recentExportsWithDimensions,
    ) { projects, favoriteTemplateIds, recentTemplateIds, recentExports ->
        val readyState: HomeUiState = buildHomeReadyState(
            projects = projects,
            favoriteTemplateIds = favoriteTemplateIds,
            recentTemplateIds = recentTemplateIds,
            templates = templates,
            recentExports = recentExports.exports,
            recentExportDimensions = recentExports.dimensionsById,
        )
        readyState
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(HomeUiState.Error)
    }

    val state: StateFlow<HomeUiState> = states
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(StopTimeoutMillis),
            initialValue = HomeUiState.Loading,
        )

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}

internal fun buildHomeReadyState(
    projects: List<RecentVideoProject>,
    favoriteTemplateIds: List<String>,
    recentTemplateIds: List<String>,
    templates: List<LayoutTemplate>,
    recentExports: List<ExportHistoryEntity> = emptyList(),
    recentExportDimensions: Map<String, ImageDimensions> = emptyMap(),
): HomeUiState.Ready {
    val templatesById = templates.associateBy(LayoutTemplate::id)
    val continueProject = projects.firstOrNull { it.status != RecentVideoProjectStatus.Corrupt }
    val favoriteLayouts = favoriteTemplateIds
        .distinct()
        .mapNotNull(templatesById::get)
        .take(MaxHomeLayouts)
    val favoriteLayoutIds = favoriteLayouts.mapTo(mutableSetOf(), LayoutTemplate::id)
    val recentlyUsedLayouts = recentTemplateIds
        .asSequence()
        .distinct()
        .filterNot(favoriteLayoutIds::contains)
        .mapNotNull(templatesById::get)
        .take(MaxHomeLayouts)
        .toList()
    val recentPhotoExports = recentExports
        .asSequence()
        .filter { export ->
            export.id.isNotBlank() &&
                export.savedUri.length in 2..MaxSavedUriLength &&
                export.savedUri.startsWith(ContentUriPrefix)
        }
        .mapNotNull { export ->
            val resolution = ExportResolution.entries.firstOrNull { it.name == export.resolution }
                ?: return@mapNotNull null
            RecentPhotoExport(
                id = export.id,
                savedUri = export.savedUri,
                template = templatesById[export.templateId],
                resolution = resolution,
                createdAtMillis = export.createdAtMillis,
                dimensions = recentExportDimensions[export.id],
            )
        }
        .take(MaxRecentPhotoExports)
        .toList()

    return HomeUiState.Ready(
        continueProject = continueProject,
        recentProjects = projects
            .filterNot { it.id == continueProject?.id }
            .take(MaxRecentProjects),
        recentPhotoExports = recentPhotoExports,
        favoriteLayouts = favoriteLayouts,
        recentlyUsedLayouts = recentlyUsedLayouts,
    )
}

private data class RecentExportsWithDimensions(
    val exports: List<ExportHistoryEntity>,
    val dimensionsById: Map<String, ImageDimensions>,
)

private const val MaxRecentProjects = 5
private const val MaxHomeLayouts = 8
private const val MaxRecentPhotoExports = 8
private const val MaxSavedUriLength = 4_096
private const val ContentUriPrefix = "content://"
