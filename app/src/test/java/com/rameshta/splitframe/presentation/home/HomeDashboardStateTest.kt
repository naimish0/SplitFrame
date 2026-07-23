package com.rameshta.splitframe.presentation.home

import com.rameshta.splitframe.data.RecentVideoProject
import com.rameshta.splitframe.data.RecentVideoProjectStatus
import com.rameshta.splitframe.data.local.ExportHistoryEntity
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.ImageDimensions
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
            recentExports = listOf(
                export(
                    id = "newest",
                    templateId = favorite.id,
                    savedUri = "content://media/newest",
                    resolution = ExportResolution.FHD_1080,
                    createdAtMillis = 40L,
                ),
                export(
                    id = "bad-resolution",
                    templateId = recent.id,
                    savedUri = "content://media/bad",
                    resolutionName = "REMOVED",
                    createdAtMillis = 35L,
                ),
                export(
                    id = "missing-template",
                    templateId = "removed-layout",
                    savedUri = "content://media/older",
                    resolution = ExportResolution.HD_720,
                    createdAtMillis = 30L,
                ),
            ),
            recentExportDimensions = mapOf(
                "newest" to ImageDimensions(widthPx = 1920, heightPx = 1080),
            ),
        )

        assertEquals(resumable, state.continueProject)
        assertEquals(listOf(corrupt, other), state.recentProjects)
        assertEquals(listOf(favorite.id), state.favoriteLayouts.map { it.id })
        assertEquals(listOf(recent.id), state.recentlyUsedLayouts.map { it.id })
        assertEquals(listOf("newest", "missing-template"), state.recentPhotoExports.map { it.id })
        assertEquals(favorite.id, state.recentPhotoExports.first().template?.id)
        assertEquals(
            ImageDimensions(widthPx = 1920, heightPx = 1080),
            state.recentPhotoExports.first().dimensions,
        )
        assertEquals(null, state.recentPhotoExports.last().template)
        assertEquals(null, state.recentPhotoExports.last().dimensions)
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
        assertEquals(emptyList<RecentPhotoExport>(), state.recentPhotoExports)
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

    private fun export(
        id: String,
        templateId: String,
        savedUri: String,
        resolution: ExportResolution = ExportResolution.FHD_1080,
        resolutionName: String = resolution.name,
        createdAtMillis: Long,
    ) = ExportHistoryEntity(
        id = id,
        templateId = templateId,
        savedUri = savedUri,
        resolution = resolutionName,
        createdAtMillis = createdAtMillis,
    )
}
