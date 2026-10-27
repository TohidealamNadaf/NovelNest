package com.novelnest.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.novelnest.app.core.designsystem.NovelBackground
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.data.bookmark.BookmarkManager
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.repository.NovelRepository
import kotlinx.coroutines.CancellationException

/** Metadata only: deciding a reader must never start a catalog/content request. */
internal suspend fun resolveReaderSource(
    novelId: String,
    repository: NovelRepository,
    bookmarkManager: BookmarkManager?
): NovelSourceType {
    val novel = repository.getNovelById(novelId) ?: bookmarkManager?.getBookmark(novelId)
    // A stored record's source is authoritative; prefixes are only cold-route hints.
    return novel?.source ?: when {
        novelId.startsWith("asura-") -> NovelSourceType.ASURA_SCANS
        novelId.startsWith("fwn-") -> NovelSourceType.FREE_WEB_NOVEL
        else -> NovelSourceType.NOVEL_PHOENIX
    }
}

/**
 * Both current and legacy routes dispatch in place. No navigation side effect can re-open a
 * reader after Back, and neither reader ViewModel exists until the source has been resolved.
 */
@Composable
internal fun ReaderEntryContent(
    novelId: String,
    repository: NovelRepository,
    bookmarkManager: BookmarkManager?,
    onSourceResolved: (NovelSourceType) -> Unit,
    onBack: () -> Unit,
    textReader: @Composable () -> Unit,
    manhwaReader: @Composable () -> Unit
) = key(novelId, repository, bookmarkManager) {
    // A reused entry with different arguments must never expose the previous source for a frame.
    var attempt by remember(novelId) { mutableIntStateOf(0) }
    val result by produceState<Result<NovelSourceType>?>(null, novelId, repository, bookmarkManager, attempt) {
        value = null
        value = try {
            Result.success(resolveReaderSource(novelId, repository, bookmarkManager))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("ReaderEntry", "Source lookup failed: ${error.javaClass.simpleName}")
            Result.failure(IllegalStateException("Unable to resolve the reader source"))
        }
    }
    val source = result?.getOrNull()
    val currentOnSourceResolved by rememberUpdatedState(onSourceResolved)
    LaunchedEffect(source) { source?.let { currentOnSourceResolved(it) } }

    when {
        source != null -> if (source.isManhwa) manhwaReader() else textReader()
        else -> Column(
            modifier = Modifier.fillMaxSize().background(NovelBackground),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            if (result?.isFailure == true) {
                Text("Unable to open reader. Please retry.", color = NovelTextPrimary)
                Button(onClick = { attempt++ }) { Text("Retry") }
            } else {
                CircularProgressIndicator(color = NovelPrimary)
            }
            TextButton(onClick = onBack) { Text("Back", color = NovelPrimary) }
        }
    }
}
