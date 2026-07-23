package com.rameshta.splitframe.data

import android.content.ContentResolver
import androidx.core.net.toUri
import com.rameshta.splitframe.data.local.RecentProjectDao
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

enum class RecentVideoProjectStatus {
    Ready,
    Empty,
    MissingMedia,
    Corrupt,
}

data class RecentVideoProject(
    val id: String,
    val name: String,
    val thumbnailUri: String?,
    val updatedAtMillis: Long,
    val mediaCount: Int,
    val status: RecentVideoProjectStatus,
    val missingMediaCount: Int = 0,
)

data class DeletedVideoProject(
    val projectId: String,
    val deletionToken: String,
)

sealed interface DeleteVideoProjectResult {
    data class Deleted(val deletion: DeletedVideoProject) : DeleteVideoProjectResult
    data object ExportActive : DeleteVideoProjectResult
    data object NotFound : DeleteVideoProjectResult
}

fun interface MediaUriAccess {
    fun isReadable(uri: String): Boolean
}

class ContentResolverMediaUriAccess(
    private val contentResolver: ContentResolver,
) : MediaUriAccess {
    override fun isReadable(uri: String): Boolean =
        try {
            contentResolver.openFileDescriptor(uri.toUri(), "r")?.use { true } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
}

class RecentVideoProjectStore(
    private val recentProjectDao: RecentProjectDao,
    private val videoProjectStore: VideoProjectStore,
    private val mediaUriAccess: MediaUriAccess,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun observeProjects(): Flow<List<RecentVideoProject>> =
        recentProjectDao.observeActive()
            .onStart { recentProjectDao.purgeDeletedVideoProjectsBefore(clock() - UndoRetentionMillis) }
            .map { entities ->
                withContext(Dispatchers.IO) {
                    entities
                        .filter { it.projectType == ProjectTypeVideo }
                        .mapNotNull { entity ->
                            val inspection = if (
                                entity.projectFormatVersion == SupportedProjectFormatVersion &&
                                entity.layoutVersion == SupportedLayoutVersion
                            ) {
                                videoProjectStore.inspect(entity.projectId)
                            } else {
                                VideoProjectReadResult.Corrupt(entity.projectId)
                            }
                            when (inspection) {
                                is VideoProjectReadResult.Ready -> {
                                    val media = inspection.project.mediaByCell.values
                                    if (media.isEmpty()) return@mapNotNull null
                                    val missingCount = media
                                        .map { it.uri }
                                        .distinct()
                                        .count { uri -> !mediaUriAccess.isReadable(uri) }
                                    val status = when {
                                        missingCount > 0 -> RecentVideoProjectStatus.MissingMedia
                                        else -> RecentVideoProjectStatus.Ready
                                    }
                                    RecentVideoProject(
                                        id = entity.projectId,
                                        name = entity.name,
                                        thumbnailUri = entity.thumbnailUri,
                                        updatedAtMillis = entity.updatedAtMillis,
                                        mediaCount = media.size,
                                        status = status,
                                        missingMediaCount = missingCount,
                                    )
                                }
                                is VideoProjectReadResult.Corrupt,
                                VideoProjectReadResult.NotFound,
                                -> RecentVideoProject(
                                    id = entity.projectId,
                                    name = entity.name,
                                    thumbnailUri = null,
                                    updatedAtMillis = entity.updatedAtMillis,
                                    mediaCount = 0,
                                    status = RecentVideoProjectStatus.Corrupt,
                                )
                            }
                        }
                }
            }

    suspend fun rename(projectId: String, requestedName: String): Boolean {
        val name = requestedName.normalizedProjectName() ?: return false
        return recentProjectDao.renameActive(projectId, name, clock()) == 1
    }

    suspend fun duplicate(projectId: String): String? {
        val source = (videoProjectStore.inspect(projectId) as? VideoProjectReadResult.Ready)?.project
            ?: return null
        val metadata = recentProjectDao.get(projectId)?.takeIf { it.deletedAtMillis == null }
            ?: return null
        val newId = idFactory()
        val duplicateName = "${metadata.name} copy".take(MaxProjectNameLength)
        return if (videoProjectStore.save(source.copy(id = newId), duplicateName)) newId else null
    }

    suspend fun delete(projectId: String): DeleteVideoProjectResult {
        if (recentProjectDao.hasActiveExport(projectId)) return DeleteVideoProjectResult.ExportActive
        val deletion = DeletedVideoProject(projectId, idFactory())
        val deleted = recentProjectDao.softDeleteActive(
            projectId = projectId,
            deletedAtMillis = clock(),
            deletionToken = deletion.deletionToken,
        )
        if (deleted == 1) return DeleteVideoProjectResult.Deleted(deletion)
        return if (recentProjectDao.hasActiveExport(projectId)) {
            DeleteVideoProjectResult.ExportActive
        } else {
            DeleteVideoProjectResult.NotFound
        }
    }

    suspend fun undoDelete(deletion: DeletedVideoProject): Boolean =
        recentProjectDao.undoDelete(
            projectId = deletion.projectId,
            deletionToken = deletion.deletionToken,
            updatedAtMillis = clock(),
        ) == 1

    suspend fun finalizeDelete(deletion: DeletedVideoProject): Boolean =
        recentProjectDao.purgeDeletedVideoProject(
            projectId = deletion.projectId,
            deletionToken = deletion.deletionToken,
        )

    private fun String.normalizedProjectName(): String? =
        trim()
            .replace(Whitespace, " ")
            .take(MaxProjectNameLength)
            .takeIf { it.isNotBlank() }

    private companion object {
        const val ProjectTypeVideo = "VIDEO"
        const val MaxProjectNameLength = 80
        const val UndoRetentionMillis = 15_000L
        const val SupportedProjectFormatVersion = 1
        const val SupportedLayoutVersion = 1
        val Whitespace = Regex("\\s+")
    }
}
