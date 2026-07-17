package com.example.splitframe.domain

import kotlin.math.max
import kotlin.math.roundToInt

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
    val durationMode: MediaDurationMode = MediaDurationMode.LOOP_SHORTER,
    val spacingDp: Float = 0f,
    val cornerRadiusDp: Float = 0f,
    val backgroundColor: ULong = 0xFF071A1Au,
) {
    val mediaCount: Int get() = mediaByCell.size
    val hasVideo: Boolean get() = mediaByCell.values.any { it is MediaSource.Video }
    val clips: Map<Int, VideoClip> get() = mediaByCell.mapNotNullValues { (_, media) -> (media as? MediaSource.Video)?.clip }
    val orderedMedia: List<MediaSource> get() = template.cells.mapNotNull { mediaByCell[it.index] }
    val orderedClips: List<VideoClip> get() = template.cells.mapNotNull { clips[it.index] }
    val containsHdr: Boolean get() = clips.values.any { it.isHdr }
    val isComplete: Boolean
        get() = template.slotCount in MixedMediaLimits.MinItems..MixedMediaLimits.MaxItems &&
            mediaByCell.size >= MixedMediaLimits.MinItems &&
            template.cells.all { mediaByCell.containsKey(it.index) }
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

object MixedMediaLimits {
    const val MinItems = 2
    const val MaxItems = 9
    const val MaxLivePreviewVideos = 4
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
        id?.let { requested -> templates().firstOrNull { it.id == requested } }

    fun defaultForCount(mediaCount: Int): LayoutTemplate =
        compatibleTemplates(mediaCount.coerceIn(MixedMediaLimits.MinItems, MixedMediaLimits.MaxItems))
            .firstOrNull()
            ?: VideoLayoutMath.templateFor(VideoLayout.SIDE_BY_SIDE, VideoCanvasAspectRatio.RATIO_16_9)
}

object VideoLayoutMath {
    const val MinTrimDurationMs: Long = 1_000L

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

    fun estimateMp4Bytes(size: OutputSize, durationMs: Long): Long {
        val pixels = size.widthPx.toLong() * size.heightPx.toLong()
        val seconds = (durationMs / 1000f).coerceAtLeast(1f)
        val bitsPerPixelSecond = when {
            pixels >= 3840L * 2160L -> 0.09f
            pixels >= 2560L * 1440L -> 0.10f
            pixels >= 1920L * 1080L -> 0.11f
            else -> 0.14f
        }
        return ((pixels * bitsPerPixelSecond * seconds) / 8f).toLong().coerceAtLeast(1_000_000L)
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

    private fun Long.floorMod(divisor: Long): Long =
        ((this % divisor) + divisor) % divisor

    private fun Int.even(): Int = if (this % 2 == 0) this else this + 1
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
