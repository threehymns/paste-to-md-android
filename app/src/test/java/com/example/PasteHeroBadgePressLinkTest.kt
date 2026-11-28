package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalReducedMotion
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
class PasteHeroBadgePressLinkTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun pressFractionToggleDoesNotRecomposeAndReadsInDrawPhase() {
        var pressFraction by mutableFloatStateOf(0f)
        var recompositionCount = 0
        val observedDrawFractions = mutableListOf<Float>()

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalReducedMotion provides false) {
                    SideEffect { recompositionCount += 1 }
                    val readPressFraction = remember {
                        {
                            pressFraction.also { observedDrawFractions.add(it) }
                        }
                    }
                    PasteToMarkdownScreen(
                        pressShapeFraction = readPressFraction
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("paste_hero_icon").assertIsDisplayed()
        assertEquals("Expected initial composition exactly once", 1, recompositionCount)
        assertTrue(
            "Expected initial draw pass to read pressFraction=0f, observed=$observedDrawFractions",
            observedDrawFractions.contains(0f)
        )

        composeTestRule.runOnIdle {
            pressFraction = 1f
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("paste_hero_icon").assertIsDisplayed()
        assertEquals(
            "Toggling pressFraction from 0f to 1f must not recompose PasteToMarkdownScreen",
            1,
            recompositionCount
        )
        assertTrue(
            "Expected draw layer to observe updated pressFraction=1f, observed=$observedDrawFractions",
            observedDrawFractions.contains(1f)
        )

        composeTestRule.runOnIdle {
            pressFraction = 0f
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("paste_hero_icon").assertIsDisplayed()
        assertEquals(
            "Releasing pressFraction back to 0f must not recompose PasteToMarkdownScreen",
            1,
            recompositionCount
        )
        assertEquals(0f, observedDrawFractions.last(), 0.0001f)
    }

    @Test
    fun reducedMotionKeepsHeroBadgeStaticAndDisplayed() {
        var pressFraction by mutableFloatStateOf(0f)
        var recompositionCount = 0
        val observedDrawFractions = mutableListOf<Float>()

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    SideEffect { recompositionCount += 1 }
                    val readPressFraction = remember {
                        {
                            pressFraction.also { observedDrawFractions.add(it) }
                        }
                    }
                    PasteToMarkdownScreen(
                        pressShapeFraction = readPressFraction
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        val restingBounds = composeTestRule.onNodeWithTag("paste_hero_icon")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertEquals(1, recompositionCount)
        assertTrue(
            "Reduced motion should bypass pressShapeFraction reads on PasteHeroBadge",
            observedDrawFractions.isEmpty()
        )

        composeTestRule.runOnIdle {
            pressFraction = 1f
        }
        composeTestRule.waitForIdle()

        val pressedBounds = composeTestRule.onNodeWithTag("paste_hero_icon")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertEquals(1, recompositionCount)
        assertEquals(restingBounds, pressedBounds)
        assertTrue(
            "Reduced motion must not apply press transform when pressFraction changes",
            observedDrawFractions.isEmpty()
        )
    }

    @Test
    fun landscapeLayoutLinksHeroBadgeWithoutRecomposition() {
        var pressFraction by mutableFloatStateOf(0f)
        var recompositionCount = 0
        val observedDrawFractions = mutableListOf<Float>()

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalReducedMotion provides false) {
                    Box(modifier = Modifier.size(width = 720.dp, height = 400.dp)) {
                        SideEffect { recompositionCount += 1 }
                        val readPressFraction = remember {
                            {
                                pressFraction.also { observedDrawFractions.add(it) }
                            }
                        }
                        PasteToMarkdownScreen(
                            pressShapeFraction = readPressFraction
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("paste_hero_icon").assertIsDisplayed()
        assertEquals(1, recompositionCount)
        assertTrue(observedDrawFractions.contains(0f))

        composeTestRule.runOnIdle {
            pressFraction = 1f
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("paste_hero_icon").assertIsDisplayed()
        assertEquals(1, recompositionCount)
        assertTrue(observedDrawFractions.contains(1f))
    }
}
