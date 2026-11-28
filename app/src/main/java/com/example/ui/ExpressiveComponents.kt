package com.example.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.MotionTokens
import kotlinx.coroutines.launch
import kotlin.math.abs

internal fun shouldDismissSnackbarDrag(
    offsetPx: Float,
    elapsedMs: Long,
    containerWidthPx: Int
): Boolean {
    val absOffset = abs(offsetPx)
    if (absOffset < 1f) return false
    val safeElapsed = elapsedMs.coerceAtLeast(1L)
    val velocityPxPerMs = absOffset / safeElapsed.toFloat()
    val distanceThreshold = if (containerWidthPx > 0) containerWidthPx * 0.35f else Float.MAX_VALUE
    return velocityPxPerMs > MotionTokens.SwipeDismissVelocityPxPerMs || absOffset > distanceThreshold
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val currentReducedMotion by rememberUpdatedState(reducedMotion)

    SnackbarHost(
        hostState = hostState,
        modifier = modifier
    ) { data ->
        key(data) {
            val coroutineScope = rememberCoroutineScope()
            val enterProgress = remember(data) { Animatable(if (reducedMotion) 1f else 0f) }
            val dragOffsetX = remember(data) { Animatable(0f) }
            var snackbarWidthPx by remember(data) { mutableIntStateOf(0) }
            var isDismissing by remember(data) { mutableStateOf(false) }

            LaunchedEffect(data, reducedMotion) {
                if (reducedMotion) {
                    enterProgress.snapTo(1f)
                } else {
                    enterProgress.snapTo(0f)
                    enterProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 200,
                            easing = MotionTokens.EaseOut
                        )
                    )
                }
            }

            Snackbar(
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .onSizeChanged { snackbarWidthPx = it.width }
                    .pointerInput(data) {
                        var fallbackStartMs = 0L
                        var dragStartEventMs = -1L
                        var dragLastEventMs = -1L
                        detectHorizontalDragGestures(
                            onDragStart = {
                                if (isDismissing) return@detectHorizontalDragGestures
                                fallbackStartMs = SystemClock.uptimeMillis()
                                dragStartEventMs = -1L
                                dragLastEventMs = -1L
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (isDismissing) return@detectHorizontalDragGestures
                                if (dragStartEventMs < 0L) {
                                    dragStartEventMs = change.previousUptimeMillis
                                }
                                dragLastEventMs = change.uptimeMillis
                                change.consume()
                                coroutineScope.launch {
                                    dragOffsetX.snapTo(dragOffsetX.value + dragAmount)
                                }
                            },
                            onDragCancel = {
                                if (isDismissing) return@detectHorizontalDragGestures
                                coroutineScope.launch {
                                    if (currentReducedMotion) {
                                        dragOffsetX.snapTo(0f)
                                    } else {
                                        dragOffsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = MotionTokens.snackbarSnapbackSpring()
                                        )
                                    }
                                }
                            },
                            onDragEnd = {
                                if (isDismissing) return@detectHorizontalDragGestures
                                val elapsedMs = if (
                                    dragStartEventMs >= 0L &&
                                    dragLastEventMs >= dragStartEventMs
                                ) {
                                    dragLastEventMs - dragStartEventMs
                                } else {
                                    SystemClock.uptimeMillis() - fallbackStartMs
                                }
                                coroutineScope.launch {
                                    if (isDismissing) return@launch
                                    val currentOffset = dragOffsetX.value
                                    if (shouldDismissSnackbarDrag(currentOffset, elapsedMs, snackbarWidthPx)) {
                                        isDismissing = true
                                        val direction = if (currentOffset >= 0f) 1f else -1f
                                        val targetX = direction * (snackbarWidthPx.coerceAtLeast(300) * 1.15f)
                                        if (!currentReducedMotion) {
                                            dragOffsetX.animateTo(
                                                targetValue = targetX,
                                                animationSpec = MotionTokens.quickExitSpec()
                                            )
                                        }
                                        data.dismiss()
                                    } else {
                                        if (currentReducedMotion) {
                                            dragOffsetX.snapTo(0f)
                                        } else {
                                            dragOffsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = MotionTokens.snackbarSnapbackSpring()
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                    .graphicsLayer {
                        val enter = enterProgress.value.coerceIn(0f, 1f)
                        val enterScale = if (reducedMotion) {
                            1f
                        } else {
                            MotionTokens.SnackbarEnterScale + (1f - MotionTokens.SnackbarEnterScale) * enter
                        }
                        val enterOffsetY = if (reducedMotion) 0f else (1f - enter) * 12.dp.toPx()
                        val offsetX = dragOffsetX.value
                        val dragAlpha = if (snackbarWidthPx > 0) {
                            (1f - (abs(offsetX) / (snackbarWidthPx * 0.85f))).coerceIn(0.25f, 1f)
                        } else {
                            1f
                        }
                        translationX = offsetX
                        translationY = enterOffsetY
                        scaleX = enterScale
                        scaleY = enterScale
                        alpha = dragAlpha
                    }
                    .testTag("expressive_snackbar")
            ) {
                Text(
                    text = data.visuals.message,
                    style = MaterialTheme.typography.bodyMediumEmphasized
                )
            }
        }
    }
}
