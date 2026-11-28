package com.example.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalContext

val LocalReducedMotion = compositionLocalOf { false }

/**
 * Allows gentle opacity-only crossfades (160ms ease-out) to remain legible
 * when Android's global ANIMATOR_DURATION_SCALE is 0f, while all spatial
 * travel and scale transforms are explicitly disabled.
 */
internal object ReducedMotionOpacityScale : MotionDurationScale {
    override val scaleFactor: Float = 1f
}

@Composable
fun rememberIsReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver

    fun readReducedMotion(): Boolean = runCatching {
        Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }.getOrDefault(false)

    var isReduced by remember(resolver) { mutableStateOf(readReducedMotion()) }

    DisposableEffect(resolver) {
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                isReduced = readReducedMotion()
            }
        }
        val registered = runCatching {
            resolver.registerContentObserver(uri, false, observer)
            true
        }.getOrDefault(false)

        onDispose {
            if (registered) {
                runCatching { resolver.unregisterContentObserver(observer) }
            }
        }
    }

    return isReduced
}
