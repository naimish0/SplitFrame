package com.example.splitframe.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.splitframe.MainActivity
import com.example.splitframe.R
import com.example.splitframe.data.VideoProjectStore
import com.example.splitframe.data.local.VideoExportWorkEntity
import com.example.splitframe.domain.ExportResult
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
            val project = projectStore.get(projectId) ?: return@withContext Result.failure()

            setForeground(createForegroundInfo(progress = 0))
            updateWork(projectStore, projectId, StateRunning, progress = 0f)

            when (
                val result = exportRepository.export(project) { progress ->
                    runBlocking {
                        setForeground(createForegroundInfo((progress * 100f).roundToInt()))
                        setProgress(workDataOf(ProgressKey to progress))
                        updateWork(projectStore, projectId, StateRunning, progress)
                    }
                }
            ) {
                is ExportResult.Success -> {
                    updateWork(
                        projectStore = projectStore,
                        projectId = projectId,
                        state = StateSucceeded,
                        progress = 1f,
                        outputUri = result.savedUri,
                    )
                    Result.success(workDataOf(OutputUriKey to result.savedUri))
                }
                is ExportResult.Failure -> {
                    updateWork(
                        projectStore = projectStore,
                        projectId = projectId,
                        state = if (isStopped) StateCancelled else StateFailed,
                        progress = 0f,
                        errorMessage = result.reason,
                    )
                    if (isStopped) Result.failure() else Result.failure(workDataOf(ErrorKey to result.reason))
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
    ) {
        projectStore.setExportWork(
            VideoExportWorkEntity(
                projectId = projectId,
                workId = id.toString(),
                state = state,
                progress = progress.coerceIn(0f, 1f),
                outputUri = outputUri,
                errorMessage = errorMessage,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        ensureNotificationChannel()
        val openAppIntent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannelId)
            .setContentTitle(applicationContext.getString(R.string.video_export_notification_title))
            .setContentText(applicationContext.getString(R.string.video_export_notification_body))
            .setSmallIcon(R.drawable.ic_splash_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NotificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NotificationId, notification)
        }
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
        private const val NotificationId = 4203

        fun uniqueWorkName(projectId: String): String = "video_export_$projectId"
    }
}
