package com.example

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Retains the active Markdown document across configuration changes without
 * serializing potentially large documents into Activity savedInstanceState Bundles.
 */
class MarkdownSessionViewModel : ViewModel() {
    private val _markdownText = MutableStateFlow("")
    val markdownText: StateFlow<String> = _markdownText.asStateFlow()

    fun updateMarkdownText(text: String) {
        _markdownText.value = text
    }

    fun clearMarkdownText() {
        _markdownText.value = ""
    }
}
