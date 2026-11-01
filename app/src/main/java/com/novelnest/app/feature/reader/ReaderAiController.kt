package com.novelnest.app.feature.reader

import com.novelnest.app.data.ai.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

enum class ReaderContentVersion { ORIGINAL, REWRITE }

data class ReaderAiState(
    val chapterId: String = "",
    val sourceTitle: String = "",
    val sourceParagraphs: List<String> = emptyList(),
    val action: AiAction? = null,
    val progress: AiProgress = AiProgress(AiPhase.IDLE),
    val failure: AiFailure? = null,
    val summary: AiResult? = null,
    val rewrite: AiResult? = null,
    val version: ReaderContentVersion = ReaderContentVersion.ORIGINAL,
    val extractionCollectionId: String? = null
) {
    val busy: Boolean get() = progress.phase !in setOf(AiPhase.IDLE, AiPhase.READY, AiPhase.FAILED, AiPhase.CANCELLED)
    val contentKey: String get() = if (version == ReaderContentVersion.REWRITE && rewrite != null)
        "rewrite:${rewrite.sourceFingerprint}:${rewrite.createdAtMs}" else "original"
    fun matches(chapter: Chapter) = chapterId == chapter.id && sourceTitle == chapter.title && sourceParagraphs == chapter.paragraphs
    fun paragraphs(chapter: Chapter): List<String> = if (matches(chapter) && version == ReaderContentVersion.REWRITE)
        rewrite?.rewrittenParagraphs ?: chapter.paragraphs else chapter.paragraphs
}

/** Main-thread actions; background progress uses an atomic generation plus StateFlow's atomic update. */
class ReaderAiController(private val scope: CoroutineScope, private val repository: ChapterAiRepository) {
    private val mutable = MutableStateFlow(ReaderAiState())
    val state: StateFlow<ReaderAiState> = mutable.asStateFlow()
    private val generation = AtomicLong()
    private var source: AiChapterInput? = null
    private var work: Job? = null

    fun sourceChanged(input: AiChapterInput?) {
        if (source == input) return
        generation.incrementAndGet(); work?.cancel()
        source = input
        mutable.value = ReaderAiState(input?.chapterId.orEmpty(), input?.title.orEmpty(), input?.paragraphs ?: emptyList())
    }

    fun request(action: AiAction) {
        if (action == AiAction.EXTRACT) return
        request(action, null, null) { _, _ -> }
    }

    fun extract(collectionId: String, target: String, commit: suspend (AiChapterInput, AiResult) -> Unit) {
        request(AiAction.EXTRACT, collectionId, target, commit)
    }

    private fun request(action: AiAction, collectionId: String?, target: String?, commit: suspend (AiChapterInput, AiResult) -> Unit) {
        if (work?.isActive == true) return
        val input = source ?: return
        val previous = mutable.value
        val existing = when (action) { AiAction.SUMMARY -> previous.summary; AiAction.REWRITE -> previous.rewrite; AiAction.EXTRACT -> null }
        if (existing != null) {
            mutable.value = previous.copy(action = action, progress = AiProgress(AiPhase.READY), failure = null,
                version = if (action == AiAction.REWRITE) ReaderContentVersion.REWRITE else previous.version)
            return
        }
        val epoch = generation.incrementAndGet()
        val previousWork = work
        mutable.value = previous.copy(action = action, progress = AiProgress(AiPhase.CHECKING_CACHE), failure = null,
            extractionCollectionId = collectionId)
        work = scope.launch {
            try {
                // Retire the cancelled owner's repository lock before starting a replacement request.
                previousWork?.join()
                val requestInput = if (action == AiAction.EXTRACT) input.copy(extractionTarget = target,
                    extractionCacheScope = collectionId.orEmpty()) else input
                val result = repository.generate(requestInput, action) { progress ->
                    mutable.update { if (generation.get() == epoch) it.copy(progress = progress) else it }
                }
                if (generation.get() != epoch || source != input) return@launch
                if (action == AiAction.EXTRACT) mutable.update { it.copy(progress = AiProgress(AiPhase.VALIDATING, result.route.provider,
                    message = "Saving discoveries")) }
                commit(requestInput, result)
                if (generation.get() != epoch || source != input) return@launch
                mutable.update { it.copy(progress = AiProgress(AiPhase.READY), failure = null,
                    summary = if (action == AiAction.SUMMARY) result else it.summary,
                    rewrite = if (action == AiAction.REWRITE) result else it.rewrite,
                    version = if (action == AiAction.REWRITE) ReaderContentVersion.REWRITE else it.version) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val failure = error as? AiException ?: AiException(AiErrorCode.UNAVAILABLE, "AI Reading Tools could not complete this request. The original is unchanged.")
                mutable.update { if (generation.get() == epoch) it.copy(progress = AiProgress(AiPhase.FAILED),
                    failure = AiFailure(failure.code, failure.message.orEmpty(), failure.retryAfterSeconds)) else it }
            }
        }
    }

    fun cancel() {
        generation.incrementAndGet(); work?.cancel()
        mutable.update { if (it.busy) it.copy(progress = AiProgress(AiPhase.CANCELLED), failure = null) else it }
    }
    fun showVersion(version: ReaderContentVersion) {
        if (version == ReaderContentVersion.REWRITE && mutable.value.rewrite == null) return
        mutable.update { it.copy(version = version) }
    }
}
