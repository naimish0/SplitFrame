package com.rameshta.splitframe.data

import com.rameshta.splitframe.data.local.ExportHistoryDao
import com.rameshta.splitframe.data.local.ExportHistoryEntity
import com.rameshta.splitframe.data.local.FavoriteTemplateDao
import com.rameshta.splitframe.data.local.FavoriteTemplateEntity
import com.rameshta.splitframe.data.local.PreferenceDao
import com.rameshta.splitframe.data.local.PreferenceEntity
import com.rameshta.splitframe.domain.ExportResolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val LastResolutionKey = "last_export_resolution"

class ProjectStore(
    private val preferenceDao: PreferenceDao,
    private val exportHistoryDao: ExportHistoryDao,
    private val favoriteTemplateDao: FavoriteTemplateDao,
) {
    fun observeLastResolution(): Flow<ExportResolution> =
        preferenceDao.observe(LastResolutionKey).map { entity ->
            entity?.value?.let(::resolutionFromName) ?: ExportResolution.FHD_1080
        }

    suspend fun getLastResolution(): ExportResolution =
        preferenceDao.get(LastResolutionKey)?.value?.let(::resolutionFromName) ?: ExportResolution.FHD_1080

    suspend fun setLastResolution(resolution: ExportResolution) {
        preferenceDao.upsert(PreferenceEntity(LastResolutionKey, resolution.name))
    }

    suspend fun addExportHistory(
        id: String,
        templateId: String,
        savedUri: String,
        resolution: ExportResolution,
        createdAtMillis: Long,
    ) {
        exportHistoryDao.insert(
            ExportHistoryEntity(
                id = id,
                templateId = templateId,
                savedUri = savedUri,
                resolution = resolution.name,
                createdAtMillis = createdAtMillis,
            ),
        )
    }

    fun observeRecentExports(): Flow<List<ExportHistoryEntity>> = exportHistoryDao.observeRecent()

    fun observeFavoriteTemplates(): Flow<List<String>> =
        favoriteTemplateDao.observeAll().map { favorites -> favorites.map { it.templateId } }

    suspend fun setTemplateFavorite(templateId: String, favorite: Boolean) {
        if (favorite) {
            favoriteTemplateDao.add(FavoriteTemplateEntity(templateId, System.currentTimeMillis()))
        } else {
            favoriteTemplateDao.remove(templateId)
        }
    }

    private fun resolutionFromName(name: String): ExportResolution =
        ExportResolution.entries.firstOrNull { it.name == name } ?: ExportResolution.FHD_1080
}
