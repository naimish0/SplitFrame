package com.rameshta.splitframe.domain

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class VideoLayout {
    SIDE_BY_SIDE,
    TOP_BOTTOM,
}

enum class VideoCanvasAspectRatio(
    val label: String,
    val ratio: Float,
) {
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_1_1("1:1", 1f),
    RATIO_4_5("4:5", 4f / 5f),
}

enum class MediaDurationMode {
    LOOP_SHORTER,
    FREEZE_SHORTER,
    STOP_AT_SHORTEST,
}

typealias VideoDurationMode = MediaDurationMode

enum class VideoAudioSource {
    CLIP_1,
    CLIP_2,
    NONE,
}

enum class VideoFitMode {
    FILL,
    FIT,
}

enum class VideoTransition {
    Cut,
    FadeThroughBlack,
}

enum class VideoSupportStatus {
    Supported,
    UnsupportedMimeType,
    Unreadable,
    MissingVideoTrack,
    UnsupportedDrmOrCodec,
    InvalidDuration,
}

sealed interface MediaSource {
    val id: String
    val uri: String
    val width: Int
    val height: Int
    val transform: ImageTransform
    val fitMode: VideoFitMode

    data class Image(
        override val id: String,
        override val uri: String,
        override val width: Int,
        override val height: Int,
        val mimeType: String? = null,
        val sizeBytes: Long? = null,
        val enhancedPath: String? = null,
        val editState: ImageEditState = ImageEditState(),
        override val transform: ImageTransform = ImageTransform.Default,
        override val fitMode: VideoFitMode = VideoFitMode.FILL,
    ) : MediaSource

    data class Video(
        val clip: VideoClip,
    ) : MediaSource {
        override val id: String get() = clip.id
        override val uri: String get() = clip.uri
        override val width: Int get() = clip.orientedWidth
        override val height: Int get() = clip.orientedHeight
        override val transform: ImageTransform get() = clip.transform
        override val fitMode: VideoFitMode get() = clip.fitMode
    }
}

data class VideoClip(
    val id: String,
    val uri: String,
    val durationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = durationMs,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val frameRate: Float? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val hasAudio: Boolean = false,
    val isHdr: Boolean = false,
    val transform: ImageTransform = ImageTransform.Default,
    val fitMode: VideoFitMode = VideoFitMode.FILL,
) {
    val orientedWidth: Int get() = if (rotationDegrees % 180 == 0) width else height
    val orientedHeight: Int get() = if (rotationDegrees % 180 == 0) height else width
    val trimmedDurationMs: Long get() = (trimEndMs - trimStartMs).coerceAtLeast(0L)
    val hasValidTrim: Boolean get() = trimmedDurationMs >= VideoLayoutMath.MinTrimDurationMs

    fun normalizedTrim(): VideoClip {
        val start = trimStartMs.coerceIn(0L, durationMs)
        val end = trimEndMs.coerceIn(start + VideoLayoutMath.MinTrimDurationMs, durationMs)
        return copy(trimStartMs = start, trimEndMs = end)
    }
}

