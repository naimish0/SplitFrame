package com.rameshta.splitframe.data

import com.rameshta.splitframe.domain.ExportContentMode
import com.rameshta.splitframe.domain.SingleImageExportSettings
import com.rameshta.splitframe.domain.SingleImageOutputFormat
import com.rameshta.splitframe.domain.SingleImageResizePreset

internal object SingleImageExportSettingsCodec {
    fun encode(settings: SingleImageExportSettings): String =
        listOf(
            Version,
            settings.preset.name,
            settings.outputFormat.name,
            settings.encodingQuality.toString(),
            settings.customWidthPx?.toString() ?: NullToken,
            settings.customHeightPx?.toString() ?: NullToken,
            settings.lockAspectRatio.toString(),
            settings.contentMode.name,
        ).joinToString(Separator)

    fun decode(value: String): SingleImageExportSettings? {
        val fields = value.split(Separator)
        if (fields.size != FieldCount || fields[0] != Version) return null
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

    private const val Version = "v1"
    private const val Separator = "|"
    private const val NullToken = "-"
    private const val FieldCount = 8
}
