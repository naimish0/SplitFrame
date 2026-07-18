package com.rameshta.splitframe.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<PreferenceEntity?>

    @Query("SELECT * FROM preferences WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): PreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: PreferenceEntity)
}

@Dao
interface ExportHistoryDao {
    @Query("SELECT * FROM export_history ORDER BY createdAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<ExportHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: ExportHistoryEntity)
}

@Dao
interface FavoriteTemplateDao {
    @Query("SELECT * FROM favorite_templates ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<FavoriteTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoriteTemplateEntity)

    @Query("DELETE FROM favorite_templates WHERE templateId = :templateId")
    suspend fun remove(templateId: String)
}

@Dao
interface VideoProjectDao {
    @Query("SELECT * FROM video_projects WHERE id = :id LIMIT 1")
    suspend fun get(id: String): VideoProjectEntity?

    @Query("SELECT * FROM video_projects WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<VideoProjectEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: VideoProjectEntity)

    @Query("DELETE FROM video_projects WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface VideoExportWorkDao {
    @Query("SELECT * FROM video_export_work WHERE projectId = :projectId LIMIT 1")
    fun observe(projectId: String): Flow<VideoExportWorkEntity?>

    @Query("SELECT * FROM video_export_work WHERE projectId = :projectId LIMIT 1")
    suspend fun get(projectId: String): VideoExportWorkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(work: VideoExportWorkEntity)

    @Query("DELETE FROM video_export_work WHERE projectId = :projectId")
    suspend fun delete(projectId: String)
}