data class VideoMergeProject(
    val id: String,
    val mediaByCell: Map<Int, MediaSource> = emptyMap(),
    val selectedCellIndex: Int? = 0,
    val template: LayoutTemplate = VideoLayoutMath.templateFor(VideoLayout.SIDE_BY_SIDE, VideoCanvasAspectRatio.RATIO_16_9),
    val canvasAspectRatio: VideoCanvasAspectRatio = VideoCanvasAspectRatio.RATIO_16_9,
    val exportResolution: ExportResolution = ExportResolution.FHD_1080,
    val primaryAudioMediaId: String? = null,
    val userAudioUri: String? = null,
    val transition: VideoTransition = VideoTransition.Cut,
    val durationMode: MediaDurationMode = MediaDurationMode.LOOP_SHORTER,
    val spacingDp: Float = 0f,
    val cornerRadiusDp: Float = 0f,
    val backgroundColor: ULong = 0xFF071A1Au,
) {
    val mediaCount: Int get() = mediaByCell.size
    val hasVideo: Boolean get() = mediaByCell.values.any { it is MediaSource.Video }
    val clips: Map<Int, VideoClip> get() = mediaByCell.mapNotNullValues { (_, media) -> (media as? MediaSource.Video)?.clip }
    val orderedMedia: List<MediaSource>
        get() {
            val templateIndexes = template.cells.map { it.index }
            val templateMedia = templateIndexes.mapNotNull { mediaByCell[it] }
            val extraMedia = mediaByCell
                .filterKeys { it !in templateIndexes }
                .toSortedMap()
                .values
            return templateMedia + extraMedia
        }
    val orderedClips: List<VideoClip> get() = orderedMedia.mapNotNull { (it as? MediaSource.Video)?.clip }
    val containsHdr: Boolean get() = clips.values.any { it.isHdr }
    val isComplete: Boolean
        get() = mediaByCell.size in MixedMediaLimits.MinItems..MixedMediaLimits.MaxItems &&
            mediaByCell.values.all { it is MediaSource.Video } &&
            orderedClips.size == mediaByCell.size
    val primaryAudioClip: VideoClip?
        get() = primaryAudioMediaId?.let { mediaId ->
            mediaByCell.values
                .filterIsInstance<MediaSource.Video>()
                .firstOrNull { it.id == mediaId }
                ?.clip
                ?.takeIf { it.hasAudio }
        }
    val legacyLayout: VideoLayout?
        get() = when (template.id) {
            TemplateIds.SIDE_BY_SIDE, VideoLayout.SIDE_BY_SIDE.name.lowercase() -> VideoLayout.SIDE_BY_SIDE
            TemplateIds.TOP_BOTTOM, VideoLayout.TOP_BOTTOM.name.lowercase() -> VideoLayout.TOP_BOTTOM
            else -> null
        }
}

data class VideoExportEstimate(
    val outputSize: OutputSize,
    val durationMs: Long,
    val estimatedBytes: Long,
    val encoderSupported: Boolean,
)

data class MergedVideoTimelinePosition(
    val clipIndex: Int,
    val clip: VideoClip,
    val positionInTrimMs: Long,
) {
    val sourcePositionMs: Long
        get() = clip.trimStartMs + positionInTrimMs.coerceIn(0L, clip.trimmedDurationMs)
}

object MixedMediaLimits {
    const val MinItems = 2
    const val MaxItems = 20
    const val MaxLivePreviewVideos = 2
    const val MaxTotalTrimmedDurationMs = 30L * 60L * 1_000L
    const val MaxEstimatedOutputBytes = 2L * 1_024L * 1_024L * 1_024L
}

internal fun videoExportResourceContractFailure(project: VideoMergeProject): String? {
    if (project.orderedClips.size > MixedMediaLimits.MaxItems) {
        return "Choose no more than ${MixedMediaLimits.MaxItems} video clips."
    }
    val durationMs = VideoLayoutMath.outputDurationForMergedVideos(project.orderedClips)
    if (durationMs > MixedMediaLimits.MaxTotalTrimmedDurationMs) {
        return "Keep the merged video at 30 minutes or less."
    }
    if (project.orderedClips.isNotEmpty()) {
        val outputSize = VideoLayoutMath.outputSizeForMedia(
            project.canvasAspectRatio,
            project.exportResolution,
            project.mediaByCell,
        )
        if (
            VideoLayoutMath.estimateMp4Bytes(outputSize, project.orderedClips) >
            MixedMediaLimits.MaxEstimatedOutputBytes
        ) {
            return "The estimated output is too large. Shorten the project or choose a lower resolution."
        }
    }
    return null
}

object MixedMediaTemplateCatalog {
    private val repository = TemplateRepository()

    fun templates(): List<LayoutTemplate> =
        repository.templates()
            .filter { it.kind == TemplateKind.Standard && it.slotCount in MixedMediaLimits.MinItems..MixedMediaLimits.MaxItems }
            .distinctBy { it.id }

    fun compatibleTemplates(mediaCount: Int): List<LayoutTemplate> =
        TemplateCatalog.compatibleTemplates(templates(), mediaCount)

    fun byId(id: String?): LayoutTemplate? =
        id?.let { requested ->
            templates().firstOrNull { it.id == requested }
                ?: VideoLayoutMath.sequenceTemplateCount(requested)?.let { count ->
                    VideoLayoutMath.sequenceTemplateFor(count, VideoCanvasAspectRatio.RATIO_16_9)
                }
        }

