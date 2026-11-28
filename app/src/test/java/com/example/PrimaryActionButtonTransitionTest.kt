package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrimaryActionButtonTransitionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun morphOverlayAppearsMidTransitionBetweenPasteAndCopyAndSettlesCleanly() {
        val clipboard = composeTestRule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("sample", "# Morph Test"))

        val pasteRestingBounds = composeTestRule.onNodeWithTag("paste_button").getUnclippedBoundsInRoot()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("paste_button").performClick()

        // Step past Frame 1 layout and into mid-flight of the spatial spring.
        composeTestRule.mainClock.advanceTimeBy(16L)
        composeTestRule.mainClock.advanceTimeBy(120L)

        composeTestRule.onNodeWithTag("primary_action_morph_overlay").assertIsDisplayed()
        val midForwardBounds = composeTestRule
            .onNodeWithTag("primary_action_morph_overlay")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Expected morph overlay to travel downward from Paste toward Copy",
            midForwardBounds.top > pasteRestingBounds.top
        )

        // Let the M3 Expressive spatial spring settle completely.
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("primary_action_morph_overlay").assertDoesNotExist()
        composeTestRule.onNodeWithTag("copy_button").assertIsDisplayed()
        val copyRestingBounds = composeTestRule.onNodeWithTag("copy_button").getUnclippedBoundsInRoot()
        assertTrue(copyRestingBounds.top > midForwardBounds.top)

        // Navigate back via Clear and verify the reverse Copy -> Paste morph.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("clear_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(16L)
        composeTestRule.mainClock.advanceTimeBy(120L)

        composeTestRule.onNodeWithTag("primary_action_morph_overlay").assertIsDisplayed()
        val midBackBounds = composeTestRule
            .onNodeWithTag("primary_action_morph_overlay")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Expected reverse morph overlay to rise upward from Copy toward Paste",
            midBackBounds.top < copyRestingBounds.top
        )

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("primary_action_morph_overlay").assertDoesNotExist()
        composeTestRule.onNodeWithTag("paste_button").assertIsDisplayed()
    }

    @Test
    fun morphOverlayDoesNotAppearWhenOpeningSettings() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(16L)
        composeTestRule.mainClock.advanceTimeBy(80L)

        composeTestRule.onNodeWithTag("primary_action_morph_overlay").assertDoesNotExist()

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_back_button").assertIsDisplayed()
    }
}
