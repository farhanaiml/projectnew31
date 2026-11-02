package com.deepscan.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/** V2 UI smoke checks: tabs reflect actual navigation, settings opens and theme choices work. */
class AppLaunchTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun displaysAppNameAndV2Navigation() {
        composeRule.onNodeWithText("DeepScan").assertExists()
        composeRule.onNodeWithText("Home").assertExists()
        composeRule.onNodeWithText("Explore").assertExists()
        composeRule.onNodeWithText("Cleanup").assertExists()
        composeRule.onNodeWithText("Settings").assertExists()
    }

    @Test fun opensSettingsAndOffersThreeThemeChoices() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("APPEARANCE").assertExists()
        composeRule.onNodeWithText("Follow system").assertExists()
        composeRule.onNodeWithText("Light").assertExists()
        composeRule.onNodeWithText("Dark").assertExists()
        composeRule.onNodeWithText("Light").performClick()
        composeRule.onNodeWithText("Dark").performClick()
        composeRule.onNodeWithText("Follow system").performClick()
    }
}
