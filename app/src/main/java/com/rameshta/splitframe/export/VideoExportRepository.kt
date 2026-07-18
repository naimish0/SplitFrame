package com.rameshta.splitframe.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.rameshta.splitframe.domain.ExportResult
import com.rameshta.splitframe.domain.LayoutMath
import com.rameshta.splitframe.domain.MediaSource
import com.rameshta.splitframe.domain.OutputSize
import com.rameshta.splitframe.domain.RectPx
import com.rameshta.splitframe.domain.VideoClip
import com.rameshta.splitframe.domain.VideoLayoutMath
import com.rameshta.splitframe.domain.VideoMergeProject
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
        val clips = project.orderedClips
        require(clips.size >= MinVideoMergeClipCount && clips.size == project.mediaByCell.size) {
            "Select at least two videos before exporting."
        }
        val outputFrame = RectPx(
            left = 0f,
            top = 0f,
            right = outputSize.widthPx.toFloat(),
            bottom = outputSize.heightPx.toFloat(),
        )
        val sequence = EditedMediaItemSequence.withAudioAndVideoFrom(
            clips.map { clip -> buildMergedVideoItem(clip, outputFrame) },
        )

        return Composition.Builder(listOf(sequence))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .setTransmuxAudio(false)
            .setTransmuxVideo(false)
            .build()
    }

    private fun buildMergedVideoItem(clip: VideoClip, outputFrame: RectPx): EditedMediaItem {
        val media = MediaSource.Video(clip)
        return EditedMediaItem.Builder(clip.mediaItem())
            .setRemoveAudio(false)
            .setRemoveVideo(false)
            .setEffects(Effects(emptyList(), fillFrameEffects(media, outputFrame)))
            .build()
    }

    private fun fillFrameEffects(
        media: MediaSource.Video,
        frame: RectPx,
    ): List<androidx.media3.common.Effect> =
        listOf(
            cropEffectFor(media, frame),
            Presentation.createForWidthAndHeight(
                frame.width.roundToInt().coerceAtLeast(2).even(),
                frame.height.roundToInt().coerceAtLeast(2).even(),
                Presentation.LAYOUT_STRETCH_TO_FIT,
            ),
        )

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
                                .setEnableFallback(false)
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

    private fun Int.even(): Int = if (this % 2 == 0) this else this + 1

    private companion object {
        const val ProgressPollMs = 500L
        const val MinVideoMergeClipCount = 2
    }
}
