package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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

private const val PasteExitEnd = 0.42f
private const val CopyEnterStart = 0.34f
private const val CopyEnterSpan = 0.46f
private const val ContentMinScale = 0.92f
private val ExpressiveMidFlightCornerRadius = 18.dp
private val ExpressiveMidFlightElevation = 6.dp
private val ContentVerticalTravel = 8.dp

/**
 * Computes symmetric fade-through alpha, subtle scale, and mid-flight arc for
 * Paste (0f) <-> Copy (1f) so both forward transitions and interactive Predictive Back
 * scrubbing remain continuous and reversible.
 */
internal fun computeMorphContentVisuals(progress: Float): MorphContentVisuals {
    val clamped = progress.coerceIn(0f, 1f)
    val pasteExitFraction = (clamped / PasteExitEnd).coerceIn(0f, 1f)
    val copyEnterFraction = ((clamped - CopyEnterStart) / CopyEnterSpan).coerceIn(0f, 1f)
    val midFlightArc = sin(clamped * PI).toFloat().coerceIn(0f, 1f)
    return MorphContentVisuals(
        pasteAlpha = 1f - pasteExitFraction,
        copyAlpha = copyEnterFraction,
        pasteScale = lerpUnclamped(1f, ContentMinScale, pasteExitFraction),
        copyScale = lerpUnclamped(ContentMinScale, 1f, copyEnterFraction),
        midFlightArc = midFlightArc
    )
}

/**
 * Material 3 Expressive layout transition overlay that morphs the Paste primary button
 * into the Copy primary button (and back) across screens using expressive spatial spring
 * bounds, subtle mid-flight shape/elevation morphing, and directional content fade-through.
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
    pasteLabel: String,
    copyLabel: String,
    copyEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val buttonColors = ButtonDefaults.buttonColors()
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
                .testTag("primary_action_morph_overlay")
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
                .graphicsLayer {
                    val visuals = computeMorphContentVisuals(spatialProgress())
                    val restingPillRadiusPx = size.height / 2f
                    val expressiveSquircleRadiusPx = ExpressiveMidFlightCornerRadius.toPx()
                        .coerceAtMost(restingPillRadiusPx)
                    val cornerRadiusPx = lerpUnclamped(
                        start = restingPillRadiusPx,
                        stop = expressiveSquircleRadiusPx,
                        fraction = visuals.midFlightArc * 0.7f
                    )
                    shape = RoundedCornerShape(cornerRadiusPx)
                    clip = true
                    shadowElevation = ExpressiveMidFlightElevation.toPx() * visuals.midFlightArc
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
                            val clamped = spatialProgress().coerceIn(0f, 1f)
                            val visuals = computeMorphContentVisuals(clamped)
                            alpha = visuals.pasteAlpha
                            scaleX = visuals.pasteScale
                            scaleY = visuals.pasteScale
                            translationY = ContentVerticalTravel.toPx() * clamped
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
                            val clamped = spatialProgress().coerceIn(0f, 1f)
                            val visuals = computeMorphContentVisuals(clamped)
                            alpha = visuals.copyAlpha
                            scaleX = visuals.copyScale
                            scaleY = visuals.copyScale
                            translationY = -ContentVerticalTravel.toPx() * (1f - clamped)
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
