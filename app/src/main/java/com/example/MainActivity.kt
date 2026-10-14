package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    PasteToMarkdownScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteToMarkdownScreen(modifier: Modifier = Modifier) {
    var markdownText by remember { mutableStateOf("") }
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
            MarkdownConverterHolder.converter
        }
    }

    fun copyToClipboard() {
        val clip = ClipData.newPlainText("Markdown", markdownText)
        clipboardManager.setPrimaryClip(clip)
        Toast.makeText(context, "Markdown copied to clipboard", Toast.LENGTH_SHORT).show()
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
                        MarkdownConverterHolder.converter.convert(html)
                    } catch (e: Exception) {
                        "Error converting HTML: ${e.message}"
                    }
                }
                markdownText = converted
                isConverting = false
            }
        } else if (text != null) {
            markdownText = text
        } else {
            Toast.makeText(context, "No text or HTML found in clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (markdownText.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
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
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
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
                    .weight(1f),
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

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { markdownText = "" },
                    enabled = markdownText.isNotEmpty(),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear")
                }

                FilledTonalButton(
                    onClick = { pasteFromClipboard() },
                    enabled = !isConverting,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                    Spacer(Modifier.width(8.dp))
                    Text("Paste Again", style = com.example.ui.theme.EmphasizedTypography.labelLarge)
                }
                
                FilledTonalIconButton(
                    onClick = { saveFileLauncher.launch("converted.md") },
                    enabled = markdownText.isNotEmpty(),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Save file")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = { copyToClipboard() },
                enabled = markdownText.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(28.dp))
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

private object MarkdownConverterHolder {
    val converter: FlexmarkHtmlConverter by lazy {
        FlexmarkHtmlConverter.builder().build()
    }
}

