package com.novelnest.app.feature.lock

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelnest.app.core.designsystem.NovelNestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubOnlyNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun restrictedRootHasBrandAndOnlyReadingPreferences() {
        compose.setContent { NovelNestTheme { EpubOnlyApp() } }
        compose.onNodeWithContentDescription("Novel Nest").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        compose.onNodeWithText("Home").assertDoesNotExist()
        compose.onNodeWithText("Discover").assertDoesNotExist()
        compose.onNodeWithContentDescription("EPUB reading preferences").performClick()
        compose.onNodeWithText("EPUB Reading Preferences").assertIsDisplayed()
        compose.onNodeWithText("Lock & Biometrics").assertDoesNotExist()
        compose.onNodeWithText("AI Reading Tools").assertDoesNotExist()
        compose.onNodeWithText("Audio Narration").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to EPUB library").performClick()
        compose.onNodeWithContentDescription("Novel Nest").assertIsDisplayed()
    }
}