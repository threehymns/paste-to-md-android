package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.ui.AppIcons
import com.example.ui.navigation.MorphEndpointSpec
import com.example.ui.navigation.MorphPairSpec
import com.example.ui.navigation.NavigationTransitionHost
import com.example.ui.navigation.PrimaryActionButton
import com.example.ui.navigation.ScreenStackState
import com.example.ui.navigation.linkToPrimaryActionPress
import com.example.ui.navigation.rememberScreenStackState
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
class NavigationTransitionHostTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private enum class TestScreen { START, DETAIL, EXTRA }

    private val testSaver = listSaver<List<TestScreen>, String>(
        save = { stack -> stack.map { it.name } },
        restore = { names -> names.map { TestScreen.valueOf(it) }.ifEmpty { listOf(TestScreen.START) } }
    )

    private val testMorphPair = MorphPairSpec(
        start = MorphEndpointSpec(TestScreen.START, "Go", AppIcons.ContentPaste),
        end = MorphEndpointSpec(TestScreen.DETAIL, "Copy", AppIcons.ContentCopy)
    )

    @Test
    fun pushPopAndMorphOverlayRunThroughHostInterfaceWithoutActivity() {
        lateinit var stack: ScreenStackState<TestScreen>
        var backCommitCount = 0
        val poppedScreens = mutableListOf<TestScreen>()

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalReducedMotion provides false) {
                    stack = rememberScreenStackState(
                        initialScreen = TestScreen.START,
                        backStackSaver = testSaver,
                        onBackCommitted = { backCommitCount++ },
                        onScreenPopped = { poppedScreens += it }
                    )
                    NavigationTransitionHost(
                        state = stack,
                        morphPair = testMorphPair
                    ) { screen ->
                        when (screen) {
                            TestScreen.START -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.Top
                                ) {
                                    PrimaryActionButton(
                                        onClick = { stack.push(TestScreen.DETAIL) },
                                        enabled = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .testTag("start_anchor")
                                    ) {
                                        Text("Go")
                                    }
                                }
                            }

                            TestScreen.DETAIL -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    PrimaryActionButton(
                                        onClick = { stack.pop() },
                                        enabled = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .testTag("detail_anchor")
                                    ) {
                                        Text("Copy")
                                    }
                                }
                            }

                            TestScreen.EXTRA -> {
                                Box(modifier = Modifier.fillMaxSize().testTag("extra_screen")) {
                                    Text("Extra")
                                }
                            }
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(TestScreen.START, stack.currentScreen)
        assertFalse(stack.canGoBack)
        val startBounds = composeTestRule.onNodeWithTag("start_anchor").getUnclippedBoundsInRoot()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("start_anchor").performClick()
        composeTestRule.mainClock.advanceTimeBy(16L)
        composeTestRule.mainClock.advanceTimeBy(100L)

        composeTestRule.onNodeWithTag("primary_action_morph_overlay").assertIsDisplayed()
        val midBounds = composeTestRule
            .onNodeWithTag("primary_action_morph_overlay")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Expected Morph Overlay to travel downward from start anchor toward detail anchor",
            midBounds.top > startBounds.top
        )

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        assertEquals(TestScreen.DETAIL, stack.currentScreen)
        assertTrue(stack.canGoBack)
        composeTestRule.onNodeWithTag("primary_action_morph_overlay").assertDoesNotExist()
        composeTestRule.onNodeWithTag("detail_anchor").assertIsDisplayed()

        // Pop back to START and confirm lifecycle hooks fire cleanly.
        composeTestRule.onNodeWithTag("detail_anchor").performClick()
        composeTestRule.waitForIdle()

        assertEquals(TestScreen.START, stack.currentScreen)
        assertFalse(stack.canGoBack)
        assertEquals(1, backCommitCount)
        assertEquals(listOf(TestScreen.DETAIL), poppedScreens)
    }

    @Test
    fun interruptingForwardWithPopAndInterruptingBackSettleWithPushKeepStackConsistent() {
        lateinit var stack: ScreenStackState<TestScreen>
        val poppedScreens = mutableListOf<TestScreen>()

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalReducedMotion provides false) {
                    stack = rememberScreenStackState(
                        initialScreen = TestScreen.START,
                        backStackSaver = testSaver,
                        onScreenPopped = { poppedScreens += it }
                    )
                    NavigationTransitionHost(
                        state = stack,
                        morphPair = testMorphPair
                    ) { screen ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("screen_${screen.name}")
                        ) {
                            Text(screen.name)
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.autoAdvance = false

        // Push EXTRA then interrupt mid-forward with pop().
        composeTestRule.runOnIdle { stack.push(TestScreen.EXTRA) }
        composeTestRule.mainClock.advanceTimeBy(16L)
        composeTestRule.mainClock.advanceTimeBy(60L)
        composeTestRule.runOnIdle { stack.pop() }

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        assertEquals(TestScreen.START, stack.currentScreen)
        assertEquals(listOf(TestScreen.EXTRA), poppedScreens)
        composeTestRule.onNodeWithTag("screen_START").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screen_EXTRA").assertDoesNotExist()

        // Push DETAIL, settle, then start popping and interrupt mid-settle with push(EXTRA).
        composeTestRule.runOnIdle { stack.push(TestScreen.DETAIL) }
        composeTestRule.waitForIdle()
        assertEquals(TestScreen.DETAIL, stack.currentScreen)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnIdle { stack.pop() }
        composeTestRule.mainClock.advanceTimeBy(16L)
        composeTestRule.mainClock.advanceTimeBy(40L)

        // Mid-back-settle: pushing EXTRA commits the pending DETAIL pop cleanly.
        composeTestRule.runOnIdle { stack.push(TestScreen.EXTRA) }
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        assertEquals(TestScreen.EXTRA, stack.currentScreen)
        assertEquals(listOf(TestScreen.EXTRA, TestScreen.DETAIL), poppedScreens)

        // Popping EXTRA lands directly on START (DETAIL was already committed as popped).
        composeTestRule.runOnIdle { stack.pop() }
        composeTestRule.waitForIdle()
        assertEquals(TestScreen.START, stack.currentScreen)
        composeTestRule.onNodeWithTag("screen_START").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screen_DETAIL").assertDoesNotExist()
    }

    @Test
    fun commitPendingPopIfSettlingExposesRootImmediatelyForMidSettleAction() {
        lateinit var stack: ScreenStackState<TestScreen>

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                stack = rememberScreenStackState(
                    initialScreen = TestScreen.START,
                    backStackSaver = testSaver
                )
                NavigationTransitionHost(state = stack) { screen ->
                    Box(modifier = Modifier.fillMaxSize().testTag("screen_${screen.name}"))
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { stack.push(TestScreen.DETAIL) }
        composeTestRule.waitForIdle()
        assertEquals(TestScreen.DETAIL, stack.currentScreen)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnIdle { stack.pop() }
        composeTestRule.mainClock.advanceTimeBy(16L)
        composeTestRule.mainClock.advanceTimeBy(40L)

        // Still mid-settle: currentScreen is DETAIL until committed.
        assertEquals(TestScreen.DETAIL, stack.currentScreen)

        composeTestRule.runOnIdle {
            stack.commitPendingPopIfSettling()
        }
        assertEquals(TestScreen.START, stack.currentScreen)

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        assertEquals(TestScreen.START, stack.currentScreen)
    }

    @Test
    fun reducedMotionOmitsMorphOverlayDuringMorphPairTransition() {
        lateinit var stack: ScreenStackState<TestScreen>

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    stack = rememberScreenStackState(
                        initialScreen = TestScreen.START,
                        backStackSaver = testSaver
                    )
                    NavigationTransitionHost(
                        state = stack,
                        morphPair = testMorphPair
                    ) { screen ->
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = if (screen == TestScreen.START) Arrangement.Top else Arrangement.Bottom
                        ) {
                            PrimaryActionButton(
                                onClick = { stack.push(TestScreen.DETAIL) },
                                enabled = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("anchor_${screen.name}")
                            ) {
                                Text(screen.name)
                            }
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("anchor_START").performClick()
        composeTestRule.mainClock.advanceTimeBy(16L)
        composeTestRule.mainClock.advanceTimeBy(80L)

        composeTestRule.onNodeWithTag("primary_action_morph_overlay").assertDoesNotExist()

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("anchor_DETAIL").assertIsDisplayed()
    }

    @Test
    fun primaryActionPrimitivesWorkStandaloneWithoutNavigationHost() {
        var clicks = 0

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .linkToPrimaryActionPress()
                            .testTag("standalone_badge")
                    ) {
                        Text("Badge")
                    }
                    PrimaryActionButton(
                        onClick = { clicks++ },
                        enabled = true,
                        modifier = Modifier.testTag("standalone_primary_button")
                    ) {
                        Text("Action")
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("standalone_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("standalone_primary_button").performClick()
        assertEquals(1, clicks)
    }
}
