package com.rameshta.splitframe.export

import androidx.media3.transformer.ExportException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFailureMessagesTest {
    @Test
    fun `storage exhaustion and revoked access are actionable`() {
        assertEquals(
            "Not enough storage is available. Free some space and try again.",
            actionableExportFailure(IOException("ENOSPC"), "fallback"),
        )
        assertEquals(
            "Media access is no longer available. Choose the affected media again and retry.",
            actionableExportFailure(IllegalStateException("write", SecurityException("revoked")), "fallback"),
        )
    }

    @Test
    fun `unknown failure preserves useful provider message and fallback`() {
        assertEquals("Provider refused output", actionableExportFailure(IOException("Provider refused output"), "fallback"))
        assertEquals("fallback", actionableExportFailure(IOException(), "fallback"))
    }

    @Test
    fun `codec error categories give recovery guidance`() {
        assertEquals(
            "This device could not encode the video at the selected size. Try 720p or a shorter project.",
            videoExportErrorCodeMessage(ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED, "fallback"),
        )
        assertEquals(
            "A selected clip uses a codec this device cannot decode. Choose a different clip.",
            videoExportErrorCodeMessage(ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, "fallback"),
        )
    }
}
