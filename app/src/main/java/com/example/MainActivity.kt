package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
    CONVERTER,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepository = AppSettingsRepository(applicationContext)

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle()
            var currentScreen by rememberSaveable { mutableStateOf(AppScreen.CONVERTER) }

            val backProgress = remember { Animatable(0f) }
            var isPredictiveBackActive by remember { mutableStateOf(false) }
            var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

            PredictiveBackHandler(enabled = currentScreen == AppScreen.SETTINGS) { progressFlow ->
                try {
                    isPredictiveBackActive = true
                    progressFlow.collect { event ->
                        swipeEdge = event.swipeEdge
                        backProgress.snapTo(event.progress)
                    }
                    backProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
                    currentScreen = AppScreen.CONVERTER
                } catch (e: CancellationException) {
                    backProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
                } finally {
                    isPredictiveBackActive = false
                    backProgress.snapTo(0f)
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
                        // Render Converter screen when active or as back preview destination
                        if (currentScreen == AppScreen.CONVERTER || isPredictiveBackActive) {
                            val converterScale = if (isPredictiveBackActive) {
                                0.92f + (0.08f * backProgress.value)
                            } else {
                                1f
                            }
                            val scrimAlpha = if (isPredictiveBackActive) {
                                (1f - backProgress.value) * 0.25f
                            } else {
                                0f
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = converterScale
                                        scaleY = converterScale
                                    }
                            ) {
                                Scaffold(
                                    modifier = Modifier.fillMaxSize()
                                ) { innerPadding ->
                                    PasteToMarkdownScreen(
                                        settings = settings,
                                        onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }

                                if (scrimAlpha > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                                    )
                                }
                            }
                        }

                        // Render Settings screen when active
                        if (currentScreen == AppScreen.SETTINGS) {
                            val progress = backProgress.value
                            val settingsScale = if (isPredictiveBackActive) {
                                1f - (progress * 0.12f)
                            } else {
                                1f
                            }
                            val cornerRadius = if (isPredictiveBackActive) {
                                (progress * 28).dp
                            } else {
                                0.dp
                            }
                            val sign = if (swipeEdge == BackEventCompat.EDGE_LEFT) 1 else -1
                            val horizontalShift = if (isPredictiveBackActive) {
                                (progress * 64 * sign).dp
                            } else {
                                0.dp
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .offset(x = horizontalShift)
                                    .graphicsLayer {
                                        scaleX = settingsScale
                                        scaleY = settingsScale
                                    }
                                    .shadow(
                                        elevation = if (isPredictiveBackActive) (progress * 16).dp else 0.dp,
                                        shape = RoundedCornerShape(cornerRadius)
                                    )
                                    .clip(RoundedCornerShape(cornerRadius))
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                SettingsScreen(
                                    settings = settings,
                                    repository = settingsRepository,
                                    onNavigateBack = { currentScreen = AppScreen.CONVERTER }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteToMarkdownScreen(
    settings: AppSettings = AppSettings(),
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var markdownText by rememberSaveable { mutableStateOf("") }
    var isConverting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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

    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // Warm up the converter in the background during idle time after the first frame paints
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            MarkdownConverter.convert("", settings)
        }
    }

    fun copyToClipboard(showToast: Boolean = true) {
        val clip = ClipData.newPlainText("Markdown", markdownText)
        clipboardManager.setPrimaryClip(clip)
        if (showToast) {
            Toast.makeText(context, "Markdown copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    fun pasteFromClipboard() {
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
            }
        } else if (text != null) {
            markdownText = text
            if (settings.autoCopyOnConvert) {
                copyToClipboard(showToast = true)
            }
        } else {
            Toast.makeText(context, "No text or HTML found in clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(settings.autoPasteOnLaunch) {
        if (settings.autoPasteOnLaunch && markdownText.isEmpty()) {
            pasteFromClipboard()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (markdownText.isEmpty()) {
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
                        onClick = { pasteFromClipboard() },
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
        } else {
            TextField(
                value = markdownText,
                onValueChange = { markdownText = it },
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
                    onClick = { markdownText = "" },
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
                    onClick = { pasteFromClipboard() },
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
                    onClick = { saveFileLauncher.launch("converted.md") },
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
                onClick = { copyToClipboard() },
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
        }

        if (isConverting) {
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
