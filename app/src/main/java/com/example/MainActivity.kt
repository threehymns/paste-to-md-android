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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppSettings
import com.example.data.AppSettingsRepository
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.BrandFontFamily
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PlainFontFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
    PASTE,
    PREVIEW,
    SETTINGS
}

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
private val PredictiveBackEasing: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
private const val FadeThroughThreshold = 0.35f
private const val BackExitScaleTarget = 0.9f
private const val BackEnterScaleStart = 1.1f
private const val ForwardExitScaleTarget = 0.96f
private const val ForwardEnterScaleStart = 0.94f

private fun fadeThroughExitAlpha(progress: Float): Float =
    (1f - progress / FadeThroughThreshold).coerceIn(0f, 1f)

private fun fadeThroughEnterAlpha(progress: Float): Float =
    ((progress - FadeThroughThreshold) / (1f - FadeThroughThreshold)).coerceIn(0f, 1f)

private fun backExitScale(progress: Float): Float =
    1f + (BackExitScaleTarget - 1f) * progress

private fun backEnterScale(progress: Float): Float =
    BackEnterScaleStart + (1f - BackEnterScaleStart) * progress

private fun forwardExitScale(progress: Float): Float =
    1f + (ForwardExitScaleTarget - 1f) * progress

private fun forwardEnterScale(progress: Float): Float =
    ForwardEnterScaleStart + (1f - ForwardEnterScaleStart) * progress

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
                val screenStateHolder = rememberSaveableStateHolder()

                val backProgress = remember { Animatable(0f) }
                var isBackTransitionActive by remember { mutableStateOf(false) }
                var backSettleTarget by remember { mutableStateOf<Float?>(null) }
                var backTransitionGeneration by remember { mutableIntStateOf(0) }
                var resetBackProgressBeforeAnimate by remember { mutableStateOf(false) }

                val forwardProgress = remember { Animatable(1f) }
                var isForwardTransitionActive by remember { mutableStateOf(false) }
                var forwardOutgoingScreen by remember { mutableStateOf<AppScreen?>(null) }
                var forwardTransitionGeneration by remember { mutableIntStateOf(0) }
                var resetForwardProgressBeforeAnimate by remember { mutableStateOf(false) }

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

                    if (resetForwardProgressBeforeAnimate) {
                        forwardProgress.snapTo(0f)
                        resetForwardProgressBeforeAnimate = false
                    }

                    forwardProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = motionScheme.defaultEffectsSpec()
                    )

                    if (forwardTransitionGeneration != generation) return@LaunchedEffect

                    forwardProgress.snapTo(1f)
                    if (forwardTransitionGeneration == generation) {
                        isForwardTransitionActive = false
                        forwardOutgoingScreen = null
                    }
                }

                fun navigateBack() {
                    if (backStack.size <= 1) return
                    if (isBackTransitionActive && backSettleTarget == 1f) return
                    cancelForwardTransition()
                    cancelActiveConversion()
                    val wasActive = isBackTransitionActive
                    backTransitionGeneration++
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

                    if (resetBackProgressBeforeAnimate) {
                        backProgress.snapTo(0f)
                        resetBackProgressBeforeAnimate = false
                    }

                    backProgress.animateTo(
                        targetValue = target,
                        animationSpec = motionScheme.defaultEffectsSpec()
                    )

                    if (backTransitionGeneration != generation) return@LaunchedEffect

                    backProgress.snapTo(target)

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
                    }
                }

                PredictiveBackHandler(enabled = backStack.size > 1) { progressFlow ->
                    cancelForwardTransition()
                    val wasActive = isBackTransitionActive
                    backTransitionGeneration++
                    val generation = backTransitionGeneration
                    backSettleTarget = null
                    resetBackProgressBeforeAnimate = false
                    isBackTransitionActive = true

                    val initialProgress = if (wasActive) backProgress.value else 0f
                    if (!wasActive) {
                        backProgress.snapTo(0f)
                    }
                    val remainingProgress = 1f - initialProgress

                    try {
                        progressFlow.collect { event ->
                            backProgress.snapTo(
                                initialProgress + remainingProgress * event.progress
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

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        visibleScreens.forEach { screen ->
                            key(screen) {
                                val isTop = screen == topScreen
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            when {
                                                isBackTransitionActive -> {
                                                    val progress = if (resetBackProgressBeforeAnimate) {
                                                        0f
                                                    } else {
                                                        backProgress.value.coerceIn(0f, 1f)
                                                    }
                                                    val eased = PredictiveBackEasing.transform(progress)
                                                    val scale = if (isTop) {
                                                        backExitScale(eased)
                                                    } else {
                                                        backEnterScale(eased)
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
                                                    val scale = if (isTop) {
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
                                    screenStateHolder.SaveableStateProvider(screen) {
                                        when (screen) {
                                            AppScreen.PASTE -> {
                                                PasteToMarkdownScreen(
                                                    isConverting = isConverting,
                                                    onPaste = onPasteAndNavigate,
                                                    onOpenSettings = onOpenSettings,
                                                    snackbarHostState = snackbarHostState
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
                                                    snackbarHostState = snackbarHostState
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
                }
            }
        }
    }
}
