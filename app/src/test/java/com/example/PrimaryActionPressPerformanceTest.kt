package com.example

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.Offset
import com.example.ui.navigation.DynamicPressMorphShape
import com.example.ui.navigation.PrimaryActionButton
import com.example.ui.navigation.PrimaryActionPressState
import com.example.ui.navigation.ProvidePrimaryActionAnchorForTest
import com.example.ui.navigation.computePressScale
import com.example.ui.navigation.rememberPrimaryActionPressState
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
                ProvidePrimaryActionAnchorForTest(
                    interactionSource = pressState.startInteractionSource,
                    pressFraction = readPressFraction
                ) {
                    PrimaryButtonHost(onRecompose = countRecomposition)
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("primary_press_button").assertIsDisplayed()
        assertEquals("Expected initial composition exactly once", 1, recompositionCount)

        composeTestRule.mainClock.autoAdvance = false

        val press = PressInteraction.Press(Offset.Zero)
        composeTestRule.runOnIdle {
            scope.launch { pressState.startInteractionSource.emit(press) }
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
            scope.launch { pressState.startInteractionSource.emit(PressInteraction.Release(press)) }
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
    fun dynamicPressMorphShapeInterpolatesClampsAndMirrorsRtlCleanly() {
        val density = Density(density = 1f, fontScale = 1f)
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

        // Resting full pill radius is 28px (height / 2); pressed topStart is 12px.
        currentFraction = 0f
        val restingLtr = dynamicShape.createOutline(buttonSize, LayoutDirection.Ltr, density) as Outline.Rounded
        assertEquals(28f, restingLtr.roundRect.topLeftCornerRadius.x, 0.001f)

        currentFraction = 0.5f
        val midLtr = dynamicShape.createOutline(buttonSize, LayoutDirection.Ltr, density) as Outline.Rounded
        assertEquals(20f, midLtr.roundRect.topLeftCornerRadius.x, 0.001f)
        assertEquals(19f, midLtr.roundRect.topRightCornerRadius.x, 0.001f)

        // RTL mirrors start/end corners symmetrically.
        val midRtl = dynamicShape.createOutline(buttonSize, LayoutDirection.Rtl, density) as Outline.Rounded
        assertEquals(midLtr.roundRect.topLeftCornerRadius.x, midRtl.roundRect.topRightCornerRadius.x, 0.001f)
        assertEquals(midLtr.roundRect.topRightCornerRadius.x, midRtl.roundRect.topLeftCornerRadius.x, 0.001f)
        assertEquals(midLtr.roundRect.bottomLeftCornerRadius.x, midRtl.roundRect.bottomRightCornerRadius.x, 0.001f)
        assertEquals(midLtr.roundRect.bottomRightCornerRadius.x, midRtl.roundRect.bottomLeftCornerRadius.x, 0.001f)

        // Negative overshoot clamps safely to maxRadius (28px).
        currentFraction = -0.25f
        val overshotLtr = dynamicShape.createOutline(buttonSize, LayoutDirection.Ltr, density) as Outline.Rounded
        assertEquals(28f, overshotLtr.roundRect.topLeftCornerRadius.x, 0.001f)
    }

    @Test
    fun computePressScaleCompressesCleanlyAndRespectsReducedMotion() {
        assertEquals(1f, computePressScale(0f, reducedMotion = false), 0.0001f)
        assertEquals(0.985f, computePressScale(0.5f, reducedMotion = false), 0.0001f)
        assertEquals(0.97f, computePressScale(1f, reducedMotion = false), 0.0001f)
        assertEquals(0.97f, computePressScale(1.1f, reducedMotion = false), 0.0001f)
        val subtleRebound = computePressScale(-0.12f, reducedMotion = false)
        assertTrue(subtleRebound > 1f && subtleRebound < 1.005f)
        assertEquals(subtleRebound, computePressScale(-0.5f, reducedMotion = false), 0.0001f)
        assertEquals(1f, computePressScale(1f, reducedMotion = true), 0.0001f)
        assertEquals(1f, computePressScale(-0.1f, reducedMotion = true), 0.0001f)
    }
}

@Composable
private fun PrimaryButtonHost(
    onRecompose: () -> Unit
) {
    PrimaryActionButton(
        onClick = {},
        enabled = true,
        modifier = Modifier.testTag("primary_press_button")
    ) {
        SideEffect { onRecompose() }
        Text("Paste")
    }
}
