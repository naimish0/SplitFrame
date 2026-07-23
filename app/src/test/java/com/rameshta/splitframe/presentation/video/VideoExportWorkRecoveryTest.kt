package com.rameshta.splitframe.presentation.video

import androidx.work.WorkInfo
import com.rameshta.splitframe.data.local.VideoExportWorkEntity
import com.rameshta.splitframe.domain.ExportResult
import com.rameshta.splitframe.export.VideoExportWorker
import com.rameshta.splitframe.export.recoveredVideoExportState
import com.rameshta.splitframe.export.isLiveVideoExportState
import com.rameshta.splitframe.export.isEligibleForStartupVideoExportRecovery
import com.rameshta.splitframe.export.videoExportRecoveryDelayMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoExportWorkRecoveryTest {
    @Test
    fun queuedAndRunningRowsRetainExactWorkIdentity() {
        assertEquals(
            VideoMergeResultEvent.ExportQueued(WorkId),
            work(VideoExportWorker.StateQueued).toVideoMergeResultEvent(),
        )
        assertEquals(
            VideoMergeResultEvent.ExportProgressChanged(WorkId, 0.42f),
            work(VideoExportWorker.StateRunning, progress = 0.42f).toVideoMergeResultEvent(),
        )
    }

    @Test
    fun terminalRowsRestoreResultAndWorkIdentity() {
        assertEquals(
            VideoMergeResultEvent.ExportFinished(WorkId, ExportResult.Success(OutputUri)),
            work(VideoExportWorker.StateSucceeded, outputUri = OutputUri).toVideoMergeResultEvent(),
        )
        assertEquals(
            VideoMergeResultEvent.ExportFinished(WorkId, ExportResult.Failure(FailureReason)),
            work(VideoExportWorker.StateFailed, errorMessage = FailureReason).toVideoMergeResultEvent(),
        )
        assertEquals(
            VideoMergeResultEvent.ExportCancelled(WorkId),
            work(VideoExportWorker.StateCancelled).toVideoMergeResultEvent(),
        )
    }

    @Test
    fun invalidPersistedTerminalDataFailsSafely() {
        assertEquals(
            VideoMergeResultEvent.ExportFinished(
                WorkId,
                ExportResult.Failure("Saved video is unavailable."),
            ),
            work(VideoExportWorker.StateSucceeded, outputUri = "").toVideoMergeResultEvent(),
        )
        assertNull(work("unknown").toVideoMergeResultEvent())
    }

    @Test
    fun activeWorkIdentityRejectsStaleWorkerEvents() {
        val activeState = VideoMergeState(exportWorkId = WorkId)

        assertTrue(activeState.acceptsExportWork(WorkId))
        assertFalse(activeState.acceptsExportWork(OtherWorkId))
        assertTrue(VideoMergeState().acceptsExportWork(OtherWorkId))
    }

    @Test
    fun `only schedulable or executing WorkManager states keep restored export active`() {
        assertTrue(WorkInfo.State.ENQUEUED.isLiveVideoExportState())
        assertTrue(WorkInfo.State.RUNNING.isLiveVideoExportState())
        assertTrue(WorkInfo.State.BLOCKED.isLiveVideoExportState())
        assertFalse(WorkInfo.State.SUCCEEDED.isLiveVideoExportState())
        assertFalse(WorkInfo.State.FAILED.isLiveVideoExportState())
        assertFalse(WorkInfo.State.CANCELLED.isLiveVideoExportState())
        assertFalse(null.isLiveVideoExportState())
    }

    @Test
    fun `orphan reconciliation preserves explicit cancellation and fails every other terminal gap`() {
        assertEquals(VideoExportWorker.StateCancelled, recoveredVideoExportState(WorkInfo.State.CANCELLED))
        assertEquals(VideoExportWorker.StateFailed, recoveredVideoExportState(WorkInfo.State.SUCCEEDED))
        assertEquals(VideoExportWorker.StateFailed, recoveredVideoExportState(WorkInfo.State.FAILED))
        assertEquals(VideoExportWorker.StateFailed, recoveredVideoExportState(null))
    }

    @Test
    fun `recovery never invalidates a row created during this process or enqueue grace`() {
        assertFalse(isEligibleForStartupVideoExportRecovery(updatedAtMillis = 1_000L, startupCutoffMillis = 1_000L))
        assertFalse(isEligibleForStartupVideoExportRecovery(updatedAtMillis = 1_001L, startupCutoffMillis = 1_000L))
        assertTrue(isEligibleForStartupVideoExportRecovery(updatedAtMillis = 999L, startupCutoffMillis = 1_000L))

        assertEquals(5_000L, videoExportRecoveryDelayMillis(1_000L, 1_000L, 5_000L))
        assertEquals(2_000L, videoExportRecoveryDelayMillis(1_000L, 4_000L, 5_000L))
        assertEquals(0L, videoExportRecoveryDelayMillis(1_000L, 6_000L, 5_000L))
    }

    private fun work(
        state: String,
        progress: Float = 0f,
        outputUri: String? = null,
        errorMessage: String? = null,
    ): VideoExportWorkEntity =
        VideoExportWorkEntity(
            projectId = ProjectId,
            workId = WorkId,
            state = state,
            progress = progress,
            outputUri = outputUri,
            errorMessage = errorMessage,
            updatedAtMillis = 1L,
        )

    private companion object {
        const val ProjectId = "66666666-6666-4666-8666-666666666666"
        const val WorkId = "77777777-7777-4777-8777-777777777777"
        const val OtherWorkId = "88888888-8888-4888-8888-888888888888"
        const val OutputUri = "content://media/video/7"
        const val FailureReason = "Codec unavailable"
    }
}
