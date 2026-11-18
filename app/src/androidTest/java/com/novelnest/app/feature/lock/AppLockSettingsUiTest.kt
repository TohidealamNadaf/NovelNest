package com.novelnest.app.feature.lock

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.core.designsystem.NovelNestTheme
import com.novelnest.app.data.security.AppAccess
import com.novelnest.app.feature.settings.AppLockPage
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLockSettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NovelNestApplication
    private var initialWasUnlocked = false

    @Before fun requireNoExistingUserLock() = runBlocking {
        app.appLock.initialize()
        Assume.assumeFalse("Never replace an existing user's app lock.", app.appLock.state.value.enabled)
        initialWasUnlocked = true
    }
    @After fun cleanup() {
        if (!initialWasUnlocked || !app.appLock.state.value.enabled) return
        runBlocking {
            if (app.appLock.state.value.access != AppAccess.FULL) {
                app.appLock.lock()
                app.appLock.unlock("1234")
            }
            app.appLock.disable("1234")
        }
    }
    private fun fill(label: String, text: String) {
        compose.onNodeWithText(label).performScrollTo().performTextReplacement(text)
    }
    @Test fun bothNewPinsAndConfirmationsAreLimitedToFourDigits() {
        compose.setContent { NovelNestTheme { AppLockPage(onBack = {}) } }
        for (label in listOf("Normal PIN", "Confirm normal PIN", "EPUB-only PIN", "Confirm EPUB-only PIN")) {
            fill(label, "001234")
            compose.onNodeWithText(label).assert(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.InputText,
                androidx.compose.ui.text.AnnotatedString("0012")))
        }
        Assert.assertFalse(app.appLock.state.value.enabled)
    }

    @Test fun settingsRequireBothConfirmationsAndNormalPinToDisable() {
        compose.setContent { NovelNestTheme { AppLockPage(onBack = {}) } }
        fill("Normal PIN", "1234")
        fill("Confirm normal PIN", "1234")
        fill("EPUB-only PIN", "4321")
        fill("Confirm EPUB-only PIN", "1111")
        compose.onNodeWithText("Enable app lock").performScrollTo().performClick()
        compose.onNodeWithText("Confirmations must match their PINs.").performScrollTo().assertIsDisplayed()
        Assert.assertFalse(app.appLock.state.value.enabled)
        fill("Confirm EPUB-only PIN", "4321")
        compose.onNodeWithText("Enable app lock").performScrollTo().performClick()
        compose.waitUntil(15000) { app.appLock.state.value.enabled }
        compose.waitUntil(15000) { compose.onAllNodesWithText("App lock saved.").fetchSemanticsNodes().isNotEmpty() }
        fill("Current normal PIN", "4321")
        compose.onNodeWithText("Disable app lock").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Incorrect PIN.").fetchSemanticsNodes().isNotEmpty() }
        Assert.assertTrue(app.appLock.state.value.enabled)
        fill("Current normal PIN", "1234")
        compose.onNodeWithText("Disable app lock").performScrollTo().performClick()
        compose.waitUntil(15000) { !app.appLock.state.value.enabled }
    }
}