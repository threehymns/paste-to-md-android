package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.session.ConversionSessionViewModel
import com.example.session.SessionEffect
import com.example.session.SessionFeedback
import com.example.ui.AppIcons
import com.example.ui.navigation.MorphEndpointSpec
import com.example.ui.navigation.MorphPairSpec
import com.example.ui.navigation.NavigationTransitionHost
import com.example.ui.navigation.rememberScreenStackState
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.BrandFontFamily
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PlainFontFamily
import kotlinx.coroutines.Dispatchers
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

private fun SessionFeedback.toLocalizedMessage(context: Context): String = when (this) {
    SessionFeedback.ClipboardEmpty -> context.getString(R.string.feedback_clipboard_empty)
    SessionFeedback.ClipboardNoText -> context.getString(R.string.feedback_clipboard_no_text)
    SessionFeedback.Copied -> context.getString(R.string.feedback_copied)
    SessionFeedback.ConvertedAndCopied -> context.getString(R.string.feedback_converted_and_copied)
    is SessionFeedback.ConversionError -> {
        if (detail != null) {
            context.getString(R.string.feedback_convert_error_detail, detail)
        } else {
            context.getString(R.string.feedback_convert_error)
        }
    }
    SessionFeedback.FileSaved -> context.getString(R.string.feedback_file_saved)
    SessionFeedback.FileSaveFailed -> context.getString(R.string.feedback_file_save_error)
}

class MainActivity : ComponentActivity() {

    private val windowFocusState = MutableStateFlow(false)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        windowFocusState.value = hasFocus
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sessionViewModel = ViewModelProvider(
            this,
            ConversionSessionViewModel.factory(applicationContext)
        )[ConversionSessionViewModel::class.java]
        val settingsRepository = sessionViewModel.settingsRepository
        val session = sessionViewModel.session

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle()
            val sessionState by session.state.collectAsStateWithLifecycle()
            val hasWindowFocus by windowFocusState.collectAsStateWithLifecycle()

            val context = LocalContext.current
            val fontFamilyResolver = LocalFontFamilyResolver.current
            val coroutineScope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }

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
                    MarkdownConverter.convert("", settingsRepository.settings.value)
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

            val saveFileLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("text/markdown")
            ) { uri: Uri? ->
                uri?.let { targetUri ->
                    session.export(targetUri.toString())
                }
            }

            MyApplicationTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor
            ) {
                val screenStack = rememberScreenStackState(
                    initialScreen = AppScreen.PASTE,
                    backStackSaver = BackStackSaver,
                    onBackCommitted = { session.cancelConversion() },
                    onScreenPopped = { poppedScreen ->
                        if (poppedScreen == AppScreen.PREVIEW) {
                            session.endSession()
                        } else {
                            session.cancelConversion()
                        }
                    }
                )

                LaunchedEffect(hasWindowFocus, screenStack.currentScreen) {
                    session.onLaunchReadinessChanged(
                        hasWindowFocus = hasWindowFocus,
                        isOnPasteScreen = screenStack.currentScreen == AppScreen.PASTE
                    )
                }

                LaunchedEffect(session) {
                    session.effects.collect { effect ->
                        when (effect) {
                            SessionEffect.OpenPreview -> {
                                if (screenStack.currentScreen == AppScreen.PASTE) {
                                    screenStack.push(AppScreen.PREVIEW)
                                }
                            }

                            is SessionEffect.ShowFeedback -> {
                                showFeedback(effect.feedback.toLocalizedMessage(context))
                            }
                        }
                    }
                }

                // Stable callbacks prevent inactive/animating screens from recomposing mid-transition.
                val latestPasteAndNavigate by rememberUpdatedState {
                    screenStack.commitPendingPopIfSettling()
                    session.paste(openPreview = true)
                }
                val latestPasteInPlace by rememberUpdatedState { session.paste(openPreview = false) }
                val latestOpenSettings by rememberUpdatedState { screenStack.push(AppScreen.SETTINGS) }
                val latestNavigateBack by rememberUpdatedState { screenStack.pop() }
                val latestCopy by rememberUpdatedState { session.copy() }
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
                val onMarkdownChange = remember(session) {
                    { updated: String -> session.updateMarkdown(updated) }
                }

                val markdownText = sessionState.markdownText
                val isConverting = sessionState.isConverting
                val pasteLabel = stringResource(R.string.action_paste)
                val copyLabel = stringResource(R.string.action_copy_markdown)
                val pasteCopyMorphPair = remember(pasteLabel, copyLabel, markdownText.isNotEmpty()) {
                    MorphPairSpec(
                        start = MorphEndpointSpec(
                            screen = AppScreen.PASTE,
                            label = pasteLabel,
                            icon = AppIcons.ContentPaste
                        ),
                        end = MorphEndpointSpec(
                            screen = AppScreen.PREVIEW,
                            label = copyLabel,
                            icon = AppIcons.ContentCopy,
                            enabled = markdownText.isNotEmpty()
                        )
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    NavigationTransitionHost(
                        state = screenStack,
                        morphPair = pasteCopyMorphPair
                    ) { screen ->
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
