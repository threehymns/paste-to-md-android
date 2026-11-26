package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppSettings
import com.example.data.AppSettingsRepository
import com.example.ui.AppIcons
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

private val BackTransitionSpec = spring<Float>(
    stiffness = Spring.StiffnessMediumLow,
    dampingRatio = Spring.DampingRatioNoBouncy
)

// Full-screen predictive-back transition. The outgoing surface scales 100% -> 90% and
// fades out by the 35% progress mark; the incoming surface scales 110% -> 100% and
// fades in after that. The fade follows raw gesture progress, while the scale is fed
// through the system predictive-back curve so the surface quickly starts to exit.
private val PredictiveBackEasing: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
private const val BackFadeThroughThreshold = 0.35f
private const val BackExitScaleTarget = 0.9f
private const val BackEnterScaleStart = 1.1f

// Fade helpers take the raw gesture progress.
private fun backExitAlpha(progress: Float): Float =
    (1f - progress / BackFadeThroughThreshold).coerceIn(0f, 1f)

private fun backEnterAlpha(progress: Float): Float =
    ((progress - BackFadeThroughThreshold) / (1f - BackFadeThroughThreshold)).coerceIn(0f, 1f)

// Scale helpers take the eased progress.
private fun backExitScale(progress: Float): Float =
    1f + (BackExitScaleTarget - 1f) * progress