    fun defaultForCount(mediaCount: Int): LayoutTemplate =
        compatibleTemplates(mediaCount.coerceIn(MixedMediaLimits.MinItems, MixedMediaLimits.MaxItems))
            .firstOrNull()
            ?: VideoLayoutMath.templateFor(VideoLayout.SIDE_BY_SIDE, VideoCanvasAspectRatio.RATIO_16_9)
}

object VideoLayoutMath {
    const val MinTrimDurationMs: Long = 1_000L
    const val EdgeToEdgeSpacingDp: Float = 0f

    fun sequenceTemplateFor(
        itemCount: Int,
        aspectRatio: VideoCanvasAspectRatio,
    ): LayoutTemplate {
        val safeCount = itemCount.coerceAtLeast(MixedMediaLimits.MinItems)
        val columns = ceil(sqrt(safeCount.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(safeCount / columns.toFloat()).toInt().coerceAtLeast(1)
        return LayoutTemplate(
            id = "$SequenceTemplateIdPrefix$safeCount",
            name = "video_sequence_$safeCount",
            cells = (0 until safeCount).map { index ->
                val row = index / columns
                val column = index % columns
                LayoutCell(
                    rect = NormalizedRect(
                        x = column / columns.toFloat(),
                        y = row / rows.toFloat(),
                        width = 1f / columns,
                        height = 1f / rows,
                    ),
                    index = index,
                )
            },
            defaultSpacingDp = 0f,
            defaultCornerRadiusDp = 0f,
            aspectRatio = aspectRatio.ratio,
        )
    }

    fun sequenceTemplateCount(templateId: String): Int? =
        templateId
            .takeIf { it.startsWith(SequenceTemplateIdPrefix) }
            ?.removePrefix(SequenceTemplateIdPrefix)
            ?.toIntOrNull()
            ?.takeIf { it >= MixedMediaLimits.MinItems }

    fun templateFor(
        layout: VideoLayout,
        aspectRatio: VideoCanvasAspectRatio,
    ): LayoutTemplate {
        val cells = when (layout) {
            VideoLayout.SIDE_BY_SIDE -> listOf(
                LayoutCell(NormalizedRect(0f, 0f, 0.5f, 1f), 0),
                LayoutCell(NormalizedRect(0.5f, 0f, 0.5f, 1f), 1),
            )
            VideoLayout.TOP_BOTTOM -> listOf(
                LayoutCell(NormalizedRect(0f, 0f, 1f, 0.5f), 0),
                LayoutCell(NormalizedRect(0f, 0.5f, 1f, 0.5f), 1),
            )
        }
        val id = when (layout) {
            VideoLayout.SIDE_BY_SIDE -> TemplateIds.SIDE_BY_SIDE
            VideoLayout.TOP_BOTTOM -> TemplateIds.TOP_BOTTOM
        }
        return LayoutTemplate(
            id = id,
            name = layout.name,
            cells = cells,
            defaultSpacingDp = 0f,
            defaultCornerRadiusDp = 0f,
            aspectRatio = aspectRatio.ratio,
        )
    }

    fun outputSizeForResolution(
        aspectRatio: VideoCanvasAspectRatio,
        resolution: ExportResolution,
        clips: Map<Int, VideoClip>,
    ): OutputSize {
        if (resolution == ExportResolution.ORIGINAL && clips.isNotEmpty()) {
            val sourceLongEdge = clips.values.maxOf { max(it.orientedWidth, it.orientedHeight) }
            val boundedLongEdge = sourceLongEdge.coerceIn(480, ExportResolution.UHD_2160.longEdgePx)
            return sizeForLongEdge(aspectRatio.ratio, boundedLongEdge)
        }
        val longEdge = resolution.longEdgePx.takeIf { it > 0 } ?: ExportResolution.FHD_1080.longEdgePx
        return sizeForLongEdge(aspectRatio.ratio, longEdge)
    }

    fun outputSizeForMedia(
        aspectRatio: VideoCanvasAspectRatio,
        resolution: ExportResolution,
        mediaByCell: Map<Int, MediaSource>,
    ): OutputSize {
        if (resolution == ExportResolution.ORIGINAL && mediaByCell.isNotEmpty()) {
            val sourceLongEdge = mediaByCell.values.maxOf { max(it.width, it.height) }
            val boundedLongEdge = sourceLongEdge.coerceIn(480, ExportResolution.UHD_2160.longEdgePx)
            return sizeForLongEdge(aspectRatio.ratio, boundedLongEdge)
        }
        val longEdge = resolution.longEdgePx.takeIf { it > 0 } ?: ExportResolution.FHD_1080.longEdgePx
        return sizeForLongEdge(aspectRatio.ratio, longEdge)
    }

    fun edgeToEdgeCellFrames(
        template: LayoutTemplate,
        outputSize: OutputSize,
    ): Map<Int, RectPx> =
        template.cells.associate { cell ->
            cell.index to RectPx(
                left = edgeStartPx(cell.rect.x, outputSize.widthPx),
                top = edgeStartPx(cell.rect.y, outputSize.heightPx),
                right = edgeEndPx(cell.rect.right, outputSize.widthPx),
                bottom = edgeEndPx(cell.rect.bottom, outputSize.heightPx),
            )
        }

    fun outputDurationMs(
        clips: Map<Int, VideoClip>,
        mode: MediaDurationMode,
    ): Long =
        outputDurationForVideos(clips.values, mode)

    fun outputDurationForMedia(
        mediaByCell: Map<Int, MediaSource>,
        mode: MediaDurationMode,
    ): Long =
        outputDurationForVideos(
            mediaByCell.values.filterIsInstance<MediaSource.Video>().map { it.clip },
            mode,
        )

    fun outputDurationForMergedVideos(clips: Collection<VideoClip>): Long =
        clips.fold(0L) { total, clip ->
            total.saturatedAdd(clip.trimmedDurationMs.coerceAtLeast(0L))
        }

    fun mergedVideoPositionAt(
        clips: List<VideoClip>,
        projectPositionMs: Long,
    ): MergedVideoTimelinePosition? {
        val playableClips = clips.withIndex().filter { (_, clip) -> clip.trimmedDurationMs > 0L }
        if (playableClips.isEmpty()) return null

        var remainingMs = projectPositionMs.coerceAtLeast(0L)
        playableClips.forEachIndexed { playableIndex, indexedClip ->
            val durationMs = indexedClip.value.trimmedDurationMs
            val isLast = playableIndex == playableClips.lastIndex
            if (remainingMs < durationMs || isLast) {
                return MergedVideoTimelinePosition(
                    clipIndex = indexedClip.index,
                    clip = indexedClip.value,
                    positionInTrimMs = remainingMs.coerceIn(0L, durationMs),
                )
            }
            remainingMs -= durationMs
        }
        return null
    }

    fun mergedVideoClipStartMs(
        clips: List<VideoClip>,
        clipIndex: Int,
    ): Long? {
        if (clipIndex !in clips.indices || clips[clipIndex].trimmedDurationMs <= 0L) return null
        return clips
            .take(clipIndex)
            .fold(0L) { total, clip ->
                total.saturatedAdd(clip.trimmedDurationMs.coerceAtLeast(0L))
            }
    }

    fun freezeDurationMs(clip: VideoClip, outputDurationMs: Long): Long =
        (outputDurationMs - clip.trimmedDurationMs).coerceAtLeast(0L)

    fun loopedPositionMs(clip: VideoClip, projectPositionMs: Long): Long {
        val trimmedDuration = clip.trimmedDurationMs
        if (trimmedDuration <= 0L) return clip.trimStartMs
        return clip.trimStartMs + projectPositionMs.floorMod(trimmedDuration)
    }

    fun normalizeTrim(
        durationMs: Long,
        startMs: Long,
        endMs: Long,
    ): Pair<Long, Long> {
        val safeDuration = durationMs.coerceAtLeast(MinTrimDurationMs)
        val start = startMs.coerceIn(0L, safeDuration - MinTrimDurationMs)
        val end = endMs.coerceIn(start + MinTrimDurationMs, safeDuration)
        return start to end
    }

    fun estimateMp4Bytes(size: OutputSize, clips: List<VideoClip>): Long {
        val pixels = size.widthPx.toLong() * size.heightPx.toLong()
        val bitsPerPixelFrame = when {
            pixels >= 3840L * 2160L -> 0.09f
            pixels >= 2560L * 1440L -> 0.10f
            pixels >= 1920L * 1080L -> 0.125f
            else -> 0.14f
        }
        val payloadBits = clips.sumOf { clip ->
            val seconds = clip.trimmedDurationMs.coerceAtLeast(0L) / 1_000.0
            val framesPerSecond = (clip.frameRate ?: DefaultEstimateFrameRate)
                .coerceIn(MinEstimateFrameRate, MaxEstimateFrameRate)
            val videoBits = pixels * bitsPerPixelFrame * framesPerSecond * seconds
            val audioBits = if (clip.hasAudio) EstimatedAacBitsPerSecond * seconds else 0.0
            videoBits + audioBits
        }
        return ((payloadBits * Mp4ContainerOverhead) / BitsPerByte)
            .toLong()
            .coerceAtLeast(MinEstimatedMp4Bytes)
    }

    private fun outputDurationForVideos(
        clips: Collection<VideoClip>,
        mode: MediaDurationMode,
    ): Long {
        val durations = clips.map { it.trimmedDurationMs }.filter { it > 0 }
        if (durations.isEmpty()) return 0L
        return when (mode) {
            MediaDurationMode.LOOP_SHORTER,
            MediaDurationMode.FREEZE_SHORTER,
            -> durations.max()
            MediaDurationMode.STOP_AT_SHORTEST -> durations.min()
        }
    }

    private fun sizeForLongEdge(aspectRatio: Float, longEdge: Int): OutputSize =
        if (aspectRatio >= 1f) {
            OutputSize(longEdge, (longEdge / aspectRatio).roundToInt().coerceAtLeast(2).even())
        } else {
            OutputSize((longEdge * aspectRatio).roundToInt().coerceAtLeast(2).even(), longEdge)
        }

    private fun edgeStartPx(normalizedPosition: Float, maxPx: Int): Float {
        val boundedMax = maxPx.coerceAtLeast(1)
        if (normalizedPosition <= 0f) return 0f
        return floor(normalizedPosition.coerceIn(0f, 1f) * boundedMax)
            .coerceIn(0f, (boundedMax - 1).toFloat())
    }

    private fun edgeEndPx(normalizedPosition: Float, maxPx: Int): Float {
        val boundedMax = maxPx.coerceAtLeast(1)
        if (normalizedPosition >= 1f) return boundedMax.toFloat()
        return (ceil(normalizedPosition.coerceIn(0f, 1f) * boundedMax) + InternalEdgeOverlapPx)
            .coerceIn(1f, boundedMax.toFloat())
    }

    private fun Long.floorMod(divisor: Long): Long =
        ((this % divisor) + divisor) % divisor

    private fun Long.saturatedAdd(value: Long): Long =
        if (value > Long.MAX_VALUE - this) Long.MAX_VALUE else this + value

    private fun Int.even(): Int = if (this % 2 == 0) this else this + 1

    private const val InternalEdgeOverlapPx = 1f
    private const val SequenceTemplateIdPrefix = "video_sequence_"
    private const val DefaultEstimateFrameRate = 30f
    private const val MinEstimateFrameRate = 1f
    private const val MaxEstimateFrameRate = 120f
    private const val EstimatedAacBitsPerSecond = 128_000.0
    private const val Mp4ContainerOverhead = 1.03
    private const val BitsPerByte = 8.0
    private const val MinEstimatedMp4Bytes = 1_000_000L
}

fun VideoClip.toMediaSource(): MediaSource.Video =
    MediaSource.Video(this)

fun MediaSource.withTransform(transform: ImageTransform): MediaSource =
    when (this) {
        is MediaSource.Image -> copy(transform = transform.normalized())
        is MediaSource.Video -> copy(clip = clip.copy(transform = transform.normalized()))
    }

fun MediaSource.withFitMode(fitMode: VideoFitMode): MediaSource =
    when (this) {
        is MediaSource.Image -> copy(fitMode = fitMode)
        is MediaSource.Video -> copy(clip = clip.copy(fitMode = fitMode))
    }

private inline fun <K, V, R : Any> Map<K, V>.mapNotNullValues(transform: (Map.Entry<K, V>) -> R?): Map<K, R> =
    buildMap {
        this@mapNotNullValues.forEach { entry ->
            transform(entry)?.let { put(entry.key, it) }
        }
    }
