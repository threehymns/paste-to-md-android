package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp

/**
 * Centralized Crisp M3 Expressive motion tokens shared across navigation,
 * primary action transitions, keyboard layout changes, and feedback surfaces.
 */
object MotionTokens {
    // Curves (Emil Kowalski strong UI curves + Android predictive back gesture curve)
    val EaseOut: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val EaseInOut: Easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    val EaseDrawer: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    val PredictiveBackGestureEasing: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

    // Durations (UI motion stays under 300ms)
    const val DurationPressMs: Int = 130
    const val DurationQuickExitMs: Int = 150
    const val DurationReducedFadeMs: Int = 160
    const val DurationFastUiMs: Int = 180
    const val DurationStandardUiMs: Int = 240
    const val DurationCopyConfirmHoldMs: Long = 1400L

    // Physical scale targets (never 0f)
    const val PressScale: Float = 0.97f
    const val ContentSwapMinScale: Float = 0.92f
    const val SnackbarEnterScale: Float = 0.96f

    // Screen fade-through transition parameters
    const val ScreenFadeThroughThreshold: Float = 0.35f
    const val BackExitScaleTarget: Float = 0.9f
    const val BackEnterScaleStart: Float = 1.1f
    const val ForwardExitScaleTarget: Float = 0.96f
    const val ForwardEnterScaleStart: Float = 0.94f

    // Non-overlapping Paste <-> Copy morph content windows
    const val MorphPasteExitEnd: Float = 0.38f
    const val MorphCopyEnterStart: Float = 0.42f
    const val MorphCopyEnterSpan: Float = 0.43f
    val MorphMidFlightElevation = 6.dp
    val MorphContentVerticalTravel = 8.dp

    // Gesture dismissal threshold (0.11 px/ms == 110 px/s)
    const val SwipeDismissVelocityPxPerMs: Float = 0.11f

    fun <T> pressInSpec(): TweenSpec<T> = tween(
        durationMillis = DurationPressMs,
        easing = EaseOut
    )

    fun <T> quickExitSpec(): TweenSpec<T> = tween(
        durationMillis = DurationQuickExitMs,
        easing = EaseOut
    )

    fun <T> fastUiSpec(): TweenSpec<T> = tween(
        durationMillis = DurationFastUiMs,
        easing = EaseOut
    )

    fun <T> reducedFadeSpec(): TweenSpec<T> = tween(
        durationMillis = DurationReducedFadeMs,
        easing = EaseOut
    )

    fun <T> pressReleaseSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.72f,
        stiffness = 700f
    )

    fun <T> snackbarSnapbackSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.78f,
        stiffness = 420f
    )
}
