package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PreviewFooterKeyboardTransitionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun footerButtonsSlideDownOffScreenWhenKeyboardOpensAndReturnWhenKeyboardCloses() {
        var keyboardOpen by mutableStateOf(false)
        val reportedCopyButtonTops = mutableListOf<Float>()

        composeTestRule.setContent {
            MyApplicationTheme {
                MarkdownPreviewScreen(
                    markdownText = "# Preview Heading\n\nBody copy.",
                    onMarkdownChange = {},
                    onClear = {},
                    onPasteAgain = {},
                    onSaveFile = {},
                    onCopy = {},
                    onOpenSettings = {},
                    onCopyButtonPlaced = { coords ->
                        reportedCopyButtonTops += coords.positionInRoot().y
                    },
                    isKeyboardOpen = keyboardOpen
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("preview_floating_toolbar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("clear_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("paste_again_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_file_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_button").assertIsDisplayed()

        val restingToolbarBounds = composeTestRule
            .onNodeWithTag("preview_floating_toolbar")
            .getUnclippedBoundsInRoot()
        val restingCopyBounds = composeTestRule
            .onNodeWithTag("copy_button")
            .getUnclippedBoundsInRoot()
        val restingEditorBounds = composeTestRule
            .onNodeWithTag("markdown_text_field")
            .getUnclippedBoundsInRoot()
        val placementsBeforeKeyboardOpen = reportedCopyButtonTops.size
        assertTrue(placementsBeforeKeyboardOpen > 0)

        // Open keyboard and advance mid-flight through the M3 Expressive spatial spring.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnIdle { keyboardOpen = true }
        composeTestRule.mainClock.advanceTimeBy(16L)
        composeTestRule.mainClock.advanceTimeBy(80L)

        val midExitToolbarBounds = composeTestRule
            .onNodeWithTag("preview_floating_toolbar")
            .getUnclippedBoundsInRoot()
        val midExitCopyBounds = composeTestRule
            .onNodeWithTag("copy_button")
            .getUnclippedBoundsInRoot()
        val midExitEditorBounds = composeTestRule
            .onNodeWithTag("markdown_text_field")
            .getUnclippedBoundsInRoot()

        assertTrue(
            "Expected floating toolbar to transition downward off-screen",
            midExitToolbarBounds.top > restingToolbarBounds.top
        )
        assertTrue(
            "Expected Copy button to transition downward off-screen",
            midExitCopyBounds.top > restingCopyBounds.top
        )
        assertTrue(
            "Expected markdown editor to expand downward as footer hides",
            midExitEditorBounds.bottom > restingEditorBounds.bottom
        )
        assertEquals(
            "Expected mid-exit sliding coordinates not to overwrite shared Copy button bounds",
            placementsBeforeKeyboardOpen,
            reportedCopyButtonTops.size
        )

        // Let the exit animation finish completely.
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("preview_footer").assertDoesNotExist()
        composeTestRule.onNodeWithTag("preview_floating_toolbar").assertDoesNotExist()
        composeTestRule.onNodeWithTag("clear_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("paste_again_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("save_file_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("copy_button").assertDoesNotExist()

        val expandedEditorBounds = composeTestRule
            .onNodeWithTag("markdown_text_field")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Expected markdown editor to occupy reclaimed footer height",
            expandedEditorBounds.bottom > midExitEditorBounds.bottom
        )

        // Close keyboard and verify the footer springs back upward from below its resting position.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnIdle { keyboardOpen = false }
        composeTestRule.mainClock.advanceTimeBy(16L)
        composeTestRule.mainClock.advanceTimeBy(64L)

        val midEnterToolbarBounds = composeTestRule
            .onNodeWithTag("preview_floating_toolbar")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Expected floating toolbar to enter upward from below its resting position",
            midEnterToolbarBounds.top > restingToolbarBounds.top
        )

        // Let the enter spatial spring settle completely.
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("preview_floating_toolbar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_button").assertIsDisplayed()

        val restoredToolbarBounds = composeTestRule
            .onNodeWithTag("preview_floating_toolbar")
            .getUnclippedBoundsInRoot()
        val restoredCopyBounds = composeTestRule
            .onNodeWithTag("copy_button")
            .getUnclippedBoundsInRoot()

        assertEquals(restingToolbarBounds.top.value, restoredToolbarBounds.top.value, 1f)
        assertEquals(restingCopyBounds.top.value, restoredCopyBounds.top.value, 1f)
        assertTrue(
            "Expected settled Copy button bounds to refresh after footer returns",
            reportedCopyButtonTops.size > placementsBeforeKeyboardOpen
        )
    }
}
