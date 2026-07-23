package com.rameshta.splitframe.data

import com.rameshta.splitframe.data.local.VideoExportWorkDao
import com.rameshta.splitframe.data.local.VideoExportWorkEntity
import com.rameshta.splitframe.data.local.RecentProjectDao
import com.rameshta.splitframe.data.local.RecentProjectEntity
import com.rameshta.splitframe.data.local.VideoProjectDao
import com.rameshta.splitframe.data.local.VideoProjectEntity
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.ImageEditState
import com.rameshta.splitframe.domain.ImageTransform
import com.rameshta.splitframe.domain.MediaDurationMode
import com.rameshta.splitframe.domain.MediaSource
import com.rameshta.splitframe.domain.MixedMediaTemplateCatalog
import com.rameshta.splitframe.domain.VideoAudioSource
import com.rameshta.splitframe.domain.VideoCanvasAspectRatio
import com.rameshta.splitframe.domain.VideoClip
import com.rameshta.splitframe.domain.VideoFitMode
import com.rameshta.splitframe.domain.VideoLayout
import com.rameshta.splitframe.domain.VideoLayoutMath
import com.rameshta.splitframe.domain.VideoMergeProject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface VideoProjectReadResult {
    data class Ready(val project: VideoMergeProject) : VideoProjectReadResult
    data class Corrupt(val projectId: String) : VideoProjectReadResult
    data object NotFound : VideoProjectReadResult
}

