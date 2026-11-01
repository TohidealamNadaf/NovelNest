package com.novelnest.app.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.repository.NovelRepository
import com.novelnest.app.feature.reader.Chapter
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Compose/NavHost dispatch tests. Reader slots count initialization without real networking. */
@RunWith(AndroidJUnit4::class)
class ReaderEntryNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController
    private var textEntries = 0
    private var imageEntries = 0
    private class Repository : NovelRepository {
        var gate: CompletableDeferred<Novel?>? = null
        var fail = false
        var chapterCalls = 0
        override suspend fun getNovelById(novelId: String): Novel? {
            if (fail) throw IOException("Unavailable")
            return gate?.await()
        }
        override fun getHomeSections(source: NovelSourceType, forceRefresh: Boolean) = flowOf(Result.success(emptyList<NovelSection>()))
        override suspend fun searchNovels(source: NovelSourceType, query: String) = Result.success(emptyList<Novel>())
        override suspend fun getNovelDetails(source: NovelSourceType, novelUrl: String) = Result.failure<Novel>(UnsupportedOperationException())
        override suspend fun getChapters(source: NovelSourceType, novelSlugOrUrl: String, forceRefresh: Boolean): Result<List<Chapter>> {
            chapterCalls++
            return Result.success(emptyList())
        }
        override suspend fun getChapterContent(source: NovelSourceType, chapterUrlOrId: String, forceRefresh: Boolean) = Result.failure<Chapter>(UnsupportedOperationException())
    }

    private fun install(repo: Repository) {
        compose.setContent {
            MaterialTheme {
                nav = rememberNavController()
                NavHost(nav, startDestination = "home") {
                    composable("home") { Text("Home") }
                    for (route in listOf("reader/{novelId}", "manhwa-reader/{novelId}")) {
                        composable(route) { entry ->
                            ReaderEntryContent(
                                novelId = entry.arguments?.getString("novelId").orEmpty(),
                                repository = repo,
                                bookmarkManager = null,
                                onSourceResolved = {},
                                onBack = { nav.popBackStack() },
                                textReader = {
                                    DisposableEffect(Unit) { textEntries++; onDispose {} }
                                    Text("Prose reader")
                                },
                                manhwaReader = {
                                    DisposableEffect(Unit) { imageEntries++; onDispose {} }
                                    Text("Manhwa reader")
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @Test fun oldAndNewRoutesDispatchBeforeEitherReaderIsCreated() {
        val repo = Repository()
        install(repo)
        for (route in listOf("reader/asura-book", "manhwa-reader/asura-book", "reader/np-book", "reader/fwn-book")) {
            val gate = CompletableDeferred<Novel?>()
            var textBefore = 0
            var imagesBefore = 0
            compose.runOnIdle {
                textBefore = textEntries
                imagesBefore = imageEntries
                repo.gate = gate
                nav.navigate(route)
            }
            compose.onNodeWithText("Back").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(0, repo.chapterCalls)
                assertEquals(textBefore, textEntries)
                assertEquals(imagesBefore, imageEntries)
            }
            compose.runOnIdle { gate.complete(null) }
            val manhwa = route.contains("asura-")
            compose.onNodeWithText(if (manhwa) "Manhwa reader" else "Prose reader").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(textBefore + if (manhwa) 0 else 1, textEntries)
                assertEquals(imagesBefore + if (manhwa) 1 else 0, imageEntries)
                assertEquals(route.substringBefore('/'), nav.currentDestination?.route?.substringBefore('/'))
                nav.popBackStack()
            }
            compose.onNodeWithText("Home").assertIsDisplayed()
        }
    }

    @Test fun leavingDuringLookupCannotReopenTheReader() {
        val repo = Repository().apply { gate = CompletableDeferred() }
        install(repo)
        compose.runOnIdle { nav.navigate("reader/asura-book") }
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.runOnIdle { repo.gate!!.complete(null) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("home", nav.currentDestination?.route)
            assertEquals(0, textEntries)
            assertEquals(0, imageEntries)
        }
    }

    @Test fun failureOffersExplicitRetryWithoutCreatingAReader() {
        val repo = Repository().apply { fail = true }
        install(repo)
        compose.runOnIdle { nav.navigate("reader/asura-book") }
        compose.onNodeWithText("Unable to open reader. Please retry.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, textEntries)
            assertEquals(0, imageEntries)
            repo.fail = false
        }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Manhwa reader").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, textEntries); assertEquals(1, imageEntries) }
    }

    @Test fun changedIdentityDoesNotReuseThePreviousSourceWhileLoading() {
        val id = mutableStateOf("np-book")
        val repo = Repository()
        compose.setContent {
            MaterialTheme {
                ReaderEntryContent(id.value, repo, null, {}, {},
                    textReader = { DisposableEffect(id.value) { textEntries++; onDispose {} }; Text("Prose reader") },
                    manhwaReader = { DisposableEffect(id.value) { imageEntries++; onDispose {} }; Text("Manhwa reader") })
            }
        }
        compose.onNodeWithText("Prose reader").assertIsDisplayed()
        compose.runOnIdle { repo.gate = CompletableDeferred(); id.value = "asura-book" }
        compose.onNodeWithText("Back").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, textEntries)
            assertEquals(0, imageEntries)
            repo.gate!!.complete(null)
        }
        compose.onNodeWithText("Manhwa reader").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, textEntries); assertEquals(1, imageEntries) }
    }
}
