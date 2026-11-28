package com.example.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.MotionTokens
import com.example.ui.theme.ReducedMotionOpacityScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Describes one endpoint of a Primary Action Anchor morph pair.
 */
@Immutable
data class MorphEndpointSpec<K>(
    val screen: K,
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true
)

/**
 * Configures the Morph Overlay between two screens' Primary Action Anchors.
 */
@Immutable
data class MorphPairSpec<K>(
    val start: MorphEndpointSpec<K>,
    val end: MorphEndpointSpec<K>
) {
    internal fun matches(first: K?, second: K?): Boolean =
        (first == start.screen && second == end.screen) ||
            (first == end.screen && second == start.screen)
}

/**
 * Owns the ordered Screen Stack and its interruptible forward, back, and predictive-back
 * transition state.
 */
@Stable
class ScreenStackState<K : Any> internal constructor(
    initialStack: List<K>,
    private val onBackCommittedProvider: () -> () -> Unit,
    private val onScreenPoppedProvider: () -> (K) -> Unit
) {
    internal var backStack by mutableStateOf(initialStack)
        private set

    internal var stateHolder: SaveableStateHolder? = null
    internal var activeMorphPair: MorphPairSpec<K>? = null

    internal val backProgress = Animatable(0f)
    internal val backSpatialProgress = Animatable(0f)
    internal var isBackTransitionActive by mutableStateOf(false)
    internal var backSettleTarget by mutableStateOf<Float?>(null)
    internal var backTransitionGeneration by mutableIntStateOf(0)
    internal var resetBackProgressBeforeAnimate by mutableStateOf(false)
    internal var backResetInitialEffects by mutableFloatStateOf(0f)
    internal var backResetInitialSpatial by mutableFloatStateOf(0f)

    internal val forwardProgress = Animatable(1f)
    internal val forwardSpatialProgress = Animatable(1f)
    internal var isForwardTransitionActive by mutableStateOf(false)
    internal var forwardOutgoingScreen by mutableStateOf<K?>(null)
    internal var forwardTransitionGeneration by mutableIntStateOf(0)
    internal var resetForwardProgressBeforeAnimate by mutableStateOf(false)

    val currentScreen: K
        get() = backStack.last()

    val canGoBack: Boolean
        get() = backStack.size > 1

    internal val previousScreen: K?
        get() = backStack.getOrNull(backStack.lastIndex - 1)

    /**
     * Immediately commits an in-flight back-settle pop if the top screen is already animating out.
     * Useful when an interactive action on the incoming screen (such as Paste) fires mid-settle.
     */
    fun commitPendingPopIfSettling() {
        if (isBackTransitionActive && backSettleTarget == 1f) {
            cancelBackTransition(commitPendingPop = true)
        }
    }

    /**
     * Pushes [screen] onto the Screen Stack with a clean Frame-1 M3 Expressive fade-through
     * transition. No-op if [screen] is already the current screen (after committing any pending pop).
     */
    fun push(screen: K) {
        if (isBackTransitionActive) {
            cancelBackTransition(commitPendingPop = true)
        }
        if (backStack.last() == screen) return
        forwardOutgoingScreen = backStack.last()
        forwardTransitionGeneration++
        resetForwardProgressBeforeAnimate = true
        isForwardTransitionActive = true
        backStack = backStack + screen
    }

    /**
     * Pops the top screen with an interruptible back transition. Reverses smoothly if a forward
     * transition is currently in flight. No-op when the Screen Stack has a single screen.
     */
    fun pop() {
        if (backStack.size <= 1) return
        if (isBackTransitionActive && backSettleTarget == 1f) return
        val morphPair = activeMorphPair
        val interruptedMorphForward = isForwardTransitionActive &&
            morphPair != null &&
            morphPair.matches(forwardOutgoingScreen, backStack.lastOrNull())
        val reversedEffects = if (isForwardTransitionActive) {
            val current = if (resetForwardProgressBeforeAnimate) 0f else forwardProgress.value
            (1f - current).coerceIn(0f, 1f)
        } else {
            0f
        }
        val reversedSpatial = if (interruptedMorphForward) {
            val current = if (resetForwardProgressBeforeAnimate) 0f else forwardSpatialProgress.value
            (1f - current).coerceIn(0f, 1f)
        } else {
            0f
        }
        cancelForwardTransition()
        onBackCommittedProvider().invoke()
        val wasActive = isBackTransitionActive
        backTransitionGeneration++
        if (!wasActive) {
            backResetInitialEffects = reversedEffects
            backResetInitialSpatial = reversedSpatial
        }
        resetBackProgressBeforeAnimate = !wasActive
        isBackTransitionActive = true
        backSettleTarget = 1f
    }

    internal fun cancelForwardTransition() {
        if (!isForwardTransitionActive) return
        forwardTransitionGeneration++
        isForwardTransitionActive = false
        forwardOutgoingScreen = null
        resetForwardProgressBeforeAnimate = false
    }

    internal fun commitBackPop() {
        if (backStack.size <= 1) return
        val poppedScreen = backStack.last()
        backStack = backStack.dropLast(1)
        stateHolder?.removeState(poppedScreen)
        onScreenPoppedProvider().invoke(poppedScreen)
    }

    internal fun cancelBackTransition(commitPendingPop: Boolean) {
        if (!isBackTransitionActive) return
        backTransitionGeneration++
        if (commitPendingPop && backSettleTarget == 1f) {
            commitBackPop()
        }
        isBackTransitionActive = false
        backSettleTarget = null
        resetBackProgressBeforeAnimate = false
        backResetInitialEffects = 0f
        backResetInitialSpatial = 0f
    }

    internal fun notifyBackCommitted() {
        onBackCommittedProvider().invoke()
    }
}

