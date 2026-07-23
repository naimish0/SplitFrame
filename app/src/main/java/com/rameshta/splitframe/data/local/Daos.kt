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
abstract class RecentLayoutDao {
    @Query(
        """
        SELECT * FROM recent_layouts
        ORDER BY usedAtMillis DESC, templateId ASC
        LIMIT :limit
        """,
    )
    abstract fun observeRecent(limit: Int = 20): Flow<List<RecentLayoutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsert(layout: RecentLayoutEntity)

    @Query("SELECT MAX(usedAtMillis) FROM recent_layouts")
    protected abstract suspend fun latestUsedAtMillis(): Long?

    @Query(
        """
        DELETE FROM recent_layouts
        WHERE templateId NOT IN (
            SELECT templateId FROM recent_layouts
            ORDER BY usedAtMillis DESC, templateId ASC
            LIMIT :limit
        )
        """,
    )
    protected abstract suspend fun pruneToLimit(limit: Int)

    @androidx.room.Transaction
    open suspend fun recordUse(
        templateId: String,
        usedAtMillis: Long,
        limit: Int = 20,
    ) {
        require(templateId.isNotBlank())
        require(limit > 0)
        val latest = latestUsedAtMillis()
        val orderedTimestamp = when {
            latest == null -> usedAtMillis
            latest == Long.MAX_VALUE -> Long.MAX_VALUE
            else -> maxOf(usedAtMillis, latest + 1L)
        }
        upsert(RecentLayoutEntity(templateId, orderedTimestamp))
        pruneToLimit(limit)
    }
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

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM video_export_work
            WHERE state IN ('queued', 'running')
        )
        """,
    )
    fun observeHasActiveExport(): Flow<Boolean>

    @Query("SELECT * FROM video_export_work WHERE projectId = :projectId LIMIT 1")
    suspend fun get(projectId: String): VideoExportWorkEntity?

    @Query("SELECT * FROM video_export_work WHERE state IN ('queued', 'running')")
    suspend fun getActive(): List<VideoExportWorkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(work: VideoExportWorkEntity)

    @Query(
        """
        UPDATE video_export_work
        SET state = :state,
            progress = :progress,
            outputUri = :outputUri,
            errorMessage = :errorMessage,
            updatedAtMillis = :updatedAtMillis
        WHERE projectId = :projectId
            AND workId = :workId
            AND state IN (:expectedStates)
        """,
    )
    suspend fun updateIfWorkMatches(
        projectId: String,
        workId: String,
        state: String,
        progress: Float,
        outputUri: String?,
        errorMessage: String?,
        updatedAtMillis: Long,
        expectedStates: List<String>,
    ): Int

    @Query("DELETE FROM video_export_work WHERE projectId = :projectId")
    suspend fun delete(projectId: String)
}

@Dao
abstract class RecentProjectDao {
    @Query(
        """
        SELECT * FROM recent_projects
        WHERE deletedAtMillis IS NULL
        ORDER BY updatedAtMillis DESC, projectId ASC
        """,
    )
    abstract fun observeActive(): Flow<List<RecentProjectEntity>>

    @Query("SELECT * FROM recent_projects WHERE projectId = :projectId LIMIT 1")
    abstract suspend fun get(projectId: String): RecentProjectEntity?

    @Query("SELECT * FROM video_projects WHERE id = :projectId LIMIT 1")
    protected abstract suspend fun getVideoProject(projectId: String): VideoProjectEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertVideoProject(project: VideoProjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun replaceVideoProject(project: VideoProjectEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertRecentProject(project: RecentProjectEntity): Long

    @Query(
        """
        UPDATE recent_projects
        SET projectFormatVersion = :projectFormatVersion,
            layoutVersion = :layoutVersion,
            thumbnailUri = :thumbnailUri,
            updatedAtMillis = :updatedAtMillis
        WHERE projectId = :projectId AND deletedAtMillis IS NULL
        """,
    )
    protected abstract suspend fun touchActiveProject(
        projectId: String,
        projectFormatVersion: Int,
        layoutVersion: Int,
        thumbnailUri: String?,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE recent_projects
        SET name = :name, updatedAtMillis = :updatedAtMillis
        WHERE projectId = :projectId AND deletedAtMillis IS NULL
        """,
    )
    abstract suspend fun renameActive(
        projectId: String,
        name: String,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE recent_projects
        SET deletedAtMillis = :deletedAtMillis, deletionToken = :deletionToken
        WHERE projectId = :projectId
            AND deletedAtMillis IS NULL
            AND NOT EXISTS (
                SELECT 1 FROM video_export_work
                WHERE video_export_work.projectId = :projectId
                    AND video_export_work.state IN ('queued', 'running')
            )
        """,
    )
    abstract suspend fun softDeleteActive(
        projectId: String,
        deletedAtMillis: Long,
        deletionToken: String,
    ): Int

    @Query(
        """
        UPDATE recent_projects
        SET deletedAtMillis = NULL, deletionToken = NULL, updatedAtMillis = :updatedAtMillis
        WHERE projectId = :projectId
            AND deletedAtMillis IS NOT NULL
            AND deletionToken = :deletionToken
        """,
    )
    abstract suspend fun undoDelete(
        projectId: String,
        deletionToken: String,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM video_export_work
            WHERE projectId = :projectId AND state IN ('queued', 'running')
        )
        """,
    )
    abstract suspend fun hasActiveExport(projectId: String): Boolean

    @Query("DELETE FROM video_export_work WHERE projectId = :projectId")
    protected abstract suspend fun deleteExportWork(projectId: String)

    @Query("DELETE FROM video_projects WHERE id = :projectId")
    protected abstract suspend fun deleteVideoProject(projectId: String)

    @Query("DELETE FROM recent_projects WHERE projectId = :projectId")
    protected abstract suspend fun deleteRecentProject(projectId: String)

    @Query(
        """
        SELECT * FROM recent_projects
        WHERE deletedAtMillis IS NOT NULL AND deletedAtMillis <= :cutoffMillis
        """,
    )
    protected abstract suspend fun getDeletedBefore(cutoffMillis: Long): List<RecentProjectEntity>

    @androidx.room.Transaction
    open suspend fun createVideoProject(
        project: VideoProjectEntity,
        recentProject: RecentProjectEntity,
    ): Boolean {
        val existingRecent = get(project.id)
        if (existingRecent != null) return false
        if (getVideoProject(project.id) != null) return false
        if (insertVideoProject(project) == -1L) return false
        if (insertRecentProject(recentProject) == -1L) {
            deleteVideoProject(project.id)
            return false
        }
        return true
    }

    @androidx.room.Transaction
    open suspend fun saveActiveVideoProject(
        project: VideoProjectEntity,
        recentProject: RecentProjectEntity,
    ): Boolean {
        val existingRecent = get(project.id)
        if (existingRecent == null) {
            val existingProject = getVideoProject(project.id)
            if (existingProject == null) {
                return createVideoProject(project, recentProject)
            }
            if (insertRecentProject(recentProject) == -1L) return false
        } else if (existingRecent.deletedAtMillis != null) {
            return false
        }
        replaceVideoProject(project)
        return touchActiveProject(
            projectId = project.id,
            projectFormatVersion = recentProject.projectFormatVersion,
            layoutVersion = recentProject.layoutVersion,
            thumbnailUri = recentProject.thumbnailUri,
            updatedAtMillis = recentProject.updatedAtMillis,
        ) == 1
    }

    @androidx.room.Transaction
    open suspend fun resetActiveVideoProject(
        project: VideoProjectEntity,
        recentProject: RecentProjectEntity,
    ): Boolean {
        val saved = saveActiveVideoProject(project, recentProject)
        if (saved) deleteExportWork(project.id)
        return saved
    }

    @androidx.room.Transaction
    open suspend fun purgeDeletedVideoProject(
        projectId: String,
        deletionToken: String,
    ): Boolean {
        val recent = get(projectId)
        if (recent?.deletedAtMillis == null || recent.deletionToken != deletionToken) return false
        deleteExportWork(projectId)
        deleteVideoProject(projectId)
        deleteRecentProject(projectId)
        return true
    }

    @androidx.room.Transaction
    open suspend fun purgeDeletedVideoProjectsBefore(cutoffMillis: Long): Int {
        var purged = 0
        getDeletedBefore(cutoffMillis).forEach { project ->
            val token = project.deletionToken ?: return@forEach
            if (purgeDeletedVideoProject(project.projectId, token)) purged += 1
        }
        return purged
    }
}
