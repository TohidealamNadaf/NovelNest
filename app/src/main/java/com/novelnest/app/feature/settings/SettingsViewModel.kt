package com.novelnest.app.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.preferences.GeneralAppSettings
import com.novelnest.app.data.preferences.ReaderFont
import com.novelnest.app.data.preferences.ReaderPreferencesManager
import com.novelnest.app.data.preferences.ReaderScrollMode
import com.novelnest.app.data.preferences.ReaderSettings
import com.novelnest.app.data.preferences.ReaderTheme
import com.novelnest.app.data.narration.NarrationCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(
    private val preferencesManager: ReaderPreferencesManager,
    private val context: Context? = null
) : ViewModel() {

    val readerSettings: StateFlow<ReaderSettings> = preferencesManager.readerSettings
    val appSettings: StateFlow<GeneralAppSettings> = preferencesManager.appSettings

    private val _cacheSizeFormatted = MutableStateFlow("12.4 MB")
    val cacheSizeFormatted: StateFlow<String> = _cacheSizeFormatted.asStateFlow()

    private val _clearCacheSuccessMessage = MutableStateFlow<String?>(null)
    val clearCacheSuccessMessage: StateFlow<String?> = _clearCacheSuccessMessage.asStateFlow()

    init {
        calculateCacheSize()
    }

    fun setFontSize(sizeSp: Float) = preferencesManager.updateFontSize(sizeSp)
    fun setFontFamily(font: ReaderFont) = preferencesManager.updateFontFamily(font)
    fun setLineHeight(multiplier: Float) = preferencesManager.updateLineHeight(multiplier)
    fun setParagraphSpacing(spacingDp: Float) = preferencesManager.updateParagraphSpacing(spacingDp)
    fun setTheme(theme: ReaderTheme) = preferencesManager.updateTheme(theme)
    fun setTextBrightness(brightness: Float) = preferencesManager.updateTextBrightness(brightness)
    fun setScrollMode(mode: ReaderScrollMode) = preferencesManager.updateScrollMode(mode)
    fun setKeepScreenOn(enabled: Boolean) = preferencesManager.updateKeepScreenOn(enabled)
    fun setVolumeKeyNavigation(enabled: Boolean) = preferencesManager.updateVolumeKeyNavigation(enabled)
    fun setFullscreen(enabled: Boolean) = preferencesManager.updateFullscreenReading(enabled)
    fun resetReadingDefaults() = preferencesManager.resetReadingSettings()

    fun setAmoledAppMode(enabled: Boolean) = preferencesManager.updateAmoledAppMode(enabled)
    fun setDataSaver(enabled: Boolean) = preferencesManager.updateDataSaver(enabled)
    fun setDefaultSource(ordinal: Int) = preferencesManager.updateDefaultSource(ordinal)
    fun setHapticFeedback(enabled: Boolean) = preferencesManager.updateHapticFeedback(enabled)
    fun setReducedMotion(enabled: Boolean) = preferencesManager.updateReducedMotion(enabled)

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = context ?: run {
                _clearCacheSuccessMessage.value = "Cache storage unavailable"
                return@launch
            }
            try {
                val cleared = deleteDir(ctx.cacheDir, NarrationCache.directory(ctx))
                val audio = NarrationCache.clear(ctx)
                _cacheSizeFormatted.value = formatBytes(getFolderSize(ctx.cacheDir))
                _clearCacheSuccessMessage.value = when {
                    audio.playbackProtected -> "Other cache cleared; active narration cache preserved"
                    !cleared || audio.failed > 0 -> "Some cache files could not be removed"
                    else -> "Cache cleared successfully"
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _clearCacheSuccessMessage.value = "Could not clear all cache files" }
        }
    }

    fun dismissClearCacheMessage() {
        _clearCacheSuccessMessage.value = null
    }

    private fun calculateCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = context?.cacheDir?.let { getFolderSize(it) } ?: (12L * 1024 * 1024)
            _cacheSizeFormatted.value = formatBytes(bytes)
        }
    }

    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        file.listFiles()?.forEach { child ->
            size += if (child.isDirectory) getFolderSize(child) else child.length()
        }
        return size
    }

    private fun deleteDir(dir: File, excluded: File): Boolean {
        val children = dir.listFiles() ?: return !dir.exists()
        var success = true
        children.forEach { child ->
            if (child != excluded) {
                val removed = if (child.isDirectory) deleteDir(child, excluded) else child.delete() || !child.exists()
                if (!removed) success = false
            }
        }
        return success
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024f)
            else -> "$bytes B"
        }
    }
}