/**
 * Remembers a [ScreenStackState] whose Screen Stack survives configuration changes via
 * [backStackSaver].
 */
@Composable
fun <K : Any> rememberScreenStackState(
    initialScreen: K,
    backStackSaver: Saver<List<K>, out Any>,
    onBackCommitted: () -> Unit = {},
    onScreenPopped: (K) -> Unit = {}
): ScreenStackState<K> {
    val latestOnBackCommitted = rememberUpdatedState(onBackCommitted)
    val latestOnScreenPopped = rememberUpdatedState(onScreenPopped)

    @Suppress("UNCHECKED_CAST")
    val typedSaver = backStackSaver as Saver<List<K>, Any>
    val stateSaver = remember(typedSaver) {
        Saver<ScreenStackState<K>, Any>(
            save = { stackState ->
                with(typedSaver) { save(stackState.backStack) }
            },
            restore = { saved ->
                val restored = typedSaver.restore(saved)?.ifEmpty { listOf(initialScreen) }
                    ?: listOf(initialScreen)
                ScreenStackState(
                    initialStack = restored,
                    onBackCommittedProvider = { latestOnBackCommitted.value },
                    onScreenPoppedProvider = { latestOnScreenPopped.value }
                )
            }
        )
    }

    return rememberSaveable(saver = stateSaver) {
        ScreenStackState(
            initialStack = listOf(initialScreen),
            onBackCommittedProvider = { latestOnBackCommitted.value },
            onScreenPoppedProvider = { latestOnScreenPopped.value }
        )
    }
}

// Full-screen fade-through transitions.
// Back: outgoing scales 100% -> 90% and fades out by 35%; incoming scales 110% -> 100% and fades in.
// Forward: outgoing scales 100% -> 96% and fades out by 35%; incoming scales 94% -> 100% and fades in.
private fun fadeThroughExitAlpha(progress: Float): Float =
    (1f - progress / MotionTokens.ScreenFadeThroughThreshold).coerceIn(0f, 1f)

private fun fadeThroughEnterAlpha(progress: Float): Float =
    ((progress - MotionTokens.ScreenFadeThroughThreshold) / (1f - MotionTokens.ScreenFadeThroughThreshold))
        .coerceIn(0f, 1f)

