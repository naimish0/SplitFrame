package com.rameshta.splitframe.export

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoExportWorkerTest {
    @Test
    fun foregroundServiceTypeMatchesPlatformCapabilities() {
        assertNull(videoExportForegroundServiceType(28))
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            videoExportForegroundServiceType(29),
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            videoExportForegroundServiceType(34),
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            videoExportForegroundServiceType(35),
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            videoExportForegroundServiceType(36),
        )
    }
}
