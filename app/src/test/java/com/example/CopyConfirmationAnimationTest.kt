package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.MotionTokens
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CopyConfirmationAnimationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun copyButtonMorphsToCopiedAndRevertsAfterHoldAndResetsOnEdit() {
        var markdownText by mutableStateOf("# Hello")
        var copyCount = 0

        composeTestRule.setContent {
            MyApplicationTheme {
                MarkdownPreviewScreen(
                    markdownText = markdownText,
                    onMarkdownChange = { markdownText = it },
                    onClear = {},
                    onPasteAgain = {},
                    onSaveFile = {},
                    onCopy = { copyCount++ },
                    onOpenSettings = {},
                    isKeyboardOpen = false
                )
            }
        }

        composeTestRule.waitForIdle()
        val restingBounds = composeTestRule
            .onNodeWithTag("copy_button")
            .getUnclippedBoundsInRoot()
        composeTestRule.onNodeWithTag("copy_button").assertTextEquals("Copy Markdown")

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("copy_button").performClick()
        // Advance past the 180ms enter morph (well before the 1400ms hold expires).
        composeTestRule.mainClock.advanceTimeBy(220L)
        composeTestRule.waitForIdle()

        assertEquals(1, copyCount)
        composeTestRule.onNodeWithText("Copied").assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_button").assertTextEquals("Copied")
        val copiedBounds = composeTestRule
            .onNodeWithTag("copy_button")
            .getUnclippedBoundsInRoot()
        assertEquals(restingBounds.width, copiedBounds.width)
        assertEquals(restingBounds.height, copiedBounds.height)

        // Advance past the 1400ms hold window and revert transition.
        composeTestRule.mainClock.advanceTimeBy(MotionTokens.DurationCopyConfirmHoldMs + 50L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Copy Markdown").assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_button").assertTextEquals("Copy Markdown")

        // Tap Copy again, then edit markdown after 300ms -> resets immediately.
        composeTestRule.onNodeWithTag("copy_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(300L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Copied").assertIsDisplayed()

        composeTestRule.runOnIdle {
            markdownText = "# Hello edited"
        }
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(300L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("markdown_text_field")
            .assertTextEquals("# Hello edited")
        composeTestRule.onNodeWithTag("copy_button").assertTextEquals("Copy Markdown")
        composeTestRule.onNodeWithText("Copy Markdown").assertIsDisplayed()
    }

    @Test
    fun rapidCopyTapsRestartHoldTimerWithoutPrematureRevert() {
        var copyCount = 0

        composeTestRule.setContent {
            MyApplicationTheme {
                MarkdownPreviewScreen(
                    markdownText = "# Rapid Copy",
                    onMarkdownChange = {},
                    onClear = {},
                    onPasteAgain = {},
                    onSaveFile = {},
                    onCopy = { copyCount++ },
                    onOpenSettings = {},
                    isKeyboardOpen = false
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.autoAdvance = false

        // First tap starts a 1400ms hold window.
        composeTestRule.onNodeWithTag("copy_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(900L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Copied").assertIsDisplayed()

        // Second tap at 900ms restarts the 1400ms hold timer.
        composeTestRule.onNodeWithTag("copy_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(700L)
        composeTestRule.waitForIdle()

        // Total elapsed is 1600ms since first tap, but only 700ms since second tap -> still "Copied".
        assertEquals(2, copyCount)
        composeTestRule.onNodeWithText("Copied").assertIsDisplayed()

        // Advance past the remaining 700ms + transition duration -> reverts cleanly.
        composeTestRule.mainClock.advanceTimeBy(950L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Copy Markdown").assertIsDisplayed()
    }

    @Test
    fun copyButtonConfirmationWorksInLandscapeAndReducedMotion() {
        composeTestRule.setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    Box(modifier = Modifier.size(width = 800.dp, height = 420.dp)) {
                        MarkdownPreviewScreen(
                            markdownText = "# Landscape",
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
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.onNodeWithTag("copy_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(200L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Copied").assertIsDisplayed()

        composeTestRule.mainClock.advanceTimeBy(MotionTokens.DurationCopyConfirmHoldMs + 50L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Copy Markdown").assertIsDisplayed()
    }
}
