package com.rameshta.splitframe.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.splitframe.ui.theme.SplitFrameTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun languageCardOpensDedicatedScreen() {
        var openedLanguage = false
        composeRule.setContent {
            SplitFrameTheme {
                SettingsScreen(
                    onBack = {},
                    onOpenPrivacyPolicy = {},
                    onOpenLanguage = { openedLanguage = true },
                )
            }
        }

        composeRule.onNodeWithTag(SettingsLanguageCardTestTag)
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(true, openedLanguage)
        }
    }

    @Test
    fun languageScreenShowsAllLanguagesAndAppliesTheChosenLocale() {
        var selectedLanguage = ""
        composeRule.setContent {
            SplitFrameTheme {
                LanguageScreen(
                    onBack = {},
                    onLanguageSelected = { selectedLanguage = it },
                )
            }
        }

        val list = composeRule.onNodeWithTag(LanguageListTestTag)
        SupportedAppLanguage.entries.forEach { language ->
            val label = InstrumentationRegistry.getInstrumentation().targetContext
                .getString(language.labelRes)
            list.performScrollToNode(hasText(label))
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }

        list.performScrollToNode(hasText("Deutsch"))
        composeRule.onNodeWithText("Deutsch").performClick()
        composeRule.onNodeWithText("Deutsch").assertIsSelected()
        composeRule.runOnIdle {
            assertEquals("de", selectedLanguage)
        }
    }
}