private fun backEnterScale(progress: Float): Float =
    BackEnterScaleStart + (1f - BackEnterScaleStart) * progress

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepository = AppSettingsRepository(applicationContext)

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle()
            var backStack by rememberSaveable(stateSaver = BackStackSaver) {
                mutableStateOf(listOf(AppScreen.PASTE))
            }
            var markdownText by rememberSaveable { mutableStateOf("") }
            var hasAutoPastedOnLaunch by rememberSaveable { mutableStateOf(false) }
            var isConverting by remember { mutableStateOf(false) }

            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            val clipboardManager = remember {
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            }

            val saveFileLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("text/markdown")
            ) { uri: Uri? ->
                uri?.let {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                                outputStream.write(markdownText.toByteArray())
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "File saved successfully", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Error saving file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }

            // Warm up the converter in the background during idle time after the first frame paints
            LaunchedEffect(Unit) {
                withContext(Dispatchers.Default) {
                    MarkdownConverter.convert("", settings)
                }
            }

            fun copyToClipboard(textToCopy: String = markdownText, showToast: Boolean = true) {
                val clip = ClipData.newPlainText("Markdown", textToCopy)
                clipboardManager.setPrimaryClip(clip)
                if (showToast) {
                    Toast.makeText(context, "Markdown copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            }

            fun pasteFromClipboard(navigateToPreview: Boolean) {
                if (!clipboardManager.hasPrimaryClip()) {
                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                    return
                }
                val clip = clipboardManager.primaryClip ?: return
                if (clip.itemCount == 0) return

                val item = clip.getItemAt(0)
                val html = item.htmlText
                val text = item.text?.toString()

                if (html != null) {
                    isConverting = true
                    coroutineScope.launch {
                        val converted = withContext(Dispatchers.Default) {
                            try {
                                MarkdownConverter.convert(html, settings)
                            } catch (e: Exception) {
                                "Error converting HTML: ${e.message}"
                            }
                        }
                        markdownText = converted
                        isConverting = false

                        if (settings.autoCopyOnConvert) {
                            val autoClip = ClipData.newPlainText("Markdown", converted)
                            clipboardManager.setPrimaryClip(autoClip)
                            Toast.makeText(context, "Converted and copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                        if (navigateToPreview && backStack.last() == AppScreen.PASTE) {
                            backStack = backStack + AppScreen.PREVIEW
                        }
                    }
                } else if (!text.isNullOrEmpty()) {
                    markdownText = text
                    if (settings.autoCopyOnConvert) {
                        copyToClipboard(textToCopy = text, showToast = true)
                    }
                    if (navigateToPreview && backStack.last() == AppScreen.PASTE) {
                        backStack = backStack + AppScreen.PREVIEW
                    }
                } else {
                    Toast.makeText(context, "No text or HTML found in clipboard", Toast.LENGTH_SHORT).show()
                }
            }

            LaunchedEffect(settings.autoPasteOnLaunch) {
                if (settings.autoPasteOnLaunch && !hasAutoPastedOnLaunch && backStack.last() == AppScreen.PASTE && markdownText.isEmpty()) {
                    hasAutoPastedOnLaunch = true
                    pasteFromClipboard(navigateToPreview = true)
                }
            }

            val backProgress = remember { Animatable(0f) }
            var isBackTransitionActive by remember { mutableStateOf(false) }
            // Non-null while the transition settles to 0 (cancelled) or 1 (committed).
            var backSettleTarget by remember { mutableStateOf<Float?>(null) }
            // Invalidates a settle animation that is finishing as a new gesture takes over.
            var backTransitionGeneration by remember { mutableIntStateOf(0) }
            var resetBackProgressBeforeAnimate by remember { mutableStateOf(false) }
            val screenStateHolder = rememberSaveableStateHolder()

            fun navigateBack() {
                if (backStack.size <= 1) return
                if (isBackTransitionActive && backSettleTarget == 1f) return
                val wasActive = isBackTransitionActive
                backTransitionGeneration++
                resetBackProgressBeforeAnimate = !wasActive
                isBackTransitionActive = true
                backSettleTarget = 1f
            }

            // The predictive back coroutine is cancelled when a gesture is cancelled, so the
            // return animation has to run outside of it. Both completion and cancellation feed
            // this effect, which then owns the transition until it settles. The generation is a
            // key too so programmatic back can restart an in-flight settle.
            LaunchedEffect(backSettleTarget, backTransitionGeneration) {
                val target = backSettleTarget ?: return@LaunchedEffect
                val generation = backTransitionGeneration

                if (resetBackProgressBeforeAnimate) {
                    resetBackProgressBeforeAnimate = false
                    backProgress.snapTo(0f)
                }

                backProgress.animateTo(
                    targetValue = target,
                    animationSpec = BackTransitionSpec
                )

                // A new gesture has taken over. Do not let the finished settle animation
                // snap progress or swap screens underneath it.
                if (backTransitionGeneration != generation) return@LaunchedEffect

                // Springs stop within their visibility threshold. Snap the last fraction so
                // the fade-through is at an exact endpoint before screens are swapped/removed.
                backProgress.snapTo(target)

                // A new gesture may have started while the final snap was applying.
                if (backTransitionGeneration != generation) return@LaunchedEffect

                if (target == 1f && backStack.size > 1) {
                    val poppedScreen = backStack.last()
                    backStack = backStack.dropLast(1)
                    screenStateHolder.removeState(poppedScreen)
                    if (poppedScreen == AppScreen.PREVIEW) {
                        markdownText = ""
                    }
                }
                isBackTransitionActive = false
                backSettleTarget = null
                if (target == 1f && backTransitionGeneration == generation) {
                    backProgress.snapTo(0f)
                }
            }

            PredictiveBackHandler(enabled = backStack.size > 1) { progressFlow ->
                val wasActive = isBackTransitionActive
                backTransitionGeneration++
                val generation = backTransitionGeneration
                backSettleTarget = null
                resetBackProgressBeforeAnimate = false
                isBackTransitionActive = true

                // If a settle was still running, continue from its current visual state
                // instead of snapping back to the start of the transition.
                val initialProgress = if (wasActive) backProgress.value else 0f
                if (!wasActive) {
                    backProgress.snapTo(0f)
                }
                val remainingProgress = 1f - initialProgress

                try {
                    progressFlow.collect { event ->
                        // Keep raw progress here; the fade threshold is based on it and
                        // the layer applies the curve to the scale.
                        backProgress.snapTo(
                            initialProgress + remainingProgress * event.progress
                        )
                    }
                    // Committed: let the fade-through finish before swapping screens.
                    // Swapping at the current progress is the visible pop.
                    if (backTransitionGeneration == generation) {
                        backSettleTarget = 1f
                    }
                } catch (e: CancellationException) {
                    // Cancelled gestures cancel this coroutine, so settle from the effect above.
                    // Ignore this if a newer gesture has already taken over.
                    if (backTransitionGeneration == generation) {
                        backSettleTarget = 0f
                    }
                }
            }

            val topScreen = backStack.last()
            val previousScreen = backStack.getOrNull(backStack.lastIndex - 1)
            val visibleScreens = remember(topScreen, previousScreen, isBackTransitionActive) {
                if (isBackTransitionActive && previousScreen != null) {
                    listOf(previousScreen, topScreen)
                } else {
                    listOf(topScreen)
                }
            }

            MyApplicationTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        visibleScreens.forEach { screen ->
                            key(screen) {
                                val isTop = screen == topScreen
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            val progress = if (isBackTransitionActive && !resetBackProgressBeforeAnimate) {
                                                backProgress.value
                                            } else {
                                                0f
                                            }
                                            val scale = if (isBackTransitionActive) {
                                                val eased = PredictiveBackEasing.transform(progress)
                                                if (isTop) backExitScale(eased) else backEnterScale(eased)
                                            } else {
                                                1f
                                            }
                                            alpha = if (isBackTransitionActive) {
                                                if (isTop) backExitAlpha(progress) else backEnterAlpha(progress)
                                            } else {
                                                1f
                                            }
                                            scaleX = scale
                                            scaleY = scale
                                            transformOrigin = TransformOrigin.Center
                                        }
                                        .background(MaterialTheme.colorScheme.background)
                                ) {
                                    screenStateHolder.SaveableStateProvider(screen) {
                                        when (screen) {
                                            AppScreen.PASTE -> {
                                                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                                                    PasteToMarkdownScreen(
                                                        isConverting = isConverting,
                                                        onPaste = { pasteFromClipboard(navigateToPreview = true) },
                                                        onOpenSettings = {
                                                            if (!isBackTransitionActive) {
                                                                backStack = backStack + AppScreen.SETTINGS
                                                            }
                                                        },
                                                        modifier = Modifier.padding(innerPadding)
                                                    )
                                                }
                                            }

                                            AppScreen.PREVIEW -> {
                                                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                                                    MarkdownPreviewScreen(
                                                        markdownText = markdownText,
                                                        isConverting = isConverting,
                                                        onMarkdownChange = { markdownText = it },
                                                        onClear = { navigateBack() },
                                                        onPasteAgain = { pasteFromClipboard(navigateToPreview = false) },
                                                        onSaveFile = { saveFileLauncher.launch("converted.md") },
                                                        onCopy = { copyToClipboard() },
                                                        onOpenSettings = {
                                                            if (!isBackTransitionActive) {
                                                                backStack = backStack + AppScreen.SETTINGS
                                                            }
                                                        },
                                                        modifier = Modifier.padding(innerPadding)
                                                    )
                                                }
                                            }

                                            AppScreen.SETTINGS -> {
                                                SettingsScreen(
                                                    settings = settings,
                                                    repository = settingsRepository,
                                                    onNavigateBack = { navigateBack() }
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

@Composable
fun PasteToMarkdownScreen(
    isConverting: Boolean = false,
    onPaste: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(52.dp)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = AppIcons.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = AppIcons.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Paste rich text here",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Copy formatted text from any app and paste it to get clean Markdown.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onPaste,
                    enabled = !isConverting,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(56.dp)
                        .testTag("paste_button")
                ) {
                    Icon(AppIcons.ContentPaste, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Paste", style = com.example.ui.theme.EmphasizedTypography.titleMedium)
                }
            }
        }

        if (isConverting) {
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownPreviewScreen(
    markdownText: String,
    isConverting: Boolean = false,
    onMarkdownChange: (String) -> Unit,
    onClear: () -> Unit,
    onPasteAgain: () -> Unit,
    onSaveFile: () -> Unit,
    onCopy: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        TextField(
            value = markdownText,
            onValueChange = onMarkdownChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("markdown_text_field"),
            placeholder = {
                Text(
                    "Pasted HTML will be converted to Markdown here...",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            visualTransformation = MarkdownSyntaxHighlighter(MaterialTheme.colorScheme),
            shape = RoundedCornerShape(32.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onClear,
                enabled = markdownText.isNotEmpty(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                    .size(52.dp)
                    .testTag("clear_button")
            ) {
                Icon(AppIcons.Delete, contentDescription = "Clear")
            }

            FilledTonalButton(
                onClick = onPasteAgain,
                enabled = !isConverting,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("paste_again_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(AppIcons.ContentPaste, contentDescription = "Paste")
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Paste Again",
                    style = com.example.ui.theme.EmphasizedTypography.labelLarge,
                    maxLines = 1
                )
            }

            FilledTonalIconButton(
                onClick = onSaveFile,
                enabled = markdownText.isNotEmpty(),
                modifier = Modifier
                    .size(52.dp)
                    .testTag("save_file_button")
            ) {
                Icon(AppIcons.Download, contentDescription = "Save file")
            }

            FilledTonalIconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(52.dp)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = AppIcons.Settings,
                    contentDescription = stringResource(R.string.settings_title)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onCopy,
            enabled = markdownText.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .testTag("copy_button"),
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(AppIcons.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text("Copy Markdown", style = com.example.ui.theme.EmphasizedTypography.titleLarge)
        }

        if (isConverting) {
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
