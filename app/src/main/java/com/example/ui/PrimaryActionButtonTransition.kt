package com.example.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
 * Captures a button's unscaled layout bounds along with the root container size
 * so stale coordinates from an earlier orientation or window resize are never reused.
 */
@Immutable
internal data class MeasuredButtonBounds(
    val rect: Rect,
    val containerSize: IntSize
)

/**
 * Hoisted press-shape spring shared by the Paste button, Copy button, and morph overlay.
 * Keeping the spring above individual screens ensures releasing the button to trigger
 * navigation never cancels or snaps its Material 3 Expressive corner-radius animation.
 */
@Stable
class PrimaryActionPressState internal constructor(
    val pasteInteractionSource: MutableInteractionSource,
    val copyInteractionSource: MutableInteractionSource,
    private val pressFractionAnimatable: Animatable<Float, AnimationVector1D>
) {
    val pressFraction: Float
        get() = pressFractionAnimatable.value
}

@Composable
fun rememberPrimaryActionPressState(): PrimaryActionPressState {
    val pasteInteractionSource = remember { MutableInteractionSource() }
    val copyInteractionSource = remember { MutableInteractionSource() }
    val pressFractionAnimatable = remember { Animatable(0f) }
    val state = remember(pasteInteractionSource, copyInteractionSource, pressFractionAnimatable) {
        PrimaryActionPressState(
            pasteInteractionSource = pasteInteractionSource,
            copyInteractionSource = copyInteractionSource,
            pressFractionAnimatable = pressFractionAnimatable
        )
    }

    val isPastePressed by pasteInteractionSource.collectIsPressedAsState()
    val isCopyPressed by copyInteractionSource.collectIsPressedAsState()
    val isPressed = isPastePressed || isCopyPressed
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

private data class InterpolatedCornerSize(
    private val start: CornerSize,
    private val end: CornerSize,
    private val fraction: Float
) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val maxRadius = (minOf(shapeSize.width, shapeSize.height) / 2f).coerceAtLeast(0f)
        return lerpUnclamped(
            start = start.toPx(shapeSize, density),
            stop = end.toPx(shapeSize, density),
            fraction = fraction
        ).coerceIn(0f, maxRadius)
    }
}

internal fun interpolatedCornerShape(
    restingShape: CornerBasedShape,
    pressedShape: CornerBasedShape,
    pressFraction: Float
): RoundedCornerShape = RoundedCornerShape(
    topStart = InterpolatedCornerSize(restingShape.topStart, pressedShape.topStart, pressFraction),
    topEnd = InterpolatedCornerSize(restingShape.topEnd, pressedShape.topEnd, pressFraction),
    bottomEnd = InterpolatedCornerSize(restingShape.bottomEnd, pressedShape.bottomEnd, pressFraction),
    bottomStart = InterpolatedCornerSize(restingShape.bottomStart, pressedShape.bottomStart, pressFraction)
)

internal fun computePressMorphCornerShape(
    restingShape: CornerBasedShape,
    pressedShape: CornerBasedShape,
    pressFraction: Float,
    size: Size,
    density: Density
): RoundedCornerShape {
    val maxRadius = (minOf(size.width, size.height) / 2f).coerceAtLeast(0f)
    fun corner(start: CornerSize, end: CornerSize): Float =
        lerpUnclamped(
            start = start.toPx(size, density),
            stop = end.toPx(size, density),
            fraction = pressFraction
        ).coerceIn(0f, maxRadius)

    return RoundedCornerShape(
        topStart = corner(restingShape.topStart, pressedShape.topStart),
        topEnd = corner(restingShape.topEnd, pressedShape.topEnd),
        bottomEnd = corner(restingShape.bottomEnd, pressedShape.bottomEnd),
        bottomStart = corner(restingShape.bottomStart, pressedShape.bottomStart)
    )
}

