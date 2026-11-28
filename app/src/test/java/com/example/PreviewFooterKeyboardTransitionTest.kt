package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.ui.navigation.ProvidePrimaryActionAnchorForTest
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
                ProvidePrimaryActionAnchorForTest(
                    onBoundsPlaced = { coords ->
                        reportedCopyButtonTops += coords.positionInRoot().y
                    }
                ) {
                    MarkdownPreviewScreen(
                        markdownText = "# Preview Heading\n\nBody copy.",
                        onMarkdownChange = {},
                        onClear = {},
                        onPasteAgain = {},
                        onSaveFile = {},
                        onCopy = {},
                        onOpenSettings = {},
                        isKeyboardOpen = keyboardOpen
                    )
                }
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
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeByFrame()
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
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeByFrame()
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

    @Test
    @Config(qualifiers = "w840dp-h412dp-land")
    fun horizontalLayoutPlacesActionsBesidePreviewAndSlidesRightWhenKeyboardOpens() {
        var keyboardOpen by mutableStateOf(false)
        val reportedCopyButtonLefts = mutableListOf<Float>()

        composeTestRule.setContent {
            MyApplicationTheme {
                ProvidePrimaryActionAnchorForTest(
                    onBoundsPlaced = { coords ->
                        reportedCopyButtonLefts += coords.positionInRoot().x
                    }
                ) {
                    MarkdownPreviewScreen(
                        markdownText = "# Horizontal Preview\n\nBody copy.",
                        onMarkdownChange = {},
                        onClear = {},
                        onPasteAgain = {},
                        onSaveFile = {},
                        onCopy = {},
                        onOpenSettings = {},
                        isKeyboardOpen = keyboardOpen
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("preview_floating_toolbar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("clear_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("paste_again_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_file_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_button").assertIsDisplayed()

        val restingEditorBounds = composeTestRule
            .onNodeWithTag("markdown_text_field")
            .getUnclippedBoundsInRoot()
        val restingFooterBounds = composeTestRule
            .onNodeWithTag("preview_footer")
            .getUnclippedBoundsInRoot()
        val restingToolbarBounds = composeTestRule
            .onNodeWithTag("preview_floating_toolbar")
            .getUnclippedBoundsInRoot()
        val restingCopyBounds = composeTestRule
            .onNodeWithTag("copy_button")
            .getUnclippedBoundsInRoot()

        assertTrue(
            "Expected action rail to sit to the right of the markdown editor in horizontal orientation",
            restingFooterBounds.left >= restingEditorBounds.right
        )
        assertTrue(
            "Expected Copy button to sit beside the markdown editor rather than below it",
            restingCopyBounds.top < restingEditorBounds.bottom
        )
        assertTrue(
            "Expected floating toolbar to sit beside the markdown editor rather than below it",
            restingToolbarBounds.bottom > restingEditorBounds.top &&
                restingToolbarBounds.top < restingEditorBounds.bottom
        )

        val placementsBeforeKeyboardOpen = reportedCopyButtonLefts.size
        assertTrue(placementsBeforeKeyboardOpen > 0)

        // Open keyboard and advance mid-flight through the horizontal spatial spring.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnIdle { keyboardOpen = true }
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(80L)

        val midExitCopyBounds = composeTestRule
            .onNodeWithTag("copy_button")
            .getUnclippedBoundsInRoot()
        val midExitEditorBounds = composeTestRule
            .onNodeWithTag("markdown_text_field")
            .getUnclippedBoundsInRoot()

        assertTrue(
            "Expected Copy button to slide right off-screen in horizontal orientation",
            midExitCopyBounds.left > restingCopyBounds.left
        )
        assertTrue(
            "Expected markdown editor to expand horizontally as side actions hide",
            midExitEditorBounds.right > restingEditorBounds.right
        )
        assertEquals(
            "Expected mid-exit sliding coordinates not to overwrite shared Copy button bounds",
            placementsBeforeKeyboardOpen,
            reportedCopyButtonLefts.size
        )

        // Let the exit animation finish completely.
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("preview_footer").assertDoesNotExist()
        composeTestRule.onNodeWithTag("copy_button").assertDoesNotExist()

        val expandedEditorBounds = composeTestRule
            .onNodeWithTag("markdown_text_field")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Expected markdown editor to occupy reclaimed horizontal width",
            expandedEditorBounds.right > midExitEditorBounds.right
        )

        // Close keyboard and verify the side actions spring back in from the right.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnIdle { keyboardOpen = false }
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(64L)

        val midEnterCopyBounds = composeTestRule
            .onNodeWithTag("copy_button")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Expected Copy button to enter from the right of its resting position",
            midEnterCopyBounds.left > restingCopyBounds.left
        )

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        val restoredCopyBounds = composeTestRule
            .onNodeWithTag("copy_button")
            .getUnclippedBoundsInRoot()
        assertEquals(restingCopyBounds.left.value, restoredCopyBounds.left.value, 1f)
        assertTrue(
            "Expected settled Copy button bounds to refresh after side actions return",
            reportedCopyButtonLefts.size > placementsBeforeKeyboardOpen
        )
    }

    @Test
    @Config(qualifiers = "w520dp-h420dp-land")
    fun narrowHorizontalContainerKeepsStackedFooterLayout() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.size(width = 480.dp, height = 400.dp)) {
                    MarkdownPreviewScreen(
                        markdownText = "# Split Screen Preview",
                        onMarkdownChange = {},
                        onClear = {},
                        onPasteAgain = {},
                        onSaveFile = {},
                        onCopy = {},
                        onOpenSettings = {},
                        isKeyboardOpen = false
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        val editorBounds = composeTestRule
            .onNodeWithTag("markdown_text_field")
            .getUnclippedBoundsInRoot()
        val footerBounds = composeTestRule
            .onNodeWithTag("preview_footer")
            .getUnclippedBoundsInRoot()

        assertTrue(
            "Expected narrow horizontal container to keep footer below the markdown editor",
            footerBounds.top >= editorBounds.bottom
        )
    }

    @Test
    fun predictiveBackScrubRevealsFooterProportionallyAndCancelsOrCommitsCleanly() {
        var keyboardOpen by mutableStateOf(false)
        var dismissProgress by mutableStateOf<Float?>(null)
        val reportedCopyButtonTops = mutableListOf<Float>()

        composeTestRule.setContent {
            MyApplicationTheme {
                ProvidePrimaryActionAnchorForTest(
                    onBoundsPlaced = { coords ->
                        reportedCopyButtonTops += coords.positionInRoot().y
                    }
                ) {
                    MarkdownPreviewScreen(
                        markdownText = "# Predictive Back Preview",
                        onMarkdownChange = {},
                        onClear = {},
                        onPasteAgain = {},
                        onSaveFile = {},
                        onCopy = {},
                        onOpenSettings = {},
                        isKeyboardOpen = keyboardOpen,
                        imeDismissProgress = dismissProgress
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        val restingToolbarBounds = composeTestRule
            .onNodeWithTag("preview_floating_toolbar")
            .getUnclippedBoundsInRoot()

        // Open keyboard and let footer exit completely.
        composeTestRule.runOnIdle { keyboardOpen = true }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("preview_footer").assertDoesNotExist()
        val placementsWhileOpen = reportedCopyButtonTops.size

        // Begin predictive back scrub while keyboard is still open (25% -> 60%).
        composeTestRule.runOnIdle { dismissProgress = 0.25f }
        composeTestRule.waitForIdle()
        val earlyScrubToolbarBounds = composeTestRule
            .onNodeWithTag("preview_floating_toolbar")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Expected toolbar to start below resting position at 25% scrub",
            earlyScrubToolbarBounds.top > restingToolbarBounds.top
        )

        composeTestRule.runOnIdle { dismissProgress = 0.60f }
        composeTestRule.waitForIdle()
        val deeperScrubToolbarBounds = composeTestRule
            .onNodeWithTag("preview_floating_toolbar")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Expected toolbar to rise smoothly as predictive back progress increases",
            deeperScrubToolbarBounds.top < earlyScrubToolbarBounds.top
        )
        assertEquals(
            "Expected mid-scrub coordinates not to overwrite shared Copy button bounds",
            placementsWhileOpen,
            reportedCopyButtonTops.size
        )

        // Cancel predictive back gesture: keyboard remains open and footer tucks away cleanly.
        composeTestRule.runOnIdle { dismissProgress = null }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("preview_footer").assertDoesNotExist()

        // Scrub again and commit dismissal.
        composeTestRule.runOnIdle { dismissProgress = 0.40f }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("preview_floating_toolbar").assertExists()

        composeTestRule.runOnIdle {
            keyboardOpen = false
            dismissProgress = null
        }
        composeTestRule.waitForIdle()

        val restoredToolbarBounds = composeTestRule
            .onNodeWithTag("preview_floating_toolbar")
            .getUnclippedBoundsInRoot()
        assertEquals(restingToolbarBounds.top.value, restoredToolbarBounds.top.value, 1f)
        assertTrue(
            "Expected Copy button bounds to refresh once committed dismiss settles",
            reportedCopyButtonTops.size > placementsWhileOpen
        )
    }

    @Test
    @Config(qualifiers = "w840dp-h412dp-land")
    fun horizontalLayoutScrubsSideActionsInDuringPredictiveBackDismiss() {
        var keyboardOpen by mutableStateOf(false)
        var dismissProgress by mutableStateOf<Float?>(null)

        composeTestRule.setContent {
            MyApplicationTheme {
                MarkdownPreviewScreen(
                    markdownText = "# Landscape Predictive Back",
                    onMarkdownChange = {},
                    onClear = {},
                    onPasteAgain = {},
                    onSaveFile = {},
                    onCopy = {},
                    onOpenSettings = {},
                    isKeyboardOpen = keyboardOpen,
                    imeDismissProgress = dismissProgress
                )
            }
        }

        composeTestRule.waitForIdle()
        val restingCopyBounds = composeTestRule
            .onNodeWithTag("copy_button")
            .getUnclippedBoundsInRoot()

        composeTestRule.runOnIdle { keyboardOpen = true }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("copy_button").assertDoesNotExist()

        composeTestRule.runOnIdle { dismissProgress = 0.30f }
        composeTestRule.waitForIdle()
        val earlyScrubCopyBounds = composeTestRule
            .onNodeWithTag("copy_button")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Expected side panel Copy button to scrub in from the right during predictive back",
            earlyScrubCopyBounds.left > restingCopyBounds.left
        )

        composeTestRule.runOnIdle { dismissProgress = 0.75f }
        composeTestRule.waitForIdle()
        val lateScrubCopyBounds = composeTestRule
            .onNodeWithTag("copy_button")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Expected side panel Copy button to move left toward resting position as scrub progresses",
            lateScrubCopyBounds.left < earlyScrubCopyBounds.left
        )

        composeTestRule.runOnIdle {
            keyboardOpen = false
            dismissProgress = null
        }
        composeTestRule.waitForIdle()
        val restoredCopyBounds = composeTestRule
            .onNodeWithTag("copy_button")
            .getUnclippedBoundsInRoot()
        assertEquals(restingCopyBounds.left.value, restoredCopyBounds.left.value, 1f)
    }
}
