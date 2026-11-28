package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.MotionTokens
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Snapshot of the soft keyboard's visibility and optional live descent progress (`0f..1f`).
 *
 * When [dismissProgress] is non-null, the keyboard is actively descending—either interactively
 * under the user's finger during an Android predictive back gesture or during a synchronized
 * IME exit animation—so Preview controls can scrub in 1:1 instead of waiting for the IME to finish.
 */
@Immutable
internal data class PreviewImeTransitionState(
    val isKeyboardOpen: Boolean,
    val dismissProgress: Float? = null
)

internal enum class PreviewKeyboardRevealOrientation {
    Vertical,
    Horizontal
}

/**
 * Tracks live IME inset height against the settled open keyboard height to derive a
 * continuous `0f..1f` dismiss/scrub progress during predictive back gestures and synchronized
 * keyboard exits, while ignoring keyboard openings and dynamic keyboard height resizes (such as
 * switching to an emoji or symbol pane).
 */
internal class ImeDismissProgressTracker {
    private var openImeHeightPx: Int = 0
    private var lastIsImeVisible: Boolean? = null
    private var lastImeBottomPx: Int = -1
    private var lastImeTargetBottomPx: Int = -1
    private var lastResult: PreviewImeTransitionState =
        PreviewImeTransitionState(isKeyboardOpen = false, dismissProgress = null)