internal fun computePressScale(
    pressFraction: Float,
    reducedMotion: Boolean
): Float {
    if (reducedMotion) return 1f
    // Allow tiny release rebound (-0.12f -> ~1.004f) while preventing overshoot compression below 0.97f.
    val clamped = pressFraction.coerceIn(-0.12f, 1.0f)
    return lerpUnclamped(1f, MotionTokens.PressScale, clamped)
}

/**
 * Stable [Shape] that defers reading [pressFraction] to [createOutline] (draw/graphicsLayer phase),
 * eliminating per-frame recomposition of the host [Button] during press-and-release springs.
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

/**
 * Primary Material 3 Expressive action button that shares its corner-radius press spring
 * with the layout transition overlay when hosted in [MainActivity], preventing corner-radius
 * animations from cancelling or snapping when navigation starts on release.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressivePrimaryActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    interactionSource: MutableInteractionSource? = null,
    pressShapeFraction: (() -> Float)? = null,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val defaultShapes = ButtonDefaults.shapes()
    val reducedMotion = LocalReducedMotion.current
    if (interactionSource != null && pressShapeFraction != null) {
        val restingShape = (defaultShapes.shape as? CornerBasedShape) ?: CircleShape
        val pressedShape = (defaultShapes.pressedShape as? CornerBasedShape) ?: MaterialTheme.shapes.medium
        val dynamicShape = remember(restingShape, pressedShape, pressShapeFraction) {
            DynamicPressMorphShape(
                restingShape = restingShape,
                pressedShape = pressedShape,
                pressFraction = pressShapeFraction
            )
        }
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = dynamicShape,
            contentPadding = ButtonDefaults.MediumContentPadding,
            interactionSource = interactionSource,
            modifier = modifier.graphicsLayer {
                val scale = computePressScale(pressShapeFraction(), reducedMotion)
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
            modifier = modifier,
            content = content
        )
    }
}

internal fun computeRelativeButtonRect(
    screenContainer: LayoutCoordinates?,
    buttonCoords: LayoutCoordinates?
): Rect? {
    if (screenContainer == null || buttonCoords == null) return null
    if (!screenContainer.isAttached || !buttonCoords.isAttached) return null
    val rect = screenContainer.localBoundingBoxOf(buttonCoords, clipBounds = false)
    return rect.takeIf { it.width > 0f && it.height > 0f }
}

internal fun lerpUnclamped(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

/**
 * Interpolates the shared primary action button bounds between Paste (0f) and Copy (1f).
 * Intentionally allows natural spring overshoot from M3 Expressive spatial physics while
 * clamping resulting dimensions to positive values.
 */
internal fun interpolateMorphButtonRect(
    pasteBounds: Rect,
    copyBounds: Rect,
    spatialProgress: Float
): Rect {
    val left = lerpUnclamped(pasteBounds.left, copyBounds.left, spatialProgress)
    val top = lerpUnclamped(pasteBounds.top, copyBounds.top, spatialProgress)
    val width = lerpUnclamped(pasteBounds.width, copyBounds.width, spatialProgress).coerceAtLeast(1f)
    val height = lerpUnclamped(pasteBounds.height, copyBounds.height, spatialProgress).coerceAtLeast(1f)
    return Rect(left = left, top = top, right = left + width, bottom = top + height)
}

internal data class MorphContentVisuals(
    val pasteAlpha: Float,
    val copyAlpha: Float,
    val pasteScale: Float,
    val copyScale: Float,
    val midFlightArc: Float
)

/**
 * Computes symmetric fade-through alpha, subtle scale, and mid-flight arc for
 * Paste (0f) <-> Copy (1f) so both forward transitions and interactive Predictive Back
 * scrubbing remain continuous and reversible.
 */
internal fun computeMorphContentVisuals(progress: Float): MorphContentVisuals {
    val clamped = progress.coerceIn(0f, 1f)
    val pasteExitFraction = (clamped / MotionTokens.MorphPasteExitEnd).coerceIn(0f, 1f)
    val copyEnterFraction = (
        (clamped - MotionTokens.MorphCopyEnterStart) / MotionTokens.MorphCopyEnterSpan
    ).coerceIn(0f, 1f)
    val midFlightArc = sin(clamped * PI).toFloat().coerceIn(0f, 1f)
    return MorphContentVisuals(
        pasteAlpha = 1f - pasteExitFraction,
        copyAlpha = copyEnterFraction,
        pasteScale = lerpUnclamped(1f, MotionTokens.ContentSwapMinScale, pasteExitFraction),
        copyScale = lerpUnclamped(MotionTokens.ContentSwapMinScale, 1f, copyEnterFraction),
        midFlightArc = midFlightArc
    )
}

