package com.rameshta.splitframe.presentation.home

import com.rameshta.splitframe.data.RecentVideoProject
import com.rameshta.splitframe.data.RecentVideoProjectStatus
import com.rameshta.splitframe.domain.TemplateRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDashboardStateTest {
    @Test
    fun mapsOnlyValidPersistedContentAndKeepsItsStoredOrder() {
        val templates = TemplateRepository().templates()
        val favorite = templates[0]
        val recent = templates[1]
        val corrupt = project("corrupt", RecentVideoProjectStatus.Corrupt, updatedAtMillis = 30L)
        val resumable = project("resumable", RecentVideoProjectStatus.Ready, updatedAtMillis = 20L)
        val other = project("other", RecentVideoProjectStatus.Empty, updatedAtMillis = 10L)

        val state = buildHomeReadyState(
            projects = listOf(corrupt, resumable, other),
            favoriteTemplateIds = listOf(favorite.id, favorite.id, "removed-layout"),
            recentTemplateIds = listOf(favorite.id, recent.id, recent.id, "removed-layout"),
            templates = templates,
        )

        assertEquals(resumable, state.continueProject)
        assertEquals(listOf(corrupt, other), state.recentProjects)
        assertEquals(listOf(favorite.id), state.favoriteLayouts.map { it.id })
        assertEquals(listOf(recent.id), state.recentlyUsedLayouts.map { it.id })
    }

    @Test
    fun emptySourcesProduceAnExplicitEmptyReadyState() {
        val state = buildHomeReadyState(
            projects = emptyList(),
            favoriteTemplateIds = emptyList(),
            recentTemplateIds = emptyList(),
            templates = TemplateRepository().templates(),
        )

        assertEquals(false, state.hasPersonalizedContent)
        assertEquals(null, state.continueProject)
        assertEquals(emptyList<RecentVideoProject>(), state.recentProjects)
    }

    private fun project(
        id: String,
        status: RecentVideoProjectStatus,
        updatedAtMillis: Long,
    ) = RecentVideoProject(
        id = id,
        name = id,
        thumbnailUri = null,
        updatedAtMillis = updatedAtMillis,
        mediaCount = if (status == RecentVideoProjectStatus.Ready) 2 else 0,
        status = status,
    )
}
