package com.rameshta.splitframe.presentation.merge

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.rameshta.splitframe.R
import com.rameshta.splitframe.domain.ExportResult
import com.rameshta.splitframe.domain.ImageSource
import com.rameshta.splitframe.domain.LayoutTemplate
import com.rameshta.splitframe.domain.MergeProject
import com.rameshta.splitframe.domain.TemplateIds
import com.rameshta.splitframe.domain.TemplateRepository
import com.rameshta.splitframe.ui.theme.SplitFrameTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExportSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exportSheetScrollsDownAndBackUp() {
        composeRule.setContent {
            SplitFrameTheme {
                ExportSheet(
                    state = testMergeState(),
                    onIntent = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithText("2160p (4K)", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Export photo")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun saveClickDispatchesSingleExportIntent() {
        val intents = mutableListOf<MergeIntent>()
        composeRule.setContent {
            SplitFrameTheme {
                ExportSheet(
                    state = testMergeState(),
                    onIntent = { intents += it },
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithText("Save")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, intents.count { it == MergeIntent.Export })
        }
    }

    @Test
    fun errorMessageUsesDialogLocalSnackbar() {
        composeRule.setContent {
            SplitFrameTheme {
                ExportSheet(
                    state = testMergeState(error = R.string.missing_images),
                    onIntent = {},
                    onClose = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Add photos to every cell before exporting.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Add photos to every cell before exporting.").assertIsDisplayed()
    }

    @Test
    fun resolutionChoicesAvoidByteEstimatesAndSuccessShowsMeasuredSize() {
        composeRule.setContent {
            SplitFrameTheme {
                ExportSheet(
                    state = testMergeState().copy(
                        exportResult = ExportResult.Success(
                            savedUri = "content://media/lossless-output",
                            sizeBytes = 2L * 1024L * 1024L,
                        ),
                    ),
                    onIntent = {},
                    onClose = {},
                )
            }
        }

        composeRule.onAllNodesWithText("~", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("Saved to gallery · 2.0 MB")
            .performScrollTo()
            .assertIsDisplayed()
    }
}

private fun testMergeState(error: Int? = null): MergeState {
    val templates = TemplateRepository().templates()
    val template = templates.first { it.id == TemplateIds.SIDE_BY_SIDE }
    return MergeState(
        availableTemplates = templates,
        project = testProject(template),
        error = error,
    )
}

private fun testProject(template: LayoutTemplate): MergeProject =
    MergeProject(
        id = "test-project",
        template = template,
        assignedImages = template.cells.associate { cell ->
            cell.index to ImageSource.LocalUri("content://splitframe-test/${cell.index}")
        },
        spacingDp = template.defaultSpacingDp,
        cornerRadiusDp = template.defaultCornerRadiusDp,
        backgroundColor = 0xFFFFFFFFuL,
        borderColor = 0x00000000uL,
        borderWidthDp = 0f,
    )
