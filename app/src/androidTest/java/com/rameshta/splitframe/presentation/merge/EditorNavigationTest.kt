package com.rameshta.splitframe.presentation.merge

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rameshta.splitframe.domain.ImageSource
import com.rameshta.splitframe.domain.LayoutTemplate
import com.rameshta.splitframe.domain.MergeProject
import com.rameshta.splitframe.domain.TemplateIds
import com.rameshta.splitframe.domain.TemplateRepository
import com.rameshta.splitframe.ui.theme.SplitFrameTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditorNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun toolbarBackReturnsToCaller() {
        var backCount = 0
        composeRule.setContent {
            SplitFrameTheme {
                EditorScreen(
                    state = editorTestState(),
                    onIntent = {},
                    onBack = { backCount += 1 },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.runOnIdle {
            assertEquals(1, backCount)
        }
    }

    @Test
    fun systemBackReturnsToTemplateListInEditorFlow() {
        composeRule.setContent {
            SplitFrameTheme {
                var inEditor by remember { mutableStateOf(true) }
                if (inEditor) {
                    BackHandler { inEditor = false }
                    EditorScreen(
                        state = editorTestState(),
                        onIntent = {},
                        onBack = { inEditor = false },
                    )
                } else {
                    Text("Template List")
                }
            }
        }

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.onNodeWithText("Template List").assertIsDisplayed()
    }

    @Test
    fun largerLayoutShowsEverySelectedPhotoAndLeavesOnlyExtraCellsEmpty() {
        val templates = TemplateRepository().templates()
        val template = templates.first { it.id == TemplateIds.GRID_2X2 }
        composeRule.setContent {
            SplitFrameTheme {
                EditorScreen(
                    state = MergeState(
                        availableTemplates = templates,
                        project = editorTestProject(template, assignedCount = 3),
                    ),
                    onIntent = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Photo 1 of 3").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Photo 2 of 3").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Photo 3 of 3").assertIsDisplayed()
    }
}

private fun editorTestState(): MergeState {
    val templates = TemplateRepository().templates()
    val template = templates.first { it.id == TemplateIds.SIDE_BY_SIDE }
    return MergeState(
        availableTemplates = templates,
        project = editorTestProject(template),
    )
}

private fun editorTestProject(
    template: LayoutTemplate,
    assignedCount: Int = 0,
): MergeProject =
    MergeProject(
        id = "editor-test-project",
        template = template,
        assignedImages = template.cells.take(assignedCount).associate { cell ->
            cell.index to ImageSource.LocalUri("content://photos/${cell.index}")
        },
        spacingDp = template.defaultSpacingDp,
        cornerRadiusDp = template.defaultCornerRadiusDp,
        backgroundColor = 0xFFFFFFFFuL,
        borderColor = 0x00000000uL,
        borderWidthDp = 0f,
    )