class VideoProjectStore(
    private val projectDao: VideoProjectDao,
    private val exportWorkDao: VideoExportWorkDao,
    private val recentProjectDao: RecentProjectDao? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun getOrCreate(projectId: String?): VideoMergeProject {
        val existing = projectId?.let { get(it) }
        return existing ?: VideoMergeProject(id = projectId ?: UUID.randomUUID().toString())
    }

    suspend fun openProject(
        projectId: String,
        createIfMissing: Boolean,
    ): VideoMergeProject? {
        when (val result = inspect(projectId)) {
            is VideoProjectReadResult.Ready -> return result.project
            is VideoProjectReadResult.Corrupt -> return null
            VideoProjectReadResult.NotFound -> Unit
        }
        if (!createIfMissing) return null
        return VideoMergeProject(id = projectId)
    }

    suspend fun get(projectId: String): VideoMergeProject? =
        (inspect(projectId) as? VideoProjectReadResult.Ready)?.project

    suspend fun inspect(projectId: String): VideoProjectReadResult {
        val recentDao = recentProjectDao
        if (recentDao != null) {
            val recent = recentDao.get(projectId) ?: return VideoProjectReadResult.NotFound
            if (recent.deletedAtMillis != null) return VideoProjectReadResult.NotFound
        }
        val entity = projectDao.get(projectId) ?: return VideoProjectReadResult.NotFound
        return entity.toProjectOrNull()
            ?.let(VideoProjectReadResult::Ready)
            ?: VideoProjectReadResult.Corrupt(projectId)
    }

    fun observeProject(projectId: String): Flow<VideoMergeProject?> =
        projectDao.observe(projectId).map { it?.toProjectOrNull() }

    suspend fun save(
        project: VideoMergeProject,
        initialName: String = DefaultProjectName,
    ): Boolean {
        val updatedAt = clock()
        val entity = project.toEntity(updatedAt)
        val recentDao = recentProjectDao
        if (recentDao == null) {
            projectDao.upsert(entity)
            return true
        }
        return recentDao.saveActiveVideoProject(
            project = entity,
            recentProject = project.toRecentEntity(initialName, updatedAt),
        )
    }

    suspend fun reset(projectId: String): Boolean {
        val reset = VideoMergeProject(id = projectId)
        val updatedAt = clock()
        val recentDao = recentProjectDao
        if (recentDao == null) {
            projectDao.upsert(reset.toEntity(updatedAt))
            exportWorkDao.delete(projectId)
            return true
        }
        return recentDao.resetActiveVideoProject(
            project = reset.toEntity(updatedAt),
            recentProject = reset.toRecentEntity(DefaultProjectName, updatedAt),
        )
    }

    fun observeExportWork(projectId: String): Flow<VideoExportWorkEntity?> =
        exportWorkDao.observe(projectId)

    fun observeHasActiveExport(): Flow<Boolean> = exportWorkDao.observeHasActiveExport()

    suspend fun getExportWork(projectId: String): VideoExportWorkEntity? =
        exportWorkDao.get(projectId)

    suspend fun getActiveExportWork(): List<VideoExportWorkEntity> = exportWorkDao.getActive()

    suspend fun setExportWork(work: VideoExportWorkEntity) {
        exportWorkDao.upsert(work)
    }

    suspend fun updateExportWorkIfCurrent(
        work: VideoExportWorkEntity,
        expectedStates: List<String>,
    ): Boolean {
        val workId = work.workId ?: return false
        return exportWorkDao.updateIfWorkMatches(
            projectId = work.projectId,
            workId = workId,
            state = work.state,
            progress = work.progress,
            outputUri = work.outputUri,
            errorMessage = work.errorMessage,
            updatedAtMillis = work.updatedAtMillis,
            expectedStates = expectedStates,
        ) == 1
    }

    suspend fun clearExportWork(projectId: String) {
        exportWorkDao.delete(projectId)
    }

    private fun VideoMergeProject.toEntity(updatedAtMillis: Long): VideoProjectEntity =
        VideoProjectEntity(
            id = id,
            layout = (legacyLayout ?: VideoLayout.SIDE_BY_SIDE).name,
            canvasAspectRatio = canvasAspectRatio.name,
            exportResolution = exportResolution.name,
            primaryAudioSource = legacyAudioSource().name,
            durationMode = durationMode.name,
            spacingDp = VideoLayoutMath.EdgeToEdgeSpacingDp,
            cornerRadiusDp = cornerRadiusDp,
            backgroundColor = backgroundColor.toLong(),
            updatedAtMillis = updatedAtMillis,
            clip0 = clips[0]?.encode(),
            clip1 = clips[1]?.encode(),
            templateId = template.id,
            selectedCellIndex = selectedCellIndex,
            primaryAudioMediaId = primaryAudioMediaId,
            mediaItems = mediaByCell.encodeMediaItems(),
            mergeMode = LegacySequenceMergeMode,
        )

    private fun VideoMergeProject.toRecentEntity(
        initialName: String,
        updatedAtMillis: Long,
    ): RecentProjectEntity =
        RecentProjectEntity(
            projectId = id,
            projectType = ProjectTypeVideo,
            name = initialName,
            projectFormatVersion = ProjectFormatVersion,
            layoutVersion = LayoutVersion,
            thumbnailUri = orderedMedia.firstOrNull()?.uri,
            createdAtMillis = updatedAtMillis,
            updatedAtMillis = updatedAtMillis,
            deletedAtMillis = null,
            deletionToken = null,
        )

    private fun VideoProjectEntity.toProjectOrNull(): VideoMergeProject? = runCatching {
        require(mergeMode == LegacySequenceMergeMode)
        val aspectRatio = enumValueStrict<VideoCanvasAspectRatio>(canvasAspectRatio)
        val legacyLayout = enumValueOrDefault(layout, VideoLayout.SIDE_BY_SIDE)
        val decodedMedia = when {
            mediaItems == null -> buildMap {
                clip0?.let { put(0, MediaSource.Video(it.decodeClipStrict())) }
                clip1?.let { put(1, MediaSource.Video(it.decodeClipStrict())) }
            }
            mediaItems.isBlank() -> emptyMap()
            else -> mediaItems.decodeMediaItemsStrict()
        }
        require(decodedMedia.keys.all { it >= 0 })
        require(decodedMedia.values.map { it.id }.distinct().size == decodedMedia.size)
        val template = VideoLayoutMath.sequenceTemplateCount(templateId.orEmpty())
            ?.let { count -> VideoLayoutMath.sequenceTemplateFor(maxOf(count, decodedMedia.size), aspectRatio) }
            ?: MixedMediaTemplateCatalog.byId(templateId)
            ?: VideoLayoutMath.sequenceTemplateFor(decodedMedia.size, aspectRatio)
        VideoMergeProject(
            id = id,
            mediaByCell = decodedMedia,
            selectedCellIndex = selectedCellIndex ?: decodedMedia.keys.minOrNull() ?: 0,
            template = template,
            canvasAspectRatio = aspectRatio,
            exportResolution = enumValueStrict(exportResolution),
            primaryAudioMediaId = primaryAudioMediaId ?: legacyAudioMediaId(primaryAudioSource, decodedMedia),
            durationMode = durationMode.toDurationModeStrict(),
            spacingDp = VideoLayoutMath.EdgeToEdgeSpacingDp,
            cornerRadiusDp = cornerRadiusDp,
            backgroundColor = backgroundColor.toULong(),
        )
    }.getOrNull()

    private fun VideoMergeProject.legacyAudioSource(): VideoAudioSource =
        when (primaryAudioMediaId) {
            clips[0]?.id -> VideoAudioSource.CLIP_1
            clips[1]?.id -> VideoAudioSource.CLIP_2
            else -> VideoAudioSource.NONE
        }

    private fun legacyAudioMediaId(
        legacySource: String,
        mediaByCell: Map<Int, MediaSource>,
    ): String? =
        when (enumValueOrDefault(legacySource, VideoAudioSource.NONE)) {
            VideoAudioSource.CLIP_1 -> (mediaByCell[0] as? MediaSource.Video)?.clip?.takeIf { it.hasAudio }?.id
            VideoAudioSource.CLIP_2 -> (mediaByCell[1] as? MediaSource.Video)?.clip?.takeIf { it.hasAudio }?.id
            VideoAudioSource.NONE -> null
        }

    private fun Map<Int, MediaSource>.encodeMediaItems(): String =
        entries.sortedBy { it.key }.joinToString(RowSeparator) { (cellIndex, media) ->
            val fields = when (media) {
                is MediaSource.Image -> listOf(
                    cellIndex.toString(),
                    TypeImage,
                    media.id,
                    media.uri,
                    media.width.toString(),
                    media.height.toString(),
                    "",
                    "",
                    "",
                    "",
                    "",
                    media.mimeType.orEmpty(),
                    media.sizeBytes?.toString().orEmpty(),
                    "",
                    "",
                    media.transform.zoom.toString(),
                    media.transform.panX.toString(),
                    media.transform.panY.toString(),
                    media.fitMode.name,
                    media.enhancedPath.orEmpty(),
                    media.editState.scale.toString(),
                    media.editState.offsetX.toString(),
                    media.editState.offsetY.toString(),
                )
                is MediaSource.Video -> listOf(
                    cellIndex.toString(),
                    TypeVideo,
                    media.clip.id,
                    media.clip.uri,
                    media.clip.width.toString(),
                    media.clip.height.toString(),
                    media.clip.durationMs.toString(),
                    media.clip.trimStartMs.toString(),
                    media.clip.trimEndMs.toString(),
                    media.clip.rotationDegrees.toString(),
                    media.clip.frameRate?.toString().orEmpty(),
                    media.clip.mimeType.orEmpty(),
                    media.clip.sizeBytes?.toString().orEmpty(),
                    media.clip.hasAudio.toString(),
                    media.clip.isHdr.toString(),
                    media.clip.transform.zoom.toString(),
                    media.clip.transform.panX.toString(),
                    media.clip.transform.panY.toString(),
                    media.clip.fitMode.name,
                    "",
                    "",
                    "",
                    "",
                )
            }
            fields.joinToString(FieldSeparator) { it.urlEncode() }
        }

    private fun String.decodeMediaItemsStrict(): Map<Int, MediaSource> =
        lineSequence().fold(mutableMapOf()) { decoded, row ->
            require(row.isNotBlank())
            val fields = row.split(FieldSeparator).map { it.urlDecode() }
            require(fields.size >= MinimumMediaFieldCount)
            val cellIndex = fields[0].toInt()
            require(cellIndex >= 0 && cellIndex !in decoded)
            val media = when (fields[1]) {
                TypeImage -> fields.decodeImageFromMediaFields()
                TypeVideo -> MediaSource.Video(fields.decodeClipFromMediaFields())
                else -> error("Unknown persisted media type")
            }
            require(media.id.isNotBlank() && media.uri.isNotBlank())
            require(media.width > 0 && media.height > 0)
            decoded[cellIndex] = media
            decoded
        }

    private fun List<String>.decodeImageFromMediaFields(): MediaSource.Image {
        require(size >= ImageMediaFieldCount)
        val editScale = this[20].toFloat()
        val editOffsetX = this[21].toFloat()
        val editOffsetY = this[22].toFloat()
        require(editScale.isFinite() && editScale > 0f && editOffsetX.isFinite() && editOffsetY.isFinite())
        return MediaSource.Image(
            id = this[2],
            uri = this[3],
            width = this[4].toInt(),
            height = this[5].toInt(),
            mimeType = this[11].ifBlank { null },
            sizeBytes = this[12].optionalLongStrict(),
            enhancedPath = this[19].ifBlank { null },
            editState = ImageEditState(
                scale = editScale,
                offsetX = editOffsetX,
                offsetY = editOffsetY,
            ),
            transform = decodeTransform(15),
            fitMode = enumValueStrict(this[18]),
        )
    }

    private fun List<String>.decodeClipFromMediaFields(): VideoClip {
        require(size >= MinimumMediaFieldCount)
        val clip = VideoClip(
            id = this[2],
            uri = this[3],
            width = this[4].toInt(),
            height = this[5].toInt(),
            durationMs = this[6].toLong(),
            trimStartMs = this[7].toLong(),
            trimEndMs = this[8].toLong(),
            rotationDegrees = this[9].toInt(),
            frameRate = this[10].optionalFloatStrict(),
            mimeType = this[11].ifBlank { null },
            sizeBytes = this[12].optionalLongStrict(),
            hasAudio = requireNotNull(this[13].toBooleanStrictOrNull()),
            isHdr = requireNotNull(this[14].toBooleanStrictOrNull()),
            transform = decodeTransform(15),
            fitMode = enumValueStrict(this[18]),
        )
        requireValidClip(clip)
        return clip
    }

    private fun VideoClip.encode(): String =
        listOf(
            id,
            uri,
            durationMs.toString(),
            trimStartMs.toString(),
            trimEndMs.toString(),
            width.toString(),
            height.toString(),
            rotationDegrees.toString(),
            frameRate?.toString().orEmpty(),
            mimeType.orEmpty(),
            sizeBytes?.toString().orEmpty(),
            hasAudio.toString(),
            isHdr.toString(),
            transform.zoom.toString(),
            transform.panX.toString(),
            transform.panY.toString(),
            fitMode.name,
        ).joinToString(FieldSeparator) { it.urlEncode() }

    private fun String.decodeClipStrict(): VideoClip {
        val fields = split(FieldSeparator).map { it.urlDecode() }
        require(fields.size >= LegacyClipFieldCount)
        val clip = VideoClip(
            id = fields[0],
            uri = fields[1],
            durationMs = fields[2].toLong(),
            trimStartMs = fields[3].toLong(),
            trimEndMs = fields[4].toLong(),
            width = fields[5].toInt(),
            height = fields[6].toInt(),
            rotationDegrees = fields[7].toInt(),
            frameRate = fields[8].optionalFloatStrict(),
            mimeType = fields[9].ifBlank { null },
            sizeBytes = fields[10].optionalLongStrict(),
            hasAudio = requireNotNull(fields[11].toBooleanStrictOrNull()),
            isHdr = requireNotNull(fields[12].toBooleanStrictOrNull()),
            transform = fields.decodeTransform(13),
            fitMode = enumValueStrict(fields[16]),
        )
        requireValidClip(clip)
        return clip
    }

    private fun List<String>.decodeTransform(startIndex: Int): ImageTransform {
        val zoom = this[startIndex].toFloat()
        val panX = this[startIndex + 1].toFloat()
        val panY = this[startIndex + 2].toFloat()
        require(zoom.isFinite() && panX.isFinite() && panY.isFinite())
        require(zoom in ImageTransform.MIN_ZOOM..ImageTransform.MAX_ZOOM)
        require(panX in -1f..1f && panY in -1f..1f)
        return ImageTransform(zoom = zoom, panX = panX, panY = panY)
    }

    private fun requireValidClip(clip: VideoClip) {
        require(clip.id.isNotBlank() && clip.uri.isNotBlank())
        require(clip.width > 0 && clip.height > 0)
        require(clip.durationMs >= VideoLayoutMath.MinTrimDurationMs)
        require(clip.trimStartMs >= 0L)
        require(clip.trimEndMs <= clip.durationMs)
        require(clip.trimEndMs - clip.trimStartMs >= VideoLayoutMath.MinTrimDurationMs)
        require(clip.rotationDegrees % 90 == 0)
        require(clip.frameRate == null || clip.frameRate.isFinite() && clip.frameRate > 0f)
        require(clip.sizeBytes == null || clip.sizeBytes >= 0L)
    }

    private fun String.optionalLongStrict(): Long? =
        if (isBlank()) null else requireNotNull(toLongOrNull())

    private fun String.optionalFloatStrict(): Float? =
        if (isBlank()) null else requireNotNull(toFloatOrNull()).also { require(it.isFinite()) }

    private fun String.toDurationModeStrict(): MediaDurationMode =
        when (this) {
            "LONGEST_FREEZE_SHORTER" -> MediaDurationMode.FREEZE_SHORTER
            "SHORTEST" -> MediaDurationMode.STOP_AT_SHORTEST
            else -> enumValueStrict(this)
        }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private inline fun <reified T : Enum<T>> enumValueStrict(name: String): T =
        requireNotNull(enumValues<T>().firstOrNull { it.name == name })

    private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun String.urlDecode(): String = URLDecoder.decode(this, Charsets.UTF_8.name())

    private companion object {
        const val FieldSeparator = "\t"
        const val RowSeparator = "\n"
        const val TypeImage = "image"
        const val TypeVideo = "video"
        const val LegacySequenceMergeMode = "SEQUENCE"
        const val ProjectTypeVideo = "VIDEO"
        const val DefaultProjectName = "Video project"
        const val ProjectFormatVersion = 1
        const val LayoutVersion = 1
        const val MinimumMediaFieldCount = 19
        const val ImageMediaFieldCount = 23
        const val LegacyClipFieldCount = 17
    }
}
