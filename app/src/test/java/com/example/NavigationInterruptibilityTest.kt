package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
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
class NavigationInterruptibilityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun interruptingSettingsForwardTransitionWithBackReturnsCleanlyToPaste() {
        composeTestRule.onNodeWithTag("paste_button").assertIsDisplayed()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("settings_button").performClick()

        // Step past Frame 1 composition and into the middle of the Settings forward fade-in.
        composeTestRule.mainClock.advanceTimeBy(16L)
        composeTestRule.mainClock.advanceTimeBy(60L)

        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.mainClock.advanceTimeBy(16L)

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        assertFalse(composeTestRule.activity.isFinishing)
        composeTestRule.onNodeWithTag("paste_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_back_button").assertDoesNotExist()
    }

    @Test
    fun tappingSettingsMidBackSettleFromPreviewOpensSettingsInsteadOfDroppingTap() {
        val clipboard =
            composeTestRule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("sample", "# Interruptibility"))

        composeTestRule.onNodeWithTag("paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("markdown_text_field").assertIsDisplayed()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("clear_button").performClick()

        // Advance into the back-settle transition so Preview -> Paste is still active.
        composeTestRule.mainClock.advanceTimeBy(16L)
        composeTestRule.mainClock.advanceTimeBy(40L)

        // Confirm both screens are still mounted mid-transition, then tap Paste's Settings button
        // (highest zIndex layer, last in z-sorted semantics order) while back settle is in flight.
        composeTestRule.onAllNodesWithTag("settings_button").onLast().performClick()

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_back_button").assertIsDisplayed()

        // Confirm the pending Preview pop was committed cleanly: back from Settings lands on Paste.
        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("paste_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("markdown_text_field").assertDoesNotExist()
    }

    @Test
    fun tappingPasteMidBackSettleFromPreviewReopensPreviewWithClipboardText() {
        val clipboard =
            composeTestRule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("sample", "# First Paste"))

        composeTestRule.onNodeWithTag("paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("markdown_text_field").assertTextContains("# First Paste")

        clipboard.setPrimaryClip(ClipData.newPlainText("sample", "# Second Paste"))

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("clear_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(16L)
        composeTestRule.mainClock.advanceTimeBy(40L)

        composeTestRule.onNodeWithTag("paste_button").performClick()

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("markdown_text_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("markdown_text_field").assertTextContains("# Second Paste")
    }
}
