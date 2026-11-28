package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppSettings
import com.example.data.AppSettingsRepository
import com.example.ui.ExpressiveButtonMorphOverlay
import com.example.ui.MeasuredButtonBounds
import com.example.ui.computeRelativeButtonRect
import com.example.ui.rememberPrimaryActionPressState
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.BrandFontFamily
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.MotionTokens
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PlainFontFamily
import com.example.ui.theme.ReducedMotionOpacityScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
    PASTE,
    PREVIEW,
    SETTINGS
}

private fun isPasteCopyTransitionPair(first: AppScreen?, second: AppScreen?): Boolean =
    (first == AppScreen.PASTE && second == AppScreen.PREVIEW) ||
        (first == AppScreen.PREVIEW && second == AppScreen.PASTE)

private val BackStackSaver = listSaver<List<AppScreen>, String>(
    save = { stack -> stack.map { it.name } },
    restore = { names ->
        names.mapNotNull { runCatching { AppScreen.valueOf(it) }.getOrNull() }
            .ifEmpty { listOf(AppScreen.PASTE) }
    }
)

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

internal fun writeMarkdownToUri(
    contentResolver: ContentResolver,
    uri: Uri,
    markdown: String
): Boolean {
    return try {
        val outputStream = contentResolver.openOutputStream(uri) ?: return false
        outputStream.use { stream ->
            stream.write(markdown.toByteArray(Charsets.UTF_8))
            stream.flush()
        }
        true
    } catch (e: Exception) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
class MainActivity : ComponentActivity() {

    private val windowFocusState = MutableStateFlow(false)

    internal var htmlConverter: suspend (String, AppSettings) -> String = { html, settings ->
        withContext(Dispatchers.Default) {
            MarkdownConverter.convert(html, settings)
        }
    }

    internal lateinit var settingsRepository: AppSettingsRepository

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        windowFocusState.value = hasFocus
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsRepository = AppSettingsRepository(applicationContext)
        val sessionViewModel = ViewModelProvider(this)[MarkdownSessionViewModel::class.java]

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle()
            val markdownText by sessionViewModel.markdownText.collectAsStateWithLifecycle()
            val hasWindowFocus by windowFocusState.collectAsStateWithLifecycle()
            var backStack by rememberSaveable(stateSaver = BackStackSaver) {
                mutableStateOf(listOf(AppScreen.PASTE))
            }
            var hasAutoPastedOnLaunch by rememberSaveable { mutableStateOf(false) }
            var isConverting by remember { mutableStateOf(false) }
            var activeConversionJob by remember { mutableStateOf<Job?>(null) }
            var conversionGeneration by remember { mutableIntStateOf(0) }

            val context = LocalContext.current
            val fontFamilyResolver = LocalFontFamilyResolver.current
            val coroutineScope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }
            val clipboardManager = remember {
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            }

            // Warm up bundled fonts and the HTML converter during idle time after first paint
            // so opening Settings or Preview never stalls on initial asset loading.
            LaunchedEffect(Unit) {
                launch {
                    runCatching {
                        fontFamilyResolver.preload(BrandFontFamily)
                        fontFamilyResolver.preload(PlainFontFamily)
                        fontFamilyResolver.preload(MonoFontFamily)
                    }
                }
                withContext(Dispatchers.Default) {
                    MarkdownConverter.convert("", settings)
                }
            }

            fun showFeedback(message: String) {
                coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Short
                    )
                }
            }

            fun cancelActiveConversion() {
                conversionGeneration++
                activeConversionJob?.cancel()
                activeConversionJob = null
                isConverting = false
            }

            val saveFileLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("text/markdown")
            ) { uri: Uri? ->
                uri?.let { targetUri ->
                    val contentToSave = sessionViewModel.markdownText.value
                    coroutineScope.launch(Dispatchers.IO) {
                        val saved = writeMarkdownToUri(
                            contentResolver = context.contentResolver,
                            uri = targetUri,
                            markdown = contentToSave
                        )
                        withContext(Dispatchers.Main) {
                            val message = context.getString(
                                if (saved) R.string.feedback_file_saved else R.string.feedback_file_save_error
                            )
                            showFeedback(message)
                        }
                    }
                }
            }

            MyApplicationTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor
            ) {
                val motionScheme = MaterialTheme.motionScheme
                val reducedMotion = LocalReducedMotion.current
                val screenStateHolder = rememberSaveableStateHolder()

                val backProgress = remember { Animatable(0f) }
                val backSpatialProgress = remember { Animatable(0f) }
                var isBackTransitionActive by remember { mutableStateOf(false) }
                var backSettleTarget by remember { mutableStateOf<Float?>(null) }
                var backTransitionGeneration by remember { mutableIntStateOf(0) }
                var resetBackProgressBeforeAnimate by remember { mutableStateOf(false) }
                var backResetInitialEffects by remember { mutableFloatStateOf(0f) }
                var backResetInitialSpatial by remember { mutableFloatStateOf(0f) }

                val forwardProgress = remember { Animatable(1f) }
                val forwardSpatialProgress = remember { Animatable(1f) }
                var isForwardTransitionActive by remember { mutableStateOf(false) }
                var forwardOutgoingScreen by remember { mutableStateOf<AppScreen?>(null) }
                var forwardTransitionGeneration by remember { mutableIntStateOf(0) }
                var resetForwardProgressBeforeAnimate by remember { mutableStateOf(false) }

                var rootContainerSize by remember { mutableStateOf(IntSize.Zero) }
                var pasteMeasuredBounds by remember { mutableStateOf<MeasuredButtonBounds?>(null) }
                var copyMeasuredBounds by remember { mutableStateOf<MeasuredButtonBounds?>(null) }
                val primaryActionPressState = rememberPrimaryActionPressState()
                val readPressShapeFraction = remember(primaryActionPressState) {
                    { primaryActionPressState.pressFraction }
                }

                fun cancelForwardTransition() {
                    if (!isForwardTransitionActive) return
                    forwardTransitionGeneration++
                    isForwardTransitionActive = false
                    forwardOutgoingScreen = null
                    resetForwardProgressBeforeAnimate = false
                }

                // Cohesive M3 Expressive fade-through transition started cleanly after Frame 1
                // composition so there is no single-frame flash or stutter.
                fun navigateTo(screen: AppScreen) {
                    if (isBackTransitionActive || backStack.last() == screen) return
                    forwardOutgoingScreen = backStack.last()
                    forwardTransitionGeneration++
                    resetForwardProgressBeforeAnimate = true
                    isForwardTransitionActive = true
                    backStack = backStack + screen
                }

                LaunchedEffect(forwardTransitionGeneration) {
                    if (!isForwardTransitionActive) return@LaunchedEffect
                    val generation = forwardTransitionGeneration
                    val animateSpatialButton = isPasteCopyTransitionPair(
                        forwardOutgoingScreen,
                        backStack.lastOrNull()
                    )

                    if (resetForwardProgressBeforeAnimate) {
                        forwardProgress.snapTo(0f)
                        forwardSpatialProgress.snapTo(0f)
                        resetForwardProgressBeforeAnimate = false
                    }

                    if (reducedMotion) {
                        forwardSpatialProgress.snapTo(1f)
                        withContext(ReducedMotionOpacityScale) {
                            forwardProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = MotionTokens.reducedFadeSpec()
                            )
                        }
                    } else if (animateSpatialButton) {
                        coroutineScope {
                            launch {
                                forwardProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = motionScheme.defaultEffectsSpec()
                                )
                            }
                            launch {
                                forwardSpatialProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = motionScheme.defaultSpatialSpec()
                                )
                            }
                        }
                    } else {
                        forwardSpatialProgress.snapTo(1f)
                        forwardProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = motionScheme.defaultEffectsSpec()
                        )
                    }

                    if (forwardTransitionGeneration != generation) return@LaunchedEffect

                    forwardProgress.snapTo(1f)
                    forwardSpatialProgress.snapTo(1f)
                    if (forwardTransitionGeneration == generation) {
                        isForwardTransitionActive = false
                        forwardOutgoingScreen = null
                    }
                }

                fun navigateBack() {
                    if (backStack.size <= 1) return
                    if (isBackTransitionActive && backSettleTarget == 1f) return
                    val interruptedPasteCopyForward = isForwardTransitionActive &&
                        isPasteCopyTransitionPair(forwardOutgoingScreen, backStack.lastOrNull())
                    val reversedEffects = if (interruptedPasteCopyForward) {
                        val current = if (resetForwardProgressBeforeAnimate) 0f else forwardProgress.value
                        (1f - current).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val reversedSpatial = if (interruptedPasteCopyForward) {
                        val current = if (resetForwardProgressBeforeAnimate) 0f else forwardSpatialProgress.value
                        (1f - current).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    cancelForwardTransition()
                    cancelActiveConversion()
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

                fun copyToClipboard(
                    textToCopy: String = sessionViewModel.markdownText.value,
                    showMessage: Boolean = true,
                    message: String = context.getString(R.string.feedback_copied)
                ) {
                    val clip = ClipData.newPlainText("Markdown", textToCopy)
                    clipboardManager.setPrimaryClip(clip)
                    if (showMessage) {
                        showFeedback(message)
                    }
                }

                fun pasteFromClipboard(navigateToPreview: Boolean) {
                    if (!clipboardManager.hasPrimaryClip()) {
                        showFeedback(context.getString(R.string.feedback_clipboard_empty))
                        return
                    }
                    val clip = clipboardManager.primaryClip ?: return
                    if (clip.itemCount == 0) return

                    val item = clip.getItemAt(0)
                    val html = item.htmlText
                    val text = item.text?.toString()

                    if (html != null) {
                        cancelActiveConversion()
                        val generation = conversionGeneration
                        val settingsSnapshot = settings
                        isConverting = true
                        activeConversionJob = coroutineScope.launch {
                            val conversionResult = try {
                                Result.success(htmlConverter(html, settingsSnapshot))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Result.failure(e)
                            }

                            if (conversionGeneration != generation) return@launch

                            activeConversionJob = null
                            isConverting = false

                            conversionResult.onSuccess { converted ->
                                sessionViewModel.updateMarkdownText(converted)
                                if (settingsSnapshot.autoCopyOnConvert) {
                                    copyToClipboard(
                                        textToCopy = converted,
                                        showMessage = true,
                                        message = context.getString(R.string.feedback_converted_and_copied)
                                    )
                                }
                                if (navigateToPreview && backStack.last() == AppScreen.PASTE) {
                                    navigateTo(AppScreen.PREVIEW)
                                }
                            }.onFailure { error ->
                                val detail = error.message?.takeIf { it.isNotBlank() }
                                val errorMessage = if (detail != null) {
                                    context.getString(R.string.feedback_convert_error_detail, detail)
                                } else {
                                    context.getString(R.string.feedback_convert_error)
                                }
                                showFeedback(errorMessage)
                            }
                        }
                    } else if (!text.isNullOrEmpty()) {
                        cancelActiveConversion()
                        sessionViewModel.updateMarkdownText(text)
                        if (settings.autoCopyOnConvert) {
                            copyToClipboard(textToCopy = text, showMessage = true)
                        }
                        if (navigateToPreview && backStack.last() == AppScreen.PASTE) {
                            navigateTo(AppScreen.PREVIEW)
                        }
                    } else {
                        showFeedback(context.getString(R.string.feedback_clipboard_no_text))
                    }
                }

                // Stable callbacks prevent inactive/animating screens from recomposing mid-transition.
                val latestPasteAndNavigate by rememberUpdatedState { pasteFromClipboard(true) }
                val latestPasteInPlace by rememberUpdatedState { pasteFromClipboard(false) }
                val latestOpenSettings by rememberUpdatedState { navigateTo(AppScreen.SETTINGS) }
                val latestNavigateBack by rememberUpdatedState { navigateBack() }
                val latestCopy by rememberUpdatedState { copyToClipboard() }
                val latestSaveFile by rememberUpdatedState { saveFileLauncher.launch("converted.md") }
                val latestResetFeedback by rememberUpdatedState {
                    showFeedback(context.getString(R.string.feedback_settings_reset))
                }
                val onPasteAndNavigate = remember { { latestPasteAndNavigate() } }
                val onPasteInPlace = remember { { latestPasteInPlace() } }
                val onOpenSettings = remember { { latestOpenSettings() } }
                val onNavigateBack = remember { { latestNavigateBack() } }
                val onCopy = remember { { latestCopy() } }
                val onSaveFile = remember { { latestSaveFile() } }
                val onResetCompleted = remember { { latestResetFeedback() } }
                val onMarkdownChange = remember(sessionViewModel) {
                    { updated: String -> sessionViewModel.updateMarkdownText(updated) }
                }

                LaunchedEffect(settings.autoPasteOnLaunch, hasWindowFocus) {
                    if (
                        settings.autoPasteOnLaunch &&
                        hasWindowFocus &&
                        !hasAutoPastedOnLaunch &&
                        backStack.last() == AppScreen.PASTE &&
                        markdownText.isEmpty()
                    ) {
                        hasAutoPastedOnLaunch = true
                        pasteFromClipboard(navigateToPreview = true)
                    }
                }

                LaunchedEffect(backSettleTarget, backTransitionGeneration) {
                    val target = backSettleTarget ?: return@LaunchedEffect
                    val generation = backTransitionGeneration
                    val animateSpatialButton = isPasteCopyTransitionPair(
                        backStack.getOrNull(backStack.lastIndex - 1),
                        backStack.lastOrNull()
                    )

                    if (resetBackProgressBeforeAnimate) {
                        backProgress.snapTo(backResetInitialEffects)
                        backSpatialProgress.snapTo(backResetInitialSpatial)
                        resetBackProgressBeforeAnimate = false
                        backResetInitialEffects = 0f
                        backResetInitialSpatial = 0f
                    }

                    if (reducedMotion) {
                        backSpatialProgress.snapTo(target)
                        withContext(ReducedMotionOpacityScale) {
                            backProgress.animateTo(
                                targetValue = target,
                                animationSpec = MotionTokens.reducedFadeSpec()
                            )
                        }
                    } else if (animateSpatialButton) {
                        coroutineScope {
                            launch {
                                backProgress.animateTo(
                                    targetValue = target,
                                    animationSpec = motionScheme.defaultEffectsSpec()
                                )
                            }
                            launch {
                                backSpatialProgress.animateTo(
                                    targetValue = target,
                                    animationSpec = motionScheme.defaultSpatialSpec()
                                )
                            }
                        }
                    } else {
                        backSpatialProgress.snapTo(target)
                        backProgress.animateTo(
                            targetValue = target,
                            animationSpec = motionScheme.defaultEffectsSpec()
                        )
                    }

                    if (backTransitionGeneration != generation) return@LaunchedEffect

                    backProgress.snapTo(target)
                    backSpatialProgress.snapTo(target)

                    if (backTransitionGeneration != generation) return@LaunchedEffect

                    if (target == 1f && backStack.size > 1) {
                        cancelActiveConversion()
                        val poppedScreen = backStack.last()
                        backStack = backStack.dropLast(1)
                        screenStateHolder.removeState(poppedScreen)
                        if (poppedScreen == AppScreen.PREVIEW) {
                            sessionViewModel.clearMarkdownText()
                        }
                    }
                    isBackTransitionActive = false
                    backSettleTarget = null
                    if (target == 1f && backTransitionGeneration == generation) {
                        backProgress.snapTo(0f)
                        backSpatialProgress.snapTo(0f)
                    }
                }

                PredictiveBackHandler(enabled = backStack.size > 1) { progressFlow ->
                    val interruptedPasteCopyForward = isForwardTransitionActive &&
                        isPasteCopyTransitionPair(forwardOutgoingScreen, backStack.lastOrNull())
                    val reversedEffects = if (interruptedPasteCopyForward) {
                        val current = if (resetForwardProgressBeforeAnimate) 0f else forwardProgress.value
                        (1f - current).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val reversedSpatial = if (interruptedPasteCopyForward) {
                        val current = if (resetForwardProgressBeforeAnimate) 0f else forwardSpatialProgress.value
                        (1f - current).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    cancelForwardTransition()
                    val wasActive = isBackTransitionActive
                    backTransitionGeneration++
                    val generation = backTransitionGeneration
                    backSettleTarget = null
                    resetBackProgressBeforeAnimate = false
                    backResetInitialEffects = 0f
                    backResetInitialSpatial = 0f
                    isBackTransitionActive = true

                    val initialProgress = when {
                        wasActive -> backProgress.value
                        interruptedPasteCopyForward -> reversedEffects
                        else -> 0f
                    }
                    val initialSpatial = when {
                        wasActive -> backSpatialProgress.value
                        interruptedPasteCopyForward -> reversedSpatial
                        else -> 0f
                    }
                    if (!wasActive) {
                        backProgress.snapTo(initialProgress)
                        backSpatialProgress.snapTo(initialSpatial)
                    }
                    val remainingProgress = 1f - initialProgress
                    val remainingSpatial = 1f - initialSpatial

                    try {
                        progressFlow.collect { event ->
                            val eventProgress = event.progress.coerceIn(0f, 1f)
                            backProgress.snapTo(
                                initialProgress + remainingProgress * eventProgress
                            )
                            val easedEvent = MotionTokens.PredictiveBackGestureEasing.transform(eventProgress)
                            backSpatialProgress.snapTo(
                                initialSpatial + remainingSpatial * easedEvent
                            )
                        }
                        if (backTransitionGeneration == generation) {
                            cancelActiveConversion()
                            backSettleTarget = 1f
                        }
                    } catch (e: CancellationException) {
                        if (backTransitionGeneration == generation) {
                            backSettleTarget = 0f
                        }
                    }
                }

                val topScreen = backStack.last()
                val previousScreen = backStack.getOrNull(backStack.lastIndex - 1)
                val visibleScreens = remember(
                    topScreen,
                    previousScreen,
                    isBackTransitionActive,
                    isForwardTransitionActive,
                    forwardOutgoingScreen
                ) {
                    when {
                        isBackTransitionActive && previousScreen != null -> {
                            listOf(previousScreen, topScreen)
                        }
                        isForwardTransitionActive &&
                            forwardOutgoingScreen != null &&
                            forwardOutgoingScreen != topScreen -> {
                            listOf(forwardOutgoingScreen!!, topScreen)
                        }
                        else -> listOf(topScreen)
                    }
                }

                val activePasteCopyForward = isForwardTransitionActive &&
                    isPasteCopyTransitionPair(forwardOutgoingScreen, topScreen)
                val activePasteCopyBack = isBackTransitionActive &&
                    isPasteCopyTransitionPair(previousScreen, topScreen)
                val validPasteBounds = pasteMeasuredBounds
                    ?.takeIf { it.containerSize == rootContainerSize }
                    ?.rect
                val validCopyBounds = copyMeasuredBounds
                    ?.takeIf { it.containerSize == rootContainerSize }
                    ?.rect
                val showButtonMorphOverlay = !reducedMotion &&
                    (activePasteCopyForward || activePasteCopyBack) &&
                    validPasteBounds != null &&
                    validCopyBounds != null
                val latestShowButtonMorphOverlay by rememberUpdatedState(showButtonMorphOverlay)
                val isPrimaryActionButtonHidden = remember { { latestShowButtonMorphOverlay } }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { rootContainerSize = it }
                    ) {
                        visibleScreens.forEach { screen ->
                            key(screen) {
                                val isTop = screen == topScreen
                                var screenRootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                                var buttonCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                                val updateButtonBounds = remember(screen) {
                                    { container: LayoutCoordinates?, target: LayoutCoordinates? ->
                                        val rect = computeRelativeButtonRect(container, target)
                                        if (rect != null && container != null) {
                                            val measured = MeasuredButtonBounds(
                                                rect = rect,
                                                containerSize = container.size
                                            )
                                            when (screen) {
                                                AppScreen.PASTE -> {
                                                    if (pasteMeasuredBounds != measured) {
                                                        pasteMeasuredBounds = measured
                                                    }
                                                }
                                                AppScreen.PREVIEW -> {
                                                    if (copyMeasuredBounds != measured) {
                                                        copyMeasuredBounds = measured
                                                    }
                                                }
                                                AppScreen.SETTINGS -> Unit
                                            }
                                        }
                                    }
                                }
                                val onButtonPlaced = remember(screen) {
                                    { coords: LayoutCoordinates ->
                                        buttonCoords = coords
                                        updateButtonBounds(screenRootCoords, coords)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            when {
                                                isBackTransitionActive -> {
                                                    val progress = if (resetBackProgressBeforeAnimate) {
                                                        backResetInitialEffects
                                                    } else {
                                                        backProgress.value.coerceIn(0f, 1f)
                                                    }
                                                    val scale = if (reducedMotion) {
                                                        1f
                                                    } else {
                                                        val eased = MotionTokens.PredictiveBackGestureEasing.transform(progress)
                                                        if (isTop) {
                                                            backExitScale(eased)
                                                        } else {
                                                            backEnterScale(eased)
                                                        }
                                                    }
                                                    alpha = if (isTop) {
                                                        fadeThroughExitAlpha(progress)
                                                    } else {
                                                        fadeThroughEnterAlpha(progress)
                                                    }
                                                    scaleX = scale
                                                    scaleY = scale
                                                }
                                                isForwardTransitionActive -> {
                                                    val progress = if (resetForwardProgressBeforeAnimate) {
                                                        0f
                                                    } else {
                                                        forwardProgress.value.coerceIn(0f, 1f)
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
                                    // Unscaled screen-space reference container so measured button bounds
                                    // remain immune to the parent graphicsLayer scale transform.
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .onGloballyPositioned { coords ->
                                                screenRootCoords = coords
                                                updateButtonBounds(coords, buttonCoords)
                                            }
                                    ) {
                                        screenStateHolder.SaveableStateProvider(screen) {
                                            when (screen) {
                                                AppScreen.PASTE -> {
                                                    PasteToMarkdownScreen(
                                                        isConverting = isConverting,
                                                        onPaste = onPasteAndNavigate,
                                                        onOpenSettings = onOpenSettings,
                                                        snackbarHostState = snackbarHostState,
                                                        isPasteButtonHidden = isPrimaryActionButtonHidden,
                                                        onPasteButtonPlaced = onButtonPlaced,
                                                        pasteInteractionSource = primaryActionPressState.pasteInteractionSource,
                                                        pressShapeFraction = readPressShapeFraction
                                                    )
                                                }

                                                AppScreen.PREVIEW -> {
                                                    MarkdownPreviewScreen(
                                                        markdownText = markdownText,
                                                        isConverting = isConverting,
                                                        onMarkdownChange = onMarkdownChange,
                                                        onClear = onNavigateBack,
                                                        onPasteAgain = onPasteInPlace,
                                                        onSaveFile = onSaveFile,
                                                        onCopy = onCopy,
                                                        onOpenSettings = onOpenSettings,
                                                        snackbarHostState = snackbarHostState,
                                                        isCopyButtonHidden = isPrimaryActionButtonHidden,
                                                        onCopyButtonPlaced = onButtonPlaced,
                                                        copyInteractionSource = primaryActionPressState.copyInteractionSource,
                                                        pressShapeFraction = readPressShapeFraction
                                                    )
                                                }

                                                AppScreen.SETTINGS -> {
                                                    SettingsScreen(
                                                        settings = settings,
                                                        repository = settingsRepository,
                                                        onNavigateBack = onNavigateBack,
                                                        snackbarHostState = snackbarHostState,
                                                        onResetCompleted = onResetCompleted
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showButtonMorphOverlay && validPasteBounds != null && validCopyBounds != null) {
                            ExpressiveButtonMorphOverlay(
                                pasteBounds = validPasteBounds,
                                copyBounds = validCopyBounds,
                                spatialProgress = {
                                    when {
                                        activePasteCopyForward -> {
                                            val raw = if (resetForwardProgressBeforeAnimate) {
                                                0f
                                            } else {
                                                forwardSpatialProgress.value
                                            }
                                            if (forwardOutgoingScreen == AppScreen.PASTE) raw else 1f - raw
                                        }
                                        activePasteCopyBack -> {
                                            val raw = if (resetBackProgressBeforeAnimate) {
                                                backResetInitialSpatial
                                            } else {
                                                backSpatialProgress.value
                                            }
                                            if (topScreen == AppScreen.PREVIEW) 1f - raw else raw
                                        }
                                        else -> 0f
                                    }
                                },
                                effectsProgress = {
                                    when {
                                        activePasteCopyForward -> {
                                            val raw = if (resetForwardProgressBeforeAnimate) {
                                                0f
                                            } else {
                                                forwardProgress.value.coerceIn(0f, 1f)
                                            }
                                            if (forwardOutgoingScreen == AppScreen.PASTE) raw else 1f - raw
                                        }
                                        activePasteCopyBack -> {
                                            val raw = if (resetBackProgressBeforeAnimate) {
                                                backResetInitialEffects
                                            } else {
                                                backProgress.value.coerceIn(0f, 1f)
                                            }
                                            if (topScreen == AppScreen.PREVIEW) 1f - raw else raw
                                        }
                                        else -> 0f
                                    }
                                },
                                pressShapeFraction = readPressShapeFraction,
                                pasteLabel = stringResource(R.string.action_paste),
                                copyLabel = stringResource(R.string.action_copy_markdown),
                                copyEnabled = markdownText.isNotEmpty()
                            )
                        }
                    }
                }
            }
        }
    }
}
