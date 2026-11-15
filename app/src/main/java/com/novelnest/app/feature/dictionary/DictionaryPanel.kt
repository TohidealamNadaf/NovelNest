package com.novelnest.app.feature.dictionary

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.R
import com.novelnest.app.core.designsystem.NovelError
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.data.preferences.ReaderTheme
import java.text.DateFormat
import java.util.Date

/** Native, lazy definition rows; no reader/WebView re-layout or audio focus changes. */
@Composable
internal fun DictionaryPanel(
    state: DictionaryUiState,
    theme: ReaderTheme,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    val isDark = theme.isDark
    val primaryAccent = if (isDark) NovelPrimary else Color(0xFF9A5B13)
    val onPrimaryAccent = if (isDark) NovelOnPrimary else Color.White
    val sheetBackground = if (isDark) Color(0xFF12151B) else theme.surfaceColor
    val cardBackground = if (isDark) Color(0xFF1B1F27) else theme.backgroundColor.copy(alpha = 0.72f)
    val cardBorderColor = if (isDark) Color(0xFF2B313D) else theme.secondaryTextColor.copy(alpha = 0.22f)
    val outerBorderColor = if (isDark) NovelPrimary.copy(alpha = 0.32f) else primaryAccent.copy(alpha = 0.35f)
    val primaryText = if (isDark) Color(0xFFE6EDF3) else theme.textColor
    val secondaryText = if (isDark) Color(0xFF8B949E) else theme.secondaryTextColor

    val colors = if (isDark) {
        darkColorScheme(
            primary = primaryAccent,
            onPrimary = onPrimaryAccent,
            surface = sheetBackground,
            onSurface = primaryText,
            background = sheetBackground,
            onSurfaceVariant = secondaryText
        )
    } else {
        lightColorScheme(
            primary = primaryAccent,
            onPrimary = onPrimaryAccent,
            surface = sheetBackground,
            onSurface = primaryText,
            background = sheetBackground,
            onSurfaceVariant = secondaryText
        )
    }

    val maxModalHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.54f).coerceIn(260.dp, 420.dp)
    val primaryPhonetic = remember(state.result) {
        state.result?.entries?.firstOrNull { it.phonetics.isNotEmpty() }?.phonetics?.firstOrNull()
    }

    MaterialTheme(colorScheme = colors) {
        // A translucent selection background retains contrast with native Compose text in every theme.
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = primaryAccent,
                backgroundColor = primaryAccent.copy(alpha = 0.25f)
            )
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = sheetBackground,
                border = BorderStroke(1.dp, outerBorderColor),
                shadowElevation = 16.dp,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxModalHeight)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ─── COMPACT HEADER (Matches EpubSubMenuHeader) ───
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(primaryAccent.copy(alpha = 0.14f))
                                .border(1.dp, primaryAccent.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                tint = primaryAccent,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = state.query.ifBlank { stringResource(R.string.dictionary_title) },
                                    color = primaryText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .semantics { heading() }
                                )
                                if (!primaryPhonetic.isNullOrBlank()) {
                                    Text(
                                        text = primaryPhonetic,
                                        color = primaryAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.dictionary_title),
                                color = secondaryText,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (state.query.isNotBlank()) {
                            val refreshDesc = stringResource(R.string.dictionary_refresh)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(cardBackground)
                                    .border(1.dp, cardBorderColor, RoundedCornerShape(9.dp))
                                    .clickable(
                                        enabled = state.canRetry,
                                        role = Role.Button,
                                        onClickLabel = refreshDesc,
                                        onClick = onRetry
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = refreshDesc,
                                    tint = if (state.canRetry) primaryAccent else secondaryText.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        val closeDesc = stringResource(R.string.dictionary_close)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(cardBackground)
                                .border(1.dp, cardBorderColor, RoundedCornerShape(9.dp))
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = closeDesc,
                                    onClick = onClose
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = closeDesc,
                                tint = secondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = cardBorderColor.copy(alpha = 0.7f))

                    // ─── STATUS BANNERS (Refreshing / Cached) ───
                    if (state.loading && state.result != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp)),
                                color = primaryAccent,
                                trackColor = cardBorderColor
                            )
                            Text(
                                text = stringResource(R.string.dictionary_refreshing),
                                fontSize = 10.5.sp,
                                color = secondaryText
                            )
                        }
                    }

                    if (state.cached) {
                        val saved = remember(state.result?.fetchedAtMs) {
                            state.result?.fetchedAtMs?.let {
                                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                            }.orEmpty()
                        }
                        Text(
                            text = stringResource(
                                if (state.stale) R.string.dictionary_stale else R.string.dictionary_cached,
                                saved
                            ),
                            fontSize = 10.sp,
                            color = secondaryText
                        )
                    }

                    // ─── SCROLLABLE DEFINITION BODY ───
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.problem?.let { problem ->
                            item(key = "problem") {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = cardBackground,
                                    border = BorderStroke(1.dp, NovelError.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ErrorOutline,
                                                contentDescription = null,
                                                tint = NovelError,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = problem.message,
                                                color = primaryText,
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .semantics { liveRegion = LiveRegionMode.Polite }
                                            )
                                        }
                                        if (state.retryBlocked) {
                                            Text(
                                                text = stringResource(R.string.dictionary_cooldown),
                                                fontSize = 10.5.sp,
                                                color = secondaryText
                                            )
                                        }
                                        if (state.canRetry) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                TextButton(
                                                    onClick = onRetry,
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.dictionary_retry),
                                                        color = primaryAccent,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (state.paused) {
                            item(key = "paused") {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = cardBackground,
                                    border = BorderStroke(1.dp, cardBorderColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = stringResource(R.string.dictionary_paused),
                                        color = secondaryText,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }

                        if (state.loading && state.result == null) {
                            item(key = "loading") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp,
                                        color = primaryAccent
                                    )
                                    Text(
                                        text = stringResource(R.string.dictionary_loading),
                                        color = secondaryText,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        state.result?.entries?.forEachIndexed { entryIndex, entry ->
                            val hasDistinctHeadword = !entry.word.equals(state.query, ignoreCase = true)
                            val extraPhonetics = if (entryIndex == 0 && primaryPhonetic != null) {
                                entry.phonetics.drop(1)
                            } else {
                                entry.phonetics
                            }
                            if (hasDistinctHeadword || extraPhonetics.isNotEmpty()) {
                                item(key = "entry-$entryIndex") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    ) {
                                        if (hasDistinctHeadword) {
                                            Text(
                                                text = entry.word,
                                                color = primaryText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                modifier = Modifier.semantics { heading() }
                                            )
                                        }
                                        if (extraPhonetics.isNotEmpty()) {
                                            Text(
                                                text = extraPhonetics.joinToString(" • "),
                                                color = secondaryText,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }

                            entry.meanings.forEachIndexed { meaningIndex, meaning ->
                                item(key = "meaning-$entryIndex-$meaningIndex") {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = cardBackground,
                                        border = BorderStroke(1.dp, cardBorderColor),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(7.dp)
                                        ) {
                                            // Part of speech badge
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = primaryAccent.copy(alpha = 0.15f),
                                                border = BorderStroke(0.5.dp, primaryAccent.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = meaning.partOfSpeech,
                                                    color = primaryAccent,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                        .semantics { heading() }
                                                )
                                            }

                                            // Definitions list
                                            meaning.definitions.forEachIndexed { index, definition ->
                                                if (index > 0) {
                                                    HorizontalDivider(
                                                        color = cardBorderColor.copy(alpha = 0.55f),
                                                        modifier = Modifier.padding(vertical = 1.dp)
                                                    )
                                                }
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    SelectionContainer {
                                                        Text(
                                                            text = "${index + 1}. ${definition.text}",
                                                            color = primaryText,
                                                            fontSize = 12.5.sp,
                                                            lineHeight = 17.5.sp
                                                        )
                                                    }
                                                    definition.example?.let { example ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(start = 6.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(2.dp)
                                                                    .heightIn(min = 14.dp)
                                                                    .background(
                                                                        primaryAccent.copy(alpha = 0.5f),
                                                                        RoundedCornerShape(1.dp)
                                                                    )
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = stringResource(R.string.dictionary_example, example),
                                                                color = secondaryText,
                                                                fontSize = 11.sp,
                                                                lineHeight = 15.sp,
                                                                fontStyle = FontStyle.Italic
                                                            )
                                                        }
                                                    }
                                                    RelatedWords(
                                                        label = stringResource(R.string.dictionary_synonyms),
                                                        words = definition.synonyms,
                                                        primaryAccent = primaryAccent,
                                                        secondaryText = secondaryText
                                                    )
                                                    RelatedWords(
                                                        label = stringResource(R.string.dictionary_antonyms),
                                                        words = definition.antonyms,
                                                        primaryAccent = primaryAccent,
                                                        secondaryText = secondaryText
                                                    )
                                                }
                                            }

                                            if (meaning.synonyms.isNotEmpty() || meaning.antonyms.isNotEmpty()) {
                                                HorizontalDivider(color = cardBorderColor.copy(alpha = 0.55f))
                                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    RelatedWords(
                                                        label = stringResource(R.string.dictionary_synonyms),
                                                        words = meaning.synonyms,
                                                        primaryAccent = primaryAccent,
                                                        secondaryText = secondaryText
                                                    )
                                                    RelatedWords(
                                                        label = stringResource(R.string.dictionary_antonyms),
                                                        words = meaning.antonyms,
                                                        primaryAccent = primaryAccent,
                                                        secondaryText = secondaryText
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (entry.sources.isNotEmpty() || entry.license != null) {
                                item(key = "source-$entryIndex") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        entry.sources.forEach { url ->
                                            DictionarySourceLink(url = url, primaryAccent = primaryAccent)
                                        }
                                        entry.license?.let { license ->
                                            if (license.url != null) {
                                                DictionarySourceLink(
                                                    url = license.url,
                                                    label = license.name,
                                                    primaryAccent = primaryAccent
                                                )
                                            } else {
                                                Text(
                                                    text = license.name,
                                                    color = secondaryText,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "provider") {
                            Text(
                                text = stringResource(R.string.dictionary_provider),
                                fontSize = 10.sp,
                                lineHeight = 13.5.sp,
                                color = secondaryText.copy(alpha = 0.8f),
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedWords(
    label: String,
    words: List<String>,
    primaryAccent: Color,
    secondaryText: Color
) {
    if (words.isEmpty()) return
    var expanded by remember(words) { mutableStateOf(false) }
    val visible = if (expanded) words else words.take(8)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "$label: ${visible.joinToString(", ")}",
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            color = secondaryText
        )
        if (words.size > 8 && !expanded) {
            Text(
                text = stringResource(R.string.dictionary_more),
                color = primaryAccent,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(role = Role.Button) { expanded = true }
                    .padding(vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun DictionarySourceLink(
    url: String,
    label: String = stringResource(R.string.dictionary_source),
    primaryAccent: Color
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button) {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
                    )
                } catch (_: ActivityNotFoundException) {
                    /* A device without a browser still has the definition. */
                } catch (_: SecurityException) {
                    /* External navigation is optional. */
                }
            }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            color = primaryAccent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            tint = primaryAccent,
            modifier = Modifier.size(11.dp)
        )
    }
}
