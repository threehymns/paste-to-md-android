package com.example.session

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.MarkdownConverter
import com.example.data.AppSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production Android adapter over [ClipboardManager].
 */
class AndroidClipboardAdapter(
    private val clipboardManager: ClipboardManager
) : ClipboardAdapter {

    override fun read(): ClipboardPayload {
        if (!clipboardManager.hasPrimaryClip()) {
            return ClipboardPayload.Empty
        }
        val clip = clipboardManager.primaryClip ?: return ClipboardPayload.Empty
        if (clip.itemCount == 0) {
            return ClipboardPayload.Empty
        }

        val item = clip.getItemAt(0)
        val html = item.htmlText
        if (html != null) {
            return ClipboardPayload.Html(html)
        }

        val text = item.text?.toString()
        if (!text.isNullOrEmpty()) {
            return ClipboardPayload.PlainText(text)
        }

        return ClipboardPayload.NoText
    }

    override fun writePlainText(text: String) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Markdown", text))
    }
}

/**
 * Production Android adapter writing UTF-8 Markdown to a document URI via [ContentResolver].
 */
class AndroidDocumentExporter(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DocumentExporter {

    override suspend fun write(targetId: String, markdown: String): Boolean =
        withContext(ioDispatcher) {
            try {
                val uri = Uri.parse(targetId)
                val outputStream = contentResolver.openOutputStream(uri) ?: return@withContext false
                outputStream.use { stream ->
                    stream.write(markdown.toByteArray(Charsets.UTF_8))
                    stream.flush()
                }
                true
            } catch (e: Exception) {
                false
            }
        }
}

/**
 * Retains [AppSettingsRepository] and [ConversionSession] across configuration changes so active
 * documents and in-flight conversions survive Activity recreation.
 */
class ConversionSessionViewModel(
    val settingsRepository: AppSettingsRepository,
    clipboard: ClipboardAdapter,
    exporter: DocumentExporter
) : ViewModel() {

    val session: ConversionSession = ConversionSession(
        scope = viewModelScope,
        settings = settingsRepository.settings,
        clipboard = clipboard,
        exporter = exporter,
        htmlConverter = { html, settings ->
            withContext(Dispatchers.Default) {
                MarkdownConverter.convert(html, settings)
            }
        }
    )

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(ConversionSessionViewModel::class.java)) {
                        "Unknown ViewModel class: ${modelClass.name}"
                    }
                    val clipboardManager =
                        appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    return ConversionSessionViewModel(
                        settingsRepository = AppSettingsRepository(appContext),
                        clipboard = AndroidClipboardAdapter(clipboardManager),
                        exporter = AndroidDocumentExporter(appContext.contentResolver)
                    ) as T
                }
            }
        }
    }
}
