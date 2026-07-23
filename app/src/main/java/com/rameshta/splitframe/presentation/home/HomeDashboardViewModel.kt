package com.rameshta.splitframe.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rameshta.splitframe.data.ProjectStore
import com.rameshta.splitframe.data.RecentVideoProject
import com.rameshta.splitframe.data.RecentVideoProjectStatus
import com.rameshta.splitframe.data.RecentVideoProjectStore
import com.rameshta.splitframe.domain.LayoutTemplate
import com.rameshta.splitframe.domain.TemplateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Ready(
        val continueProject: RecentVideoProject? = null,
        val recentProjects: List<RecentVideoProject> = emptyList(),
        val favoriteLayouts: List<LayoutTemplate> = emptyList(),
        val recentlyUsedLayouts: List<LayoutTemplate> = emptyList(),
    ) : HomeUiState {
        val hasPersonalizedContent: Boolean
            get() = continueProject != null ||
                recentProjects.isNotEmpty() ||
                favoriteLayouts.isNotEmpty() ||
                recentlyUsedLayouts.isNotEmpty()
    }

    data object Error : HomeUiState
}

class HomeDashboardViewModel(
    recentVideoProjectStore: RecentVideoProjectStore,
    projectStore: ProjectStore,
    templateRepository: TemplateRepository,
) : ViewModel() {
    private val templates = templateRepository.templates()

    private val states: Flow<HomeUiState> = combine(
        recentVideoProjectStore.observeProjects(),
        projectStore.observeFavoriteTemplates(),
        projectStore.observeRecentLayouts(),
    ) { projects, favoriteTemplateIds, recentTemplateIds ->
        val readyState: HomeUiState = buildHomeReadyState(
            projects = projects,
            favoriteTemplateIds = favoriteTemplateIds,
            recentTemplateIds = recentTemplateIds,
            templates = templates,
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

    return HomeUiState.Ready(
        continueProject = continueProject,
        recentProjects = projects
            .filterNot { it.id == continueProject?.id }
            .take(MaxRecentProjects),
        favoriteLayouts = favoriteLayouts,
        recentlyUsedLayouts = recentlyUsedLayouts,
    )
}

private const val MaxRecentProjects = 5
private const val MaxHomeLayouts = 8
