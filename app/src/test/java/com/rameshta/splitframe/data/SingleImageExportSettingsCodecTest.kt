package com.rameshta.splitframe.data

import com.rameshta.splitframe.domain.ExportContentMode
import com.rameshta.splitframe.domain.ImageDimensions
import com.rameshta.splitframe.domain.SingleImageExportSettings
import com.rameshta.splitframe.domain.SingleImageOutputFormat
import com.rameshta.splitframe.domain.SingleImageResizePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SingleImageExportSettingsCodecTest {
    @Test
    fun settingsRoundTripWithoutPersistingDeviceSpecificDimensions() {
        val settings = SingleImageExportSettings(
            preset = SingleImageResizePreset.DeviceWallpaper,
            outputFormat = SingleImageOutputFormat.Webp,
            encodingQuality = 88,
            customWidthPx = 1234,
            customHeightPx = 2345,
            lockAspectRatio = false,
            contentMode = ExportContentMode.Fill,
        )

        val restored = SingleImageExportSettingsCodec.decode(
            SingleImageExportSettingsCodec.encode(settings),
        )

        assertEquals(settings, restored)
        assertNull(restored?.toRequest()?.deviceWallpaperDimensions)
        assertEquals(
            ImageDimensions(1440, 3200),
            restored?.toRequest(ImageDimensions(1440, 3200))?.deviceWallpaperDimensions,
        )
    }

    @Test
    fun partialCustomDimensionsAndFitModeRoundTrip() {
        val settings = SingleImageExportSettings(
            preset = SingleImageResizePreset.Custom,
            customWidthPx = 2048,
            customHeightPx = null,
            lockAspectRatio = true,
            contentMode = ExportContentMode.Fit,
        )

        assertEquals(
            settings,
            SingleImageExportSettingsCodec.decode(SingleImageExportSettingsCodec.encode(settings)),
        )
    }

    @Test
    fun unknownVersionPresetAndInvalidQualityFailClosed() {
        assertNull(SingleImageExportSettingsCodec.decode("v2|Custom|Jpeg|94|-|-|true|Fit"))
        assertNull(SingleImageExportSettingsCodec.decode("v1|Unknown|Jpeg|94|-|-|true|Fit"))
        assertNull(SingleImageExportSettingsCodec.decode("v1|Custom|Jpeg|101|-|-|true|Fit"))
        assertNull(SingleImageExportSettingsCodec.decode("not-a-settings-payload"))
    }

    @Test
    fun everyPresetAndQualityBoundaryRoundTrips() {
        SingleImageResizePreset.entries.forEach { preset ->
            listOf(60, 100).forEach { quality ->
                val settings = SingleImageExportSettings(
                    preset = preset,
                    encodingQuality = quality,
                    contentMode = ExportContentMode.Fill,
                )
                assertEquals(
                    settings,
                    SingleImageExportSettingsCodec.decode(SingleImageExportSettingsCodec.encode(settings)),
                )
            }
        }
    }

    @Test
    fun malformedFieldsAndAmbiguousLockedCustomInputFailClosed() {
        assertNull(SingleImageExportSettingsCodec.decode("v1|Custom|Unknown|94|-|-|true|Fit"))
        assertNull(SingleImageExportSettingsCodec.decode("v1|Custom|Jpeg|59|-|-|true|Fit"))
        assertNull(SingleImageExportSettingsCodec.decode("v1|Custom|Jpeg|94|wide|-|true|Fit"))
        assertNull(SingleImageExportSettingsCodec.decode("v1|Custom|Jpeg|94|-|-|maybe|Fit"))
        assertNull(SingleImageExportSettingsCodec.decode("v1|Custom|Jpeg|94|-|-|true|Unknown"))
        assertNull(SingleImageExportSettingsCodec.decode("v1|Custom|Jpeg|94|1000|1000|true|Fit"))
    }
}
