package com.novelnest.app.feature.epub

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url

private fun resourceKey(href: Url): String = href.normalize().removeFragment().toString()

/** A percentage seek must not carry an old text selector, HTML anchor or position. */
internal fun epubChapterSeekLocator(current: Locator, expectedResource: String, progression: Double): Locator? {
    if (!progression.isFinite() || progression !in 0.0..1.0 || resourceKey(current.href) != expectedResource) return null
    return Locator(
        href = current.href.removeFragment(), mediaType = current.mediaType,
        title = current.title, locations = Locator.Locations(progression = progression)
    )
}

private data class ChapterSeekPreview(val value: Float, val source: Locator, val committed: Boolean = false)

/** Dragging only updates this control; Readium receives one jump on release, never one per pixel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpubChapterProgressControl(
    currentChapterNumber: Int,
    totalChapters: Int,
    chapterLocator: State<Locator?>,
    bookPercent: Int,
    enabled: Boolean,
    onSeek: (String, Double) -> Boolean
) {
    val current = chapterLocator.value
    val resource = current?.href?.let(::resourceKey)
    val progression = current?.locations?.progression?.takeIf { it.isFinite() && it in 0.0..1.0 }
    val canSeek = enabled && resource != null && progression != null
    var preview by remember(resource, canSeek) { mutableStateOf<ChapterSeekPreview?>(null) }
    val interactions = remember(resource, canSeek) { MutableInteractionSource() }
    LaunchedEffect(interactions) {
        interactions.interactions.collect { interaction ->
            if (interaction is DragInteraction.Cancel &&
                preview?.committed != true) preview = null
        }
    }
    val pending = preview?.takeIf { it.committed }
    // A clamped endpoint or one-screen chapter may emit no locator. Release the preview without
    // polling or writing a synthetic position; Readium's actual locator remains authoritative.
    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        withTimeoutOrNull(2500) { snapshotFlow { chapterLocator.value }.first { it != pending.source } }
        if (preview == pending) preview = null
    }
    val value = preview?.value ?: progression?.toFloat() ?: 0f
    val chapterPercent = (value * 100).toInt().coerceIn(0, 100)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = NovelTextPrimary, fontWeight = FontWeight.Medium)) { append("Chapter ") }
                    withStyle(SpanStyle(color = NovelPrimary, fontWeight = FontWeight.Bold)) { append(currentChapterNumber.toString()) }
                    withStyle(SpanStyle(color = NovelTextSecondary, fontWeight = FontWeight.Medium)) { append(" / $totalChapters") }
                },
                fontSize = 14.sp
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = NovelPrimary, fontWeight = FontWeight.SemiBold)) { append("$chapterPercent%") }
                    withStyle(SpanStyle(color = NovelTextSecondary, fontWeight = FontWeight.Medium)) { append(" \u2022 Book $bookPercent%") }
                },
                fontSize = 13.sp
            )
        }
        Slider(
            value = value,
            onValueChange = { next ->
                if (canSeek && next.isFinite())
                    preview = ChapterSeekPreview(next.coerceIn(0f, 1f), preview?.source ?: current)
            },
            onValueChangeFinished = {
                val target = preview
                if (canSeek && target != null && !target.committed) {
                    preview = target.copy(committed = true)
                    if (!onSeek(resource, target.value.toDouble())) preview = null
                } else if (!canSeek) preview = null
            },
            enabled = canSeek,
            interactionSource = interactions,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = NovelPrimary,
                activeTrackColor = NovelPrimary,
                inactiveTrackColor = Color(0xFF2B313D)
            ),
            modifier = Modifier.fillMaxWidth().height(28.dp)
                .semantics { contentDescription = "Chapter progress" }
        )
    }
}
