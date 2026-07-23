package com.rameshta.splitframe.data

import com.rameshta.splitframe.domain.ExportContentMode
import com.rameshta.splitframe.domain.ImageMetadataPolicy
import com.rameshta.splitframe.domain.SingleImageExportSettings
import com.rameshta.splitframe.domain.SingleImageOutputFormat
import com.rameshta.splitframe.domain.SingleImageResizePreset
import com.rameshta.splitframe.domain.TargetSizeUnit

internal object SingleImageExportSettingsCodec {
    fun encode(settings: SingleImageExportSettings): String =
        listOf(
            Version,
            settings.preset.name,
            settings.outputFormat.name,
            settings.encodingQuality.toString(),
            settings.resizePercent.toString(),
            settings.targetSizeValue?.toString() ?: NullToken,
            settings.targetSizeUnit.name,
            settings.customWidthPx?.toString() ?: NullToken,
            settings.customHeightPx?.toString() ?: NullToken,
            settings.lockAspectRatio.toString(),
            settings.contentMode.name,
            settings.metadataPolicy.name,
        ).joinToString(Separator)

    fun decode(value: String): SingleImageExportSettings? {
        val fields = value.split(Separator)
        if (fields.firstOrNull() == LegacyVersion) return decodeLegacy(fields)
        if (fields.firstOrNull() == PreviousVersion) return decodePrevious(fields)
        if (fields.size != FieldCount || fields[0] != Version) return null
        val preset = SingleImageResizePreset.entries.firstOrNull { it.name == fields[1] } ?: return null
        val format = SingleImageOutputFormat.entries.firstOrNull { it.name == fields[2] } ?: return null
        val quality = fields[3].toIntOrNull()?.takeIf { it in 60..100 } ?: return null
        val resizePercent = fields[4].toIntOrNull()?.takeIf { it in 1..400 } ?: return null
        val targetSize = fields[5].decodeNullableInt() ?: return null
        if (targetSize.value != null && targetSize.value <= 0) return null
        val targetUnit = TargetSizeUnit.entries.firstOrNull { it.name == fields[6] } ?: return null
        val customWidth = fields[7].decodeNullableInt() ?: return null
        val customHeight = fields[8].decodeNullableInt() ?: return null
        val lockAspectRatio = fields[9].toBooleanStrictOrNull() ?: return null
        val contentMode = ExportContentMode.entries.firstOrNull { it.name == fields[10] } ?: return null
        val metadataPolicy = ImageMetadataPolicy.entries.firstOrNull { it.name == fields[11] } ?: return null
        if (lockAspectRatio && customWidth.value != null && customHeight.value != null) return null
        return SingleImageExportSettings(
            preset = preset,
            outputFormat = format,
            encodingQuality = quality,
            resizePercent = resizePercent,
            targetSizeValue = targetSize.value,
            targetSizeUnit = targetUnit,
            customWidthPx = customWidth.value,
            customHeightPx = customHeight.value,
            lockAspectRatio = lockAspectRatio,
            contentMode = contentMode,
            metadataPolicy = metadataPolicy,
        )
    }

    private fun decodePrevious(fields: List<String>): SingleImageExportSettings? {
        if (fields.size != PreviousFieldCount) return null
        return decode(fields + ImageMetadataPolicy.RemoveMetadata.name, expectedVersion = PreviousVersion)
    }

    private fun decode(fields: List<String>, expectedVersion: String): SingleImageExportSettings? {
        if (fields.size != FieldCount || fields[0] != expectedVersion) return null
        val currentFields = fields.toMutableList().also { it[0] = Version }
        return decode(currentFields.joinToString(Separator))
    }

    private fun decodeLegacy(fields: List<String>): SingleImageExportSettings? {
        if (fields.size != LegacyFieldCount) return null
        val preset = SingleImageResizePreset.entries.firstOrNull { it.name == fields[1] } ?: return null
        val format = SingleImageOutputFormat.entries.firstOrNull { it.name == fields[2] } ?: return null
        val quality = fields[3].toIntOrNull()?.takeIf { it in 60..100 } ?: return null
        val customWidth = fields[4].decodeNullableInt() ?: return null
        val customHeight = fields[5].decodeNullableInt() ?: return null
        val lockAspectRatio = fields[6].toBooleanStrictOrNull() ?: return null
        val contentMode = ExportContentMode.entries.firstOrNull { it.name == fields[7] } ?: return null
        if (lockAspectRatio && customWidth.value != null && customHeight.value != null) return null
        return SingleImageExportSettings(
            preset = preset,
            outputFormat = format,
            encodingQuality = quality,
            customWidthPx = customWidth.value,
            customHeightPx = customHeight.value,
            lockAspectRatio = lockAspectRatio,
            contentMode = contentMode,
        )
    }

    private fun String.decodeNullableInt(): DecodedInt? =
        if (this == NullToken) {
            DecodedInt(null)
        } else {
            toIntOrNull()?.let(::DecodedInt)
        }

    private data class DecodedInt(val value: Int?)

    private const val Version = "v3"
    private const val PreviousVersion = "v2"
    private const val LegacyVersion = "v1"
    private const val Separator = "|"
    private const val NullToken = "-"
    private const val FieldCount = 12
    private const val PreviousFieldCount = 11
    private const val LegacyFieldCount = 8
}
