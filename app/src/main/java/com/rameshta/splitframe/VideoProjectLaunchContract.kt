package com.rameshta.splitframe

import java.util.UUID

data class VideoProjectLaunchRequest(
    val projectId: String,
    val requestId: Long,
)

internal object VideoProjectLaunchContract {
    const val ActionOpenVideoProject = "com.rameshta.splitframe.action.OPEN_VIDEO_PROJECT"
    const val ExtraDestination = "com.rameshta.splitframe.extra.DESTINATION"
    const val ExtraProjectId = "com.rameshta.splitframe.extra.VIDEO_PROJECT_ID"
    const val DestinationVideoEditor = "video_editor"

    fun projectIdFromLaunch(
        action: String?,
        destination: String?,
        rawProjectId: String?,
        dataString: String?,
    ): String? {
        if (action != ActionOpenVideoProject || destination != DestinationVideoEditor) return null
        val projectId = canonicalProjectIdOrNull(rawProjectId) ?: return null
        return projectId.takeIf { dataString == notificationData(it) }
    }

    fun canonicalProjectIdOrNull(rawProjectId: String?): String? {
        val raw = rawProjectId ?: return null
        if (raw.isBlank() || raw != raw.trim()) return null
        val parsed = runCatching { UUID.fromString(raw) }.getOrNull() ?: return null
        return parsed.toString().takeIf { it == raw }
    }

    fun notificationData(projectId: String): String = "splitframe://video-project/$projectId"

    fun progressNotificationId(workId: String): Int =
        ProgressNotificationBase or (workId.hashCode() and NotificationHashMask)

    fun completionNotificationId(projectId: String): Int =
        CompletionNotificationBase or (projectId.hashCode() and NotificationHashMask)

    fun pendingIntentRequestCode(projectId: String): Int = projectId.hashCode()

    private const val NotificationHashMask = 0x0FFF_FFFF
    private const val ProgressNotificationBase = 0x1000_0000
    private const val CompletionNotificationBase = 0x2000_0000
}
