package com.novelnest.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Persists and reactively exposes user reading preferences and general app settings.
 * Persists immediately to SharedPreferences so values survive app restarts.
 */
class ReaderPreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _readerSettings = MutableStateFlow(loadReaderSettings())
    val readerSettings: StateFlow<ReaderSettings> = _readerSettings.asStateFlow()

    private val _appSettings = MutableStateFlow(loadAppSettings())
    val appSettings: StateFlow<GeneralAppSettings> = _appSettings.asStateFlow()

    private fun loadReaderSettings(): ReaderSettings {
        val fontSize = prefs.getFloat(KEY_FONT_SIZE, 17f)
        val fontName = prefs.getString(KEY_FONT_FAMILY, ReaderFont.SERIF.name) ?: ReaderFont.SERIF.name
        val lineHeight = prefs.getFloat(KEY_LINE_HEIGHT, 1.6f)
        val paragraphSpacing = prefs.getFloat(KEY_PARAGRAPH_SPACING, 12f)
        val themeName = prefs.getString(KEY_THEME, ReaderTheme.DARK.name) ?: ReaderTheme.DARK.name
        val textBrightness = prefs.getFloat(KEY_TEXT_BRIGHTNESS, 1.0f)
        val scrollModeName = prefs.getString(KEY_SCROLL_MODE, ReaderScrollMode.CONTINUOUS.name) ?: ReaderScrollMode.CONTINUOUS.name
        val keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        val volumeNav = prefs.getBoolean(KEY_VOLUME_NAV, false)
        val fullscreen = prefs.getBoolean(KEY_FULLSCREEN, false)

        val font = try { ReaderFont.valueOf(fontName) } catch (_: Exception) { ReaderFont.SERIF }
        val theme = try { ReaderTheme.valueOf(themeName) } catch (_: Exception) { ReaderTheme.DARK }
        val scrollMode = try { ReaderScrollMode.valueOf(scrollModeName) } catch (_: Exception) { ReaderScrollMode.CONTINUOUS }

        return ReaderSettings(
            fontSizeSp = fontSize,
            fontFamily = font,
            lineHeightMultiplier = lineHeight,
            paragraphSpacingDp = paragraphSpacing,
            theme = theme,
            textBrightness = textBrightness,
            scrollMode = scrollMode,
            keepScreenOn = keepScreenOn,
            volumeKeyNavigation = volumeNav,
            fullscreenReading = fullscreen
        )
    }

    private fun loadAppSettings(): GeneralAppSettings {
        val amoled = prefs.getBoolean(KEY_AMOLED_APP, false)
        val dataSaver = prefs.getBoolean(KEY_DATA_SAVER, false)
        val defaultSource = prefs.getInt(KEY_DEFAULT_SOURCE, 0)
        val autoBookmark = prefs.getBoolean(KEY_AUTO_BOOKMARK, true)

        return GeneralAppSettings(
            amoledAppMode = amoled,
            dataSaver = dataSaver,
            defaultSourceOrdinal = defaultSource,
            autoBookmarkOnRead = autoBookmark
        )
    }

    fun updateFontSize(fontSizeSp: Float) {
        val clamped = fontSizeSp.coerceIn(12f, 32f)
        prefs.edit().putFloat(KEY_FONT_SIZE, clamped).apply()
        _readerSettings.update { it.copy(fontSizeSp = clamped) }
    }

    fun updateFontFamily(font: ReaderFont) {
        prefs.edit().putString(KEY_FONT_FAMILY, font.name).apply()
        _readerSettings.update { it.copy(fontFamily = font) }
    }

    fun updateLineHeight(multiplier: Float) {
        val clamped = multiplier.coerceIn(1.2f, 2.2f)
        prefs.edit().putFloat(KEY_LINE_HEIGHT, clamped).apply()
        _readerSettings.update { it.copy(lineHeightMultiplier = clamped) }
    }

    fun updateParagraphSpacing(spacingDp: Float) {
        val clamped = spacingDp.coerceIn(4f, 24f)
        prefs.edit().putFloat(KEY_PARAGRAPH_SPACING, clamped).apply()
        _readerSettings.update { it.copy(paragraphSpacingDp = clamped) }
    }

    fun updateTheme(theme: ReaderTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _readerSettings.update { it.copy(theme = theme) }
    }

    fun updateTextBrightness(brightness: Float) {
        val clamped = brightness.coerceIn(0.4f, 1.0f)
        prefs.edit().putFloat(KEY_TEXT_BRIGHTNESS, clamped).apply()
        _readerSettings.update { it.copy(textBrightness = clamped) }
    }

    fun updateScrollMode(mode: ReaderScrollMode) {
        prefs.edit().putString(KEY_SCROLL_MODE, mode.name).apply()
        _readerSettings.update { it.copy(scrollMode = mode) }
    }

    fun updateKeepScreenOn(keepScreenOn: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, keepScreenOn).apply()
        _readerSettings.update { it.copy(keepScreenOn = keepScreenOn) }
    }

    fun updateVolumeKeyNavigation(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOLUME_NAV, enabled).apply()
        _readerSettings.update { it.copy(volumeKeyNavigation = enabled) }
    }

    fun updateFullscreenReading(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FULLSCREEN, enabled).apply()
        _readerSettings.update { it.copy(fullscreenReading = enabled) }
    }

    fun resetReadingSettings() {
        val defaults = ReaderSettings()
        prefs.edit()
            .putFloat(KEY_FONT_SIZE, defaults.fontSizeSp)
            .putString(KEY_FONT_FAMILY, defaults.fontFamily.name)
            .putFloat(KEY_LINE_HEIGHT, defaults.lineHeightMultiplier)
            .putFloat(KEY_PARAGRAPH_SPACING, defaults.paragraphSpacingDp)
            .putString(KEY_THEME, defaults.theme.name)
            .putFloat(KEY_TEXT_BRIGHTNESS, defaults.textBrightness)
            .putString(KEY_SCROLL_MODE, defaults.scrollMode.name)
            .putBoolean(KEY_KEEP_SCREEN_ON, defaults.keepScreenOn)
            .putBoolean(KEY_VOLUME_NAV, defaults.volumeKeyNavigation)
            .putBoolean(KEY_FULLSCREEN, defaults.fullscreenReading)
            .apply()
        _readerSettings.value = defaults
    }

    fun updateAmoledAppMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMOLED_APP, enabled).apply()
        _appSettings.update { it.copy(amoledAppMode = enabled) }
    }

    fun updateDataSaver(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DATA_SAVER, enabled).apply()
        _appSettings.update { it.copy(dataSaver = enabled) }
    }

    fun updateDefaultSource(ordinal: Int) {
        prefs.edit().putInt(KEY_DEFAULT_SOURCE, ordinal).apply()
        _appSettings.update { it.copy(defaultSourceOrdinal = ordinal) }
    }

    fun updateAutoBookmark(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BOOKMARK, enabled).apply()
        _appSettings.update { it.copy(autoBookmarkOnRead = enabled) }
    }

    companion object {
        private const val PREFS_NAME = "novelnest_preferences"

        // Reader preference keys
        private const val KEY_FONT_SIZE = "reader_font_size"
        private const val KEY_FONT_FAMILY = "reader_font_family"
        private const val KEY_LINE_HEIGHT = "reader_line_height"
        private const val KEY_PARAGRAPH_SPACING = "reader_paragraph_spacing"
        private const val KEY_THEME = "reader_theme"
        private const val KEY_TEXT_BRIGHTNESS = "reader_text_brightness"
        private const val KEY_SCROLL_MODE = "reader_scroll_mode"
        private const val KEY_KEEP_SCREEN_ON = "reader_keep_screen_on"
        private const val KEY_VOLUME_NAV = "reader_volume_nav"
        private const val KEY_FULLSCREEN = "reader_fullscreen"

        // App preference keys
        private const val KEY_AMOLED_APP = "app_amoled_mode"
        private const val KEY_DATA_SAVER = "app_data_saver"
        private const val KEY_DEFAULT_SOURCE = "app_default_source"
        private const val KEY_AUTO_BOOKMARK = "app_auto_bookmark"
    }
}
