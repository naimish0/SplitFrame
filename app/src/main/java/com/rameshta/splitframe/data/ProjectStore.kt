package com.rameshta.splitframe.data

import com.rameshta.splitframe.data.local.ExportHistoryDao
import com.rameshta.splitframe.data.local.ExportHistoryEntity
import com.rameshta.splitframe.data.local.FavoriteTemplateDao
import com.rameshta.splitframe.data.local.FavoriteTemplateEntity
import com.rameshta.splitframe.data.local.PreferenceDao
import com.rameshta.splitframe.data.local.PreferenceEntity
import com.rameshta.splitframe.data.local.RecentLayoutDao
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.SingleImageExportSettings
import com.rameshta.splitframe.domain.SavedResizePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val LastResolutionKey = "last_export_resolution"
private const val SingleImageExportSettingsKey = "single_image_export_settings_v1"
private const val ActivePhotoDraftKey = "active_photo_draft_v1"
private const val SavedResizePresetsKey = "saved_resize_presets_v1"

class ProjectStore(
    private val preferenceDao: PreferenceDao,
    private val exportHistoryDao: ExportHistoryDao,
    private val favoriteTemplateDao: FavoriteTemplateDao,
    private val recentLayoutDao: RecentLayoutDao,
    private val clock: () -> Long = System::currentTimeMillis,
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

    suspend fun getSingleImageExportSettings(): SingleImageExportSettings? =
        preferenceDao.get(SingleImageExportSettingsKey)
            ?.value
            ?.let(SingleImageExportSettingsCodec::decode)

    suspend fun setSingleImageExportSettings(settings: SingleImageExportSettings) {
        preferenceDao.upsert(
            PreferenceEntity(
                key = SingleImageExportSettingsKey,
                value = SingleImageExportSettingsCodec.encode(settings),
            ),
        )
    }

    suspend fun getSavedResizePresets(): List<SavedResizePreset> =
        preferenceDao.get(SavedResizePresetsKey)
            ?.value
            ?.let(SavedResizePresetsCodec::decode)
            .orEmpty()

    suspend fun setSavedResizePresets(presets: List<SavedResizePreset>) {
        require(presets.size <= SavedResizePresetsCodec.MaxPresets)
        preferenceDao.upsert(
            PreferenceEntity(
                key = SavedResizePresetsKey,
                value = SavedResizePresetsCodec.encode(presets),
            ),
        )
    }

    suspend fun getActivePhotoDraft(): String? = preferenceDao.get(ActivePhotoDraftKey)?.value

    suspend fun setActivePhotoDraft(encodedDraft: String) {
        preferenceDao.upsert(PreferenceEntity(ActivePhotoDraftKey, encodedDraft))
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

    fun observeRecentLayouts(): Flow<List<String>> =
        recentLayoutDao.observeRecent().map { layouts -> layouts.map { it.templateId } }

    suspend fun setTemplateFavorite(templateId: String, favorite: Boolean) {
        if (favorite) {
            favoriteTemplateDao.add(FavoriteTemplateEntity(templateId, clock()))
        } else {
            favoriteTemplateDao.remove(templateId)
        }
    }

    suspend fun recordRecentLayout(templateId: String) {
        recentLayoutDao.recordUse(
            templateId = templateId,
            usedAtMillis = clock(),
            limit = RecentLayoutLimit,
        )
    }

    private fun resolutionFromName(name: String): ExportResolution =
        ExportResolution.entries.firstOrNull { it.name == name } ?: ExportResolution.FHD_1080

    private companion object {
        const val RecentLayoutLimit = 20
    }
}
