package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NavigationBackStackTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun pasteNavigatesToPreviewAndSystemBackReturnsToPaste() {
        val clipboard = composeTestRule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("sample", "# Hello World"))

        composeTestRule.onNodeWithTag("paste_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("paste_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("markdown_text_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("markdown_text_field").assertTextContains("# Hello World")

        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertFalse(composeTestRule.activity.isFinishing)
        composeTestRule.onNodeWithTag("paste_button").assertIsDisplayed()
    }

    @Test
    fun settingsFromPreviewPopsBackToPreviewPreservingMarkdown() {
        val clipboard = composeTestRule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("sample", "**Preserved Text**"))

        composeTestRule.onNodeWithTag("paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("markdown_text_field").assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_back_button").assertIsDisplayed()

        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("markdown_text_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("markdown_text_field").assertTextContains("**Preserved Text**")

        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("paste_button").assertIsDisplayed()
    }
}
