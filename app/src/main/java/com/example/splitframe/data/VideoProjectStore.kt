package com.example.splitframe.data

import com.example.splitframe.data.local.VideoExportWorkDao
import com.example.splitframe.data.local.VideoExportWorkEntity
import com.example.splitframe.data.local.VideoProjectDao
import com.example.splitframe.data.local.VideoProjectEntity
import com.example.splitframe.domain.ExportResolution
import com.example.splitframe.domain.ImageEditState
import com.example.splitframe.domain.ImageTransform
import com.example.splitframe.domain.MediaDurationMode
import com.example.splitframe.domain.MediaSource
import com.example.splitframe.domain.MixedMediaTemplateCatalog
import com.example.splitframe.domain.VideoAudioSource
import com.example.splitframe.domain.VideoCanvasAspectRatio
import com.example.splitframe.domain.VideoClip
import com.example.splitframe.domain.VideoFitMode
import com.example.splitframe.domain.VideoLayout
import com.example.splitframe.domain.VideoLayoutMath
import com.example.splitframe.domain.VideoMergeProject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VideoProjectStore(
    private val projectDao: VideoProjectDao,
    private val exportWorkDao: VideoExportWorkDao,
) {
    suspend fun getOrCreate(projectId: String?): VideoMergeProject {
        val existing = projectId?.let { projectDao.get(it)?.toProject() }
        return existing ?: VideoMergeProject(id = projectId ?: UUID.randomUUID().toString())
    }

    suspend fun get(projectId: String): VideoMergeProject? =
        projectDao.get(projectId)?.toProject()

    fun observeProject(projectId: String): Flow<VideoMergeProject?> =
        projectDao.observe(projectId).map { it?.toProject() }

    suspend fun save(project: VideoMergeProject) {
        projectDao.upsert(project.toEntity())
    }

    suspend fun reset(projectId: String) {
        projectDao.upsert(VideoMergeProject(id = projectId).toEntity())
        exportWorkDao.delete(projectId)
    }

    fun observeExportWork(projectId: String): Flow<VideoExportWorkEntity?> =
        exportWorkDao.observe(projectId)

    suspend fun setExportWork(work: VideoExportWorkEntity) {
        exportWorkDao.upsert(work)
    }

    suspend fun clearExportWork(projectId: String) {
        exportWorkDao.delete(projectId)
    }

    private fun VideoMergeProject.toEntity(): VideoProjectEntity =
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
            updatedAtMillis = System.currentTimeMillis(),
            clip0 = clips[0]?.encode(),
            clip1 = clips[1]?.encode(),
            templateId = template.id,
            selectedCellIndex = selectedCellIndex,
            primaryAudioMediaId = primaryAudioMediaId,
            mediaItems = mediaByCell.encodeMediaItems(),
        )

    private fun VideoProjectEntity.toProject(): VideoMergeProject {
        val aspectRatio = enumValueOrDefault(canvasAspectRatio, VideoCanvasAspectRatio.RATIO_16_9)
        val legacyLayout = enumValueOrDefault(layout, VideoLayout.SIDE_BY_SIDE)
        val decodedMedia = mediaItems?.decodeMediaItems().orEmpty().ifEmpty {
            buildMap {
                clip0?.decodeClip()?.let { put(0, MediaSource.Video(it)) }
                clip1?.decodeClip()?.let { put(1, MediaSource.Video(it)) }
            }
        }
        val template = VideoLayoutMath.sequenceTemplateCount(templateId.orEmpty())
            ?.let { count -> VideoLayoutMath.sequenceTemplateFor(maxOf(count, decodedMedia.size), aspectRatio) }
            ?: MixedMediaTemplateCatalog.byId(templateId)
            ?: VideoLayoutMath.sequenceTemplateFor(decodedMedia.size, aspectRatio)
        return VideoMergeProject(
            id = id,
            mediaByCell = decodedMedia,
            selectedCellIndex = selectedCellIndex ?: decodedMedia.keys.minOrNull() ?: 0,
            template = template,
            canvasAspectRatio = aspectRatio,
            exportResolution = enumValueOrDefault(exportResolution, ExportResolution.FHD_1080),
            primaryAudioMediaId = primaryAudioMediaId ?: legacyAudioMediaId(primaryAudioSource, decodedMedia),
            durationMode = durationMode.toDurationMode(),
            spacingDp = VideoLayoutMath.EdgeToEdgeSpacingDp,
            cornerRadiusDp = cornerRadiusDp,
            backgroundColor = backgroundColor.toULong(),
        )
    }

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

    private fun String.decodeMediaItems(): Map<Int, MediaSource> =
        lineSequence()
            .mapNotNull { row ->
                runCatching {
                    val fields = row.split(FieldSeparator).map { it.urlDecode() }
                    val cellIndex = fields[0].toInt()
                    val media = when (fields[1]) {
                        TypeImage -> MediaSource.Image(
                            id = fields[2],
                            uri = fields[3],
                            width = fields[4].toInt(),
                            height = fields[5].toInt(),
                            mimeType = fields.getOrNull(11)?.ifBlank { null },
                            sizeBytes = fields.getOrNull(12)?.toLongOrNull(),
                            enhancedPath = fields.getOrNull(19)?.ifBlank { null },
                            editState = ImageEditState(
                                scale = fields.getOrNull(20)?.toFloatOrNull() ?: 1f,
                                offsetX = fields.getOrNull(21)?.toFloatOrNull() ?: 0f,
                                offsetY = fields.getOrNull(22)?.toFloatOrNull() ?: 0f,
                            ),
                            transform = ImageTransform(
                                zoom = fields[15].toFloat(),
                                panX = fields[16].toFloat(),
                                panY = fields[17].toFloat(),
                            ).normalized(),
                            fitMode = enumValueOrDefault(fields[18], VideoFitMode.FILL),
                        )
                        TypeVideo -> MediaSource.Video(fields.decodeClipFromMediaFields())
                        else -> null
                    }
                    media?.let { cellIndex to it }
                }.getOrNull()
            }
            .toMap()

    private fun List<String>.decodeClipFromMediaFields(): VideoClip =
        VideoClip(
            id = this[2],
            uri = this[3],
            width = this[4].toInt(),
            height = this[5].toInt(),
            durationMs = this[6].toLong(),
            trimStartMs = this[7].toLong(),
            trimEndMs = this[8].toLong(),
            rotationDegrees = this[9].toInt(),
            frameRate = this[10].toFloatOrNull(),
            mimeType = this[11].ifBlank { null },
            sizeBytes = this[12].toLongOrNull(),
            hasAudio = this[13].toBooleanStrictOrNull() ?: false,
            isHdr = this[14].toBooleanStrictOrNull() ?: false,
            transform = ImageTransform(
                zoom = this[15].toFloat(),
                panX = this[16].toFloat(),
                panY = this[17].toFloat(),
            ).normalized(),
            fitMode = enumValueOrDefault(this[18], VideoFitMode.FILL),
        ).normalizedTrim()

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

    private fun String.decodeClip(): VideoClip? =
        runCatching {
            val fields = split(FieldSeparator).map { it.urlDecode() }
            VideoClip(
                id = fields[0],
                uri = fields[1],
                durationMs = fields[2].toLong(),
                trimStartMs = fields[3].toLong(),
                trimEndMs = fields[4].toLong(),
                width = fields[5].toInt(),
                height = fields[6].toInt(),
                rotationDegrees = fields[7].toInt(),
                frameRate = fields[8].toFloatOrNull(),
                mimeType = fields[9].ifBlank { null },
                sizeBytes = fields[10].toLongOrNull(),
                hasAudio = fields[11].toBooleanStrictOrNull() ?: false,
                isHdr = fields[12].toBooleanStrictOrNull() ?: false,
                transform = ImageTransform(
                    zoom = fields[13].toFloat(),
                    panX = fields[14].toFloat(),
                    panY = fields[15].toFloat(),
                ).normalized(),
                fitMode = enumValueOrDefault(fields[16], VideoFitMode.FILL),
            ).normalizedTrim()
        }.getOrNull()

    private fun String.toDurationMode(): MediaDurationMode =
        when (this) {
            "LONGEST_FREEZE_SHORTER" -> MediaDurationMode.FREEZE_SHORTER
            "SHORTEST" -> MediaDurationMode.STOP_AT_SHORTEST
            else -> enumValues<MediaDurationMode>().firstOrNull { it.name == this } ?: MediaDurationMode.LOOP_SHORTER
        }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun String.urlDecode(): String = URLDecoder.decode(this, Charsets.UTF_8.name())

    private companion object {
        const val FieldSeparator = "\t"
        const val RowSeparator = "\n"
        const val TypeImage = "image"
        const val TypeVideo = "video"
    }
}
