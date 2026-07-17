package com.example.splitframe.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Pair
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.splitframe.domain.ExportResult
import com.example.splitframe.domain.LayoutMath
import com.example.splitframe.domain.MediaDurationMode
import com.example.splitframe.domain.MediaSource
import com.example.splitframe.domain.OutputSize
import com.example.splitframe.domain.RectPx
import com.example.splitframe.domain.VideoClip
import com.example.splitframe.domain.VideoFitMode
import com.example.splitframe.domain.VideoLayoutMath
import com.example.splitframe.domain.VideoMergeProject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class VideoExportRepository(
    private val context: Context,
) {
    suspend fun export(
        project: VideoMergeProject,
        onProgress: (Float) -> Unit,
    ): ExportResult =
        withContext(Dispatchers.IO) {
            val outputSize = VideoLayoutMath.outputSizeForMedia(
                aspectRatio = project.canvasAspectRatio,
                resolution = project.exportResolution,
                mediaByCell = project.mediaByCell,
            )
            val tempFile = createTempOutputFile(project.id)
            try {
                val composition = buildComposition(project, outputSize)
                runTransformer(composition, tempFile, onProgress)
                val savedUri = publishToMediaStore(tempFile)
                ExportResult.Success(savedUri.toString())
            } catch (oom: OutOfMemoryError) {
                ExportResult.Failure("Not enough memory to export this video.")
            } catch (throwable: Throwable) {
                ExportResult.Failure(throwable.message ?: "Video export failed.")
            } finally {
                tempFile.delete()
            }
        }

    private fun buildComposition(
        project: VideoMergeProject,
        outputSize: OutputSize,
    ): Composition {
        val frames = project.template.cells.associate { cell ->
            cell.index to LayoutMath.cellFrame(
                cell = cell,
                outputWidthPx = outputSize.widthPx.toFloat(),
                outputHeightPx = outputSize.heightPx.toFloat(),
                spacingPx = project.spacingDp,
            )
        }
        val sequences = mutableListOf<EditedMediaItemSequence>()
        val outputDurationMs = VideoLayoutMath.outputDurationForMedia(project.mediaByCell, project.durationMode)
            .coerceAtLeast(DefaultImageDurationMs)
        val longestVideoDuration = project.clips.values.maxOfOrNull { it.trimmedDurationMs } ?: 0L

        project.template.cells.forEach { cell ->
            val media = project.mediaByCell[cell.index] ?: return@forEach
            val frame = frames.getValue(cell.index)
            val sequenceDuration = media.visualDurationMs()
            val shouldLoop = project.durationMode == MediaDurationMode.LOOP_SHORTER &&
                media is MediaSource.Video &&
                sequenceDuration in 1 until outputDurationMs
            val exportMedia = if (project.durationMode == MediaDurationMode.STOP_AT_SHORTEST && media is MediaSource.Video) {
                media.copy(
                    clip = media.clip.copy(
                        trimEndMs = (media.clip.trimStartMs + outputDurationMs).coerceAtMost(media.clip.trimEndMs),
                    ),
                )
            } else {
                media
            }
            val forceFiniteDuration = sequenceDuration == longestVideoDuration || media is MediaSource.Image
            sequences += buildVisualSequence(
                media = exportMedia,
                frame = frame,
                outputDurationMs = outputDurationMs,
                looping = shouldLoop && !forceFiniteDuration,
            )
        }

        selectedAudioClip(project)?.let { audioClip ->
            val clippedAudio = if (project.durationMode == MediaDurationMode.STOP_AT_SHORTEST) {
                audioClip.copy(trimEndMs = (audioClip.trimStartMs + outputDurationMs).coerceAtMost(audioClip.trimEndMs))
            } else {
                audioClip
            }
            val loopAudio = project.durationMode == MediaDurationMode.LOOP_SHORTER &&
                clippedAudio.trimmedDurationMs in 1 until outputDurationMs
            sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                .addItems(listOf(buildAudioItem(clippedAudio)))
                .setIsLooping(loopAudio)
                .build()
        }

        return Composition.Builder(sequences)
            .setVideoCompositorSettings(SplitScreenCompositorSettings(outputSize, frames))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .setTransmuxAudio(false)
            .setTransmuxVideo(false)
            .build()
    }

    private fun buildVisualSequence(
        media: MediaSource,
        frame: RectPx,
        outputDurationMs: Long,
        looping: Boolean,
    ): EditedMediaItemSequence {
        val item = when (media) {
            is MediaSource.Image -> buildImageItem(media, frame, outputDurationMs)
            is MediaSource.Video -> buildVideoItem(media, frame)
        }
        return EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
            .addItems(listOf(item))
            .setIsLooping(looping)
            .build()
    }

    private fun buildVideoItem(
        media: MediaSource.Video,
        frame: RectPx,
    ): EditedMediaItem {
        val clip = media.clip
        val cellWidth = frame.width.roundToInt().coerceAtLeast(2).even()
        val cellHeight = frame.height.roundToInt().coerceAtLeast(2).even()
        val effects = if (clip.fitMode == VideoFitMode.FILL) {
            listOf(
                cropEffectFor(media, frame),
                Presentation.createForWidthAndHeight(
                    cellWidth,
                    cellHeight,
                    Presentation.LAYOUT_STRETCH_TO_FIT,
                ),
            )
        } else {
            listOf(
                Presentation.createForWidthAndHeight(
                    cellWidth,
                    cellHeight,
                    Presentation.LAYOUT_SCALE_TO_FIT,
                ),
            )
        }
        return EditedMediaItem.Builder(clip.mediaItem())
            .setRemoveAudio(true)
            .setRemoveVideo(false)
            .setEffects(androidx.media3.transformer.Effects(emptyList(), effects))
            .build()
    }

    private fun buildImageItem(
        media: MediaSource.Image,
        frame: RectPx,
        outputDurationMs: Long,
    ): EditedMediaItem {
        val cellWidth = frame.width.roundToInt().coerceAtLeast(2).even()
        val cellHeight = frame.height.roundToInt().coerceAtLeast(2).even()
        val effects = if (media.fitMode == VideoFitMode.FILL) {
            listOf(
                cropEffectFor(media, frame),
                Presentation.createForWidthAndHeight(
                    cellWidth,
                    cellHeight,
                    Presentation.LAYOUT_STRETCH_TO_FIT,
                ),
            )
        } else {
            listOf(
                Presentation.createForWidthAndHeight(
                    cellWidth,
                    cellHeight,
                    Presentation.LAYOUT_SCALE_TO_FIT,
                ),
            )
        }
        val sourceUri = media.enhancedPath ?: media.uri
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(sourceUri))
            .setImageDurationMs(outputDurationMs)
            .build()
        return EditedMediaItem.Builder(mediaItem)
            .setFrameRate(ImageFrameRate)
            .setEffects(androidx.media3.transformer.Effects(emptyList(), effects))
            .build()
    }

    private fun buildVideoItem(
        clip: VideoClip,
        frame: RectPx,
    ): EditedMediaItem =
        buildVideoItem(MediaSource.Video(clip), frame)

    private fun buildAudioItem(clip: VideoClip): EditedMediaItem =
        EditedMediaItem.Builder(clip.mediaItem())
            .setRemoveAudio(false)
            .setRemoveVideo(true)
            .build()

    private fun cropEffectFor(media: MediaSource, frame: RectPx): Crop {
        val crop = LayoutMath.cropToFillSourceRect(
            sourceWidthPx = media.width.toFloat(),
            sourceHeightPx = media.height.toFloat(),
            destinationWidthPx = frame.width,
            destinationHeightPx = frame.height,
            transform = media.transform,
        )
        val left = (crop.left / media.width.toFloat()) * 2f - 1f
        val right = (crop.right / media.width.toFloat()) * 2f - 1f
        val top = 1f - (crop.top / media.height.toFloat()) * 2f
        val bottom = 1f - (crop.bottom / media.height.toFloat()) * 2f
        return Crop(left, right, bottom, top)
    }

    private fun VideoClip.mediaItem(): MediaItem =
        MediaItem.Builder()
            .setUri(Uri.parse(uri))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(trimStartMs)
                    .setEndPositionMs(trimEndMs)
                    .build(),
            )
            .build()

    private fun selectedAudioClip(project: VideoMergeProject): VideoClip? =
        project.primaryAudioClip

    private fun MediaSource.visualDurationMs(): Long =
        when (this) {
            is MediaSource.Image -> DefaultImageDurationMs
            is MediaSource.Video -> clip.trimmedDurationMs
        }

    private suspend fun runTransformer(
        composition: Composition,
        outputFile: File,
        onProgress: (Float) -> Unit,
    ) {
        suspendCancellableCoroutine { continuation ->
            val thread = HandlerThread("SplitFrameVideoExport")
            thread.start()
            val handler = Handler(thread.looper)
            val finished = AtomicBoolean(false)
            var transformer: Transformer? = null

            fun closeThread() {
                if (finished.compareAndSet(false, true)) {
                    thread.quitSafely()
                }
            }

            continuation.invokeOnCancellation {
                handler.post {
                    transformer?.cancel()
                    closeThread()
                }
            }

            handler.post {
                try {
                    transformer = Transformer.Builder(context)
                        .setLooper(thread.looper)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(
                            DefaultEncoderFactory.Builder(context)
                                .setEnableFallback(true)
                                .build(),
                        )
                        .addListener(
                            object : Transformer.Listener {
                                override fun onCompleted(
                                    composition: Composition,
                                    exportResult: androidx.media3.transformer.ExportResult,
                                ) {
                                    if (continuation.isActive) continuation.resume(Unit)
                                    closeThread()
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: androidx.media3.transformer.ExportResult,
                                    exportException: ExportException,
                                ) {
                                    if (continuation.isActive) continuation.resumeWithException(exportException)
                                    closeThread()
                                }
                            },
                        )
                        .build()
                    transformer?.start(composition, outputFile.absolutePath)
                    pollProgress(handler, transformer, onProgress, ::closeThread)
                } catch (throwable: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(throwable)
                    closeThread()
                }
            }
        }
    }

    private fun pollProgress(
        handler: Handler,
        transformer: Transformer?,
        onProgress: (Float) -> Unit,
        onDone: () -> Unit,
    ) {
        val activeTransformer = transformer ?: return
        val holder = ProgressHolder()
        val state = activeTransformer.getProgress(holder)
        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
            onProgress(holder.progress.coerceIn(0, 100) / 100f)
        }
        if (state == Transformer.PROGRESS_STATE_NOT_STARTED) {
            onDone()
        } else {
            handler.postDelayed(
                { pollProgress(handler, activeTransformer, onProgress, onDone) },
                ProgressPollMs,
            )
        }
    }

    private fun createTempOutputFile(projectId: String): File {
        val dir = File(context.cacheDir, "video_exports").apply { mkdirs() }
        return File(dir, "${projectId}_${System.currentTimeMillis()}.mp4")
    }

    private fun publishToMediaStore(tempFile: File): Uri {
        val resolver = context.contentResolver
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "SplitFrame_$timestamp.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SplitFrame")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)) {
            "Could not create MediaStore video."
        }
        resolver.openOutputStream(uri)?.use { output ->
            tempFile.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Could not open MediaStore output.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    private class SplitScreenCompositorSettings(
        private val outputSize: OutputSize,
        private val frames: Map<Int, RectPx>,
    ) : VideoCompositorSettings {
        override fun getOutputSize(inputSizes: MutableList<Size>): Size =
            Size(outputSize.widthPx, outputSize.heightPx)

        override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
            val frame = frames[inputId] ?: RectPx(0f, 0f, outputSize.widthPx.toFloat(), outputSize.heightPx.toFloat())
            return object : OverlaySettings {
                override fun getBackgroundFrameAnchor(): Pair<Float, Float> =
                    Pair.create(
                        (frame.centerX / outputSize.widthPx.toFloat()) * 2f - 1f,
                        1f - (frame.centerY / outputSize.heightPx.toFloat()) * 2f,
                    )

                override fun getOverlayFrameAnchor(): Pair<Float, Float> =
                    Pair.create(0f, 0f)

                override fun getScale(): Pair<Float, Float> =
                    Pair.create(
                        frame.width / outputSize.widthPx.toFloat(),
                        frame.height / outputSize.heightPx.toFloat(),
                    )
            }
        }
    }

    private fun Int.even(): Int = if (this % 2 == 0) this else this + 1

    private companion object {
        const val ProgressPollMs = 500L
        const val DefaultImageDurationMs = 5_000L
        const val ImageFrameRate = 30
    }
}
