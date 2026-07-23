package com.rameshta.splitframe.export

import android.content.ContentResolver
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.RequiresApi
import com.rameshta.splitframe.domain.VideoClip
import com.rameshta.splitframe.domain.VideoSupportStatus
import java.util.UUID

class VideoMetadataReader(
    private val contentResolver: ContentResolver,
) {
    fun read(uriString: String): VideoMetadataResult {
        val uri = Uri.parse(uriString)
        if (!canOpen(uri)) return VideoMetadataResult.Unsupported(VideoSupportStatus.Unreadable)
        val resolverMimeType = try {
            contentResolver.getType(uri)?.lowercase()
        } catch (_: SecurityException) {
            return VideoMetadataResult.Unsupported(VideoSupportStatus.Unreadable)
        } catch (_: IllegalArgumentException) {
            return VideoMetadataResult.Unsupported(VideoSupportStatus.UnsupportedMimeType)
        }
        if (resolverMimeType != null && resolverMimeType !in SupportedMimeTypes) {
            return VideoMetadataResult.Unsupported(VideoSupportStatus.UnsupportedMimeType)
        }

        val retriever = MediaMetadataRetriever()
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                retriever.setDataSource(descriptor.fileDescriptor)
            } ?: return VideoMetadataResult.Unsupported(VideoSupportStatus.Unreadable)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            if (!hasVideo) return VideoMetadataResult.Unsupported(VideoSupportStatus.MissingVideoTrack)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return VideoMetadataResult.Unsupported(VideoSupportStatus.InvalidDuration)
            if (durationMs < VideoMinDurationMs) {
                return VideoMetadataResult.Unsupported(VideoSupportStatus.InvalidDuration)
            }

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                ?: return VideoMetadataResult.Unsupported(VideoSupportStatus.Unreadable)
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                ?: return VideoMetadataResult.Unsupported(VideoSupportStatus.Unreadable)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val mimeType = resolverMimeType
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.lowercase()
            if (mimeType != null && mimeType !in SupportedMimeTypes) {
                return VideoMetadataResult.Unsupported(VideoSupportStatus.UnsupportedMimeType)
            }

            VideoMetadataResult.Valid(
                VideoClip(
                    id = UUID.randomUUID().toString(),
                    uri = uriString,
                    durationMs = durationMs,
                    width = width,
                    height = height,
                    rotationDegrees = rotation,
                    frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull(),
                    mimeType = mimeType,
                    sizeBytes = querySize(uri),
                    hasAudio = hasAudio,
                    isHdr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        retriever.hasHdrMetadata()
                    } else {
                        false
                    },
                ),
            )
        } catch (security: SecurityException) {
            Log.w(TAG, "Video URI permission is unavailable.", security)
            VideoMetadataResult.Unsupported(VideoSupportStatus.Unreadable)
        } catch (illegal: IllegalArgumentException) {
            Log.w(TAG, "Video metadata source is unsupported.", illegal)
            VideoMetadataResult.Unsupported(VideoSupportStatus.UnsupportedDrmOrCodec)
        } catch (throwable: Throwable) {
            Log.w(TAG, "Could not inspect selected video.", throwable)
            VideoMetadataResult.Unsupported(VideoSupportStatus.Unreadable)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun canOpen(uri: Uri): Boolean =
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: Throwable) {
            false
        }

    private fun querySize(uri: Uri): Long? =
        runCatching {
            val cursor: Cursor = contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null) ?: return null
            cursor.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !it.isNull(index)) it.getLong(index) else null
                } else {
                    null
                }
            }
        }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.R)
    private fun MediaMetadataRetriever.hasHdrMetadata(): Boolean {
        val transfer = extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)?.toIntOrNull()
        val standard = extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_STANDARD)?.toIntOrNull()
        return transfer in HdrTransfers || standard in HdrStandards
    }

    private companion object {
        const val TAG = "VideoMetadataReader"
        const val VideoMinDurationMs = 1_000L
        val SupportedMimeTypes = setOf("video/mp4", "video/quicktime", "video/3gpp", "video/webm", "video/x-matroska")
        val HdrTransfers = setOf(6, 7)
        val HdrStandards = setOf(6)
    }
}

sealed interface VideoMetadataResult {
    data class Valid(val clip: VideoClip) : VideoMetadataResult
    data class Unsupported(val status: VideoSupportStatus) : VideoMetadataResult
}