private fun backExitScale(progress: Float): Float =
    1f + (MotionTokens.BackExitScaleTarget - 1f) * progress

private fun backEnterScale(progress: Float): Float =
    MotionTokens.BackEnterScaleStart + (1f - MotionTokens.BackEnterScaleStart) * progress

private fun forwardExitScale(progress: Float): Float =
    1f + (MotionTokens.ForwardExitScaleTarget - 1f) * progress

private fun forwardEnterScale(progress: Float): Float =
    MotionTokens.ForwardEnterScaleStart + (1f - MotionTokens.ForwardEnterScaleStart) * progress

private class ScreenAnchorMeasureState(
    var rootCoords: LayoutCoordinates? = null,
    var anchorCoords: LayoutCoordinates? = null,
    var isSettled: Boolean = true
)

/**
 * Hosts the Screen Stack with interruptible M3 Expressive fade-through transitions, interactive
 * predictive-back scrubbing, Reduced Motion fallbacks, per-screen Primary Action Anchor binding,
 * and the Morph Overlay.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <K : Any> NavigationTransitionHost(
    state: ScreenStackState<K>,
    morphPair: MorphPairSpec<K>? = null,
    modifier: Modifier = Modifier,
    content: @Composable (K) -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme
    val reducedMotion = LocalReducedMotion.current
    val screenStateHolder = rememberSaveableStateHolder()

    state.stateHolder = screenStateHolder
    state.activeMorphPair = morphPair

    var rootContainerSize by remember { mutableStateOf(IntSize.Zero) }
    var startMeasuredBounds by remember { mutableStateOf<MeasuredAnchorBounds?>(null) }
    var endMeasuredBounds by remember { mutableStateOf<MeasuredAnchorBounds?>(null) }
    val pressState = rememberPrimaryActionPressState()
    val readPressShapeFraction = remember(pressState) {
        { pressState.pressFraction }
    }

    LaunchedEffect(state.forwardTransitionGeneration) {
        if (!state.isForwardTransitionActive) return@LaunchedEffect
        val generation = state.forwardTransitionGeneration
        val animateSpatialButton = morphPair != null && morphPair.matches(
            state.forwardOutgoingScreen,
            state.backStack.lastOrNull()
        )

        if (state.resetForwardProgressBeforeAnimate) {
            state.forwardProgress.snapTo(0f)
            state.forwardSpatialProgress.snapTo(0f)
            state.resetForwardProgressBeforeAnimate = false
        }

        if (reducedMotion) {
            state.forwardSpatialProgress.snapTo(1f)
            withContext(ReducedMotionOpacityScale) {
                state.forwardProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = MotionTokens.reducedFadeSpec()
                )
            }
        } else if (animateSpatialButton) {
            coroutineScope {
                launch {
                    state.forwardProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = motionScheme.defaultEffectsSpec()
                    )
                }
                launch {
                    state.forwardSpatialProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = motionScheme.defaultSpatialSpec()
                    )
                }
            }
        } else {
            state.forwardSpatialProgress.snapTo(1f)
            state.forwardProgress.animateTo(
                targetValue = 1f,
                animationSpec = motionScheme.defaultEffectsSpec()
            )
        }

        if (state.forwardTransitionGeneration != generation) return@LaunchedEffect

        state.forwardProgress.snapTo(1f)
        state.forwardSpatialProgress.snapTo(1f)
        if (state.forwardTransitionGeneration == generation) {
            state.isForwardTransitionActive = false
            state.forwardOutgoingScreen = null
        }
    }

    LaunchedEffect(state.backSettleTarget, state.backTransitionGeneration) {
        val target = state.backSettleTarget ?: return@LaunchedEffect
        val generation = state.backTransitionGeneration
        val animateSpatialButton = morphPair != null && morphPair.matches(
            state.previousScreen,
            state.backStack.lastOrNull()
        )

        if (state.resetBackProgressBeforeAnimate) {
            state.backProgress.snapTo(state.backResetInitialEffects)
            state.backSpatialProgress.snapTo(state.backResetInitialSpatial)
            state.resetBackProgressBeforeAnimate = false
            state.backResetInitialEffects = 0f
            state.backResetInitialSpatial = 0f
        }

        val effectsSpec = if (target == 1f) {
            MotionTokens.fastUiSpec<Float>()
        } else {
            motionScheme.defaultEffectsSpec()
        }

        if (reducedMotion) {
            state.backSpatialProgress.snapTo(target)
            withContext(ReducedMotionOpacityScale) {
                state.backProgress.animateTo(
                    targetValue = target,
                    animationSpec = MotionTokens.reducedFadeSpec()
                )
            }
        } else if (animateSpatialButton) {
            coroutineScope {
                launch {
                    state.backProgress.animateTo(
                        targetValue = target,
                        animationSpec = effectsSpec
                    )
                }
                launch {
                    state.backSpatialProgress.animateTo(
                        targetValue = target,
                        animationSpec = motionScheme.defaultSpatialSpec()
                    )
                }
            }
        } else {
            state.backSpatialProgress.snapTo(target)
            state.backProgress.animateTo(
                targetValue = target,
                animationSpec = effectsSpec
            )
        }

        if (state.backTransitionGeneration != generation) return@LaunchedEffect

        state.backProgress.snapTo(target)
        state.backSpatialProgress.snapTo(target)

        if (state.backTransitionGeneration != generation) return@LaunchedEffect

        if (target == 1f) {
            state.commitBackPop()
        }
        state.isBackTransitionActive = false
        state.backSettleTarget = null
        if (target == 1f && state.backTransitionGeneration == generation) {
            state.backProgress.snapTo(0f)
            state.backSpatialProgress.snapTo(0f)
        }
    }

    PredictiveBackHandler(enabled = state.canGoBack) { progressFlow ->
        val interruptedForward = state.isForwardTransitionActive
        val interruptedMorphForward = interruptedForward &&
            morphPair != null &&
            morphPair.matches(state.forwardOutgoingScreen, state.backStack.lastOrNull())
        val reversedEffects = if (interruptedForward) {
            val current = if (state.resetForwardProgressBeforeAnimate) 0f else state.forwardProgress.value
            (1f - current).coerceIn(0f, 1f)
        } else {
            0f
        }
        val reversedSpatial = if (interruptedMorphForward) {
            val current = if (state.resetForwardProgressBeforeAnimate) 0f else state.forwardSpatialProgress.value
            (1f - current).coerceIn(0f, 1f)
        } else {
            0f
        }
        state.cancelForwardTransition()
        val wasActive = state.isBackTransitionActive
        state.backTransitionGeneration++
        val generation = state.backTransitionGeneration
        state.backSettleTarget = null
        state.resetBackProgressBeforeAnimate = false
        state.backResetInitialEffects = 0f
        state.backResetInitialSpatial = 0f
        state.isBackTransitionActive = true

        val initialProgress = when {
            wasActive -> state.backProgress.value
            interruptedForward -> reversedEffects
            else -> 0f
        }
        val initialSpatial = when {
            wasActive -> state.backSpatialProgress.value
            interruptedMorphForward -> reversedSpatial
            else -> 0f
        }
        if (!wasActive) {
            state.backProgress.snapTo(initialProgress)
            state.backSpatialProgress.snapTo(initialSpatial)
        }
        val remainingProgress = 1f - initialProgress
        val remainingSpatial = 1f - initialSpatial

        try {
            progressFlow.collect { event ->
                val eventProgress = event.progress.coerceIn(0f, 1f)
                val easedEvent = MotionTokens.PredictiveBackGestureEasing.transform(eventProgress)
                state.backProgress.snapTo(initialProgress + remainingProgress * easedEvent)
                state.backSpatialProgress.snapTo(initialSpatial + remainingSpatial * easedEvent)
            }
            if (state.backTransitionGeneration == generation) {
                state.notifyBackCommitted()
                state.backSettleTarget = 1f
            }
        } catch (e: CancellationException) {
            if (state.backTransitionGeneration == generation) {
                state.backSettleTarget = 0f
            }
        }
    }

    val topScreen = state.currentScreen
    val previousScreen = state.previousScreen
    val visibleScreens = remember(
        topScreen,
        previousScreen,
        state.isBackTransitionActive,
        state.isForwardTransitionActive,
        state.forwardOutgoingScreen
    ) {
        when {
            state.isBackTransitionActive && previousScreen != null -> {
                listOf(previousScreen, topScreen)
            }
            state.isForwardTransitionActive &&
                state.forwardOutgoingScreen != null &&
                state.forwardOutgoingScreen != topScreen -> {
                listOf(state.forwardOutgoingScreen!!, topScreen)
            }
            else -> listOf(topScreen)
        }
    }

    val activeMorphForward = morphPair != null &&
        state.isForwardTransitionActive &&
        morphPair.matches(state.forwardOutgoingScreen, topScreen)
    val activeMorphBack = morphPair != null &&
        state.isBackTransitionActive &&
        morphPair.matches(previousScreen, topScreen)
    val validStartBounds = startMeasuredBounds
        ?.takeIf { it.containerSize == rootContainerSize }
        ?.rect
    val validEndBounds = endMeasuredBounds
        ?.takeIf { it.containerSize == rootContainerSize }
        ?.rect
    val showMorphOverlay = !reducedMotion &&
        morphPair != null &&
        (activeMorphForward || activeMorphBack) &&
        validStartBounds != null &&
        validEndBounds != null
    val latestShowMorphOverlay by rememberUpdatedState(showMorphOverlay)
    val isAnchorHidden = remember { { latestShowMorphOverlay } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { rootContainerSize = it }
    ) {
        visibleScreens.forEach { screen ->
            key(screen) {
                val isTop = screen == topScreen
                val measureState = remember { ScreenAnchorMeasureState() }

                val startScreen = morphPair?.start?.screen
                val endScreen = morphPair?.end?.screen
                val isStartEndpoint = morphPair != null && screen == startScreen
                val isEndEndpoint = morphPair != null && screen == endScreen

                val updateAnchorBounds = remember(screen, startScreen, endScreen) {
                    { container: LayoutCoordinates?, target: LayoutCoordinates? ->
                        val rect = computeRelativeAnchorRect(container, target)
                        if (rect != null && container != null) {
                            val measured = MeasuredAnchorBounds(
                                rect = rect,
                                containerSize = container.size
                            )
                            when (screen) {
                                startScreen -> {
                                    if (startMeasuredBounds != measured) {
                                        startMeasuredBounds = measured
                                    }
                                }
                                endScreen -> {
                                    if (endMeasuredBounds != measured) {
                                        endMeasuredBounds = measured
                                    }
                                }
                            }
                        }
                    }
                }

                val screenAnchor = remember(screen, isStartEndpoint, isEndEndpoint, pressState, updateAnchorBounds) {
                    when {
                        isStartEndpoint -> PrimaryActionAnchor(
                            interactionSource = pressState.startInteractionSource,
                            pressFraction = readPressShapeFraction,
                            isHidden = isAnchorHidden,
                            onBoundsPlaced = { coords ->
                                measureState.anchorCoords = coords
                                updateAnchorBounds(measureState.rootCoords, coords)
                            },
                            onSettledChanged = { settled ->
                                measureState.isSettled = settled
                            }
                        )
                        isEndEndpoint -> PrimaryActionAnchor(
                            interactionSource = pressState.endInteractionSource,
                            pressFraction = readPressShapeFraction,
                            isHidden = isAnchorHidden,
                            onBoundsPlaced = { coords ->
                                measureState.anchorCoords = coords
                                updateAnchorBounds(measureState.rootCoords, coords)
                            },
                            onSettledChanged = { settled ->
                                measureState.isSettled = settled
                            }
                        )
                        else -> PrimaryActionAnchor.NoOp
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(
                            if (state.isBackTransitionActive && state.backSettleTarget == 1f && isTop) {
                                -1f
                            } else {
                                0f
                            }
                        )
                        .graphicsLayer {
                            when {
                                state.isBackTransitionActive -> {
                                    val progress = if (state.resetBackProgressBeforeAnimate) {
                                        state.backResetInitialEffects
                                    } else {
                                        state.backProgress.value.coerceIn(0f, 1f)
                                    }
                                    val scale = if (reducedMotion) {
                                        1f
                                    } else if (isTop) {
                                        backExitScale(progress)
                                    } else {
                                        backEnterScale(progress)
                                    }
                                    alpha = if (isTop) {
                                        fadeThroughExitAlpha(progress)
                                    } else {
                                        fadeThroughEnterAlpha(progress)
                                    }
                                    scaleX = scale
                                    scaleY = scale
                                }
                                state.isForwardTransitionActive -> {
                                    val progress = if (state.resetForwardProgressBeforeAnimate) {
                                        0f
                                    } else {
                                        state.forwardProgress.value.coerceIn(0f, 1f)
                                    }
                                    val scale = if (reducedMotion) {
                                        1f
                                    } else if (isTop) {
                                        forwardEnterScale(progress)
                                    } else {
                                        forwardExitScale(progress)
                                    }
                                    alpha = if (isTop) {
                                        fadeThroughEnterAlpha(progress)
                                    } else {
                                        fadeThroughExitAlpha(progress)
                                    }
                                    scaleX = scale
                                    scaleY = scale
                                }
                                else -> {
                                    alpha = 1f
                                    scaleX = 1f
                                    scaleY = 1f
                                }
                            }
                            transformOrigin = TransformOrigin.Center
                        }
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    // Unscaled screen-space reference container so measured anchor bounds
                    // remain immune to the parent graphicsLayer scale transform.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coords ->
                                measureState.rootCoords = coords
                                if (measureState.isSettled) {
                                    updateAnchorBounds(coords, measureState.anchorCoords)
                                }
                            }
                    ) {
                        CompositionLocalProvider(LocalPrimaryActionAnchor provides screenAnchor) {
                            screenStateHolder.SaveableStateProvider(screen) {
                                content(screen)
                            }
                        }
                    }
                }
            }
        }

        if (showMorphOverlay && morphPair != null && validStartBounds != null && validEndBounds != null) {
            MorphOverlay(
                morphPair = morphPair,
                startBounds = validStartBounds,
                endBounds = validEndBounds,
                spatialProgress = {
                    when {
                        activeMorphForward -> {
                            val raw = if (state.resetForwardProgressBeforeAnimate) {
                                0f
                            } else {
                                state.forwardSpatialProgress.value
                            }
                            if (state.forwardOutgoingScreen == morphPair.start.screen) raw else 1f - raw
                        }
                        activeMorphBack -> {
                            val raw = if (state.resetBackProgressBeforeAnimate) {
                                state.backResetInitialSpatial
                            } else {
                                state.backSpatialProgress.value
                            }
                            if (topScreen == morphPair.end.screen) 1f - raw else raw
                        }
                        else -> 0f
                    }
                },
                effectsProgress = {
                    when {
                        activeMorphForward -> {
                            val raw = if (state.resetForwardProgressBeforeAnimate) {
                                0f
                            } else {
                                state.forwardProgress.value.coerceIn(0f, 1f)
                            }
                            if (state.forwardOutgoingScreen == morphPair.start.screen) raw else 1f - raw
                        }
                        activeMorphBack -> {
                            val raw = if (state.resetBackProgressBeforeAnimate) {
                                state.backResetInitialEffects
                            } else {
                                state.backProgress.value.coerceIn(0f, 1f)
                            }
                            if (topScreen == morphPair.end.screen) 1f - raw else raw
                        }
                        else -> 0f
                    }
                },
                pressShapeFraction = readPressShapeFraction
            )
        }
    }
}
