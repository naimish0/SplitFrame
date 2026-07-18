package com.rameshta.splitframe.presentation.single

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.ImageDimensions
import com.rameshta.splitframe.domain.ImageSource
import com.rameshta.splitframe.domain.MergeProject
import com.rameshta.splitframe.domain.SingleImagePlanResult
import com.rameshta.splitframe.domain.SingleImageResizePlan
import com.rameshta.splitframe.domain.TemplateFilter
import com.rameshta.splitframe.domain.TemplateIds
import com.rameshta.splitframe.domain.TemplateRepository
import com.rameshta.splitframe.presentation.merge.MergeState
import com.rameshta.splitframe.presentation.merge.TemplatePickerScreen
import com.rameshta.splitframe.ui.theme.SplitFrameTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SingleImageProcessingReachabilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun templatePickerExposesSingleImageProcessingAction() {
        var opened = false
        composeRule.setContent {
            SplitFrameTheme {
                TemplatePickerScreen(
                    state = pickerState(),
                    paletteSeed = 1,
                    gridState = LazyGridState(),
                    selectedFilter = TemplateFilter.ALL,
                    onFilterSelected = {},
                    onOpenSingleImageTool = { opened = true },
                    onTemplateSelected = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Resize one image")
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(opened)
        }
    }

    @Test
    fun singleImageScreenShowsExistingProcessingActions() {
        composeRule.setContent {
            SplitFrameTheme {
                SingleImageScreen(
                    state = SingleImageState(
                        source = ImageSource.LocalUri("file:///tmp/splitframe-test.png"),
                        planResult = SingleImagePlanResult.Valid(
                            SingleImageResizePlan(
                                originalDimensions = ImageDimensions(1200, 800),
                                outputDimensions = ImageDimensions(2400, 1600),
                                estimatedBytes = 3_000_000L,
                                isUpscale = true,
                            ),
                        ),
                    ),
                    onIntent = {},
                    onBack = {},
                    onUseInCollage = {},
                )
            }
        }

        composeRule.onNodeWithText("Select photo").assertIsDisplayed()
        composeRule.onNodeWithText("Save").performScrollTo().assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Quality mode").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Resize preset").assertIsDisplayed()
        composeRule.onNodeWithText("Output format").assertIsDisplayed()
    }

    private fun pickerState(): MergeState {
        val templates = TemplateRepository().templates()
        val template = templates.first { it.id == TemplateIds.SIDE_BY_SIDE }
        return MergeState(
            availableTemplates = templates,
            project = MergeProject(
                id = "single-image-reachability",
                template = template,
                assignedImages = emptyMap(),
                spacingDp = template.defaultSpacingDp,
                cornerRadiusDp = template.defaultCornerRadiusDp,
                backgroundColor = 0xFFFFFFFFuL,
                borderColor = 0x00000000uL,
                borderWidthDp = 0f,
                exportResolution = ExportResolution.FHD_1080,
            ),
        )
    }
}
