package com.example.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.MotionTokens
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Ambient per-screen seam connecting a screen's Primary Action Anchor and any press-linked
 * chrome to the enclosing [NavigationTransitionHost]. Defaults to [NoOp] so screens remain
 * renderable standalone in previews and tests.
 */
@Stable
internal class PrimaryActionAnchor(
    val interactionSource: MutableInteractionSource? = null,
    val pressFraction: (() -> Float)? = null,
    val isHidden: () -> Boolean = { false },
    val onBoundsPlaced: ((LayoutCoordinates) -> Unit)? = null,
    val onSettledChanged: ((Boolean) -> Unit)? = null
) {
    companion object {
        val NoOp = PrimaryActionAnchor()
    }
}

internal val LocalPrimaryActionAnchor = compositionLocalOf { PrimaryActionAnchor.NoOp }

/**
 * Test seam allowing standalone screen tests to observe settled Primary Action Anchor bounds
 * or drive press-spring progress without mounting [NavigationTransitionHost] or [MainActivity].
 */
@Composable
internal fun ProvidePrimaryActionAnchorForTest(
    interactionSource: MutableInteractionSource? = null,
    pressFraction: (() -> Float)? = null,
    isHidden: () -> Boolean = { false },
    onBoundsPlaced: ((LayoutCoordinates) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val anchor = remember(interactionSource, pressFraction, isHidden, onBoundsPlaced) {
        PrimaryActionAnchor(
            interactionSource = interactionSource,
            pressFraction = pressFraction,
            isHidden = isHidden,
            onBoundsPlaced = onBoundsPlaced
        )
    }
    CompositionLocalProvider(LocalPrimaryActionAnchor provides anchor, content = content)
}

/**
 * Captures a Primary Action Anchor's unscaled layout bounds along with the root container size
 * so stale coordinates from an earlier orientation or window resize are never reused.
 */
@Immutable
internal data class MeasuredAnchorBounds(
    val rect: Rect,
    val containerSize: IntSize
)

/**
 * Hoisted press-shape spring shared by the start Primary Action Anchor, end Primary Action Anchor,
 * and the Morph Overlay. Keeping the spring above individual screens ensures releasing the button
 * to trigger navigation never cancels or snaps its Material 3 Expressive corner-radius animation.
 */
@Stable
internal class PrimaryActionPressState(
    val startInteractionSource: MutableInteractionSource,
    val endInteractionSource: MutableInteractionSource,
    private val pressFractionAnimatable: Animatable<Float, AnimationVector1D>
) {
    val pressFraction: Float
        get() = pressFractionAnimatable.value
}

@Composable
internal fun rememberPrimaryActionPressState(): PrimaryActionPressState {
    val startInteractionSource = remember { MutableInteractionSource() }
    val endInteractionSource = remember { MutableInteractionSource() }
    val pressFractionAnimatable = remember { Animatable(0f) }
    val state = remember(startInteractionSource, endInteractionSource, pressFractionAnimatable) {
        PrimaryActionPressState(
            startInteractionSource = startInteractionSource,
            endInteractionSource = endInteractionSource,
            pressFractionAnimatable = pressFractionAnimatable
        )
    }

    val isStartPressed by startInteractionSource.collectIsPressedAsState()
    val isEndPressed by endInteractionSource.collectIsPressedAsState()
    val isPressed = isStartPressed || isEndPressed
    val reducedMotion = LocalReducedMotion.current

    LaunchedEffect(isPressed, reducedMotion) {
        pressFractionAnimatable.animateTo(
            targetValue = if (isPressed) 1f else 0f,
            animationSpec = if (isPressed || reducedMotion) {
                MotionTokens.pressInSpec()
            } else {
                MotionTokens.pressReleaseSpring()
            }
        )
    }

    return state
}

private class CoordinatesRef(var value: LayoutCoordinates? = null)

/**
 * Primary Material 3 Expressive action button that automatically attaches as the hosting screen's
 * Primary Action Anchor when rendered inside [NavigationTransitionHost], sharing its press spring
 * and resting bounds with the Morph Overlay. Falls back cleanly to a standalone Expressive button
 * when unhosted.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PrimaryActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isSettled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val anchor = LocalPrimaryActionAnchor.current
    val defaultShapes = ButtonDefaults.shapes()
    val reducedMotion = LocalReducedMotion.current
    val latestIsSettled = rememberUpdatedState(isSettled)
    val latestCoordsRef = remember { CoordinatesRef() }

    val onBoundsPlaced = anchor.onBoundsPlaced
    val onSettledChanged = anchor.onSettledChanged

    LaunchedEffect(isSettled, onBoundsPlaced, onSettledChanged) {
        onSettledChanged?.invoke(isSettled)
        if (isSettled && onBoundsPlaced != null) {
            val coords = latestCoordsRef.value
            if (coords != null && coords.isAttached) {
                onBoundsPlaced(coords)
            }
        }
    }

    val boundsModifier = if (onBoundsPlaced != null) {
        Modifier.onGloballyPositioned { coords ->
            latestCoordsRef.value = coords
            if (latestIsSettled.value) {
                onBoundsPlaced(coords)
            }
        }
    } else {
        Modifier
    }

    val interactionSource = anchor.interactionSource
    val pressFraction = anchor.pressFraction
    if (interactionSource != null && pressFraction != null) {
        val restingShape = (defaultShapes.shape as? CornerBasedShape) ?: CircleShape
        val pressedShape = (defaultShapes.pressedShape as? CornerBasedShape) ?: MaterialTheme.shapes.medium
        val dynamicShape = remember(restingShape, pressedShape, pressFraction) {
            DynamicPressMorphShape(
                restingShape = restingShape,
                pressedShape = pressedShape,
                pressFraction = pressFraction
            )
        }
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = dynamicShape,
            contentPadding = ButtonDefaults.MediumContentPadding,
            interactionSource = interactionSource,
            modifier = modifier
                .then(boundsModifier)
                .graphicsLayer {
                    alpha = if (anchor.isHidden()) 0f else 1f
                    val scale = computePressScale(pressFraction(), reducedMotion)
                    scaleX = scale
                    scaleY = scale
                },
            content = content
        )
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            shapes = defaultShapes,
            contentPadding = ButtonDefaults.MediumContentPadding,
            modifier = modifier
                .then(boundsModifier)
                .graphicsLayer {
                    alpha = if (anchor.isHidden()) 0f else 1f
                },
            content = content
        )
    }
}

/**
 * Links an element's scale and subtle tilt to the hosting screen's Primary Action Anchor press
 * spring strictly in the draw phase (zero recompositions). No-op when unhosted or when
 * [LocalReducedMotion] is enabled.
 */
@Composable
fun Modifier.linkToPrimaryActionPress(tiltDegrees: Float = -3f): Modifier {
    val reducedMotion = LocalReducedMotion.current
    if (reducedMotion) return this
    val readPressFraction = LocalPrimaryActionAnchor.current.pressFraction ?: return this
    return this.graphicsLayer {
        val fraction = clampPressFraction(readPressFraction())
        val scale = computePressScale(fraction, reducedMotion = false)
        scaleX = scale
        scaleY = scale
        rotationZ = tiltDegrees * fraction
    }
}

private const val PressReboundMinFraction = -0.12f
private const val PressMaxFraction = 1.0f

private fun clampPressFraction(pressFraction: Float): Float =
    pressFraction.coerceIn(PressReboundMinFraction, PressMaxFraction)

internal fun lerpUnclamped(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

internal fun computePressScale(
    pressFraction: Float,
    reducedMotion: Boolean
): Float {
    if (reducedMotion) return 1f
    val clamped = clampPressFraction(pressFraction)
    return lerpUnclamped(1f, MotionTokens.PressScale, clamped)
}

/**
 * Single consolidated corner-morph [Shape] shared by [PrimaryActionButton] and [MorphOverlay].
 * Defers reading [pressFraction] to [createOutline] so press-and-release springs trigger zero
 * recompositions.
 */
@Stable
internal class DynamicPressMorphShape(
    private val restingShape: CornerBasedShape,
    private val pressedShape: CornerBasedShape,
    private val pressFraction: () -> Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val fraction = pressFraction()
        val maxRadius = (minOf(size.width, size.height) / 2f).coerceAtLeast(0f)
        fun radius(start: CornerSize, end: CornerSize): Float =
            lerpUnclamped(
                start = start.toPx(size, density),
                stop = end.toPx(size, density),
                fraction = fraction
            ).coerceIn(0f, maxRadius)

        val topStart = radius(restingShape.topStart, pressedShape.topStart)
        val topEnd = radius(restingShape.topEnd, pressedShape.topEnd)
        val bottomEnd = radius(restingShape.bottomEnd, pressedShape.bottomEnd)
        val bottomStart = radius(restingShape.bottomStart, pressedShape.bottomStart)

        val leftTop = if (layoutDirection == LayoutDirection.Ltr) topStart else topEnd
        val rightTop = if (layoutDirection == LayoutDirection.Ltr) topEnd else topStart
        val rightBottom = if (layoutDirection == LayoutDirection.Ltr) bottomEnd else bottomStart
        val leftBottom = if (layoutDirection == LayoutDirection.Ltr) bottomStart else bottomEnd

        return Outline.Rounded(
            RoundRect(
                rect = Rect(Offset.Zero, size),
                topLeft = CornerRadius(leftTop, leftTop),
                topRight = CornerRadius(rightTop, rightTop),
                bottomRight = CornerRadius(rightBottom, rightBottom),
                bottomLeft = CornerRadius(leftBottom, leftBottom)
            )
        )
    }
}

private class PrecomputedOutlineShape(private val outline: Outline) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = outline
}

