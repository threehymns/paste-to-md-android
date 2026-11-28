package com.example

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.DynamicPressMorphShape
import com.example.ui.ExpressivePrimaryActionButton
import com.example.ui.PrimaryActionPressState
import com.example.ui.computePressMorphCornerShape
import com.example.ui.rememberPrimaryActionPressState
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrimaryActionPressPerformanceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun pressAndReleaseSpringTriggersZeroRecompositionsAndUpdatesDrawOutline() {
        var recompositionCount = 0
        val observedDrawFractions = mutableListOf<Float>()
        lateinit var pressState: PrimaryActionPressState
        lateinit var scope: CoroutineScope

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                pressState = rememberPrimaryActionPressState()
                scope = rememberCoroutineScope()
                val readPressFraction = remember(pressState) {
                    {
                        pressState.pressFraction.also { fraction ->
                            observedDrawFractions.add(fraction)
                        }
                    }
                }
                val countRecomposition: () -> Unit = remember {
                    {
                        recompositionCount += 1
                    }
                }
                PrimaryButtonHost(
                    interactionSource = pressState.pasteInteractionSource,
                    pressShapeFraction = readPressFraction,
                    onRecompose = countRecomposition
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("primary_press_button").assertIsDisplayed()
        assertEquals("Expected initial composition exactly once", 1, recompositionCount)

        composeTestRule.mainClock.autoAdvance = false

        val press = PressInteraction.Press(Offset.Zero)
        composeTestRule.runOnIdle {
            scope.launch { pressState.pasteInteractionSource.emit(press) }
        }

        val pressSamples = mutableListOf<Float>()
        repeat(30) {
            composeTestRule.mainClock.advanceTimeByFrame()
            pressSamples.add(pressState.pressFraction)
        }

        assertTrue(
            "Expected press spring to animate toward 1f, observed=$pressSamples",
            pressSamples.any { it > 0.1f && it < 0.99f } && pressSamples.last() > 0.95f
        )
        assertEquals(
            "Press animation must trigger zero recompositions",
            1,
            recompositionCount
        )

        composeTestRule.runOnIdle {
            scope.launch { pressState.pasteInteractionSource.emit(PressInteraction.Release(press)) }
        }

        val releaseSamples = mutableListOf<Float>()
        repeat(40) {
            composeTestRule.mainClock.advanceTimeByFrame()
            releaseSamples.add(pressState.pressFraction)
        }

        assertTrue(
            "Expected release spring to animate back toward 0f, observed=$releaseSamples",
            releaseSamples.any { it > 0.05f && it < 0.95f } && releaseSamples.last() < 0.05f
        )
        assertEquals(
            "Press and release springs must trigger zero recompositions across all frames",
            1,
            recompositionCount
        )
        assertTrue(
            "Expected draw/graphicsLayer phase to re-read pressFraction during animation, observed=$observedDrawFractions",
            observedDrawFractions.any { it > 0.1f } && observedDrawFractions.last() < 0.1f
        )
    }

    @Test
    fun dynamicPressMorphShapeMatchesOverlayCornerShapeAcrossFractionsAndDirections() {
        val density = Density(density = 2f, fontScale = 1f)
        val buttonSize = Size(width = 360f, height = 56f)
        val restingShape = CircleShape
        val pressedShape = RoundedCornerShape(
            topStart = 12.dp,
            topEnd = 10.dp,
            bottomEnd = 8.dp,
            bottomStart = 14.dp
        )

        var currentFraction = 0f
        val dynamicShape = DynamicPressMorphShape(
            restingShape = restingShape,
            pressedShape = pressedShape,
            pressFraction = { currentFraction }
        )

        val fractions = listOf(-0.08f, 0f, 0.35f, 0.5f, 0.7f, 1f, 1.08f)
        for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            for (fraction in fractions) {
                currentFraction = fraction
                val dynamicOutline = dynamicShape.createOutline(
                    size = buttonSize,
                    layoutDirection = layoutDirection,
                    density = density
                ) as Outline.Rounded
                val overlayOutline = computePressMorphCornerShape(
                    restingShape = restingShape,
                    pressedShape = pressedShape,
                    pressFraction = fraction,
                    size = buttonSize,
                    density = density
                ).createOutline(
                    size = buttonSize,
                    layoutDirection = layoutDirection,
                    density = density
                ) as Outline.Rounded

                assertEquals(
                    overlayOutline.roundRect.topLeftCornerRadius.x,
                    dynamicOutline.roundRect.topLeftCornerRadius.x,
                    0.001f
                )
                assertEquals(
                    overlayOutline.roundRect.topRightCornerRadius.x,
                    dynamicOutline.roundRect.topRightCornerRadius.x,
                    0.001f
                )
                assertEquals(
                    overlayOutline.roundRect.bottomRightCornerRadius.x,
                    dynamicOutline.roundRect.bottomRightCornerRadius.x,
                    0.001f
                )
                assertEquals(
                    overlayOutline.roundRect.bottomLeftCornerRadius.x,
                    dynamicOutline.roundRect.bottomLeftCornerRadius.x,
                    0.001f
                )
            }
        }
    }
}

@Composable
private fun PrimaryButtonHost(
    interactionSource: MutableInteractionSource,
    pressShapeFraction: () -> Float,
    onRecompose: () -> Unit
) {
    ExpressivePrimaryActionButton(
        onClick = {},
        enabled = true,
        interactionSource = interactionSource,
        pressShapeFraction = pressShapeFraction,
        modifier = Modifier.testTag("primary_press_button")
    ) {
        SideEffect { onRecompose() }
        Text("Paste")
    }
}