/**
 * Material 3 Expressive layout transition overlay that morphs the Paste primary button
 * into the Copy primary button (and back) across screens using expressive spatial spring
 * bounds, uninterrupted press/release corner-radius physics, subtle mid-flight elevation,
 * and directional content fade-through.
 *
 * All per-frame progress reads occur inside layout and draw phases so the transition runs
 * without per-frame recomposition.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveButtonMorphOverlay(
    pasteBounds: Rect,
    copyBounds: Rect,
    spatialProgress: () -> Float,
    effectsProgress: () -> Float,
    pressShapeFraction: () -> Float,
    pasteLabel: String,
    copyLabel: String,
    copyEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val buttonColors = ButtonDefaults.buttonColors()
    val defaultShapes = ButtonDefaults.shapes()
    val reducedMotion = LocalReducedMotion.current
    val restingShape = (defaultShapes.shape as? CornerBasedShape) ?: CircleShape
    val pressedShape = (defaultShapes.pressedShape as? CornerBasedShape) ?: MaterialTheme.shapes.medium
    val pasteContainerColor = buttonColors.containerColor
    val pasteContentColor = buttonColors.contentColor
    val copyContainerColor = if (copyEnabled) {
        buttonColors.containerColor
    } else {
        buttonColors.disabledContainerColor
    }
    val copyContentColor = if (copyEnabled) {
        buttonColors.contentColor
    } else {
        buttonColors.disabledContentColor
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .layout { measurable, _ ->
                    val rect = interpolateMorphButtonRect(
                        pasteBounds = pasteBounds,
                        copyBounds = copyBounds,
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
                    shape = computePressMorphCornerShape(
                        restingShape = restingShape,
                        pressedShape = pressedShape,
                        pressFraction = pressFraction,
                        size = size,
                        density = this
                    )
                    clip = true
                    shadowElevation = MotionTokens.MorphMidFlightElevation.toPx() * visuals.midFlightArc
                }
                .drawBehind {
                    val colorFraction = effectsProgress().coerceIn(0f, 1f)
                    drawRect(
                        color = lerpColor(pasteContainerColor, copyContainerColor, colorFraction)
                    )
                }
        ) {
            CompositionLocalProvider(LocalContentColor provides pasteContentColor) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(ButtonDefaults.MediumContentPadding)
                        .graphicsLayer {
                            val clamped = effectsProgress().coerceIn(0f, 1f)
                            val visuals = computeMorphContentVisuals(clamped)
                            alpha = visuals.pasteAlpha
                            scaleX = visuals.pasteScale
                            scaleY = visuals.pasteScale
                            translationY = MotionTokens.MorphContentVerticalTravel.toPx() * clamped
                        }
                ) {
                    Icon(
                        imageVector = AppIcons.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.MediumIconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.MediumIconSpacing))
                    Text(
                        text = pasteLabel,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }

            CompositionLocalProvider(LocalContentColor provides copyContentColor) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(ButtonDefaults.MediumContentPadding)
                        .graphicsLayer {
                            val clamped = effectsProgress().coerceIn(0f, 1f)
                            val visuals = computeMorphContentVisuals(clamped)
                            alpha = visuals.copyAlpha
                            scaleX = visuals.copyScale
                            scaleY = visuals.copyScale
                            translationY = -MotionTokens.MorphContentVerticalTravel.toPx() * (1f - clamped)
                        }
                ) {
                    Icon(
                        imageVector = AppIcons.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.MediumIconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.MediumIconSpacing))
                    Text(
                        text = copyLabel,
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