internal fun computeRelativeAnchorRect(
    screenContainer: LayoutCoordinates?,
    anchorCoords: LayoutCoordinates?
): Rect? {
    if (screenContainer == null || anchorCoords == null) return null
    if (!screenContainer.isAttached || !anchorCoords.isAttached) return null
    val rect = screenContainer.localBoundingBoxOf(anchorCoords, clipBounds = false)
    return rect.takeIf { it.width > 0f && it.height > 0f }
}

private fun interpolateMorphRect(
    startBounds: Rect,
    endBounds: Rect,
    spatialProgress: Float
): Rect {
    val left = lerpUnclamped(startBounds.left, endBounds.left, spatialProgress)
    val top = lerpUnclamped(startBounds.top, endBounds.top, spatialProgress)
    val width = lerpUnclamped(startBounds.width, endBounds.width, spatialProgress).coerceAtLeast(1f)
    val height = lerpUnclamped(startBounds.height, endBounds.height, spatialProgress).coerceAtLeast(1f)
    return Rect(left = left, top = top, right = left + width, bottom = top + height)
}

private data class MorphContentVisuals(
    val startAlpha: Float,
    val endAlpha: Float,
    val startScale: Float,
    val endScale: Float,
    val midFlightArc: Float
)

private fun computeMorphContentVisuals(progress: Float): MorphContentVisuals {
    val clamped = progress.coerceIn(0f, 1f)
    val startExitFraction = (clamped / MotionTokens.MorphPasteExitEnd).coerceIn(0f, 1f)
    val endEnterFraction = (
        (clamped - MotionTokens.MorphCopyEnterStart) / MotionTokens.MorphCopyEnterSpan
    ).coerceIn(0f, 1f)
    val midFlightArc = sin(clamped * PI).toFloat().coerceIn(0f, 1f)
    return MorphContentVisuals(
        startAlpha = 1f - startExitFraction,
        endAlpha = endEnterFraction,
        startScale = lerpUnclamped(1f, MotionTokens.ContentSwapMinScale, startExitFraction),
        endScale = lerpUnclamped(MotionTokens.ContentSwapMinScale, 1f, endEnterFraction),
        midFlightArc = midFlightArc
    )
}

