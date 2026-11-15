package com.novelnest.app.feature.reader

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.preferences.ReaderPreferencesManager
import com.novelnest.app.feature.reader.components.ReaderQuickSettingsSheet
import com.novelnest.app.feature.settings.ReadingBehaviorPage
import com.novelnest.app.feature.settings.SettingsViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderUnicodeToggleUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun preferencesAndReaderSwitchStaySynchronizedWithoutDismissingSheet() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val prefName = "unicode-ui-" + java.util.UUID.randomUUID()
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(prefName, mode)
        }
        val manager = ReaderPreferencesManager(context)
        val model = SettingsViewModel(manager)
        val showReader = mutableStateOf(false)
        var dismissals = 0
        val chapter = Chapter("one", "Chapter 1", listOf("Paragraph"))
        try {
            compose.setContent {
                val settings by manager.readerSettings.collectAsState()
                if (!showReader.value) ReadingBehaviorPage(settings, model, {})
                else ReaderQuickSettingsSheet(
                    settings = settings, textColor = settings.theme.textColor, secondaryColor = settings.theme.secondaryTextColor,
                    reader = ReaderUiState(novel = Novel("book", "Book", "", NovelSourceType.NOVEL_PHOENIX, ""), chapters = listOf(chapter)),
                    onDismiss = { dismissals++ }, onSetTheme = {}, onSetFontSize = {}, onSetLineHeight = {}, onSetFontFamily = {},
                    onSetUnicodeEscapeDecoding = manager::updateUnicodeEscapeDecoding
                )
            }
            compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("UnicodeEscapePreference"))).performClick()
            compose.runOnIdle { assertTrue(manager.readerSettings.value.unicodeEscapeDecodingEnabled); showReader.value = true }
            compose.onNodeWithText("Size & Spacing").performClick()
            compose.onNodeWithTag("ReaderUnicodeEscapeToggle").assertIsOn().performClick().assertIsOff()
            compose.runOnIdle {
                assertFalse(manager.readerSettings.value.unicodeEscapeDecodingEnabled)
                assertEquals(0, dismissals)
                showReader.value = false
            }
            compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("UnicodeEscapePreference"))).assertIsOff()
            assertFalse(ReaderPreferencesManager(context).readerSettings.value.unicodeEscapeDecodingEnabled)
        } finally { app.deleteSharedPreferences(prefName) }
    }
}
