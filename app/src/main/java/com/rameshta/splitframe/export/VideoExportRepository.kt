package com.rameshta.splitframe.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
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
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class VideoExportRepository(
    private val context: Context,
) {
    suspend fun export(
        project: VideoMergeProject,
        onProgress: (Float) -> Boolean,
        onPublished: suspend (String) -> Boolean,
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
                val savedUri = publishToMediaStore(tempFile, onProgress, onPublished)
                ExportResult.Success(savedUri.toString())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (oom: OutOfMemoryError) {
                ExportResult.Failure("Not enough memory to export this video.")
            } catch (throwable: Throwable) {
                ExportResult.Failure(videoExportFailureMessage(throwable))
            } finally {
                runCatching { tempFile.delete() }
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
        onProgress: (Float) -> Boolean,
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
                                .setEnableFallback(VideoEncoderFallbackEnabled)
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
                    pollProgress(
                        handler = handler,
                        transformer = transformer,
                        onProgress = onProgress,
                        onCancelled = {
                            if (continuation.isActive) {
                                continuation.cancel(CancellationException("Video export was superseded."))
                            }
                        },
                        onFailure = { throwable ->
                            transformer?.cancel()
                            if (continuation.isActive) continuation.resumeWithException(throwable)
                            closeThread()
                        },
                        onDone = ::closeThread,
                    )
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
        onProgress: (Float) -> Boolean,
        onCancelled: () -> Unit,
        onFailure: (Throwable) -> Unit,
        onDone: () -> Unit,
    ) {
        val activeTransformer = transformer ?: return
        val holder = ProgressHolder()
        val state = activeTransformer.getProgress(holder)
        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
            val shouldContinue = try {
                onProgress(holder.progress.coerceIn(0, 100) / 100f)
            } catch (throwable: Throwable) {
                onFailure(throwable)
                return
            }
            if (!shouldContinue) {
                onCancelled()
                return
            }
        }
        if (state == Transformer.PROGRESS_STATE_NOT_STARTED) {
            onDone()
        } else {
            handler.postDelayed(
                {
                    pollProgress(
                        handler,
                        activeTransformer,
                        onProgress,
                        onCancelled,
                        onFailure,
                        onDone,
                    )
                },
                ProgressPollMs,
            )
        }
    }

    private fun createTempOutputFile(projectId: String): File {
        val dir = File(context.cacheDir, "video_exports")
        check((dir.isDirectory || dir.mkdirs()) && dir.isDirectory) {
            "Could not create temporary video workspace."
        }
        cleanupStaleVideoExportFiles(
            directory = dir,
            olderThanMillis = System.currentTimeMillis() - StaleTempFileAgeMillis,
        )
        return File(dir, "${projectId}_${UUID.randomUUID()}.mp4")
    }

    private suspend fun publishToMediaStore(
        tempFile: File,
        onProgress: (Float) -> Boolean,
        onPublished: suspend (String) -> Boolean,
    ): Uri {
        val expectedBytes = tempFile.requirePlayableVideoOutputSize()
        val processingContext = currentCoroutineContext()
        fun ensureCurrentExport() {
            processingContext.ensureActive()
            if (!onProgress(1f)) {
                throw CancellationException("Video export was superseded during publication.")
            }
        }
        ensureCurrentExport()

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
        return transactionalVideoPublication(
            insert = { resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) },
            write = { uri ->
                resolver.openOutputStream(uri)?.use { output ->
                    tempFile.inputStream().use { input ->
                        copyVideoOutput(
                            input = input,
                            output = output,
                            expectedBytes = expectedBytes,
                            ensureActive = processingContext::ensureActive,
                        )
                    }
                } ?: error("Could not open MediaStore output.")
            },
            beforePublish = ::ensureCurrentExport,
            publish = { uri ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val publishValues = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    check(resolver.update(uri, publishValues, null, null) == 1) {
                        "Could not publish MediaStore video."
                    }
                }
            },
            commit = { uri ->
                commitVideoPublication(
                    ensureActive = processingContext::ensureActive,
                    commit = { onPublished(uri.toString()) },
                )
            },
            rollback = { uri -> resolver.delete(uri, null, null) > 0 },
        )
    }

    private fun Int.even(): Int = if (this % 2 == 0) this else this + 1

    private companion object {
        const val ProgressPollMs = 500L
        const val MinVideoMergeClipCount = 2
        const val StaleTempFileAgeMillis = 24L * 60L * 60L * 1_000L
    }
}

