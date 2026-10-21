package com.novelnest.app.data.preferences

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * Reading theme configurations with high contrast, calm color pairings.
 */
enum class ReaderTheme(
    val displayName: String,
    val backgroundColor: Color,
    val surfaceColor: Color,
    val textColor: Color,
    val secondaryTextColor: Color,
    val isDark: Boolean
) {
    DARK(
        displayName = "Obsidian",
        backgroundColor = Color(0xFF0F1216),
        surfaceColor = Color(0xFF161B22),
        textColor = Color(0xFFF4F0E6),
        secondaryTextColor = Color(0xFFA5A096),
        isDark = true
    ),
    AMOLED(
        displayName = "AMOLED Black",
        backgroundColor = Color(0xFF000000),
        surfaceColor = Color(0xFF121212),
        textColor = Color(0xFFFFFFFF),
        secondaryTextColor = Color(0xFF8E8E93),
        isDark = true
    ),
    SEPIA(
        displayName = "Parchment",
        backgroundColor = Color(0xFFF5EEDA),
        surfaceColor = Color(0xFFEADFC6),
        textColor = Color(0xFF2E2419),
        secondaryTextColor = Color(0xFF6B5846),
        isDark = false
    ),
    SLATE(
        displayName = "Slate",
        backgroundColor = Color(0xFF1E242C),
        surfaceColor = Color(0xFF28303A),
        textColor = Color(0xFFE2E8F0),
        secondaryTextColor = Color(0xFF94A3B8),
        isDark = true
    ),
    LIGHT(
        displayName = "Paper",
        backgroundColor = Color(0xFFFAF9F6),
        surfaceColor = Color(0xFFF0EFE9),
        textColor = Color(0xFF1A1A1A),
        secondaryTextColor = Color(0xFF666666),
        isDark = false
    )
}

/**
 * Supported reader typography families.
 */
enum class ReaderFont(val displayName: String, val fontFamily: FontFamily) {
    SYSTEM("System Default", FontFamily.Default),
    SERIF("Classic Serif", FontFamily.Serif),
    SANS_SERIF("Modern Sans", FontFamily.SansSerif),
    MONOSPACE("Monospace", FontFamily.Monospace)
}

/**
 * Supported reading page progression styles.
 */
enum class ReaderScrollMode(val displayName: String) {
    CONTINUOUS("Continuous Scroll"),
    PAGED("Paged Flip")
}

/**
 * Complete novel reading personalization state.
 */
data class ReaderSettings(
    val fontSizeSp: Float = 17f,
    val fontFamily: ReaderFont = ReaderFont.SERIF,
    val lineHeightMultiplier: Float = 1.6f,
    val paragraphSpacingDp: Float = 12f,
    val theme: ReaderTheme = ReaderTheme.DARK,
    val textBrightness: Float = 1.0f,
    val scrollMode: ReaderScrollMode = ReaderScrollMode.CONTINUOUS,
    val keepScreenOn: Boolean = true,
    val volumeKeyNavigation: Boolean = false,
    val fullscreenReading: Boolean = false
)

/**
 * General application preferences separate from novel reader preferences.
 */
data class GeneralAppSettings(
    val amoledAppMode: Boolean = false,
    val dataSaver: Boolean = false,
    val defaultSourceOrdinal: Int = 0,
    val autoBookmarkOnRead: Boolean = true
)
