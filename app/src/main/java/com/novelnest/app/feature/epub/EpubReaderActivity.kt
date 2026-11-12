package com.novelnest.app.feature.epub

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.List
import kotlinx.coroutines.flow.first
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.R
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.data.preferences.*
import kotlinx.coroutines.CancellationException
import org.readium.r2.navigator.epub.*
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFont
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.ExperimentalReadiumApi

/** Publication-dependent fragments are rebuilt from the durable locator, never restored without their factory. */
@OptIn(ExperimentalReadiumApi::class)
class EpubReaderActivity : FragmentActivity() {
    private lateinit var model: EpubReaderViewModel
    private var navigator by mutableStateOf<EpubNavigatorFragment?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Readium requires its Publication-backed FragmentFactory before fragment restoration.
        // Rebuild this dedicated activity's fragment instead; rotation retains the ViewModel and
        // process recreation uses Room. MainActivity and its navigation restoration are untouched.
        super.onCreate(null)
        enableEdgeToEdge()
        val id = intent.getStringExtra(BOOK_ID)
        if (id == null || !id.matches(Regex("epub-[0-9a-f]{64}"))) { finish(); return }
        val app = application as NovelNestApplication
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EpubReaderViewModel(id, app.epubRepository) as T
        })[EpubReaderViewModel::class.java]
        setContent {
            NovelNestTheme {
                val state by model.state.collectAsStateWithLifecycle()
                val settings by app.preferencesManager.readerSettings.collectAsStateWithLifecycle()
                val appSettings by app.preferencesManager.appSettings.collectAsStateWithLifecycle()
                val saveError by app.epubRepository.positionError.collectAsStateWithLifecycle()
                var contents by remember { mutableStateOf(false) }
                var preferences by remember { mutableStateOf(false) }
                var resourceError by remember { mutableStateOf<String?>(null) }
                var percent by remember { mutableIntStateOf(0) }
                val ready = state as? EpubReaderState.Ready
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !settings.theme.isDark
                        isAppearanceLightNavigationBars = !settings.theme.isDark
                    }
                    window.decorView.keepScreenOn = settings.keepScreenOn
                }
                Column(Modifier.fillMaxSize().background(settings.theme.backgroundColor).windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = settings.theme.textColor) }
                        Text(ready?.book?.title ?: "Local EPUB", Modifier.weight(1f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis, color = settings.theme.textColor, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { contents = true }, enabled = navigator != null) { Icon(Icons.AutoMirrored.Rounded.List, "Table of contents", tint = settings.theme.textColor) }
                        IconButton(onClick = { preferences = true }, enabled = ready != null) { Icon(Icons.Rounded.TextFields, "Reading settings", tint = settings.theme.textColor) }
                    }
                    when (val result = state) {
                        EpubReaderState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = NovelPrimary) }
                        is EpubReaderState.Failed -> Column(Modifier.weight(1f).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(result.message, color = settings.theme.textColor)
                            Button(onClick = model::load) { Text("Retry", color = LocalContentColor.current) }
                        }
                        is EpubReaderState.Ready -> {
                            AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
                                FragmentContainerView(context).apply { this.id = R.id.epub_navigator }
                            })
                            LaunchedEffect(result.publication) {
                                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                                try {
                                    val factory = EpubNavigatorFactory(result.publication).createFragmentFactory(
                                        initialLocator = model.resumeLocator() ?: result.initialLocator, initialPreferences = app.preferencesManager.readerSettings.value.toEpubPreferences(),
                                        listener = object : EpubNavigatorFragment.Listener {
                                            override fun onResourceLoadFailed(href: org.readium.r2.shared.util.Url, error: org.readium.r2.shared.util.data.ReadError) {
                                                runOnUiThread { if (!isDestroyed) resourceError = "A section or image could not be displayed. The EPUB may contain a missing resource." }
                                            }
                                            override fun onExternalLinkActivated(url: org.readium.r2.shared.util.AbsoluteUrl) {
                                                runOnUiThread { resourceError = "External links are disabled in the offline EPUB reader." }
                                            }
                                        })
                                    supportFragmentManager.fragmentFactory = factory
                                    val fragment = factory.instantiate(classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
                                    supportFragmentManager.beginTransaction().replace(R.id.epub_navigator, fragment, "local-epub").commitNow()
                                    navigator = fragment
                                    fragment.currentLocator.collect { locator ->
                                        model.checkpoint(locator)
                                        percent = ((locator.locations.totalProgression ?: 0.0) * 100).toInt().coerceIn(0, 100)
                                    }
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { resourceError = "Could not display this EPUB. Close the reader and try opening the book again." }
                            }
                            LaunchedEffect(settings, navigator) {
                                val fragment = navigator ?: return@LaunchedEffect
                                try { fragment.submitPreferences(settings.toEpubPreferences()) }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { resourceError = "Could not apply EPUB reading settings. Please reopen the book." }
                            }
                            resourceError?.let { message ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(message, Modifier.weight(1f).padding(start = 16.dp), color = NovelError, style = MaterialTheme.typography.bodySmall)
                                    IconButton(onClick = { resourceError = null }) { Icon(Icons.Rounded.Close, "Dismiss message", tint = NovelError) }
                                }
                            }
                            saveError?.let {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(it, Modifier.weight(1f).padding(start = 16.dp), color = NovelError, style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = app.epubRepository::retryPositionWrites) { Text("Retry") }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                IconButton(enabled = navigator != null, onClick = { navigate({ resourceError = it }) { goBackward(animated = !appSettings.reducedMotion) } }) { Icon(Icons.AutoMirrored.Rounded.NavigateBefore, "Previous page", tint = settings.theme.textColor) }
                                Text(percent.toString() + "% read", color = settings.theme.secondaryTextColor)
                                IconButton(enabled = navigator != null, onClick = { navigate({ resourceError = it }) { goForward(animated = !appSettings.reducedMotion) } }) { Icon(Icons.AutoMirrored.Rounded.NavigateNext, "Next page", tint = settings.theme.textColor) }
                            }
                        }
                    }
                }
                if (contents && ready != null) {
                    val toc = remember(ready.publication) { flattenContents(ready.publication.tableOfContents.ifEmpty { ready.publication.readingOrder }) }
                    AlertDialog(onDismissRequest = { contents = false }, title = { Text("Contents") }, text = {
                        LazyColumn(Modifier.heightIn(max = 440.dp)) {
                            items(toc.indices.toList(), key = { it }) { index ->
                                TextButton(onClick = {
                                    if (navigate({ resourceError = it }) { go(toc[index].second, animated = false) }) contents = false
                                    else resourceError = "This contents entry cannot be opened. It may reference a missing or unsupported section."
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Text("  ".repeat(toc[index].first) + (toc[index].second.title ?: "Section " + (index + 1)), modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }, confirmButton = { TextButton(onClick = { contents = false }) { Text("Close") } })
                }
                if (preferences) EpubPreferencesDialog(settings, app.preferencesManager) { preferences = false }
            }
        }
    }
    override fun onPause() {
        if (::model.isInitialized) model.flush()
        super.onPause()
    }
    override fun onDestroy() {
        navigator = null
        super.onDestroy()
    }
    private fun navigate(onError: (String) -> Unit, action: EpubNavigatorFragment.() -> Boolean): Boolean {
        val fragment = navigator ?: return false
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !fragment.isResumed || fragment.view == null) return false
        return try { fragment.action() }
        catch (_: Exception) {
            onError("Could not navigate to this EPUB section. Please reopen the book.")
            false
        }
    }
    companion object {
        private const val BOOK_ID = "local_epub_id"
        fun open(context: Context, id: String): Boolean {
            if (!id.matches(Regex("epub-[0-9a-f]{64}"))) return false
            var owner = context
            while (owner is ContextWrapper && owner !is Activity && owner.baseContext !== owner) owner = owner.baseContext
            val intent = Intent(context, EpubReaderActivity::class.java).putExtra(BOOK_ID, id)
            if (owner !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try { context.startActivity(intent); true }
            catch (_: android.content.ActivityNotFoundException) { false }
            catch (_: SecurityException) { false }
        }
    }
}

internal fun flattenContents(links: List<Link>): List<Pair<Int, Link>> = buildList {
    fun visit(children: List<Link>, depth: Int) {
        if (depth > 12) return
        for (link in children) {
            if (size >= 5000) return
            add(depth to link)
            visit(link.children, depth + 1)
        }
    }
    visit(links, 0)
}

@OptIn(ExperimentalReadiumApi::class)
private fun ReaderSettings.toEpubPreferences() = EpubPreferences(
    backgroundColor = ReadiumColor(theme.backgroundColor.toArgb()),
    textColor = ReadiumColor(theme.textColor.toArgb()),
    fontFamily = ReadiumFont(when (fontFamily) {
        ReaderFont.MONOSPACE -> "monospace"
        ReaderFont.ROBOTO, ReaderFont.SANS_SERIF, ReaderFont.SYSTEM -> "sans-serif"
        else -> "serif"
    }),
    fontSize = fontSizeSp.toDouble() / 16.0,
    lineHeight = lineHeightMultiplier.toDouble(),
    publisherStyles = false,
    scroll = scrollMode == ReaderScrollMode.CONTINUOUS
)

@Composable
private fun EpubPreferencesDialog(settings: ReaderSettings, preferences: ReaderPreferencesManager, onDismiss: () -> Unit) {
    var size by remember { mutableFloatStateOf(settings.fontSizeSp) }
    var lineHeight by remember { mutableFloatStateOf(settings.lineHeightMultiplier) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Reading settings") }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Text size: " + size.toInt())
            Slider(value = size, onValueChange = { size = it }, valueRange = 12f..32f,
                onValueChangeFinished = { preferences.updateFontSize(size) })
            Text("Line spacing")
            Slider(value = lineHeight, onValueChange = { lineHeight = it }, valueRange = 1.2f..2.2f,
                onValueChangeFinished = { preferences.updateLineHeight(lineHeight) })
            Text("Theme")
            ReaderTheme.entries.forEach { theme ->
                TextButton(onClick = { preferences.updateTheme(theme) }) { Text((if (settings.theme == theme) "Selected: " else "") + theme.displayName) }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
