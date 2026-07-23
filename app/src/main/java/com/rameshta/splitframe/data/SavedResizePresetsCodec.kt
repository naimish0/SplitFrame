package com.rameshta.splitframe.data

import com.rameshta.splitframe.domain.SavedResizePreset
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object SavedResizePresetsCodec {
    fun encode(presets: List<SavedResizePreset>): String =
        presets.take(MaxPresets).joinToString("\n") { preset ->
            "${preset.name.urlEncode()}:${SingleImageExportSettingsCodec.encode(preset.settings).urlEncode()}"
        }

    fun decode(value: String): List<SavedResizePreset>? {
        if (value.isBlank()) return emptyList()
        val decoded = value.lines().map { line ->
            val separator = line.indexOf(':')
            if (separator <= 0 || separator == line.lastIndex) return null
            val name = line.substring(0, separator).urlDecode()
                ?.trim()
                ?.takeIf { it.length in 1..MaxNameLength }
                ?: return null
            val settingsPayload = line.substring(separator + 1).urlDecode() ?: return null
            val settings = SingleImageExportSettingsCodec.decode(settingsPayload) ?: return null
            SavedResizePreset(name, settings)
        }
        if (decoded.size > MaxPresets) return null
        if (decoded.map { it.name.lowercase() }.distinct().size != decoded.size) return null
        return decoded
    }

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private fun String.urlDecode(): String? =
        runCatching { URLDecoder.decode(this, StandardCharsets.UTF_8.name()) }.getOrNull()

    const val MaxPresets = 12
    const val MaxNameLength = 40
}
