package com.novelnest.app.feature.epub

import android.app.Activity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.novelnest.app.NovelNestApp
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.core.designsystem.NovelNestTheme
import com.novelnest.app.data.local.epub.*
import com.novelnest.app.data.model.*
import com.novelnest.app.data.repository.NovelRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubLibraryNavigationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun homeLibrarySeriesReaderAndBackKeepTheWebsiteSource() {
        val app = ApplicationProvider.getApplicationContext<NovelNestApplication>()
        val previousSource = app.navigationPreferences.lastSource
        val online = object : NovelRepository {
            override fun getHomeSections(source: NovelSourceType, forceRefresh: Boolean) = flowOf(Result.success(emptyList<NovelSection>()))
            override suspend fun searchNovels(source: NovelSourceType, query: String) = Result.success(emptyList<Novel>())
            override suspend fun getNovelById(novelId: String): Novel? = null
            override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String) = Result.failure<Novel>(IllegalStateException("Offline test"))
            override suspend fun getChapters(source: NovelSourceType, novelSlugOrUrl: String, forceRefresh: Boolean) = Result.success(emptyList<Chapter>())
            override suspend fun getChapterContent(source: NovelSourceType, chapterUrlOrId: String, forceRefresh: Boolean) = Result.failure<Chapter>(IllegalStateException("No online reader may be used"))
        }
        EpubLibraryTestStore().use { fixtures ->
            val series = runBlocking { app.epubRepository.createCollection("Navigation " + java.util.UUID.randomUUID().toString().take(8)) }
            var bookId: String? = null
            var reader: Activity? = null
            try {
                runBlocking {
                    app.epubRepository.importBooks(listOf(fixtures.fixture(1)), series)
                    val result = withTimeout(20_000) { app.epubRepository.importState.first { it is EpubImportState.Complete || it is EpubImportState.Failed } }
                    assertTrue(result.toString(), result is EpubImportState.Complete)
                    bookId = (result as EpubImportState.Complete).book.id
                    app.epubRepository.clearImportMessage()
                }
                compose.setContent { NovelNestTheme { NovelNestApp(online, app.navigationPreferences) } }
                compose.onNodeWithContentDescription("Open EPUB library").performClick()
                compose.onNodeWithText("EPUB Library").assertIsDisplayed()
                compose.waitUntil(5000) { compose.onAllNodesWithTag("EpubShelf:" + series).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("EpubShelf:" + series).performClick()
                compose.waitUntil(5000) { compose.onAllNodesWithTag("EpubShelf:" + bookId).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("EpubShelf:" + bookId).performClick()
                compose.waitUntil(5000) {
                    InstrumentationRegistry.getInstrumentation().runOnMainSync {
                        reader = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull { it is EpubReaderActivity }
                    }
                    reader != null
                }
                pressBack()
                compose.onNodeWithContentDescription("Series options").assertIsDisplayed()
                compose.onNodeWithContentDescription("Back").performClick()
                compose.onNodeWithText("EPUB Library").assertIsDisplayed()
                compose.onNodeWithContentDescription("Back").performClick()
                compose.onNodeWithContentDescription("Open EPUB library").assertIsDisplayed()
                assertEquals(previousSource, app.navigationPreferences.lastSource)
            } finally {
                reader?.let { activity -> InstrumentationRegistry.getInstrumentation().runOnMainSync { if (!activity.isFinishing) activity.finish() } }
                runBlocking { bookId?.let { app.epubRepository.delete(it) }; app.epubRepository.removeCollection(series) }
            }
        }
    }
}
