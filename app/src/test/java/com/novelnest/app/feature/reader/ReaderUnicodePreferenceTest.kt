package com.novelnest.app.feature.reader

import com.novelnest.app.data.preferences.ReaderPreferencesManager
import com.novelnest.app.testsupport.readerTestContext
import org.junit.Assert.*
import org.junit.Test

class ReaderUnicodePreferenceTest {
    @Test fun preferenceDefaultsOffPersistsAndResetTurnsItOff() {
        val context = readerTestContext()
        val manager = ReaderPreferencesManager(context)
        assertFalse(manager.readerSettings.value.unicodeEscapeDecodingEnabled)
        manager.updateUnicodeEscapeDecoding(true)
        assertTrue(manager.readerSettings.value.unicodeEscapeDecodingEnabled)
        assertTrue(ReaderPreferencesManager(context).readerSettings.value.unicodeEscapeDecodingEnabled)
        manager.resetReadingSettings()
        assertFalse(manager.readerSettings.value.unicodeEscapeDecodingEnabled)
        assertFalse(ReaderPreferencesManager(context).readerSettings.value.unicodeEscapeDecodingEnabled)
    }
}
