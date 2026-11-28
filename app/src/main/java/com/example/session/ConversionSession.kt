package com.example.session

import com.example.data.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Snapshot of clipboard content read across the platform seam.
 */
sealed interface ClipboardPayload {
    data object Empty : ClipboardPayload
    data object NoText : ClipboardPayload
    data class Html(val html: String) : ClipboardPayload
    data class PlainText(val text: String) : ClipboardPayload
}

/**
 * Seam for reading rich clipboard content and writing Markdown plain text back.
 */
interface ClipboardAdapter {
    fun read(): ClipboardPayload
    fun writePlainText(text: String)
}

/**
 * Seam for writing UTF-8 Markdown content to a platform document target.
 */
interface DocumentExporter {
    suspend fun write(targetId: String, markdown: String): Boolean
}

data class ConversionSessionState(
    val markdownText: String = "",
    val isConverting: Boolean = false
)

sealed interface SessionFeedback {
    data object ClipboardEmpty : SessionFeedback
    data object ClipboardNoText : SessionFeedback
    data object Copied : SessionFeedback
    data object ConvertedAndCopied : SessionFeedback
    data class ConversionError(val detail: String?) : SessionFeedback
    data object FileSaved : SessionFeedback
    data object FileSaveFailed : SessionFeedback
}

sealed interface SessionEffect {
    data object OpenPreview : SessionEffect
    data class ShowFeedback(val feedback: SessionFeedback) : SessionEffect
}

/**
 * Owns one active Conversion Session: clipboard intake, generation-guarded HTML conversion,
 * auto-copy, launch auto-paste focus gating, manual copy-back, and document export.
 */
class ConversionSession(
    private val scope: CoroutineScope,
    private val settings: StateFlow<AppSettings>,
    private val clipboard: ClipboardAdapter,
    private val exporter: DocumentExporter,
    private val htmlConverter: suspend (String, AppSettings) -> String
) {
    private val _state = MutableStateFlow(ConversionSessionState())
    val state: StateFlow<ConversionSessionState> = _state.asStateFlow()

    private val _effects = Channel<SessionEffect>(Channel.BUFFERED)
    val effects: Flow<SessionEffect> = _effects.receiveAsFlow()

    private var activeConversionJob: Job? = null
    private var conversionGeneration = 0
    private var hasAutoPastedOnLaunch = false
    private var hasWindowFocus = false
    private var isOnPasteScreen = false

    init {
        scope.launch {
            settings
                .map { it.autoPasteOnLaunch }
                .distinctUntilChanged()
                .collect {
                    maybeTriggerAutoPaste()
                }
        }
    }

    fun onLaunchReadinessChanged(hasWindowFocus: Boolean, isOnPasteScreen: Boolean) {
        this.hasWindowFocus = hasWindowFocus
        this.isOnPasteScreen = isOnPasteScreen
        maybeTriggerAutoPaste()
    }

    fun paste(openPreview: Boolean) {
        when (val payload = clipboard.read()) {
            ClipboardPayload.Empty -> {
                emitFeedback(SessionFeedback.ClipboardEmpty)
            }

            ClipboardPayload.NoText -> {
                emitFeedback(SessionFeedback.ClipboardNoText)
            }

            is ClipboardPayload.PlainText -> {
                cancelConversion()
                _state.update { it.copy(markdownText = payload.text) }
                if (settings.value.autoCopyOnConvert) {
                    clipboard.writePlainText(payload.text)
                    emitFeedback(SessionFeedback.Copied)
                }
                if (openPreview) {
                    _effects.trySend(SessionEffect.OpenPreview)
                }
            }

            is ClipboardPayload.Html -> {
                cancelConversion()
                val generation = conversionGeneration
                val settingsSnapshot = settings.value
                _state.update { it.copy(isConverting = true) }
                activeConversionJob = scope.launch {
                    val conversionResult = try {
                        Result.success(htmlConverter(payload.html, settingsSnapshot))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }

                    if (conversionGeneration != generation) return@launch

                    activeConversionJob = null
                    _state.update { it.copy(isConverting = false) }

                    conversionResult.onSuccess { converted ->
                        _state.update { it.copy(markdownText = converted) }
                        if (settingsSnapshot.autoCopyOnConvert) {
                            clipboard.writePlainText(converted)
                            emitFeedback(SessionFeedback.ConvertedAndCopied)
                        }
                        if (openPreview) {
                            _effects.trySend(SessionEffect.OpenPreview)
                        }
                    }.onFailure { error ->
                        val detail = error.message?.takeIf { it.isNotBlank() }
                        emitFeedback(SessionFeedback.ConversionError(detail))
                    }
                }
            }
        }
    }

    fun updateMarkdown(text: String) {
        _state.update { it.copy(markdownText = text) }
    }

    fun copy() {
        clipboard.writePlainText(_state.value.markdownText)
    }

    fun export(targetId: String) {
        val contentToSave = _state.value.markdownText
        scope.launch {
            val saved = exporter.write(targetId, contentToSave)
            emitFeedback(
                if (saved) SessionFeedback.FileSaved else SessionFeedback.FileSaveFailed
            )
        }
    }

    fun cancelConversion() {
        conversionGeneration++
        activeConversionJob?.cancel()
        activeConversionJob = null
        _state.update { it.copy(isConverting = false) }
    }

    fun endSession() {
        cancelConversion()
        _state.update { it.copy(markdownText = "") }
    }

    private fun maybeTriggerAutoPaste() {
        if (
            settings.value.autoPasteOnLaunch &&
            hasWindowFocus &&
            !hasAutoPastedOnLaunch &&
            isOnPasteScreen &&
            _state.value.markdownText.isEmpty()
        ) {
            hasAutoPastedOnLaunch = true
            paste(openPreview = true)
        }
    }

    private fun emitFeedback(feedback: SessionFeedback) {
        _effects.trySend(SessionEffect.ShowFeedback(feedback))
    }
}
