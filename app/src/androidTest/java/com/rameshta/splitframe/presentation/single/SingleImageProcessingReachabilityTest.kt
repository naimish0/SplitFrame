package com.rameshta.splitframe.presentation.single

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.ExportContentMode
import com.rameshta.splitframe.domain.ImageDimensions
import com.rameshta.splitframe.domain.ImageSource
import com.rameshta.splitframe.domain.MergeProject
import com.rameshta.splitframe.domain.SingleImagePlanResult
import com.rameshta.splitframe.domain.SingleImageCanvasMath
import com.rameshta.splitframe.domain.SingleImageResizePlan
import com.rameshta.splitframe.domain.SingleImageResizePreset
import com.rameshta.splitframe.domain.SingleImageResizeRequest
import com.rameshta.splitframe.domain.TemplateIds
import com.rameshta.splitframe.domain.TemplateRepository
import com.rameshta.splitframe.presentation.merge.MergeState
import com.rameshta.splitframe.presentation.merge.TemplatePickerScreen
import com.rameshta.splitframe.ui.theme.SplitFrameTheme
import org.junit.Assert.assertEquals
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
                    onIntent = {},
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
        composeRule.onNodeWithText("Export preset").assertIsDisplayed()
        composeRule.onNodeWithText("Instagram Square Post — 1080 × 1080")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Output format").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun socialPresetAndExplicitFillEmitFocusedIntents() {
        val intents = mutableListOf<SingleImageIntent>()
        composeRule.setContent {
            SplitFrameTheme {
                SingleImageScreen(
                    state = singleImageState(
                        request = SingleImageResizeRequest(
                            preset = SingleImageResizePreset.InstagramSquarePost,
                            contentMode = ExportContentMode.Fit,
                        ),
                        outputDimensions = ImageDimensions(1080, 1080),
                    ),
                    onIntent = intents::add,
                    onBack = {},
                    onUseInCollage = {},
                )
            }
        }

        composeRule.onNodeWithText("YouTube Thumbnail — 1280 × 720")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Fill — crop to the canvas")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    SingleImageIntent.SelectPreset(SingleImageResizePreset.YouTubeThumbnail),
                    SingleImageIntent.SelectContentMode(ExportContentMode.Fill),
                ),
                intents,
            )
        }
    }

    @Test
    fun previewCanvasUsesTheResolvedExportAspectRatio() {
        composeRule.setContent {
            SplitFrameTheme {
                SingleImageScreen(
                    state = singleImageState(
                        request = SingleImageResizeRequest(
                            preset = SingleImageResizePreset.InstagramStoryReel,
                            contentMode = ExportContentMode.Fit,
                        ),
                        outputDimensions = ImageDimensions(1080, 1920),
                    ),
                    onIntent = {},
                    onBack = {},
                    onUseInCollage = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "Export preview, 1080 by 1920 pixels, Fit — show the whole image",
        ).assertIsDisplayed()
        val bounds = composeRule.onNodeWithTag(SingleImagePreviewCanvasTag)
            .fetchSemanticsNode()
            .boundsInRoot
        assertEquals(1080f / 1920f, bounds.width / bounds.height, 0.02f)
    }

    @Test
    fun processingDisablesCustomInputsAndLabelsControlsForAccessibility() {
        composeRule.setContent {
            SplitFrameTheme {
                SingleImageScreen(
                    state = singleImageState(
                        request = SingleImageResizeRequest(
                            preset = SingleImageResizePreset.Custom,
                            customWidthPx = 1080,
                            customHeightPx = 1920,
                            lockAspectRatio = false,
                            contentMode = ExportContentMode.Fill,
                        ),
                        outputDimensions = ImageDimensions(1080, 1920),
                    ).copy(isProcessing = true),
                    onIntent = {},
                    onBack = {},
                    onUseInCollage = {},
                )
            }
        }

        composeRule.onNode(hasText("Width px") and hasSetTextAction())
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNode(hasText("Height px") and hasSetTextAction())
            .assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Lock aspect ratio")
            .assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Encoding quality 94")
            .assertIsNotEnabled()
        composeRule.onAllNodesWithText(
            "Fill crops image edges to match the selected canvas. The preview shows the crop before export.",
        ).assertCountEquals(1)
    }

    private fun singleImageState(
        request: SingleImageResizeRequest,
        outputDimensions: ImageDimensions,
    ): SingleImageState =
        SingleImageState(
            source = ImageSource.LocalUri("file:///tmp/splitframe-test.png"),
            request = request,
            planResult = SingleImagePlanResult.Valid(
                SingleImageResizePlan(
                    originalDimensions = ImageDimensions(1200, 800),
                    outputDimensions = outputDimensions,
                    estimatedBytes = 3_000_000L,
                    isUpscale = false,
                    canvasGeometry = SingleImageCanvasMath.geometry(
                        source = ImageDimensions(1200, 800),
                        canvas = outputDimensions,
                        contentMode = request.contentMode,
                    ),
                ),
            ),
        )

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