    fun update(
        isImeVisible: Boolean,
        imeBottomPx: Int,
        imeTargetBottomPx: Int
    ): PreviewImeTransitionState {
        if (
            lastIsImeVisible == isImeVisible &&
            lastImeBottomPx == imeBottomPx &&
            lastImeTargetBottomPx == imeTargetBottomPx
        ) {
            return lastResult
        }

        val clampedBottom = imeBottomPx.coerceAtLeast(0)
        val clampedTarget = imeTargetBottomPx.coerceAtLeast(0)

        val result = when {
            // Fully closed and settled.
            !isImeVisible && clampedBottom == 0 && clampedTarget == 0 -> {
                openImeHeightPx = 0
                PreviewImeTransitionState(
                    isKeyboardOpen = false,
                    dismissProgress = null
                )
            }

            // Committed to closing while the keyboard still occupies screen space:
            // track its descent 1:1 from openImeHeightPx down to 0.
            !isImeVisible -> {
                val referenceHeight = maxOf(openImeHeightPx, clampedBottom)
                openImeHeightPx = referenceHeight
                if (referenceHeight > 0 && clampedBottom > 0) {
                    val progress = ((referenceHeight - clampedBottom).toFloat() / referenceHeight)
                        .coerceIn(0f, 1f)
                    PreviewImeTransitionState(
                        isKeyboardOpen = false,
                        dismissProgress = progress
                    )
                } else {
                    openImeHeightPx = 0
                    PreviewImeTransitionState(
                        isKeyboardOpen = false,
                        dismissProgress = null
                    )
                }
            }

            // Opening, settled open, or resizing to a different open keyboard height:
            // keep controls hidden without triggering a false reveal peek.
            clampedBottom >= openImeHeightPx ||
                (clampedTarget > 0 && clampedBottom >= clampedTarget) -> {
                openImeHeightPx = clampedBottom
                PreviewImeTransitionState(
                    isKeyboardOpen = true,
                    dismissProgress = null
                )
            }

            // Keyboard was open and is actively descending below its open and target height
            // (e.g. Android predictive back IME scrub via ImeBackAnimationController).
            else -> {
                val progress = ((openImeHeightPx - clampedBottom).toFloat() / openImeHeightPx)
                    .coerceIn(0f, 1f)
                PreviewImeTransitionState(
                    isKeyboardOpen = true,
                    dismissProgress = progress
                )
            }
        }

        lastIsImeVisible = isImeVisible
        lastImeBottomPx = imeBottomPx
        lastImeTargetBottomPx = imeTargetBottomPx
        lastResult = result
        return result
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberPreviewImeTransitionState(): PreviewImeTransitionState {
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeTargetInsets = WindowInsets.imeAnimationTarget
    val latestIsImeVisible = rememberUpdatedState(WindowInsets.isImeVisible)
    val tracker = remember { ImeDismissProgressTracker() }

    val state by remember(imeInsets, imeTargetInsets, density, tracker) {
        derivedStateOf {
            tracker.update(
                isImeVisible = latestIsImeVisible.value,
                imeBottomPx = imeInsets.getBottom(density),
                imeTargetBottomPx = imeTargetInsets.getBottom(density)
            )
        }
    }
    return state
}

private const val VisibilityMountEpsilon = 0.001f
private const val SettledVisibleEpsilon = 0.999f
private const val LiveScrubAlphaGain = 1.6f

/**
 * Animates Preview action chrome (portrait footer or landscape side panel) out crisply when
 * the keyboard opens, scrubs it back in 1:1 with predictive back / synchronized IME dismissal
 * when [imeDismissProgress] is provided, and falls back to the M3 Expressive fast spring
 * when the keyboard toggles without intermediate inset frames.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PreviewKeyboardAnimatedContainer(
    isKeyboardOpen: Boolean,
    imeDismissProgress: Float?,
    orientation: PreviewKeyboardRevealOrientation,
    offscreenClearancePx: Int,
    scrubPeekOffsetPx: Int,
    modifier: Modifier = Modifier,
    content: @Composable (isSettledVisible: Boolean) -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme
    val reducedMotion = LocalReducedMotion.current

    val initialVisibility = when {
        imeDismissProgress != null -> imeDismissProgress.coerceIn(0f, 1f)
        isKeyboardOpen -> 0f
        else -> 1f
    }
    val spatialProgress = remember {
        Animatable(if (reducedMotion && initialVisibility > 0f) 1f else initialVisibility)
    }
    val alphaProgress = remember {
        Animatable(
            if (imeDismissProgress != null && !reducedMotion) {
                (initialVisibility * LiveScrubAlphaGain).coerceIn(0f, 1f)
            } else {
                initialVisibility
            }
        )
    }

    // Preserve the scrub clearance offset during any handoff from a live IME descent
    // until the container settles, avoiding a mid-flight jump in slide clearance.
    var useScrubClearance by remember { mutableStateOf(imeDismissProgress != null) }

    val currentKeyboardOpen by rememberUpdatedState(isKeyboardOpen)
    val currentDismissProgress by rememberUpdatedState(imeDismissProgress)
    val currentReducedMotion by rememberUpdatedState(reducedMotion)
    val currentEnterSpatialSpec by rememberUpdatedState(motionScheme.fastSpatialSpec<Float>())

    LaunchedEffect(Unit) {
        snapshotFlow {
            Triple(currentKeyboardOpen, currentDismissProgress, currentReducedMotion)
        }.collectLatest { (keyboardOpen, dismissProgress, isReduced) ->
            if (dismissProgress != null) {
                useScrubClearance = true
                val clamped = dismissProgress.coerceIn(0f, 1f)
                val targetAlpha = if (isReduced) {
                    clamped
                } else {
                    (clamped * LiveScrubAlphaGain).coerceIn(0f, 1f)
                }
                spatialProgress.snapTo(if (isReduced) 1f else clamped)
                alphaProgress.snapTo(targetAlpha)
            } else if (keyboardOpen) {
                if (isReduced) {
                    if (alphaProgress.value != 0f) {
                        alphaProgress.animateTo(0f, MotionTokens.reducedFadeSpec())
                    }
                    if (spatialProgress.value != 0f) {
                        spatialProgress.snapTo(0f)
                    }
                } else {
                    coroutineScope {
                        if (spatialProgress.value != 0f) {
                            launch {
                                spatialProgress.animateTo(0f, MotionTokens.fastUiSpec())
                            }
                        }
                        if (alphaProgress.value != 0f) {
                            launch {
                                alphaProgress.animateTo(0f, MotionTokens.quickExitSpec())
                            }
                        }
                    }
                }
                useScrubClearance = false
            } else {
                if (isReduced) {
                    if (spatialProgress.value != 1f) {
                        spatialProgress.snapTo(1f)
                    }
                    if (alphaProgress.value != 1f) {
                        alphaProgress.animateTo(1f, MotionTokens.reducedFadeSpec())
                    }
                } else {
                    coroutineScope {
                        if (spatialProgress.value != 1f) {
                            launch {
                                spatialProgress.animateTo(1f, currentEnterSpatialSpec)
                            }
                        }
                        if (alphaProgress.value != 1f) {
                            launch {
                                alphaProgress.animateTo(1f, MotionTokens.fastUiSpec())
                            }
                        }
                    }
                }
                useScrubClearance = false
            }
        }
    }

    val isContentComposed by remember {
        derivedStateOf {
            val dismiss = currentDismissProgress
            !currentKeyboardOpen ||
                (dismiss != null && dismiss > VisibilityMountEpsilon) ||
                spatialProgress.value > VisibilityMountEpsilon ||
                alphaProgress.value > VisibilityMountEpsilon
        }
    }

    val isSettledVisible by remember {
        derivedStateOf {
            !currentKeyboardOpen &&
                currentDismissProgress == null &&
                !spatialProgress.isRunning &&
                !alphaProgress.isRunning &&
                spatialProgress.value >= SettledVisibleEpsilon &&
                alphaProgress.value >= SettledVisibleEpsilon
        }
    }

    if (!isContentComposed) return

    Box(
        modifier = modifier.layout { measurable, constraints ->
            val childConstraints = when (orientation) {
                PreviewKeyboardRevealOrientation.Vertical -> constraints.copy(minHeight = 0)
                PreviewKeyboardRevealOrientation.Horizontal -> constraints.copy(minWidth = 0)
            }
            val placeable = measurable.measure(childConstraints)
            val activeDismiss = currentDismissProgress?.coerceIn(0f, 1f)
            val fraction = when {
                activeDismiss != null && reducedMotion -> 1f
                activeDismiss != null -> activeDismiss
                else -> spatialProgress.value
            }
            val clampedFraction = fraction.coerceIn(0f, 1f)
            val clearance = if (activeDismiss != null || useScrubClearance) {
                scrubPeekOffsetPx
            } else {
                offscreenClearancePx
            }

            when (orientation) {
                PreviewKeyboardRevealOrientation.Vertical -> {
                    val layoutHeight = if (reducedMotion) {
                        placeable.height
                    } else {
                        (placeable.height * clampedFraction).roundToInt()
                    }
                    val offsetY = if (reducedMotion) {
                        0
                    } else {
                        ((1f - fraction) * clearance).roundToInt()
                    }
                    layout(placeable.width, layoutHeight) {
                        placeable.placeRelativeWithLayer(x = 0, y = offsetY) {
                            val liveDismiss = currentDismissProgress?.coerceIn(0f, 1f)
                            alpha = when {
                                liveDismiss != null && reducedMotion -> liveDismiss
                                liveDismiss != null -> (liveDismiss * LiveScrubAlphaGain)
                                else -> alphaProgress.value
                            }.coerceIn(0f, 1f)
                        }
                    }
                }

                PreviewKeyboardRevealOrientation.Horizontal -> {
                    val layoutWidth = if (reducedMotion) {
                        placeable.width
                    } else {
                        (placeable.width * clampedFraction).roundToInt()
                    }
                    val offsetX = if (reducedMotion) {
                        0
                    } else {
                        ((1f - fraction) * clearance).roundToInt()
                    }
                    layout(layoutWidth, placeable.height) {
                        placeable.placeRelativeWithLayer(x = offsetX, y = 0) {
                            val liveDismiss = currentDismissProgress?.coerceIn(0f, 1f)
                            alpha = when {
                                liveDismiss != null && reducedMotion -> liveDismiss
                                liveDismiss != null -> (liveDismiss * LiveScrubAlphaGain)
                                else -> alphaProgress.value
                            }.coerceIn(0f, 1f)
                        }
                    }
                }
            }
        }
    ) {
        content(isSettledVisible)
    }
}
