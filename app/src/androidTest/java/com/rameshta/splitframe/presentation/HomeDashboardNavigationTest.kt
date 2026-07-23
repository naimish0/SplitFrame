package com.rameshta.splitframe.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.splitframe.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeDashboardNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun resizeToolbarAndSystemBackBothReturnToHome() {
        val resizeAction = "Resize Image. Resize and export one photo."
        waitForContentDescription(resizeAction)
        composeRule.onNodeWithContentDescription(resizeAction).performClick()
        waitForText("Resize image")

        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("Create Collage")
        composeRule.onAllNodesWithText("Create Collage")[0].assertIsDisplayed()

        composeRule.onNodeWithContentDescription(resizeAction).performClick()
        waitForText("Resize image")
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForText("Create Collage")
        composeRule.onAllNodesWithText("Create Collage")[0].assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForContentDescription(description: String) {
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