internal fun cleanupStaleVideoExportFiles(
    directory: File,
    olderThanMillis: Long,
): Int = directory.listFiles()
    ?.asSequence()
    ?.filter { file -> file.isFile && file.extension.equals("mp4", ignoreCase = true) }
    ?.filter { file -> file.lastModified() in 1 until olderThanMillis }
    ?.count(File::delete)
    ?: 0

internal const val VideoEncoderFallbackEnabled = false

@OptIn(UnstableApi::class)
internal fun videoExportFailureMessage(failure: Throwable): String {
    val errorCode = (failure as? ExportException)?.errorCode
    return videoExportErrorCodeMessage(
        errorCode = errorCode,
        fallback = actionableExportFailure(failure, "Video export failed."),
    )
}

@OptIn(UnstableApi::class)
internal fun videoExportErrorCodeMessage(errorCode: Int?, fallback: String): String =
    when (errorCode) {
        ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
        ExportException.ERROR_CODE_ENCODING_FAILED,
        ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
        -> "This device could not encode the video at the selected size. Try 720p or a shorter project."
        ExportException.ERROR_CODE_DECODER_INIT_FAILED,
        ExportException.ERROR_CODE_DECODING_FAILED,
        ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        -> "A selected clip uses a codec this device cannot decode. Choose a different clip."
        else -> fallback
    }

internal fun File.requireVideoOutputSize(): Long {
    check(isFile) { "Rendered video is unavailable." }
    return length().also { size ->
        check(size > 0L) { "Rendered video is empty." }
    }
}

@OptIn(UnstableApi::class)
private fun File.requirePlayableVideoOutputSize(): Long {
    val size = requireVideoOutputSize()
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(absolutePath)
        val videoFormats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
            .filter { format -> format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
        check(videoFormats.isNotEmpty()) { "Rendered output does not contain a playable video track." }
        val knownDurations = videoFormats.mapNotNull { format ->
            if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else null
        }
        check(knownDurations.isEmpty() || knownDurations.any { it > 0L }) {
            "Rendered video has an invalid duration."
        }
    } finally {
        extractor.release()
    }
    return size
}

internal fun copyVideoOutput(
    input: InputStream,
    output: OutputStream,
    expectedBytes: Long,
    ensureActive: () -> Unit,
) {
    check(expectedBytes > 0L) { "Rendered video is empty." }
    val buffer = ByteArray(VideoCopyBufferBytes)
    var copiedBytes = 0L
    while (true) {
        ensureActive()
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        output.write(buffer, 0, read)
        copiedBytes += read
        check(copiedBytes <= expectedBytes) { "Could not copy complete video." }
    }
    ensureActive()
    check(copiedBytes == expectedBytes) { "Could not copy complete video." }
    output.flush()
    ensureActive()
}

internal suspend fun commitVideoPublication(
    ensureActive: () -> Unit,
    commit: suspend () -> Boolean,
) {
    ensureActive()
    withContext(NonCancellable) {
        if (!commit()) {
            throw CancellationException("Video export ownership changed during publication.")
        }
    }
}

internal suspend fun <Entry : Any> transactionalVideoPublication(
    insert: () -> Entry?,
    write: (Entry) -> Unit,
    beforePublish: () -> Unit,
    publish: (Entry) -> Unit,
    commit: suspend (Entry) -> Unit,
    rollback: (Entry) -> Boolean,
): Entry {
    val entry = insert() ?: error("Could not create MediaStore video.")
    return try {
        write(entry)
        beforePublish()
        publish(entry)
        commit(entry)
        entry
    } catch (failure: Throwable) {
        val rollbackFailure = runCatching {
            check(rollback(entry)) { "Could not remove incomplete video." }
        }.exceptionOrNull()
        if (rollbackFailure != null && rollbackFailure !== failure) {
            failure.addSuppressed(rollbackFailure)
        }
        throw failure
    }
}

private const val VideoCopyBufferBytes = 64 * 1024