/**
 * Transient mid-flight Morph Overlay rendered above transitioning screens between two
 * Primary Action Anchors. Reads all per-frame progress in layout and draw phases.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <K> MorphOverlay(
    morphPair: MorphPairSpec<K>,
    startBounds: Rect,
    endBounds: Rect,
    spatialProgress: () -> Float,
    effectsProgress: () -> Float,
    pressShapeFraction: () -> Float,
    modifier: Modifier = Modifier
) {
    val buttonColors = ButtonDefaults.buttonColors()
    val defaultShapes = ButtonDefaults.shapes()
    val reducedMotion = LocalReducedMotion.current
    val layoutDirection = LocalLayoutDirection.current
    val restingShape = (defaultShapes.shape as? CornerBasedShape) ?: CircleShape
    val pressedShape = (defaultShapes.pressedShape as? CornerBasedShape) ?: MaterialTheme.shapes.medium
    val dynamicShape = remember(restingShape, pressedShape, pressShapeFraction) {
        DynamicPressMorphShape(
            restingShape = restingShape,
            pressedShape = pressedShape,
            pressFraction = pressShapeFraction
        )
    }

    val startContainerColor = if (morphPair.start.enabled) {
        buttonColors.containerColor
    } else {
        buttonColors.disabledContainerColor
    }
    val startContentColor = if (morphPair.start.enabled) {
        buttonColors.contentColor
    } else {
        buttonColors.disabledContentColor
    }
    val endContainerColor = if (morphPair.end.enabled) {
        buttonColors.containerColor
    } else {
        buttonColors.disabledContainerColor
    }
    val endContentColor = if (morphPair.end.enabled) {
        buttonColors.contentColor
    } else {
        buttonColors.disabledContentColor
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .layout { measurable, _ ->
                    val rect = interpolateMorphRect(
                        startBounds = startBounds,
                        endBounds = endBounds,
                        spatialProgress = spatialProgress()
                    )
                    val widthPx = rect.width.roundToInt().coerceAtLeast(1)
                    val heightPx = rect.height.roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx))
                    layout(widthPx, heightPx) {
                        placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
                    }
                }
                .testTag("primary_action_morph_overlay")
                .graphicsLayer {
                    val visuals = computeMorphContentVisuals(effectsProgress().coerceIn(0f, 1f))
                    val pressFraction = pressShapeFraction()
                    val pressScale = computePressScale(pressFraction, reducedMotion)
                    scaleX = pressScale
                    scaleY = pressScale
                    // Reuse DynamicPressMorphShape so button and overlay share one corner-morph
                    // implementation, wrapping the frame's outline so graphicsLayer invalidates
                    // even when start and end anchors have identical dimensions.
                    shape = PrecomputedOutlineShape(
                        dynamicShape.createOutline(
                            size = size,
                            layoutDirection = layoutDirection,
                            density = this
                        )
                    )
                    clip = true
                    shadowElevation = MotionTokens.MorphMidFlightElevation.toPx() * visuals.midFlightArc
                }
                .drawBehind {
                    val colorFraction = effectsProgress().coerceIn(0f, 1f)
                    drawRect(
                        color = lerpColor(startContainerColor, endContainerColor, colorFraction)
                    )
                }
        ) {
            CompositionLocalProvider(LocalContentColor provides startContentColor) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(ButtonDefaults.MediumContentPadding)
                        .graphicsLayer {
                            val clamped = effectsProgress().coerceIn(0f, 1f)
                            val visuals = computeMorphContentVisuals(clamped)
                            alpha = visuals.startAlpha
                            scaleX = visuals.startScale
                            scaleY = visuals.startScale
                            translationY = MotionTokens.MorphContentVerticalTravel.toPx() * clamped
                        }
                ) {
                    Icon(
                        imageVector = morphPair.start.icon,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.MediumIconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.MediumIconSpacing))
                    Text(
                        text = morphPair.start.label,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }

            CompositionLocalProvider(LocalContentColor provides endContentColor) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(ButtonDefaults.MediumContentPadding)
                        .graphicsLayer {
                            val clamped = effectsProgress().coerceIn(0f, 1f)
                            val visuals = computeMorphContentVisuals(clamped)
                            alpha = visuals.endAlpha
                            scaleX = visuals.endScale
                            scaleY = visuals.endScale
                            translationY = -MotionTokens.MorphContentVerticalTravel.toPx() * (1f - clamped)
                        }
                ) {
                    Icon(
                        imageVector = morphPair.end.icon,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.MediumIconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.MediumIconSpacing))
                    Text(
                        text = morphPair.end.label,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}
