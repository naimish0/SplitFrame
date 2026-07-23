package com.rameshta.splitframe.presentation

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.dp
import com.rameshta.splitframe.data.RecentVideoProject
import com.rameshta.splitframe.data.RecentVideoProjectStatus
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.TemplateRepository
import com.rameshta.splitframe.presentation.home.HomeUiState
import com.rameshta.splitframe.presentation.home.RecentPhotoExport
import com.rameshta.splitframe.ui.theme.SplitFrameTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeDashboardScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun primaryActionsAreVisibleAccessibleAndDispatchOnce() {
        val opened = mutableListOf<String>()
        setHome(
            onOpenPhotoCollage = { opened += "collage" },
            onOpenResizeImage = { opened += "resize" },
            onOpenVideoProjects = { opened += "video" },
        )

        primaryAction("Create Collage. Choose a layout and combine your photos.")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        primaryAction("Resize Image. Resize and export one photo.")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        primaryAction("Merge Videos. Join clips in sequence or resume a saved merge.")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("collage", "resize", "video"), opened)
        }
    }

    @Test
    fun loadingKeepsPrimaryActionsAvailableAndHasAProgressLabel() {
        setHome(state = HomeUiState.Loading)

        composeRule.onNodeWithText("Create Collage").assertIsDisplayed()
        homeList().performScrollToKey("home-loading")
        composeRule.onNodeWithContentDescription("Loading saved work").assertIsDisplayed()
    }

    @Test
    fun emptyStateDoesNotRenderEmptyConditionalSections() {
        setHome(state = HomeUiState.Ready())

        homeList().performScrollToKey("home-empty")
        composeRule.onNodeWithText("Your saved work will appear here").assertIsDisplayed()
        composeRule.onNodeWithText("Continue Editing").assertDoesNotExist()
        composeRule.onNodeWithText("Recent Projects").assertDoesNotExist()
        composeRule.onNodeWithText("Favorite Layouts").assertDoesNotExist()
        composeRule.onNodeWithText("Recently Used Layouts").assertDoesNotExist()
        composeRule.onNodeWithText("Recent Photo Exports").assertDoesNotExist()
    }

    @Test
    fun continueEditingOpensTheExactProject() {
        var openedProjectId: String? = null
        val project = project(id = "continue-id", name = "Summer clips")
        setHome(
            state = HomeUiState.Ready(continueProject = project),
            onOpenVideoProject = { openedProjectId = it },
        )

        homeList().performScrollToKey("home-continue-editing")
        composeRule.onNodeWithContentDescription("Continue editing Summer clips")
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(project.id, openedProjectId)
        }
    }

    @Test
    fun recentProjectsFavoritesAndRecentLayoutsRenderOnlyWithData() {
        val templates = TemplateRepository().templates()
        val favorite = templates[0]
        val recent = templates[1]
        val openedProjects = mutableListOf<String>()
        val openedLayouts = mutableListOf<String>()
        val openedExports = mutableListOf<String>()
        setHome(
            state = HomeUiState.Ready(
                continueProject = project("continue", "Current merge"),
                recentProjects = listOf(project("recent", "Earlier merge")),
                recentPhotoExports = listOf(
                    RecentPhotoExport(
                        id = "photo-1",
                        savedUri = "content://media/photo-1",
                        template = favorite,
                        resolution = ExportResolution.FHD_1080,
                        createdAtMillis = 2L,
                    ),
                ),
                favoriteLayouts = listOf(favorite),
                recentlyUsedLayouts = listOf(recent),
            ),
            onOpenVideoProject = openedProjects::add,
            onOpenLayout = openedLayouts::add,
            onOpenRecentPhotoExport = openedExports::add,
        )

        homeList().performScrollToKey("home-recent-projects")
        composeRule.onNodeWithText("Recent Projects").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open recent project Earlier merge")
            .assertHasClickAction()
            .performClick()
        homeList().performScrollToKey("home-recent-photo-exports")
        composeRule.onNodeWithText("Recent Photo Exports").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open recent Side by side export at 1080p")
            .assertHasClickAction()
            .performClick()
        homeList().performScrollToKey("home-favorite-layouts")
        composeRule.onNodeWithText("Favorite Layouts").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Use Side by side layout")
            .assertHasClickAction()
            .performClick()
        homeList().performScrollToKey("home-recent-layouts")
        composeRule.onNodeWithText("Recently Used Layouts").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Use Top and bottom layout")
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("recent"), openedProjects)
            assertEquals(listOf(favorite.id, recent.id), openedLayouts)
            assertEquals(listOf("content://media/photo-1"), openedExports)
        }
    }

    private fun setHome(
        state: HomeUiState = HomeUiState.Ready(),
        onOpenPhotoCollage: () -> Unit = {},
        onOpenResizeImage: () -> Unit = {},
        onOpenVideoProjects: () -> Unit = {},
        onOpenVideoProject: (String) -> Unit = {},
        onOpenLayout: (String) -> Unit = {},
        onOpenRecentPhotoExport: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            SplitFrameTheme {
                ModeSelectionScreen(
                    state = state,
                    onOpenPhotoCollage = onOpenPhotoCollage,
                    onOpenResizeImage = onOpenResizeImage,
                    onOpenVideoProjects = onOpenVideoProjects,
                    onOpenVideoProject = onOpenVideoProject,
                    onOpenLayout = onOpenLayout,
                    onOpenRecentPhotoExport = onOpenRecentPhotoExport,
                    onOpenPrivacyPolicy = {},
                )
            }
        }
    }

    private fun primaryAction(label: String) =
        composeRule.onNodeWithContentDescription(label)

    private fun homeList() = composeRule.onNodeWithTag(HomeDashboardListTestTag)

    private fun project(id: String, name: String) = RecentVideoProject(
        id = id,
        name = name,
        thumbnailUri = null,
        updatedAtMillis = 1L,
        mediaCount = 2,
        status = RecentVideoProjectStatus.Ready,
    )
}
