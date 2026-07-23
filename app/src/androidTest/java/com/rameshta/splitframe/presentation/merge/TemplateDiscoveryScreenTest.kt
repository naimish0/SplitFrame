package com.rameshta.splitframe.presentation.merge

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.rameshta.splitframe.R
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.ImageDimensions
import com.rameshta.splitframe.domain.ImageSource
import com.rameshta.splitframe.domain.LayoutTemplate
import com.rameshta.splitframe.domain.MergeProject
import com.rameshta.splitframe.domain.TemplateDiscovery
import com.rameshta.splitframe.domain.TemplateDiscoveryFilter
import com.rameshta.splitframe.domain.TemplateCategory
import com.rameshta.splitframe.domain.TemplateOrientation
import com.rameshta.splitframe.domain.TemplateRepository
import com.rameshta.splitframe.ui.theme.SplitFrameTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TemplateDiscoveryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchShowsHelpfulEmptyStateAndClearRestoresResults() {
        val templates = TemplateRepository().templates().take(6)
        setPicker(initialState = pickerState(templates))

        composeRule.onNode(hasSetTextAction()).performTextInput("not a real layout")
        grid().performScrollToKey("template-discovery-empty")
        composeRule.onNodeWithText("No layouts match this search and filter combination.")
            .assertIsDisplayed()

        grid().performScrollToKey("template-discovery-controls")
        composeRule.onNodeWithContentDescription("Clear layout search").performClick()
        grid().performScrollToKey(templates.first().id)
        composeRule.onNodeWithText("Side by side").assertIsDisplayed()
    }

    @Test
    fun searchCollectionAspectAndMediaCountComposeAndResetVisibly() {
        val templates = TemplateRepository().templates()
        val target = templates.first {
            it.metadata.category == TemplateCategory.Grid &&
                it.slotCount == 2 &&
                TemplateDiscovery.orientationOf(it) == TemplateOrientation.Portrait
        }
        val otherFavorite = templates.first { it.id != target.id }
        setPicker(
            initialState = pickerState(
                templates = templates,
                favoriteIds = listOf(target.id, otherFavorite.id),
            ),
        )

        composeRule.onNode(hasSetTextAction()).performTextInput("grid")
        composeRule.onNodeWithText("Favorites").performScrollTo().performClick()
        composeRule.onNodeWithText("Portrait 4:5").performScrollTo().performClick()
        composeRule.onAllNodes(hasText("2 photos") and hasClickAction())[0].performClick()

        grid().performScrollToKey(target.id)
        composeRule.onNodeWithText(target.name).assertIsDisplayed()

        grid().performScrollToKey("template-discovery-controls")
        composeRule.onNodeWithText("Reset search and filters").performClick()
        composeRule.onNodeWithText("All").assertIsSelected()
    }

    @Test
    fun recentCollectionUsesPersistedOrderAndHidesOtherLayouts() {
        val templates = TemplateRepository().templates().take(3)
        val recent = templates[1]
        setPicker(
            initialState = pickerState(
                templates = templates,
                recentIds = listOf(recent.id),
            ),
        )

        composeRule.onNodeWithText("Recently Used Layouts").performScrollTo().performClick()
        grid().performScrollToKey(recent.id)
        composeRule.onNodeWithText("Top and bottom").assertIsDisplayed()
        composeRule.onNodeWithText("Side by side").assertDoesNotExist()
    }

    @Test
    fun favoriteActionIsIndependentAndRemainsAvailableOnIncompatibleCard() {
        val templates = TemplateRepository().templates()
        val currentTemplate = templates.first { it.id == "triptych_vertical" }
        val template = templates.first { it.id == "side_by_side" }
        val intents = mutableListOf<MergeIntent>()
        var selections = 0
        setPicker(
            initialState = pickerState(
                templates = listOf(currentTemplate, template),
                assignedPhotoCount = 3,
            ),
            onIntentObserved = intents::add,
            onTemplateSelected = { selections += 1 },
        )

        grid().performScrollToKey(template.id)
        composeRule.onNodeWithContentDescription("Side by side layout requires 2 photos")
            .assertIsEnabled()
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("Add Side by side to favorites")
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(0, selections)
            assertTrue(intents.contains(MergeIntent.ToggleTemplateFavorite(template.id)))
        }
    }

    @Test
    fun everyLayoutCardRespondsWhenThreePhotosAreSelected() {
        val templates = TemplateRepository().templates()
        val currentTemplate = templates.first { it.id == "triptych_vertical" }
        val smallerTemplate = templates.first { it.id == "side_by_side" }
        val largerTemplate = templates.first { it.id == "grid_2x2" }
        val selections = mutableListOf<String>()
        setPicker(
            initialState = pickerState(
                templates = listOf(currentTemplate, smallerTemplate, largerTemplate),
                assignedPhotoCount = 3,
            ),
            onTemplateSelected = selections::add,
        )

        grid().performScrollToKey(smallerTemplate.id)
        composeRule.onNodeWithContentDescription("Side by side layout requires 2 photos")
            .assertIsEnabled()
            .performClick()
        grid().performScrollToKey(largerTemplate.id)
        composeRule.onNodeWithContentDescription("Use Grid 2 x 2 layout")
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(smallerTemplate.id, largerTemplate.id), selections)
        }
    }

    @Test
    fun rejectedSmallerLayoutShowsActionableMessage() {
        val template = TemplateRepository().templates().first()
        setPicker(
            initialState = pickerState(templates = listOf(template)).copy(
                error = R.string.layout_requires_photo_count,
            ),
        )

        composeRule.onNodeWithText(
            "This layout has fewer spaces than your selected photos. Remove extra photos first.",
        ).assertIsDisplayed()
    }

    @Test
    fun compatibleCardBodyOpensTheLayout() {
        val template = TemplateRepository().templates().first()
        var selectedTemplateId: String? = null
        setPicker(
            initialState = pickerState(templates = listOf(template)),
            onTemplateSelected = { selectedTemplateId = it },
        )

        grid().performScrollToKey(template.id)
        composeRule.onNodeWithContentDescription("Use Side by side layout")
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(template.id, selectedTemplateId)
        }
    }

    @Test
    fun favoriteTapOnCompatibleCardDoesNotOpenTheLayout() {
        val template = TemplateRepository().templates().first()
        val intents = mutableListOf<MergeIntent>()
        var selections = 0
        setPicker(
            initialState = pickerState(templates = listOf(template)),
            onIntentObserved = intents::add,
            onTemplateSelected = { selections += 1 },
        )

        grid().performScrollToKey(template.id)
        composeRule.onNodeWithContentDescription("Add Side by side to favorites")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(0, selections)
            assertTrue(intents.contains(MergeIntent.ToggleTemplateFavorite(template.id)))
        }
    }

    @Test
    fun disabledFavoriteOverlayDoesNotBlockCompatibleCardSelection() {
        val template = TemplateRepository().templates().first()
        var selections = 0
        setPicker(
            initialState = pickerState(
                templates = listOf(template),
                pendingFavoriteIds = setOf(template.id),
            ),
            onTemplateSelected = { selections += 1 },
        )

        grid().performScrollToKey(template.id)
        composeRule.onNodeWithContentDescription("Use Side by side layout")
            .performTouchInput {
                click(Offset(width - 28f, 28f))
            }

        composeRule.runOnIdle {
            assertEquals(1, selections)
        }
    }

    @Test
    fun smartRecommendationsExplainLocalRankingAndAllKeepsIncompatibleLayoutsReachable() {
        val templates = TemplateRepository().templates()
        setPicker(
            initialState = pickerState(
                templates = templates,
                assignedPhotoCount = 2,
                sourceDimensions = mapOf(
                    0 to ImageDimensions(600, 1_000),
                    1 to ImageDimensions(600, 1_000),
                ),
            ),
        )

        composeRule.onNodeWithText("Recommended").performScrollTo().performClick()
        composeRule.onNodeWithText(
            "Ranked on this device from the selected photo shapes, favorites, and recent layouts.",
        ).assertIsDisplayed()
        grid().performScrollToKey("side_by_side")
        composeRule.onNodeWithText("Why: Fits 2 photos", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Use Side by side layout. Recommendation details: " +
                "Fits 2 photos • Fits photo shapes • Matches orientations • Matches the canvas",
        ).assertIsDisplayed()

        grid().performScrollToKey("template-discovery-controls")
        composeRule.onNodeWithText("All").performScrollTo().performClick()
        grid().performScrollToKey("grid_2x2")
        composeRule.onNodeWithContentDescription("Use Grid 2 x 2 layout")
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    private fun setPicker(
        initialState: MergeState,
        onIntentObserved: (MergeIntent) -> Unit = {},
        onTemplateSelected: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            var state by remember { mutableStateOf(initialState) }
            SplitFrameTheme {
                TemplatePickerScreen(
                    state = state,
                    paletteSeed = 1,
                    gridState = rememberLazyGridState(),
                    onIntent = { intent ->
                        onIntentObserved(intent)
                        state = reduceDiscovery(state, intent)
                    },
                    onOpenSingleImageTool = {},
                    onTemplateSelected = onTemplateSelected,
                )
            }
        }
    }

    private fun grid() = composeRule.onNodeWithTag(TemplatePickerGridTestTag)

    private fun reduceDiscovery(state: MergeState, intent: MergeIntent): MergeState {
        val current = state.templateDiscovery
        val nextFilter = when (intent) {
            is MergeIntent.UpdateTemplateSearch -> current.filter.copy(query = intent.query)
            is MergeIntent.SelectTemplateCollection -> current.filter.copy(collection = intent.collection)
            is MergeIntent.SelectTemplateAspect -> current.filter.copy(aspect = intent.aspect)
            is MergeIntent.SelectTemplateMediaCount -> current.filter.copy(mediaCount = intent.mediaCount)
            MergeIntent.ResetTemplateDiscovery -> TemplateDiscoveryFilter()
            else -> current.filter
        }
        return state.copy(templateDiscovery = current.copy(filter = nextFilter))
    }

    private fun pickerState(
        templates: List<LayoutTemplate>,
        favoriteIds: List<String> = emptyList(),
        recentIds: List<String> = emptyList(),
        pendingFavoriteIds: Set<String> = emptySet(),
        assignedPhotoCount: Int = 0,
        sourceDimensions: Map<Int, ImageDimensions> = emptyMap(),
    ): MergeState {
        val template = templates.first()
        val assigned = (0 until assignedPhotoCount).associateWith { index ->
            ImageSource.LocalUri("content://photos/$index")
        }
        return MergeState(
            availableTemplates = templates,
            sourceDimensions = sourceDimensions,
            project = MergeProject(
                id = "template-discovery-test",
                template = template,
                assignedImages = assigned,
                spacingDp = template.defaultSpacingDp,
                cornerRadiusDp = template.defaultCornerRadiusDp,
                backgroundColor = 0xFFFFFFFFuL,
                borderColor = 0x00000000uL,
                borderWidthDp = 0f,
                exportResolution = ExportResolution.FHD_1080,
            ),
            templateDiscovery = TemplateDiscoveryState(
                favoriteTemplateIds = favoriteIds,
                recentTemplateIds = recentIds,
                pendingFavoriteIds = pendingFavoriteIds,
                isLoading = false,
            ),
        )
    }
}
