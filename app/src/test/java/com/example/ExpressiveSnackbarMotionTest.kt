package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.example.ui.ExpressiveSnackbarHost
import com.example.ui.shouldDismissSnackbarDrag
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExpressiveSnackbarMotionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun shortFastFlickDismissesSnackbar() {
        assertTrue(
            shouldDismissSnackbarDrag(
                offsetPx = 60f,
                elapsedMs = 100L,
                containerWidthPx = 1000
            )
        )
        assertTrue(
            shouldDismissSnackbarDrag(
                offsetPx = -60f,
                elapsedMs = 100L,
                containerWidthPx = 1000
            )
        )
    }

    @Test
    fun slowShortDragSnapsBackWithoutDismissing() {
        assertFalse(
            shouldDismissSnackbarDrag(
                offsetPx = 60f,
                elapsedMs = 1000L,
                containerWidthPx = 1000
            )
        )
    }

    @Test
    fun slowLongDragPastDistanceThresholdDismissesSnackbar() {
        assertTrue(
            shouldDismissSnackbarDrag(
                offsetPx = 400f,
                elapsedMs = 5000L,
                containerWidthPx = 1000
            )
        )
        assertTrue(
            shouldDismissSnackbarDrag(
                offsetPx = -400f,
                elapsedMs = 5000L,
                containerWidthPx = 1000
            )
        )
    }

    @Test
    fun subPixelDragDoesNotDismissSnackbar() {
        assertFalse(
            shouldDismissSnackbarDrag(
                offsetPx = 0.5f,
                elapsedMs = 1L,
                containerWidthPx = 1000
            )
        )
    }

    @Test
    fun snackbarRendersAndDismissesCleanlyOnShowAndFastFlick() {
        var result: SnackbarResult? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                val hostState = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    result = hostState.showSnackbar(
                        message = "Clipboard is empty",
                        duration = SnackbarDuration.Indefinite
                    )
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    ExpressiveSnackbarHost(hostState = hostState)
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("expressive_snackbar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clipboard is empty").assertIsDisplayed()

        composeTestRule.onNodeWithTag("expressive_snackbar").performTouchInput {
            swipeRight(
                startX = centerX,
                endX = centerX + 120f,
                durationMillis = 80L
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("expressive_snackbar").assertDoesNotExist()
        composeTestRule.onNodeWithText("Clipboard is empty").assertDoesNotExist()
        assertEquals(SnackbarResult.Dismissed, result)
    }

    @Test
    fun snackbarSnapsBackOnSlowShortDrag() {
        var result: SnackbarResult? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                val hostState = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    result = hostState.showSnackbar(
                        message = "Clipboard is empty",
                        duration = SnackbarDuration.Indefinite
                    )
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    ExpressiveSnackbarHost(hostState = hostState)
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("expressive_snackbar").assertIsDisplayed()

        composeTestRule.onNodeWithTag("expressive_snackbar").performTouchInput {
            swipeRight(
                startX = centerX,
                endX = centerX + 45f,
                durationMillis = 1200L
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("expressive_snackbar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clipboard is empty").assertIsDisplayed()
        assertEquals(null, result)
    }

    @Test
    fun snackbarRespectsReducedMotionAndAllowsSwipeDismiss() {
        var result: SnackbarResult? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    val hostState = remember { SnackbarHostState() }
                    LaunchedEffect(Unit) {
                        result = hostState.showSnackbar(
                            message = "Markdown copied to clipboard",
                            duration = SnackbarDuration.Indefinite
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExpressiveSnackbarHost(hostState = hostState)
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("expressive_snackbar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Markdown copied to clipboard").assertIsDisplayed()

        composeTestRule.onNodeWithTag("expressive_snackbar").performTouchInput {
            swipeRight(
                startX = centerX,
                endX = centerX + 120f,
                durationMillis = 80L
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("expressive_snackbar").assertDoesNotExist()
        assertEquals(SnackbarResult.Dismissed, result)
    }
}
