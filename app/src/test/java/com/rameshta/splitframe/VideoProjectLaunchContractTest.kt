package com.rameshta.splitframe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoProjectLaunchContractTest {
    @Test
    fun exactNotificationContractReturnsCanonicalProjectId() {
        val projectId = FirstProjectId

        assertEquals(
            projectId,
            VideoProjectLaunchContract.projectIdFromLaunch(
                action = VideoProjectLaunchContract.ActionOpenVideoProject,
                destination = VideoProjectLaunchContract.DestinationVideoEditor,
                rawProjectId = projectId,
                dataString = VideoProjectLaunchContract.notificationData(projectId),
            ),
        )
    }

    @Test
    fun malformedOrMismatchedLaunchDataIsRejected() {
        val invalidInputs = listOf(
            null,
            "",
            " $FirstProjectId",
            FirstProjectId.uppercase(),
            "not-a-project-id",
            "1-1-1-1-1",
        )

        invalidInputs.forEach { projectId ->
            assertNull(
                VideoProjectLaunchContract.projectIdFromLaunch(
                    action = VideoProjectLaunchContract.ActionOpenVideoProject,
                    destination = VideoProjectLaunchContract.DestinationVideoEditor,
                    rawProjectId = projectId,
                    dataString = projectId?.let(VideoProjectLaunchContract::notificationData),
                ),
            )
        }
        assertNull(
            VideoProjectLaunchContract.projectIdFromLaunch(
                action = "wrong-action",
                destination = VideoProjectLaunchContract.DestinationVideoEditor,
                rawProjectId = FirstProjectId,
                dataString = VideoProjectLaunchContract.notificationData(FirstProjectId),
            ),
        )
        assertNull(
            VideoProjectLaunchContract.projectIdFromLaunch(
                action = VideoProjectLaunchContract.ActionOpenVideoProject,
                destination = "wrong-destination",
                rawProjectId = FirstProjectId,
                dataString = VideoProjectLaunchContract.notificationData(FirstProjectId),
            ),
        )
        assertNull(
            VideoProjectLaunchContract.projectIdFromLaunch(
                action = VideoProjectLaunchContract.ActionOpenVideoProject,
                destination = VideoProjectLaunchContract.DestinationVideoEditor,
                rawProjectId = FirstProjectId,
                dataString = VideoProjectLaunchContract.notificationData(SecondProjectId),
            ),
        )
    }

    @Test
    fun notificationIdentityIsStableAndProjectSpecific() {
        assertEquals(
            VideoProjectLaunchContract.progressNotificationId(FirstWorkId),
            VideoProjectLaunchContract.progressNotificationId(FirstWorkId),
        )
        assertNotEquals(
            VideoProjectLaunchContract.progressNotificationId(FirstWorkId),
            VideoProjectLaunchContract.progressNotificationId(SecondWorkId),
        )
        assertNotEquals(
            VideoProjectLaunchContract.completionNotificationId(FirstProjectId),
            VideoProjectLaunchContract.completionNotificationId(SecondProjectId),
        )
        assertNotEquals(
            VideoProjectLaunchContract.progressNotificationId(FirstWorkId),
            VideoProjectLaunchContract.completionNotificationId(FirstProjectId),
        )
        assertNotEquals(
            VideoProjectLaunchContract.notificationData(FirstProjectId),
            VideoProjectLaunchContract.notificationData(SecondProjectId),
        )
    }

    private companion object {
        const val FirstProjectId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        const val SecondProjectId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        const val FirstWorkId = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
        const val SecondWorkId = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
    }
}
