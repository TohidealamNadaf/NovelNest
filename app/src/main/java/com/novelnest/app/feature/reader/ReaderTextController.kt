package com.novelnest.app.feature.reader

import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.text.ChapterEscapeDecoder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PreparedReaderChapter(val source: NovelSourceType, val original: Chapter, val chapter: Chapter)

class ReaderTextState internal constructor(
    internal val entries: List<PreparedReaderChapter> = emptyList(),
    val failure: String? = null
) {
    fun resolve(source: NovelSourceType?, chapter: Chapter, enabled: Boolean): Chapter? {
        if (!enabled || !ChapterEscapeDecoder.supports(source)) return chapter
        return entries.firstOrNull { it.source == source && (it.original === chapter || it.original == chapter) }?.chapter
    }
}

/** Owned by one reader ViewModel. No Activity, view, repository cache, polling, or network work. */
internal class ReaderTextController(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val preparationTimeoutMs: Long = PREPARATION_TIMEOUT_MS,
    private val prepare: suspend (NovelSourceType?, Chapter, Boolean, () -> Unit) -> Chapter = { source, chapter, enabled, check ->
        ChapterEscapeDecoder.prepare(source, chapter, enabled, check)
    }
) {
    private val mutable = MutableStateFlow(ReaderTextState())
    val state = mutable.asStateFlow()
    private var source: NovelSourceType? = null
    private var original: Chapter? = null
    private var enabled = false
    private var generation = 0L
    private var work: Job? = null
    private var cache = emptyList<PreparedReaderChapter>()

    fun submit(source: NovelSourceType?, chapter: Chapter?, enabled: Boolean) {
        val decode = enabled && ChapterEscapeDecoder.supports(source)
        if (this.source == source && original === chapter && this.enabled == decode) return
        this.source = source; original = chapter; this.enabled = decode
        val epoch = ++generation
        work?.cancel()
        if (chapter == null || !decode || source == null) {
            mutable.value = ReaderTextState(cache)
            return
        }
        val cached = cache.firstOrNull { it.source == source && (it.original === chapter || it.original == chapter) }
        if (cached != null) {
            // StateFlow may conflate a refreshed object with identical data. Rebind the cache entry
            // to the new instance without rescanning its text.
            cache = listOf(cached.copy(original = chapter)) + cache.filterNot { it === cached }
            mutable.value = ReaderTextState(cache)
            return
        }
        mutable.value = ReaderTextState(cache)
        work = scope.launch {
            try {
                val prepared = withTimeout(preparationTimeoutMs) {
                    withContext(dispatcher) {
                        val context = currentCoroutineContext()
                        prepare(source, chapter, true) { context.ensureActive() }
                    }
                }
                ensureActive()
                if (generation != epoch) return@launch
                if (chapter.hasReadableText() && !prepared.hasReadableText()) {
                    failed(epoch, source, chapter, "Decoded chapter has no readable text. Original chapter shown.")
                    return@launch
                }
                val entry = PreparedReaderChapter(source, chapter, prepared)
                var candidates = listOf(entry) + cache.filterNot { it.source == source && it.original.id == chapter.id }
                candidates = candidates.take(2)
                while (candidates.size > 1 && candidates.sumOf(::weight) > MAX_RETAINED_BYTES) candidates = candidates.dropLast(1)
                cache = if (weight(entry) <= MAX_RETAINED_BYTES) candidates else emptyList()
                mutable.value = ReaderTextState(if (cache.isEmpty()) listOf(entry) else cache)
            } catch (_: TimeoutCancellationException) {
                ensureActive() // A cancelled reader must never publish a timeout fallback.
                failed(epoch, source, chapter, "Decoding took too long. Original chapter shown.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed(epoch, source, chapter, "Could not decode escaped characters. Original chapter shown.") }
            catch (_: OutOfMemoryError) { failed(epoch, source, chapter, "Not enough memory to decode this chapter. Original chapter shown.") }
        }
    }
    private fun failed(epoch: Long, source: NovelSourceType, chapter: Chapter, message: String) {
        if (generation != epoch) return
        cache = emptyList()
        mutable.value = ReaderTextState(listOf(PreparedReaderChapter(source, chapter, chapter)), message)
    }
    private fun weight(entry: PreparedReaderChapter): Long =
        (entry.original.title.length.toLong() + entry.chapter.title.length +
            entry.original.paragraphs.sumOf { it.length.toLong() } + entry.chapter.paragraphs.sumOf { it.length.toLong() }) * 2L
    companion object {
        internal const val MAX_RETAINED_BYTES = 1024L * 1024
        internal const val PREPARATION_TIMEOUT_MS = 2_000L
    }
}
