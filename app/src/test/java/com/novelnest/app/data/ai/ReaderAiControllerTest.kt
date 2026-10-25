package com.novelnest.app.data.ai

import com.novelnest.app.feature.reader.Chapter
import com.novelnest.app.feature.reader.ReaderAiController
import com.novelnest.app.feature.reader.ReaderContentVersion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ReaderAiControllerTest {
    private suspend fun ready(controller: ReaderAiController) = withTimeout(5_000) {
        while (controller.state.value.busy) delay(5)
        assertEquals(AiPhase.READY, controller.state.value.progress.phase)
    }
    @Test fun repeatedTapsShareOneRequestAndReuseCompletedResult() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val provider = FakeProvider(AiProviderId.OPEN_ROUTER).apply { respond = { _, prompt -> entered.complete(Unit); release.await(); validResponse(prompt) } }
            val controller = ReaderAiController(scope, ChapterAiRepository(FakeConfiguration(configured(freeRoute)), FakeCache(), listOf(provider)))
            controller.sourceChanged(chapter()); controller.request(AiAction.SUMMARY)
            repeat(30) { controller.request(AiAction.SUMMARY) }
            entered.await(); release.complete(Unit); ready(controller)
            controller.request(AiAction.SUMMARY)
            assertEquals(1, provider.calls.get())
        } finally { scope.cancel() }
    }
    @Test fun chapterSwitchCannotReceiveAnOutgoingResult() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val provider = FakeProvider(AiProviderId.OPEN_ROUTER).apply { respond = { _, prompt -> entered.complete(Unit); release.await(); validResponse(prompt) } }
            val controller = ReaderAiController(scope, ChapterAiRepository(FakeConfiguration(configured(freeRoute)), FakeCache(), listOf(provider)))
            controller.sourceChanged(chapter("one")); controller.request(AiAction.REWRITE)
            entered.await(); controller.sourceChanged(chapter("two")); release.complete(Unit)
            delay(30)
            assertEquals("two", controller.state.value.chapterId)
            assertNull(controller.state.value.rewrite)
            assertEquals(ReaderContentVersion.ORIGINAL, controller.state.value.version)
        } finally { scope.cancel() }
    }
    @Test fun immediateRequestAfterChapterChangeWaitsForCancelledOwner() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val entered = CompletableDeferred<Unit>(); val wait = CompletableDeferred<Unit>()
            val provider = FakeProvider(AiProviderId.OPEN_ROUTER).apply { respond = { _, prompt ->
                if (calls.get() == 1) { entered.complete(Unit); wait.await() }
                validResponse(prompt)
            } }
            val controller = ReaderAiController(scope, ChapterAiRepository(FakeConfiguration(configured(freeRoute)), FakeCache(), listOf(provider)))
            controller.sourceChanged(chapter("one")); controller.request(AiAction.SUMMARY); entered.await()
            controller.sourceChanged(chapter("two")); controller.request(AiAction.SUMMARY); ready(controller)
            assertEquals("two", controller.state.value.chapterId)
            assertNotNull(controller.state.value.summary)
            assertNull(controller.state.value.failure)
        } finally { scope.cancel() }
    }
    @Test fun rewrittenDisplayNeverMutatesSourceAndCanBeReverted() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val provider = FakeProvider(AiProviderId.OPEN_ROUTER)
            val controller = ReaderAiController(scope, ChapterAiRepository(FakeConfiguration(configured(freeRoute)), FakeCache(), listOf(provider)))
            val source = chapter()
            controller.sourceChanged(source); controller.request(AiAction.REWRITE); ready(controller)
            assertEquals(ReaderContentVersion.REWRITE, controller.state.value.version)
            controller.showVersion(ReaderContentVersion.ORIGINAL)
            assertEquals("original", controller.state.value.contentKey)
            assertEquals(source.paragraphs, chapter().paragraphs)
            assertNotNull(controller.state.value.rewrite)
        } finally { scope.cancel() }
    }
    @Test fun refreshedSourceInvalidatesBothResultsAndDisplayVersion() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val controller = ReaderAiController(scope, ChapterAiRepository(FakeConfiguration(configured(freeRoute)), FakeCache(), listOf(FakeProvider(AiProviderId.OPEN_ROUTER))))
            controller.sourceChanged(chapter()); controller.request(AiAction.REWRITE); ready(controller)
            controller.sourceChanged(chapter().copy(paragraphs = listOf("Changed source.")))
            assertNull(controller.state.value.summary); assertNull(controller.state.value.rewrite)
            assertEquals(ReaderContentVersion.ORIGINAL, controller.state.value.version)
        } finally { scope.cancel() }
    }
    @Test fun staleDisplayIsNeverAppliedToSameIdWithDifferentText() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val controller = ReaderAiController(scope, ChapterAiRepository(FakeConfiguration(configured(freeRoute)), FakeCache(), listOf(FakeProvider(AiProviderId.OPEN_ROUTER))))
            controller.sourceChanged(chapter()); controller.request(AiAction.REWRITE); ready(controller)
            val refreshed = Chapter("one", "Chapter 1", listOf("Different paragraph."))
            assertEquals(refreshed.paragraphs, controller.state.value.paragraphs(refreshed))
        } finally { scope.cancel() }
    }
    @Test fun cancelDoesNotClearCompletedResultsOrResumeGeneration() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val controller = ReaderAiController(scope, ChapterAiRepository(FakeConfiguration(configured(freeRoute)), FakeCache(), listOf(FakeProvider(AiProviderId.OPEN_ROUTER))))
            controller.sourceChanged(chapter()); controller.request(AiAction.SUMMARY); ready(controller)
            val result = controller.state.value.summary
            controller.cancel()
            assertEquals(result, controller.state.value.summary)
            assertFalse(controller.state.value.busy)
        } finally { scope.cancel() }
    }
}
