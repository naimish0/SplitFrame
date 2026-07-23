package com.rameshta.splitframe.export

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.rameshta.splitframe.MainActivity
import com.rameshta.splitframe.R
import com.rameshta.splitframe.VideoProjectLaunchContract
import com.rameshta.splitframe.ads.WorkflowCompletionEvents
import com.rameshta.splitframe.ads.WorkflowInterstitialTracker
import com.rameshta.splitframe.data.VideoProjectStore
import com.rameshta.splitframe.data.local.VideoExportWorkEntity
import com.rameshta.splitframe.domain.ExportResult
import com.rameshta.splitframe.domain.VideoClip
import com.rameshta.splitframe.domain.VideoMergeProject
import com.rameshta.splitframe.domain.VideoSupportStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import kotlin.math.roundToInt

class VideoExportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val projectId = inputData.getString(ProjectIdKey) ?: return@withContext Result.failure()
            val koin = GlobalContext.get()
            val projectStore: VideoProjectStore = koin.get()
            val exportRepository: VideoExportRepository = koin.get()
            val workId = id.toString()
            val storedWork = projectStore.getExportWork(projectId)
            if (storedWork?.workId != workId || storedWork.state !in ActiveStates) {
                return@withContext Result.failure()
            }
            val project = projectStore.get(projectId)
            if (project == null) {
                val reason = applicationContext.getString(R.string.video_export_worker_missing_project)
                val failed = updateWork(
                    projectStore = projectStore,
                    projectId = projectId,
                    state = StateFailed,
                    progress = 0f,
                    errorMessage = reason,
                    expectedStates = ActiveStates,
                )
                if (failed) {
                    cancelTerminalNotification(projectId)
                    showTerminalNotification(projectId = projectId, succeeded = false)
                }
                return@withContext Result.failure(workDataOf(ErrorKey to reason))
            }
            val metadataReader: VideoMetadataReader = koin.get()
            val preflightFailure = videoExportPreflightFailure(project, metadataReader::read)
            if (preflightFailure != null) {
                val failed = updateWork(
                    projectStore = projectStore,
                    projectId = projectId,
                    state = StateFailed,
                    progress = 0f,
                    errorMessage = preflightFailure,
                    expectedStates = ActiveStates,
                )
                if (failed) {
                    cancelTerminalNotification(projectId)
                    showTerminalNotification(projectId = projectId, succeeded = false)
                }
                return@withContext Result.failure(workDataOf(ErrorKey to preflightFailure))
            }

            try {
                setForeground(createForegroundInfo(projectId = projectId, progress = 0))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                val reason = throwable.message ?: "Could not start video export."
                val failed = updateWork(
                    projectStore = projectStore,
                    projectId = projectId,
                    state = StateFailed,
                    progress = 0f,
                    errorMessage = reason,
                    expectedStates = ActiveStates,
                )
                if (failed) {
                    cancelTerminalNotification(projectId)
                    showTerminalNotification(projectId = projectId, succeeded = false)
                }
                return@withContext Result.failure(workDataOf(ErrorKey to reason))
            }
            if (
                !updateWork(
                    projectStore = projectStore,
                    projectId = projectId,
                    state = StateRunning,
                    progress = 0f,
                    expectedStates = ActiveStates,
                )
            ) {
                return@withContext Result.failure()
            }
            cancelTerminalNotification(projectId)

            when (
                val result = exportRepository.export(
                    project = project,
                    onProgress = { progress ->
                        runBlocking {
                            val updated = updateWork(
                                projectStore = projectStore,
                                projectId = projectId,
                                state = StateRunning,
                                progress = progress,
                                expectedStates = RunningState,
                            )
                            if (!updated) return@runBlocking false
                            setForeground(
                                createForegroundInfo(
                                    projectId = projectId,
                                    progress = (progress * 100f).roundToInt(),
                                ),
                            )
                            setProgress(workDataOf(ProgressKey to progress))
                            true
                        }
                    },
                    onPublished = { outputUri ->
                        updateWork(
                            projectStore = projectStore,
                            projectId = projectId,
                            state = StateSucceeded,
                            progress = 1f,
                            outputUri = outputUri,
                            expectedStates = RunningState,
                        )
                    },
                )
            ) {
                is ExportResult.Success -> {
                    runCatching {
                        val tracker: WorkflowInterstitialTracker = koin.get()
                        WorkflowCompletionEvents.videoComposition(workId)?.let(tracker::record)
                    }
                    showTerminalNotification(projectId = projectId, succeeded = true)
                    Result.success(workDataOf(OutputUriKey to result.savedUri))
                }
                is ExportResult.Failure -> {
                    val updated = updateWork(
                        projectStore = projectStore,
                        projectId = projectId,
                        state = if (isStopped) StateCancelled else StateFailed,
                        progress = 0f,
                        errorMessage = result.reason,
                        expectedStates = RunningState,
                    )
                    if (updated && !isStopped) {
                        showTerminalNotification(projectId = projectId, succeeded = false)
                    }
                    if (!updated || isStopped) {
                        Result.failure()
                    } else {
                        Result.failure(workDataOf(ErrorKey to result.reason))
                    }
                }
            }
        }

    private suspend fun updateWork(
        projectStore: VideoProjectStore,
        projectId: String,
        state: String,
        progress: Float,
        outputUri: String? = null,
        errorMessage: String? = null,
        expectedStates: List<String>,
    ): Boolean =
        projectStore.updateExportWorkIfCurrent(
            VideoExportWorkEntity(
                projectId = projectId,
                workId = id.toString(),
                state = state,
                progress = progress.coerceIn(0f, 1f),
                outputUri = outputUri,
                errorMessage = errorMessage,
                updatedAtMillis = System.currentTimeMillis(),
            ),
            expectedStates = expectedStates,
        )

    private fun createForegroundInfo(projectId: String, progress: Int): ForegroundInfo {
        ensureNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannelId)
            .setContentTitle(applicationContext.getString(R.string.video_export_notification_title))
            .setContentText(applicationContext.getString(R.string.video_export_notification_body))
            .setSmallIcon(R.drawable.ic_splash_logo)
            .setContentIntent(openProjectPendingIntent(projectId))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress == 0)
            .build()
        return when (val serviceType = videoExportForegroundServiceType(Build.VERSION.SDK_INT)) {
            null -> ForegroundInfo(VideoProjectLaunchContract.progressNotificationId(id.toString()), notification)
            else -> ForegroundInfo(
                VideoProjectLaunchContract.progressNotificationId(id.toString()),
                notification,
                serviceType,
            )
        }
    }

    private fun showTerminalNotification(projectId: String, succeeded: Boolean) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            ensureNotificationChannel()
            val notification = NotificationCompat.Builder(applicationContext, NotificationChannelId)
                .setContentTitle(
                    applicationContext.getString(
                        if (succeeded) {
                            R.string.video_export_notification_complete_title
                        } else {
                            R.string.video_export_notification_failed_title
                        },
                    ),
                )
                .setContentText(
                    applicationContext.getString(
                        if (succeeded) {
                            R.string.video_export_notification_complete_body
                        } else {
                            R.string.video_export_notification_failed_body
                        },
                    ),
                )
                .setSmallIcon(R.drawable.ic_splash_logo)
                .setContentIntent(openProjectPendingIntent(projectId))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.notify(VideoProjectLaunchContract.completionNotificationId(projectId), notification)
        }
    }

    private fun cancelTerminalNotification(projectId: String) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        runCatching {
            manager.cancel(VideoProjectLaunchContract.completionNotificationId(projectId))
        }
    }

    private fun openProjectPendingIntent(projectId: String): PendingIntent {
        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            action = VideoProjectLaunchContract.ActionOpenVideoProject
            data = Uri.parse(VideoProjectLaunchContract.notificationData(projectId))
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(VideoProjectLaunchContract.ExtraDestination, VideoProjectLaunchContract.DestinationVideoEditor)
            putExtra(VideoProjectLaunchContract.ExtraProjectId, projectId)
        }
        return PendingIntent.getActivity(
            applicationContext,
            VideoProjectLaunchContract.pendingIntentRequestCode(projectId),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NotificationChannelId,
            applicationContext.getString(R.string.video_export_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ProjectIdKey = "project_id"
        const val OutputUriKey = "output_uri"
        const val ErrorKey = "error"
        const val ProgressKey = "progress"
        const val StateQueued = "queued"
        const val StateRunning = "running"
        const val StateSucceeded = "succeeded"
        const val StateFailed = "failed"
        const val StateCancelled = "cancelled"

        private const val NotificationChannelId = "splitframe_video_export"
        private val ActiveStates = listOf(StateQueued, StateRunning)
        private val RunningState = listOf(StateRunning)
        fun uniqueWorkName(projectId: String): String = "video_export_$projectId"
    }
}

