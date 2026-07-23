package com.rameshta.splitframe.export

import android.content.ContentResolver
import android.net.Uri
import com.rameshta.splitframe.domain.ImageSource
import com.rameshta.splitframe.domain.MediaSource
import com.rameshta.splitframe.domain.VideoSupportStatus
import java.util.UUID

class MixedMediaMetadataReader(
    private val contentResolver: ContentResolver,
    private val imageSourceReader: ImageSourceReader,
    private val videoMetadataReader: VideoMetadataReader,
) {
    fun read(uri: String): MixedMediaMetadataResult {
        val parsed = Uri.parse(uri)
        val mimeType = try {
            contentResolver.getType(parsed)?.lowercase()
        } catch (_: SecurityException) {
            return MixedMediaMetadataResult.Unsupported(MediaMetadataFailure.Unreadable)
        } catch (_: IllegalArgumentException) {
            return MixedMediaMetadataResult.Unsupported(MediaMetadataFailure.UnsupportedFormat)
        }
        return when {
            mimeType?.startsWith("image/") == true -> readImage(uri, mimeType)
            mimeType?.startsWith("video/") == true -> readVideo(uri)
            mimeType == null -> readUnknown(uri)
            else -> MixedMediaMetadataResult.Unsupported(MediaMetadataFailure.UnsupportedFormat)
        }
    }

    private fun readImage(uri: String, mimeType: String?): MixedMediaMetadataResult {
        val source = ImageSource.LocalUri(uri)
        return when (val validation = imageSourceReader.validate(source)) {
            is ImageValidationResult.Valid -> MixedMediaMetadataResult.Valid(
                MediaSource.Image(
                    id = UUID.randomUUID().toString(),
                    uri = uri,
                    width = validation.dimensions.widthPx,
                    height = validation.dimensions.heightPx,
                    mimeType = mimeType,
                ),
            )
            ImageValidationResult.Unreadable -> MixedMediaMetadataResult.Unsupported(MediaMetadataFailure.Unreadable)
            ImageValidationResult.UnsupportedFormat -> MixedMediaMetadataResult.Unsupported(MediaMetadataFailure.UnsupportedFormat)
        }
    }

    private fun readVideo(uri: String): MixedMediaMetadataResult =
        when (val result = videoMetadataReader.read(uri)) {
            is VideoMetadataResult.Valid -> MixedMediaMetadataResult.Valid(MediaSource.Video(result.clip))
            is VideoMetadataResult.Unsupported -> MixedMediaMetadataResult.Unsupported(result.status.toFailure())
        }

    private fun readUnknown(uri: String): MixedMediaMetadataResult =
        when (val image = readImage(uri, mimeType = null)) {
            is MixedMediaMetadataResult.Valid -> image
            is MixedMediaMetadataResult.Unsupported -> readVideo(uri)
        }

    private fun VideoSupportStatus.toFailure(): MediaMetadataFailure =
        when (this) {
            VideoSupportStatus.Supported -> MediaMetadataFailure.UnsupportedFormat
            VideoSupportStatus.UnsupportedMimeType -> MediaMetadataFailure.UnsupportedFormat
            VideoSupportStatus.Unreadable -> MediaMetadataFailure.Unreadable
            VideoSupportStatus.MissingVideoTrack -> MediaMetadataFailure.MissingVideoTrack
            VideoSupportStatus.UnsupportedDrmOrCodec -> MediaMetadataFailure.UnsupportedDrmOrCodec
            VideoSupportStatus.InvalidDuration -> MediaMetadataFailure.InvalidDuration
        }
}

sealed interface MixedMediaMetadataResult {
    data class Valid(val media: MediaSource) : MixedMediaMetadataResult
    data class Unsupported(val failure: MediaMetadataFailure) : MixedMediaMetadataResult
}

enum class MediaMetadataFailure {
    UnsupportedFormat,
    Unreadable,
    MissingVideoTrack,
    UnsupportedDrmOrCodec,
    InvalidDuration,
}
