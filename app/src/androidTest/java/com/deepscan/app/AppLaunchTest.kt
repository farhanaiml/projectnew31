package com.deepscan.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/** Real emulator smoke test: verifies the native Compose activity renders. */
class AppLaunchTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun displaysAppNameAndNavigation() {
        composeRule.onNodeWithText("DeepScan").assertExists()
        composeRule.onNodeWithText("Overview").assertExists()
        composeRule.onNodeWithText("Explore").assertExists()
        composeRule.onNodeWithText("Duplicates").assertExists()
    }
}
