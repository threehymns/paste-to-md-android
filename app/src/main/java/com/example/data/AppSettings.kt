package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val title: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class HeadingStyle(val title: String, val example: String) {
    ATX("ATX", "# Heading"),
    SETEXT("Setext", "Heading\n======")
}

enum class BulletMarker(val title: String, val char: Char) {
    HYPHEN("- Hyphen", '-'),
    ASTERISK("* Asterisk", '*'),
    PLUS("+ Plus", '+')
}

enum class CodeFenceStyle(val title: String, val fence: String) {
    BACKTICKS("``` Backticks", "```"),
    TILDE("~~~ Tildes", "~~~")
}

enum class EmphasisMarker(val title: String, val char: Char) {
    ASTERISK("* Asterisks (*italic*, **bold**)", '*'),
    UNDERSCORE("_ Underscores (_italic_, __bold__)", '_')
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val autoPasteOnLaunch: Boolean = false,
    val autoCopyOnConvert: Boolean = false,
    val headingStyle: HeadingStyle = HeadingStyle.ATX,
    val bulletMarker: BulletMarker = BulletMarker.HYPHEN,
    val codeFenceStyle: CodeFenceStyle = CodeFenceStyle.BACKTICKS,
    val emphasisMarker: EmphasisMarker = EmphasisMarker.ASTERISK,
    val preserveLineBreaks: Boolean = false
)

class AppSettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val themeModeStr = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val themeMode = runCatching { ThemeMode.valueOf(themeModeStr) }.getOrDefault(ThemeMode.SYSTEM)

        val headingStyleStr = prefs.getString(KEY_HEADING_STYLE, HeadingStyle.ATX.name) ?: HeadingStyle.ATX.name
        val headingStyle = runCatching { HeadingStyle.valueOf(headingStyleStr) }.getOrDefault(HeadingStyle.ATX)

        val bulletMarkerStr = prefs.getString(KEY_BULLET_MARKER, BulletMarker.HYPHEN.name) ?: BulletMarker.HYPHEN.name
        val bulletMarker = runCatching { BulletMarker.valueOf(bulletMarkerStr) }.getOrDefault(BulletMarker.HYPHEN)

        val codeFenceStr = prefs.getString(KEY_CODE_FENCE, CodeFenceStyle.BACKTICKS.name) ?: CodeFenceStyle.BACKTICKS.name
        val codeFence = runCatching { CodeFenceStyle.valueOf(codeFenceStr) }.getOrDefault(CodeFenceStyle.BACKTICKS)

        val emphasisStr = prefs.getString(KEY_EMPHASIS, EmphasisMarker.ASTERISK.name) ?: EmphasisMarker.ASTERISK.name
        val emphasis = runCatching { EmphasisMarker.valueOf(emphasisStr) }.getOrDefault(EmphasisMarker.ASTERISK)

        return AppSettings(
            themeMode = themeMode,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
            autoPasteOnLaunch = prefs.getBoolean(KEY_AUTO_PASTE, false),
            autoCopyOnConvert = prefs.getBoolean(KEY_AUTO_COPY, false),
            headingStyle = headingStyle,
            bulletMarker = bulletMarker,
            codeFenceStyle = codeFence,
            emphasisMarker = emphasis,
            preserveLineBreaks = prefs.getBoolean(KEY_PRESERVE_BREAKS, false)
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _settings.value = _settings.value.copy(dynamicColor = enabled)
    }

    fun setAutoPasteOnLaunch(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PASTE, enabled).apply()
        _settings.value = _settings.value.copy(autoPasteOnLaunch = enabled)
    }

    fun setAutoCopyOnConvert(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_COPY, enabled).apply()
        _settings.value = _settings.value.copy(autoCopyOnConvert = enabled)
    }

    fun setHeadingStyle(style: HeadingStyle) {
        prefs.edit().putString(KEY_HEADING_STYLE, style.name).apply()
        _settings.value = _settings.value.copy(headingStyle = style)
    }

    fun setBulletMarker(marker: BulletMarker) {
        prefs.edit().putString(KEY_BULLET_MARKER, marker.name).apply()
        _settings.value = _settings.value.copy(bulletMarker = marker)
    }

    fun setCodeFenceStyle(style: CodeFenceStyle) {
        prefs.edit().putString(KEY_CODE_FENCE, style.name).apply()
        _settings.value = _settings.value.copy(codeFenceStyle = style)
    }

    fun setEmphasisMarker(marker: EmphasisMarker) {
        prefs.edit().putString(KEY_EMPHASIS, marker.name).apply()
        _settings.value = _settings.value.copy(emphasisMarker = marker)
    }

    fun setPreserveLineBreaks(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRESERVE_BREAKS, enabled).apply()
        _settings.value = _settings.value.copy(preserveLineBreaks = enabled)
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
        _settings.value = AppSettings()
    }

    companion object {
        private const val PREFS_NAME = "paste_to_markdown_settings"
        private const val KEY_THEME_MODE = "pref_theme_mode"
        private const val KEY_DYNAMIC_COLOR = "pref_dynamic_color"
        private const val KEY_AUTO_PASTE = "pref_auto_paste"
        private const val KEY_AUTO_COPY = "pref_auto_copy"
        private const val KEY_HEADING_STYLE = "pref_heading_style"
        private const val KEY_BULLET_MARKER = "pref_bullet_marker"
        private const val KEY_CODE_FENCE = "pref_code_fence"
        private const val KEY_EMPHASIS = "pref_emphasis"
        private const val KEY_PRESERVE_BREAKS = "pref_preserve_breaks"
    }
}
