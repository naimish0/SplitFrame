package com.rameshta.splitframe

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.rameshta.splitframe.data.VideoProjectStore
import com.rameshta.splitframe.ads.LastAppOpenAdShownAtPreferenceKey
import com.rameshta.splitframe.ads.SplitFrameAdPreferencesName
import com.rameshta.splitframe.data.local.VideoExportWorkDao
import com.rameshta.splitframe.data.local.VideoProjectDao
import com.rameshta.splitframe.data.local.SplitFrameDatabase
import com.rameshta.splitframe.data.local.RecentProjectDao
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.MediaSource
import com.rameshta.splitframe.domain.VideoMergeProject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class VideoProjectRecoveryTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var activity: MainActivity

    @Before
    fun seedExactProjectAndLaunchActivity() {
        runBlocking {
            removeExactProject()
            GlobalContext.get().get<VideoProjectStore>().save(
                VideoMergeProject(
                    id = ProjectId,
                    exportResolution = ExportResolution.UHD_2160,
                    mediaByCell = mapOf(
                        0 to MediaSource.Image(
                            id = "recovery-media",
                            uri = "content://media/image/recovery",
                            width = 1_920,
                            height = 1_080,
                        ),
                    ),
                ),
                initialName = RecoveryProjectName,
            )
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences(SplitFrameAdPreferencesName, android.content.Context.MODE_PRIVATE)
            .edit()
            .putLong(LastAppOpenAdShownAtPreferenceKey, System.currentTimeMillis())
            .commit()
        val launchIntent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        activity = instrumentation.startActivitySync(launchIntent) as MainActivity
        instrumentation.waitForIdleSync()
    }

    @After
    fun closeActivityAndRemoveExactProject() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            listOf(Stage.RESUMED, Stage.PAUSED, Stage.STARTED, Stage.CREATED, Stage.STOPPED)
                .flatMap(monitor::getActivitiesInStage)
                .filterIsInstance<MainActivity>()
                .distinct()
                .forEach(MainActivity::finish)
        }
        runBlocking {
            removeExactProject()
        }
    }

    @Test
    fun notificationRouteAndActivityRecreationRestoreExactProject() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity.startActivity(
                Intent(activity, MainActivity::class.java).apply {
                    action = VideoProjectLaunchContract.ActionOpenVideoProject
                    data = Uri.parse(VideoProjectLaunchContract.notificationData(ProjectId))
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(
                        VideoProjectLaunchContract.ExtraDestination,
                        VideoProjectLaunchContract.DestinationVideoEditor,
                    )
                    putExtra(VideoProjectLaunchContract.ExtraProjectId, ProjectId)
                },
            )
        }

        waitForVideoEditor()
        composeRule.onNodeWithText("Merge videos").assertIsDisplayed()
        composeRule.onNodeWithText("2160p (4K)").assertIsSelected()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity.recreate()
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) { activity.isDestroyed }

        waitForVideoEditor()
        composeRule.onNodeWithText("Merge videos").assertIsDisplayed()
        composeRule.onNodeWithText("2160p (4K)").assertIsSelected()
    }

    @Test
    fun recentBrowserAndEditorRoutesSurviveActivityRecreation() {
        waitForText("Merge Videos")
        composeRule.onNodeWithText("Merge Videos").performClick()
        waitForText("Video projects")
        composeRule.onNodeWithText("Video projects").assertIsDisplayed()

        InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.recreate() }
        composeRule.waitUntil(timeoutMillis = 10_000L) { activity.isDestroyed }
        waitForText("Video projects")
        composeRule.onNodeWithText("Video projects").assertIsDisplayed()
        activity = resumedActivity()

        composeRule.onNodeWithText(RecoveryProjectName).performClick()
        waitForVideoEditor()
        composeRule.onNodeWithText("2160p (4K)").assertIsSelected()

        InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.recreate() }
        composeRule.waitUntil(timeoutMillis = 10_000L) { activity.isDestroyed }
        waitForVideoEditor()
        composeRule.onNodeWithText("2160p (4K)").assertIsSelected()
    }

    @Test
    fun privacyRouteSurvivesActivityRecreation() {
        composeRule.onNodeWithContentDescription("Privacy policy").performClick()
        waitForText("Photos and videos")

        val activityBeforeRecreation = activity
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activityBeforeRecreation.recreate()
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) { activityBeforeRecreation.isDestroyed }

        waitForText("Photos and videos")
        composeRule.onNodeWithText("Photos and videos").assertIsDisplayed()
        activity = resumedActivity()
    }

    @Test
    fun abandoningUntouchedNewProjectDoesNotCreateARecentProject() {
        val recentProjectDao = GlobalContext.get().get<RecentProjectDao>()
        val idsBefore = runBlocking {
            recentProjectDao.observeActive().first().map { it.projectId }.toSet()
        }

        waitForText("Merge Videos")
        composeRule.onNodeWithText("Merge Videos").performClick()
        waitForText("Video projects")
        composeRule.onNodeWithContentDescription("New video project").performClick()
        waitForText("Merge videos")
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("Video projects")

        val idsAfter = runBlocking {
            recentProjectDao.observeActive().first().map { it.projectId }.toSet()
        }
        assertEquals(idsBefore, idsAfter)
    }

    private fun waitForVideoEditor() {
        waitForText("Merge videos")
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            runCatching {
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private suspend fun removeExactProject() {
        GlobalContext.get().get<VideoExportWorkDao>().delete(ProjectId)
        GlobalContext.get().get<VideoProjectDao>().delete(ProjectId)
        GlobalContext.get().get<SplitFrameDatabase>().openHelper.writableDatabase.execSQL(
            "DELETE FROM recent_projects WHERE projectId = ?",
            arrayOf(ProjectId),
        )
    }

    private fun resumedActivity(): MainActivity {
        var resumed: MainActivity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>()
                .firstOrNull()
        }
        return requireNotNull(resumed)
    }

    private companion object {
        const val ProjectId = "88888888-8888-4888-8888-888888888888"
        const val RecoveryProjectName = "Recovery project"
    }
}
