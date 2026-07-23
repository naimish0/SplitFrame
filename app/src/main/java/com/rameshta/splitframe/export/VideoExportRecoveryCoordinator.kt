package com.rameshta.splitframe.export

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.rameshta.splitframe.data.VideoProjectStore
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoExportRecoveryCoordinator(
    private val projectStore: VideoProjectStore,
    private val workManager: WorkManager,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val processStartupCutoffMillis = clock()

    suspend fun reconcileActiveRows() {
        projectStore.getActiveExportWork().forEach { stored ->
            if (!isEligibleForStartupVideoExportRecovery(stored.updatedAtMillis, processStartupCutoffMillis)) {
                return@forEach
            }
            val uuid = stored.workId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            val workInfo = if (uuid == null) {
                null
            } else {
                try {
                    withContext(Dispatchers.IO) { workManager.getWorkInfoById(uuid).get() }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    return@forEach
                }
            }
            if (workInfo?.state.isLiveVideoExportState()) return@forEach
            val recoveredState = recoveredVideoExportState(workInfo?.state)
            projectStore.updateExportWorkIfCurrent(
                work = stored.copy(
                    state = recoveredState,
                    progress = 0f,
                    outputUri = null,
                    errorMessage = if (recoveredState == VideoExportWorker.StateFailed) {
                        RecoveryFailure
                    } else {
                        null
                    },
                    updatedAtMillis = clock(),
                ),
                expectedStates = listOf(VideoExportWorker.StateQueued, VideoExportWorker.StateRunning),
            )
        }
    }

    private companion object {
        const val RecoveryFailure = "The previous video export could not be recovered. Export again."
    }
}

internal fun recoveredVideoExportState(workState: WorkInfo.State?): String =
    if (workState == WorkInfo.State.CANCELLED) {
        VideoExportWorker.StateCancelled
    } else {
        VideoExportWorker.StateFailed
    }

internal fun WorkInfo.State?.isLiveVideoExportState(): Boolean =
    this == WorkInfo.State.ENQUEUED || this == WorkInfo.State.RUNNING || this == WorkInfo.State.BLOCKED

internal fun isEligibleForStartupVideoExportRecovery(updatedAtMillis: Long, startupCutoffMillis: Long): Boolean =
    updatedAtMillis < startupCutoffMillis

internal fun videoExportRecoveryDelayMillis(
    updatedAtMillis: Long,
    nowMillis: Long,
    graceMillis: Long,
): Long {
    if (graceMillis <= 0L) return 0L
    if (updatedAtMillis >= nowMillis) return graceMillis
    return (graceMillis - (nowMillis - updatedAtMillis)).coerceAtLeast(0L)
}
