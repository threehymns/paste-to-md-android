package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReducedMotionTransitionTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Test
    fun pasteToPreviewSkipsMorphOverlayWhenReducedMotionIsEnabled() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val previousScale = Settings.Global.getFloat(
            appContext.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        Settings.Global.putFloat(
            appContext.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val clipboard =
                        activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("sample", "# Reduced Motion"))
                }

                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("paste_button").assertIsDisplayed()

                composeTestRule.mainClock.autoAdvance = false
                composeTestRule.onNodeWithTag("paste_button").performClick()

                // Step mid-way through the 160ms reduced-motion opacity dissolve.
                composeTestRule.mainClock.advanceTimeBy(16L)
                composeTestRule.mainClock.advanceTimeBy(80L)

                composeTestRule.onNodeWithTag("primary_action_morph_overlay").assertDoesNotExist()

                // Allow the opacity dissolve to complete cleanly.
                composeTestRule.mainClock.autoAdvance = true
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithTag("primary_action_morph_overlay").assertDoesNotExist()
                composeTestRule.onNodeWithTag("copy_button").assertIsDisplayed()

                // Navigate back via Clear and verify reverse transition also omits the morph overlay.
                composeTestRule.mainClock.autoAdvance = false
                composeTestRule.onNodeWithTag("clear_button").performClick()
                composeTestRule.mainClock.advanceTimeBy(16L)
                composeTestRule.mainClock.advanceTimeBy(80L)

                composeTestRule.onNodeWithTag("primary_action_morph_overlay").assertDoesNotExist()

                composeTestRule.mainClock.autoAdvance = true
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithTag("primary_action_morph_overlay").assertDoesNotExist()
                composeTestRule.onNodeWithTag("paste_button").assertIsDisplayed()
            }
        } finally {
            Settings.Global.putFloat(
                appContext.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                previousScale
            )
        }
    }
}
