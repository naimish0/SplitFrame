package com.rameshta.splitframe.data

import com.rameshta.splitframe.domain.SavedResizePreset
import com.rameshta.splitframe.domain.SingleImageExportSettings
import com.rameshta.splitframe.domain.SingleImageOutputFormat
import com.rameshta.splitframe.domain.SingleImageResizePreset
import com.rameshta.splitframe.domain.TargetSizeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedResizePresetsCodecTest {
    @Test
    fun namesAndCompleteSettingsRoundTrip() {
        val presets = listOf(
            SavedResizePreset(
                name = "Marketplace 500 KB",
                settings = SingleImageExportSettings(
                    preset = SingleImageResizePreset.Percentage,
                    outputFormat = SingleImageOutputFormat.Webp,
                    resizePercent = 75,
                    targetSizeValue = 500,
                    targetSizeUnit = TargetSizeUnit.KB,
                ),
            ),
            SavedResizePreset(
                name = "Print: 2 MB",
                settings = SingleImageExportSettings(
                    preset = SingleImageResizePreset.Custom,
                    customWidthPx = 2400,
                    targetSizeValue = 2,
                    targetSizeUnit = TargetSizeUnit.MB,
                ),
            ),
        )

        assertEquals(presets, SavedResizePresetsCodec.decode(SavedResizePresetsCodec.encode(presets)))
    }

    @Test
    fun duplicateNamesAndMalformedPayloadFailClosed() {
        val settings = SingleImageExportSettings()
        val duplicate = listOf(
            SavedResizePreset("Email", settings),
            SavedResizePreset("email", settings),
        )

        assertNull(SavedResizePresetsCodec.decode(SavedResizePresetsCodec.encode(duplicate)))
        assertNull(SavedResizePresetsCodec.decode("missing-separator"))
    }
}