@SuppressLint("InlinedApi")
internal fun videoExportForegroundServiceType(sdkInt: Int): Int? =
    when {
        sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        sdkInt >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        else -> null
    }

internal fun videoExportPreflightFailure(
    project: VideoMergeProject,
    readMetadata: (String) -> VideoMetadataResult,
): String? = project.orderedClips
    .distinctBy(VideoClip::uri)
    .firstNotNullOfOrNull { expected ->
        when (val current = readMetadata(expected.uri)) {
            is VideoMetadataResult.Valid -> if (
                current.clip.durationMs < expected.trimEndMs ||
                current.clip.width <= 0 ||
                current.clip.height <= 0
            ) {
                "A selected video changed since it was added. Choose it again."
            } else {
                null
            }
            is VideoMetadataResult.Unsupported -> when (current.status) {
                VideoSupportStatus.Unreadable ->
                    "A selected video is missing or access was revoked. Choose it again."
                VideoSupportStatus.UnsupportedMimeType,
                VideoSupportStatus.UnsupportedDrmOrCodec,
                VideoSupportStatus.MissingVideoTrack,
                -> "A selected video cannot be decoded on this device. Choose a different clip."
                VideoSupportStatus.InvalidDuration,
                VideoSupportStatus.Supported,
                -> "A selected video changed since it was added. Choose it again."
            }
        }
    }
