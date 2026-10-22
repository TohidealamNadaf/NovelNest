package com.novelnest.app.feature.reader

import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.repository.NovelRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

/** Release startup as soon as the requested chapter exists; keep pagination in the background. */
internal fun CoroutineScope.startReaderCatalog(
    repository: NovelRepository,
    source: NovelSourceType,
    novelUrl: String,
    requestedIndex: Int
): Deferred<Result<List<Chapter>>> {
    val ready = CompletableDeferred<Result<List<Chapter>>>()
    launch {
        var latest: Result<List<Chapter>> = Result.failure(IOException("No chapters available from source."))
        try {
            repository.getReaderChaptersFlow(source, novelUrl).collect { result ->
                // Keep usable navigation if a later catalog page fails.
                if (result.isSuccess || latest.getOrNull().isNullOrEmpty()) latest = result
                if (!ready.isCompleted && requestedIndex in result.getOrNull().orEmpty().indices) ready.complete(result)
            }
            if (!ready.isCompleted) ready.complete(latest)
        } catch (cancelled: CancellationException) {
            ready.cancel(cancelled)
            throw cancelled
        } catch (error: Exception) {
            if (!ready.isCompleted) ready.complete(latest.takeIf { !it.getOrNull().isNullOrEmpty() } ?: Result.failure(error))
        }
    }
    return ready
}
