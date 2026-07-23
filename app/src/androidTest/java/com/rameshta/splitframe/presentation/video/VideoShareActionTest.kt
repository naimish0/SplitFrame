package com.rameshta.splitframe.presentation.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.rameshta.splitframe.ads.ExternalUiLauncher
import com.rameshta.splitframe.ads.ExternalUiReason
import com.rameshta.splitframe.ads.LocalExternalUiLauncher
import com.rameshta.splitframe.data.DeleteVideoProjectResult
import com.rameshta.splitframe.domain.ExportResult
import com.rameshta.splitframe.domain.VideoMergeProject
import com.rameshta.splitframe.ui.theme.SplitFrameTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VideoShareActionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun successfulExportExposesShareAction() {
        composeRule.setContent {
            SplitFrameTheme {
                VideoEditorScreen(
                    state = VideoMergeState(
                        project = VideoMergeProject(id = "share-project"),
                        exportResult = ExportResult.Success(SavedUri),
                    ),
                    onIntent = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Share")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun emptyPreviewCardOpensTheVideoPicker() {
        var launchedReason: ExternalUiReason? = null
        composeRule.setContent {
            CompositionLocalProvider(
                LocalExternalUiLauncher provides ExternalUiLauncher { reason, _ ->
                    launchedReason = reason
                },
            ) {
                SplitFrameTheme {
                    VideoEditorScreen(
                        state = VideoMergeState(
                            project = VideoMergeProject(id = "empty-preview-project"),
                        ),
                        onIntent = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Add videos to preview")
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(ExternalUiReason.MediaPicker, launchedReason)
        }
    }

    @Test
    fun projectsLoadingStateNeverShowsTheEmptyMessage() {
        composeRule.setContent {
            SplitFrameTheme {
                VideoProjectsScreen(
                    projects = null,
                    onBack = {},
                    onNewProject = {},
                    onOpenProject = {},
                    onRenameProject = { _, _ -> false },
                    onDuplicateProject = { null },
                    onDeleteProject = { DeleteVideoProjectResult.NotFound },
                    onUndoDelete = { false },
                    onFinalizeDelete = { false },
                )
            }
        }

        composeRule.onNodeWithText("Loading video projects…").assertIsDisplayed()
        composeRule.onAllNodesWithText("No video projects yet").assertCountEquals(0)
    }

    @Test
    fun videoSendIntentGrantsOnlyTheExportUri() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent = context.createVideoSendIntent(SavedUri)

        requireNotNull(intent)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("video/mp4", intent.type)
        assertEquals(Uri.parse(SavedUri), intent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertEquals("SplitFrame video export", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals(Uri.parse(SavedUri), intent.clipData?.getItemAt(0)?.uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNull(context.createVideoSendIntent("not-a-uri"))
    }

    private companion object {
        const val SavedUri = "content://media/external/video/media/42"
    }
}
