package com.novelnest.app.feature.epub

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.R
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.bounceSelectable
import com.novelnest.app.data.preferences.*
import com.novelnest.app.data.local.epub.EpubContentSanitizer
import com.novelnest.app.data.local.epub.containsReadingResource
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import org.readium.r2.navigator.epub.*
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        // Draw the control surface behind both gesture and three-button navigation.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        val id = intent.getStringExtra(BOOK_ID)
        if (id == null || !id.matches(Regex("epub-[0-9a-f]{64}"))) { finish(); return }
        val app = application as NovelNestApplication
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EpubReaderViewModel(id, app.epubRepository) as T
        })[EpubReaderViewModel::class.java]
        setContent {
            NovelNestTheme(manageSystemBars = false) {
                val state by model.state.collectAsStateWithLifecycle()
                val settings by app.preferencesManager.readerSettings.collectAsStateWithLifecycle()
                val appSettings by app.preferencesManager.appSettings.collectAsStateWithLifecycle()
                val saveError by app.epubRepository.positionError.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                var showControlSheet by rememberSaveable { mutableStateOf(false) }
                var activeSheetPage by rememberSaveable { mutableStateOf(EpubBottomSheetPage.CONTROLS) }
                var isBookmarkedPlaceholder by remember { mutableStateOf(false) }
                var immersiveModePlaceholder by remember { mutableStateOf(true) }
                var resourceError by remember { mutableStateOf<String?>(null) }
                var percent by remember { mutableIntStateOf(0) }
                var chapterPercent by remember { mutableIntStateOf(0) }
                var currentHref by remember { mutableStateOf<org.readium.r2.shared.util.Url?>(null) }
                var currentLocatorTitle by remember { mutableStateOf<String?>(null) }
                val ready = state as? EpubReaderState.Ready
                val toc = remember(ready?.publication) {
                    ready?.publication?.let { pub ->
                        flattenContents(pub.tableOfContents.ifEmpty { pub.readingOrder })
                    } ?: emptyList()
                }
                val currentChapterIndex = remember(toc, currentHref, currentLocatorTitle) {
                    resolveEpubContentsIndex(toc, currentHref, currentLocatorTitle)
                }
                val epubPreferences = remember(settings.theme, settings.fontFamily, settings.fontSizeSp,
                    settings.lineHeightMultiplier, settings.scrollMode) { settings.toEpubPreferences() }
                val resolvedChapterTitle = remember(currentLocatorTitle, toc, currentChapterIndex) {
                    currentLocatorTitle?.takeIf { it.isNotBlank() }
                        ?: toc.getOrNull(currentChapterIndex)?.second?.title?.takeIf { it.isNotBlank() }
                        ?: "Chapter ${currentChapterIndex + 1}"
                }

                SideEffect {
                    EpubContentSanitizer.updateImageAlignment(
                        settings.epubImageVerticalAlignment,
                        settings.epubImageHorizontalAlignment
                    )
                }
                LaunchedEffect(settings.keepScreenOn) { window.decorView.keepScreenOn = settings.keepScreenOn }
                val canTapReader = ready != null && drawerState.currentValue == DrawerValue.Closed &&
                    drawerState.targetValue == DrawerValue.Closed
                val toggleReaderControls by rememberUpdatedState<() -> Boolean> {
                    if (!canTapReader || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) false
                    else {
                        if (showControlSheet) {
                            showControlSheet = false
                        } else {
                            activeSheetPage = EpubBottomSheetPage.CONTROLS
                            showControlSheet = true
                        }
                        true
                    }
                }
                // Readium forwards only unhandled content taps, preserving links, scrolling and selection.
                DisposableEffect(navigator) {
                    val fragment = navigator
                    val listener = object : InputListener {
                        override fun onTap(event: TapEvent): Boolean = toggleReaderControls()
                    }
                    fragment?.addInputListener(listener)
                    onDispose { fragment?.removeInputListener(listener) }
                }
                BackHandler(enabled = showControlSheet && canTapReader) {
                    if (activeSheetPage == EpubBottomSheetPage.FONT ||
                        activeSheetPage == EpubBottomSheetPage.THEME ||
                        activeSheetPage == EpubBottomSheetPage.IMAGE_ALIGNMENT
                    ) {
                        activeSheetPage = EpubBottomSheetPage.CONTROLS
                    } else {
                        showControlSheet = false
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = drawerState.isOpen,
                    drawerContent = {
                        EpubChaptersDrawer(
                            toc = toc,
                            currentChapterIndex = currentChapterIndex,
                            isOpen = drawerState.isOpen,
                            onSelectChapter = { target ->
                                val pub = ready?.publication
                                if (pub == null) false else {
                                    val opened = navigate({ resourceError = it }) {
                                        pub.containsReadingResource(target.url()) && go(target, animated = false)
                                    }
                                    if (!opened) resourceError = "This contents entry cannot be opened. It may reference a missing or unsupported section."
                                    opened
                                }
                            },
                            onCloseDrawer = { scope.launch { drawerState.close() } }
                        )
                    }
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(NovelBackground)
                    ) {
                        EpubReaderReferenceTopBar(
                            bookTitle = ready?.book?.title ?: "Local EPUB",
                            chapterSubtitle = if (ready != null) {
                                "Chapter ${currentChapterIndex + 1} • $resolvedChapterTitle"
                            } else {
                                "Loading EPUB..."
                            },
                            isBookmarked = isBookmarkedPlaceholder,
                            enabled = ready != null,
                            onBack = { finish() },
                            onOpenChapters = {
                                if (ready != null) {
                                    scope.launch { drawerState.open() }
                                }
                            },
                            onToggleBookmark = { isBookmarkedPlaceholder = !isBookmarkedPlaceholder },
                            onOpenSettings = {
                                if (showControlSheet && activeSheetPage == EpubBottomSheetPage.READING_MODE) {
                                    showControlSheet = false
                                } else {
                                    activeSheetPage = EpubBottomSheetPage.READING_MODE
                                    showControlSheet = true
                                }
                            },
                            onToggleControlSheet = {
                                if (showControlSheet && activeSheetPage == EpubBottomSheetPage.CONTROLS) {
                                    showControlSheet = false
                                } else {
                                    activeSheetPage = EpubBottomSheetPage.CONTROLS
                                    showControlSheet = true
                                }
                            }
                        )

                        when (val result = state) {
                            EpubReaderState.Loading -> Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth().background(settings.theme.backgroundColor),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = NovelPrimary)
                            }
                            is EpubReaderState.Failed -> Column(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth().background(settings.theme.backgroundColor)
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(result.message, color = settings.theme.textColor)
                                Button(onClick = model::load) { Text("Retry", color = LocalContentColor.current) }
                            }
                            is EpubReaderState.Ready -> {
                                BoxWithConstraints(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .background(settings.theme.backgroundColor)
                                ) {
                                    val maximumControlsHeight = maxHeight * 0.65f
                                    Column(Modifier.fillMaxSize()) {
                                        AndroidView(
                                            modifier = Modifier.weight(1f).fillMaxWidth()
                                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                                            factory = { context ->
                                                FragmentContainerView(context).apply { this.id = R.id.epub_navigator }
                                            }
                                        )

                                        LaunchedEffect(result.publication) {
                                            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                                            try {
                                                val factory = EpubNavigatorFactory(result.publication).createFragmentFactory(
                                                    // Header/footer own vertical insets; Compose protects horizontal cutouts.
                                                    // Readium must not add the status-bar/cutout height a second time.
                                                    configuration = EpubNavigatorFragment.Configuration(shouldApplyInsetsPadding = false),
                                                    initialLocator = model.resumeLocator() ?: result.initialLocator,
                                                    initialPreferences = epubPreferences,
                                                    listener = object : EpubNavigatorFragment.Listener {
                                                        override fun onResourceLoadFailed(href: org.readium.r2.shared.util.Url, error: org.readium.r2.shared.util.data.ReadError) {
                                                            runOnUiThread { if (!isDestroyed) resourceError = "A section or image could not be displayed. The EPUB may contain a missing resource." }
                                                        }
                                                        override fun onExternalLinkActivated(url: org.readium.r2.shared.util.AbsoluteUrl) {
                                                            runOnUiThread { if (!isDestroyed) resourceError = "External links are disabled in the offline EPUB reader." }
                                                        }
                                                    }
                                                )
                                                supportFragmentManager.fragmentFactory = factory
                                                val fragment = factory.instantiate(classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
                                                supportFragmentManager.beginTransaction().replace(R.id.epub_navigator, fragment, "local-epub").commitNow()
                                                navigator = fragment
                                                fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                                    fragment.currentLocator.collect { locator ->
                                                        model.checkpoint(locator)
                                                        percent = (model.progression() * 100).toInt().coerceIn(0, 100)
                                                        currentHref = locator.href
                                                        currentLocatorTitle = locator.title
                                                        chapterPercent = ((locator.locations.progression ?: model.progression()) * 100)
                                                            .toInt()
                                                            .coerceIn(0, 100)
                                                    }
                                                }
                                            } catch (cancelled: CancellationException) { throw cancelled }
                                            catch (_: Exception) { resourceError = "Could not display this EPUB. Close the reader and try opening the book again." }
                                        }

                                        LaunchedEffect(epubPreferences, navigator) {
                                            val fragment = navigator ?: return@LaunchedEffect
                                            try { fragment.submitPreferences(epubPreferences) }
                                            catch (cancelled: CancellationException) { throw cancelled }
                                            catch (_: Exception) { resourceError = "Could not apply EPUB reading settings. Please reopen the book." }
                                        }

                                        LaunchedEffect(
                                            settings.epubImageVerticalAlignment,
                                            settings.epubImageHorizontalAlignment,
                                            currentHref,
                                            navigator
                                        ) {
                                            EpubContentSanitizer.updateImageAlignment(
                                                settings.epubImageVerticalAlignment,
                                                settings.epubImageHorizontalAlignment
                                            )
                                            val rootView = navigator?.view ?: return@LaunchedEffect
                                            val js = EpubContentSanitizer.buildLiveUpdateJs(
                                                settings.epubImageVerticalAlignment,
                                                settings.epubImageHorizontalAlignment
                                            )
                                            rootView.applyEpubImageAlignmentJs(js)
                                            delay(120)
                                            rootView.applyEpubImageAlignmentJs(js)
                                        }

                                        LaunchedEffect(resourceError) {
                                            val message = resourceError
                                            if (!message.isNullOrBlank()) {
                                                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                                                resourceError = null
                                            }
                                        }

                                        // Persistent compact footer keeps the Readium WebView height constant
                                        // so opening/closing the bottom sheet never triggers WebView repagination.
                                        // Only a top divider is drawn; no side or bottom borders in collapsed state.
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(NovelBackground)
                                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                                        ) {
                                            saveError?.let {
                                                Surface(
                                                    color = NovelSurface,
                                                    border = BorderStroke(1.dp, NovelError.copy(alpha = 0.4f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = it,
                                                            modifier = Modifier.weight(1f),
                                                            color = NovelError,
                                                            style = MaterialTheme.typography.bodySmall
                                                        )
                                                        TextButton(onClick = app.epubRepository::retryPositionWrites) {
                                                            Text("Retry", color = NovelPrimary)
                                                        }
                                                    }
                                                }
                                            }

                                            HorizontalDivider(
                                                thickness = 1.dp,
                                                color = NovelSurfaceBorder
                                            )

                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .background(NovelBackground.copy(alpha = 0.94f))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                IconButton(
                                                    enabled = navigator != null,
                                                    onClick = {
                                                        navigate({ resourceError = it }) {
                                                            goBackward(animated = !appSettings.reducedMotion)
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Rounded.NavigateBefore,
                                                        "Previous page",
                                                        tint = NovelPrimary
                                                    )
                                                }
                                                Text(
                                                    text = "Chapter ${currentChapterIndex + 1}/${toc.size.coerceAtLeast(1)} • $percent% read",
                                                    color = NovelTextSecondary,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            activeSheetPage = EpubBottomSheetPage.CONTROLS
                                                            showControlSheet = true
                                                        }
                                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                                IconButton(
                                                    enabled = navigator != null,
                                                    onClick = {
                                                        navigate({ resourceError = it }) {
                                                            goForward(animated = !appSettings.reducedMotion)
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Rounded.NavigateNext,
                                                        "Next page",
                                                        tint = NovelPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // ─── SLIDING BOTTOM SHEET OVERLAY (Controls, Font, Theme, Image Alignment, Reading Mode) ───
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = showControlSheet,
                                        modifier = Modifier.align(Alignment.BottomCenter),
                                        enter = slideInVertically(
                                            initialOffsetY = { it },
                                            animationSpec = tween(
                                                durationMillis = if (appSettings.reducedMotion) 0 else 280,
                                                easing = FastOutSlowInEasing
                                            )
                                        ) + fadeIn(
                                            animationSpec = tween(
                                                durationMillis = if (appSettings.reducedMotion) 0 else 180
                                            )
                                        ),
                                        exit = slideOutVertically(
                                            targetOffsetY = { it },
                                            animationSpec = tween(
                                                durationMillis = if (appSettings.reducedMotion) 0 else 240,
                                                easing = FastOutSlowInEasing
                                            )
                                        ) + fadeOut(
                                            animationSpec = tween(
                                                durationMillis = if (appSettings.reducedMotion) 0 else 150
                                            )
                                        )
                                    ) {
                                        EpubBottomSheetContainer(maxHeight = maximumControlsHeight) {
                                            saveError?.let {
                                                Surface(
                                                    color = NovelSurface,
                                                    border = BorderStroke(1.dp, NovelError.copy(alpha = 0.4f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = it,
                                                            modifier = Modifier.weight(1f),
                                                            color = NovelError,
                                                            style = MaterialTheme.typography.bodySmall
                                                        )
                                                        TextButton(onClick = app.epubRepository::retryPositionWrites) {
                                                            Text("Retry", color = NovelPrimary)
                                                        }
                                                    }
                                                }
                                            }

                                            AnimatedContent(
                                                targetState = activeSheetPage,
                                                label = "EPUB Bottom Sheet SubMenu",
                                                transitionSpec = {
                                                    (fadeIn(tween(if (appSettings.reducedMotion) 0 else 180)) togetherWith
                                                        fadeOut(tween(if (appSettings.reducedMotion) 0 else 120)))
                                                }
                                            ) { page ->
                                                when (page) {
                                                    EpubBottomSheetPage.CONTROLS -> {
                                                        EpubReaderBottomControlPanel(
                                                            currentChapterNumber = currentChapterIndex + 1,
                                                            totalChapters = toc.size.coerceAtLeast(1),
                                                            chapterPercent = chapterPercent,
                                                            bookPercent = percent,
                                                            navigationEnabled = navigator != null,
                                                            isBookmarked = isBookmarkedPlaceholder,
                                                            immersiveMode = immersiveModePlaceholder,
                                                            onPrevious = {
                                                                navigate({ resourceError = it }) {
                                                                    goBackward(animated = !appSettings.reducedMotion)
                                                                }
                                                            },
                                                            onOpenChapters = {
                                                                scope.launch { drawerState.open() }
                                                            },
                                                            onToggleBookmark = {
                                                                isBookmarkedPlaceholder = !isBookmarkedPlaceholder
                                                            },
                                                            onNext = {
                                                                navigate({ resourceError = it }) {
                                                                    goForward(animated = !appSettings.reducedMotion)
                                                                }
                                                            },
                                                            onOpenFontSettings = {
                                                                activeSheetPage = EpubBottomSheetPage.FONT
                                                            },
                                                            onOpenThemeSettings = {
                                                                activeSheetPage = EpubBottomSheetPage.THEME
                                                            },
                                                            onOpenImageAlignmentSettings = {
                                                                activeSheetPage = EpubBottomSheetPage.IMAGE_ALIGNMENT
                                                            },
                                                            onToggleImmersiveMode = {
                                                                immersiveModePlaceholder = it
                                                            }
                                                        )
                                                    }
                                                    EpubBottomSheetPage.FONT -> {
                                                        EpubFontSettingsSubMenu(
                                                            settings = settings,
                                                            preferences = app.preferencesManager,
                                                            onBackToControls = {
                                                                activeSheetPage = EpubBottomSheetPage.CONTROLS
                                                            },
                                                            onCloseSheet = {
                                                                showControlSheet = false
                                                            }
                                                        )
                                                    }
                                                    EpubBottomSheetPage.THEME -> {
                                                        EpubThemeSettingsSubMenu(
                                                            settings = settings,
                                                            preferences = app.preferencesManager,
                                                            onBackToControls = {
                                                                activeSheetPage = EpubBottomSheetPage.CONTROLS
                                                            },
                                                            onCloseSheet = {
                                                                showControlSheet = false
                                                            }
                                                        )
                                                    }
                                                    EpubBottomSheetPage.IMAGE_ALIGNMENT -> {
                                                        EpubImageAlignmentSubMenu(
                                                            settings = settings,
                                                            preferences = app.preferencesManager,
                                                            onBackToControls = {
                                                                activeSheetPage = EpubBottomSheetPage.CONTROLS
                                                            },
                                                            onCloseSheet = {
                                                                showControlSheet = false
                                                            }
                                                        )
                                                    }
                                                    EpubBottomSheetPage.READING_MODE -> {
                                                        EpubReadingModeSubMenu(
                                                            settings = settings,
                                                            preferences = app.preferencesManager,
                                                            onCloseSheet = {
                                                                showControlSheet = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    override fun onPause() {
        if (::model.isInitialized) model.flush()
        super.onPause()
    }
    override fun onDestroy() {
        window.decorView.keepScreenOn = false
        navigator = null
        super.onDestroy()
    }
    private fun navigate(onError: (String) -> Unit, action: EpubNavigatorFragment.() -> Boolean): Boolean {
        val fragment = navigator ?: return false
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !fragment.isResumed || fragment.view == null) return false
        return try { fragment.action() }
        catch (cancelled: CancellationException) { throw cancelled }
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

private fun View.applyEpubImageAlignmentJs(js: String) {
    if (this is WebView) {
        evaluateJavascript(js, null)
        return
    }
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i)?.applyEpubImageAlignmentJs(js)
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

private enum class EpubBottomSheetPage {
    CONTROLS,
    FONT,
    THEME,
    IMAGE_ALIGNMENT,
    READING_MODE
}

@Composable
private fun EpubBottomSheetContainer(
    maxHeight: androidx.compose.ui.unit.Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val topShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val borderColor = NovelPrimary.copy(alpha = 0.35f)

    // Strictly clips to topStart/topEnd = 24.dp with no outer background so the area
    // outside the top rounded corners is 100% transparent over the EPUB reader content,
    // and draws the 1.dp accent border only along the top and sides (never on the bottom).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(topShape)
            .background(EpubControlPanelBackground)
            .drawWithContent {
                drawContent()
                val strokeWidth = 1.dp.toPx()
                val halfStroke = strokeWidth / 2f
                val radius = 24.dp.toPx()
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(halfStroke, halfStroke),
                    size = Size(size.width - strokeWidth, size.height + radius * 2f),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = strokeWidth)
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState()),
        content = content
    )
}

@Composable
private fun EpubSubMenuHeader(
    icon: ImageVector? = null,
    textBadge: String? = null,
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    onReset: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    val cardBackground = Color(0xFF1B1F27)
    val cardBorderColor = Color(0xFF2B313D)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(cardBackground)
                    .border(1.dp, cardBorderColor, RoundedCornerShape(11.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Back to controls",
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to controls",
                    tint = NovelPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(NovelPrimary.copy(alpha = 0.14f))
                    .border(1.dp, NovelPrimary.copy(alpha = 0.35f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (textBadge != null) {
                    Text(
                        text = textBadge,
                        color = NovelPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = NovelPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = NovelTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = NovelTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (onReset != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(cardBackground)
                    .border(1.dp, cardBorderColor, RoundedCornerShape(10.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Reset reading settings",
                        onClick = onReset
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = NovelPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Reset",
                        color = NovelTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(cardBackground)
                .border(1.dp, cardBorderColor, RoundedCornerShape(10.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = "Close reading settings",
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close reading settings",
                tint = NovelTextSecondary,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun EpubFontSettingsSubMenu(
    settings: ReaderSettings,
    preferences: ReaderPreferencesManager,
    onBackToControls: () -> Unit,
    onCloseSheet: () -> Unit
) {
    val sheetBackground = EpubControlPanelBackground
    val cardBackground = Color(0xFF1B1F27)
    val cardBorderColor = Color(0xFF2B313D)
    val dividerColor = Color(0xFF2D3340)

    var size by remember(settings.fontSizeSp) { mutableFloatStateOf(settings.fontSizeSp) }
    var lineHeight by remember(settings.lineHeightMultiplier) { mutableFloatStateOf(settings.lineHeightMultiplier) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        EpubSubMenuHeader(
            textBadge = "Aa",
            title = "Font Settings",
            subtitle = "Font family, text size & line spacing",
            onBack = onBackToControls,
            onReset = preferences::resetReadingSettings,
            onClose = onCloseSheet
        )

        // ─── 1. FONT FAMILY CARD ───
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = cardBackground,
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.TextFields,
                            contentDescription = null,
                            tint = NovelPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Font Family",
                            color = NovelTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NovelPrimary.copy(alpha = 0.16f),
                        border = BorderStroke(0.5.dp, NovelPrimary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = settings.fontFamily.displayName,
                            color = NovelPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                ) {
                    items(ReaderFont.entries) { font ->
                        val isSelected = font == settings.fontFamily
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) NovelPrimary else sheetBackground)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) NovelPrimary else cardBorderColor,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .bounceSelectable(
                                    selected = isSelected,
                                    scaleDown = BounceScale.Small,
                                    onClick = { preferences.updateFontFamily(font) }
                                )
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = font.displayName,
                                fontFamily = font.fontFamily,
                                color = if (isSelected) NovelOnPrimary else NovelTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ─── 2. TEXT SIZE & LINE SPACING CARD ───
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = cardBackground,
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Text Size Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.FormatSize,
                            contentDescription = null,
                            tint = NovelPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Text Size",
                            color = NovelTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NovelPrimary.copy(alpha = 0.16f),
                        border = BorderStroke(0.5.dp, NovelPrimary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${size.roundToInt()} sp",
                            color = NovelPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NovelNestIconButton(
                        icon = Icons.Rounded.Remove,
                        contentDescription = "Decrease text size",
                        tint = NovelPrimary,
                        iconSize = 18.dp,
                        enabled = size > 12f,
                        onClick = {
                            val next = (size.roundToInt() - 1).coerceAtLeast(12).toFloat()
                            size = next
                            preferences.updateFontSize(next)
                        }
                    )
                    Slider(
                        value = size,
                        onValueChange = { size = it.roundToInt().toFloat() },
                        valueRange = 12f..32f,
                        onValueChangeFinished = { preferences.updateFontSize(size) },
                        colors = SliderDefaults.colors(
                            thumbColor = NovelPrimary,
                            activeTrackColor = NovelPrimary,
                            inactiveTrackColor = cardBorderColor
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .padding(horizontal = 4.dp)
                    )
                    NovelNestIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "Increase text size",
                        tint = NovelPrimary,
                        iconSize = 18.dp,
                        enabled = size < 32f,
                        onClick = {
                            val next = (size.roundToInt() + 1).coerceAtMost(32).toFloat()
                            size = next
                            preferences.updateFontSize(next)
                        }
                    )
                }

                HorizontalDivider(color = dividerColor)

                // Line Spacing Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.FormatLineSpacing,
                            contentDescription = null,
                            tint = NovelPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Line Spacing",
                            color = NovelTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NovelPrimary.copy(alpha = 0.16f),
                        border = BorderStroke(0.5.dp, NovelPrimary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = String.format("%.1fx", lineHeight),
                            color = NovelPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NovelNestIconButton(
                        icon = Icons.Rounded.Remove,
                        contentDescription = "Decrease line spacing",
                        tint = NovelPrimary,
                        iconSize = 18.dp,
                        enabled = lineHeight > 1.2f,
                        onClick = {
                            val next = ((lineHeight * 10f).roundToInt() - 1).coerceAtLeast(12) / 10f
                            lineHeight = next
                            preferences.updateLineHeight(next)
                        }
                    )
                    Slider(
                        value = lineHeight,
                        onValueChange = { lineHeight = (it * 10f).roundToInt() / 10f },
                        valueRange = 1.2f..2.2f,
                        onValueChangeFinished = { preferences.updateLineHeight(lineHeight) },
                        colors = SliderDefaults.colors(
                            thumbColor = NovelPrimary,
                            activeTrackColor = NovelPrimary,
                            inactiveTrackColor = cardBorderColor
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .padding(horizontal = 4.dp)
                    )
                    NovelNestIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "Increase line spacing",
                        tint = NovelPrimary,
                        iconSize = 18.dp,
                        enabled = lineHeight < 2.2f,
                        onClick = {
                            val next = ((lineHeight * 10f).roundToInt() + 1).coerceAtMost(22) / 10f
                            lineHeight = next
                            preferences.updateLineHeight(next)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EpubThemeSettingsSubMenu(
    settings: ReaderSettings,
    preferences: ReaderPreferencesManager,
    onBackToControls: () -> Unit,
    onCloseSheet: () -> Unit
) {
    val cardBackground = Color(0xFF1B1F27)
    val cardBorderColor = Color(0xFF2B313D)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        EpubSubMenuHeader(
            icon = Icons.Rounded.Palette,
            title = "Theme Settings",
            subtitle = "Reading background & text contrast",
            onBack = onBackToControls,
            onReset = preferences::resetReadingSettings,
            onClose = onCloseSheet
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = cardBackground,
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = NovelPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Theme Palette",
                            color = NovelTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NovelPrimary.copy(alpha = 0.16f),
                        border = BorderStroke(0.5.dp, NovelPrimary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = settings.theme.displayName,
                            color = NovelPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReaderTheme.entries.forEach { theme ->
                        val isSelected = theme == settings.theme
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(74.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.backgroundColor)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) {
                                        NovelPrimary
                                    } else if (theme.isDark) {
                                        cardBorderColor
                                    } else {
                                        Color(0xFFD6C8B4)
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .bounceSelectable(
                                    selected = isSelected,
                                    scaleDown = BounceScale.Small,
                                    onClick = { preferences.updateTheme(theme) }
                                )
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(NovelPrimary)
                                        .align(Alignment.TopEnd),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = NovelOnPrimary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                                Text(
                                    text = "Aa",
                                    color = theme.textColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = theme.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) theme.textColor else theme.secondaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpubImageAlignmentSubMenu(
    settings: ReaderSettings,
    preferences: ReaderPreferencesManager,
    onBackToControls: () -> Unit,
    onCloseSheet: () -> Unit
) {
    val sheetBackground = EpubControlPanelBackground
    val cardBackground = Color(0xFF1B1F27)
    val cardBorderColor = Color(0xFF2B313D)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        EpubSubMenuHeader(
            icon = Icons.Rounded.FormatAlignCenter,
            title = "Image Alignment",
            subtitle = "Vertical & horizontal illustration position",
            onBack = onBackToControls,
            onReset = preferences::resetEpubImageAlignment,
            onClose = onCloseSheet
        )

        // ─── 1. VERTICAL ALIGNMENT CARD ───
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = cardBackground,
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.VerticalAlignCenter,
                            contentDescription = null,
                            tint = NovelPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Vertical Position",
                            color = NovelTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NovelPrimary.copy(alpha = 0.16f),
                        border = BorderStroke(0.5.dp, NovelPrimary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = settings.epubImageVerticalAlignment.displayName,
                            color = NovelPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                val verticalOptions = listOf(
                    EpubImageVerticalAlignment.TOP to Icons.Rounded.VerticalAlignTop,
                    EpubImageVerticalAlignment.CENTER to Icons.Rounded.VerticalAlignCenter,
                    EpubImageVerticalAlignment.BOTTOM to Icons.Rounded.VerticalAlignBottom
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    verticalOptions.forEach { (alignment, alignIcon) ->
                        val isSelected = settings.epubImageVerticalAlignment == alignment
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (isSelected) NovelPrimary else sheetBackground
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) NovelPrimary else cardBorderColor,
                                    shape = RoundedCornerShape(11.dp)
                                )
                                .bounceSelectable(
                                    selected = isSelected,
                                    scaleDown = BounceScale.Small,
                                    onClick = { preferences.updateEpubImageVerticalAlignment(alignment) }
                                )
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = alignIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) NovelOnPrimary else NovelPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = alignment.displayName,
                                    color = if (isSelected) NovelOnPrimary else NovelTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // ─── 2. HORIZONTAL ALIGNMENT CARD ───
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = cardBackground,
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.FormatAlignCenter,
                            contentDescription = null,
                            tint = NovelPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Horizontal Alignment",
                            color = NovelTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NovelPrimary.copy(alpha = 0.16f),
                        border = BorderStroke(0.5.dp, NovelPrimary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = settings.epubImageHorizontalAlignment.displayName,
                            color = NovelPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                val horizontalOptions = listOf(
                    EpubImageHorizontalAlignment.LEFT to Icons.AutoMirrored.Rounded.FormatAlignLeft,
                    EpubImageHorizontalAlignment.CENTER to Icons.Rounded.FormatAlignCenter,
                    EpubImageHorizontalAlignment.RIGHT to Icons.AutoMirrored.Rounded.FormatAlignRight
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    horizontalOptions.forEach { (alignment, alignIcon) ->
                        val isSelected = settings.epubImageHorizontalAlignment == alignment
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (isSelected) NovelPrimary else sheetBackground
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) NovelPrimary else cardBorderColor,
                                    shape = RoundedCornerShape(11.dp)
                                )
                                .bounceSelectable(
                                    selected = isSelected,
                                    scaleDown = BounceScale.Small,
                                    onClick = { preferences.updateEpubImageHorizontalAlignment(alignment) }
                                )
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = alignIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) NovelOnPrimary else NovelPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = alignment.displayName,
                                    color = if (isSelected) NovelOnPrimary else NovelTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpubReadingModeSubMenu(
    settings: ReaderSettings,
    preferences: ReaderPreferencesManager,
    onCloseSheet: () -> Unit
) {
    val sheetBackground = EpubControlPanelBackground
    val cardBackground = Color(0xFF1B1F27)
    val cardBorderColor = Color(0xFF2B313D)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        EpubSubMenuHeader(
            icon = Icons.Rounded.Settings,
            title = "Reading Mode",
            subtitle = "Choose continuous scroll or page flipped",
            onClose = onCloseSheet
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = cardBackground,
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val options = listOf(
                    Triple(
                        ReaderScrollMode.CONTINUOUS,
                        "Continuous Scroll",
                        Icons.Rounded.SwapVert
                    ),
                    Triple(
                        ReaderScrollMode.PAGED,
                        "Page Flipped",
                        Icons.AutoMirrored.Rounded.MenuBook
                    )
                )

                options.forEach { (mode, label, modeIcon) ->
                    val isSelected = settings.scrollMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) NovelPrimary.copy(alpha = 0.14f) else sheetBackground
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) NovelPrimary else cardBorderColor,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .bounceSelectable(
                                selected = isSelected,
                                scaleDown = BounceScale.Small,
                                onClick = { preferences.updateScrollMode(mode) }
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(
                                            if (isSelected) NovelPrimary else cardBackground
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = modeIcon,
                                        contentDescription = null,
                                        tint = if (isSelected) NovelOnPrimary else NovelPrimary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(NovelPrimary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            tint = NovelOnPrimary,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = label,
                                    color = if (isSelected) NovelPrimary else NovelTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (mode == ReaderScrollMode.CONTINUOUS) {
                                        "Vertical scrolling"
                                    } else {
                                        "Horizontal page turn"
                                    },
                                    color = NovelTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpubReaderReferenceTopBar(
    bookTitle: String,
    chapterSubtitle: String,
    isBookmarked: Boolean,
    enabled: Boolean,
    onBack: () -> Unit,
    onOpenChapters: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleControlSheet: () -> Unit
) {
    Surface(
        color = NovelBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = NovelPrimary
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                enabled = enabled,
                                role = Role.Button,
                                onClickLabel = "Table of contents",
                                onClick = onOpenChapters
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = bookTitle,
                                color = NovelTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Table of contents",
                                tint = NovelTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = chapterSubtitle,
                            color = NovelTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Placeholder bookmark icon
                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = NovelPrimary
                        )
                    }
                    // Settings gear icon (opens preferences or toggles control sheet)
                    IconButton(
                        onClick = onOpenSettings,
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Reading settings",
                            tint = NovelPrimary
                        )
                    }
                    // More options icon (toggles bottom control sheet)
                    IconButton(onClick = onToggleControlSheet) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More options",
                            tint = NovelPrimary
                        )
                    }
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = NovelPrimary.copy(alpha = 0.42f)
            )
        }
    }
}

private val EpubControlPanelBackground = Color(0xFF12151B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpubReaderBottomControlPanel(
    currentChapterNumber: Int,
    totalChapters: Int,
    chapterPercent: Int,
    bookPercent: Int,
    navigationEnabled: Boolean,
    isBookmarked: Boolean,
    immersiveMode: Boolean,
    onPrevious: () -> Unit,
    onOpenChapters: () -> Unit,
    onToggleBookmark: () -> Unit,
    onNext: () -> Unit,
    onOpenFontSettings: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenImageAlignmentSettings: () -> Unit,
    onToggleImmersiveMode: (Boolean) -> Unit
) {
    val cardBackground = Color(0xFF1B1F27)
    val cardBorderColor = Color(0xFF2B313D)
    val dividerColor = Color(0xFF2D3340)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ─── 1. CHAPTER & BOOK PROGRESS HEADER + SLIDER ───
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = NovelTextPrimary, fontWeight = FontWeight.Medium)) {
                            append("Chapter ")
                        }
                        withStyle(SpanStyle(color = NovelPrimary, fontWeight = FontWeight.Bold)) {
                            append(currentChapterNumber.toString())
                        }
                        withStyle(SpanStyle(color = NovelTextSecondary, fontWeight = FontWeight.Medium)) {
                            append(" / $totalChapters")
                        }
                    },
                    fontSize = 14.sp
                )

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = NovelPrimary, fontWeight = FontWeight.SemiBold)) {
                            append("${chapterPercent}%")
                        }
                        withStyle(SpanStyle(color = NovelTextSecondary, fontWeight = FontWeight.Medium)) {
                            append(" • Book ${bookPercent}%")
                        }
                    },
                    fontSize = 13.sp
                )
            }

            Slider(
                value = (chapterPercent / 100f).coerceIn(0f, 1f),
                onValueChange = {},
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = NovelPrimary,
                    activeTrackColor = NovelPrimary,
                    inactiveTrackColor = cardBorderColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            )
        }

        // ─── 2. PRIMARY 4-ACTION NAVIGATION CARD ───
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = cardBackground,
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EpubPrimaryNavActionItem(
                    icon = Icons.AutoMirrored.Rounded.NavigateBefore,
                    label = "Previous",
                    contentDescription = "Previous page",
                    enabled = navigationEnabled,
                    onClick = onPrevious,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    color = dividerColor,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 6.dp)
                )
                EpubPrimaryNavActionItem(
                    icon = Icons.AutoMirrored.Rounded.List,
                    label = "Chapters",
                    contentDescription = "Table of contents",
                    enabled = navigationEnabled,
                    onClick = onOpenChapters,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    color = dividerColor,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 6.dp)
                )
                EpubPrimaryNavActionItem(
                    icon = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    label = "Bookmark",
                    contentDescription = "Bookmark",
                    enabled = true,
                    onClick = onToggleBookmark,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    color = dividerColor,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 6.dp)
                )
                EpubPrimaryNavActionItem(
                    icon = Icons.AutoMirrored.Rounded.NavigateNext,
                    label = "Next",
                    contentDescription = "Next page",
                    enabled = navigationEnabled,
                    onClick = onNext,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ─── 3. 2x5 TOOLS GRID CARD (Font, Theme & Align working; others placeholders) ───
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = cardBackground,
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Row 1: Font, Theme, Brightness, Highlight, Note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EpubToolGridItem(
                        textBadge = "Aa",
                        icon = null,
                        label = "Font",
                        onClick = onOpenFontSettings,
                        modifier = Modifier.weight(1f)
                    )
                    EpubToolVerticalDivider(dividerColor)
                    EpubToolGridItem(
                        icon = Icons.Rounded.Palette,
                        label = "Theme",
                        onClick = onOpenThemeSettings,
                        modifier = Modifier.weight(1f)
                    )
                    EpubToolVerticalDivider(dividerColor)
                    EpubToolGridItem(
                        icon = Icons.Rounded.LightMode,
                        label = "Brightness",
                        onClick = {}, // Placeholder
                        modifier = Modifier.weight(1f)
                    )
                    EpubToolVerticalDivider(dividerColor)
                    EpubToolGridItem(
                        icon = Icons.Rounded.BorderColor,
                        label = "Highlight",
                        onClick = {}, // Placeholder
                        modifier = Modifier.weight(1f)
                    )
                    EpubToolVerticalDivider(dividerColor)
                    EpubToolGridItem(
                        icon = Icons.Rounded.Description,
                        label = "Note",
                        onClick = {}, // Placeholder
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Search, Translate, TTS, Align, Fit Width
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EpubToolGridItem(
                        icon = Icons.Rounded.Search,
                        label = "Search",
                        onClick = {}, // Placeholder
                        modifier = Modifier.weight(1f)
                    )
                    EpubToolVerticalDivider(dividerColor)
                    EpubToolGridItem(
                        icon = Icons.Rounded.Translate,
                        label = "Translate",
                        onClick = {}, // Placeholder
                        modifier = Modifier.weight(1f)
                    )
                    EpubToolVerticalDivider(dividerColor)
                    EpubToolGridItem(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        label = "TTS",
                        onClick = {}, // Placeholder
                        modifier = Modifier.weight(1f)
                    )
                    EpubToolVerticalDivider(dividerColor)
                    EpubToolGridItem(
                        icon = Icons.Rounded.FormatAlignCenter,
                        label = "Align",
                        onClick = onOpenImageAlignmentSettings,
                        modifier = Modifier.weight(1f)
                    )
                    EpubToolVerticalDivider(dividerColor)
                    EpubToolGridItem(
                        icon = Icons.Rounded.CropFree,
                        label = "Fit Width",
                        onClick = {}, // Placeholder
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ─── 4. BOTTOM IMMERSIVE MODE + FULLSCREEN PILL BAR (Placeholders) ───
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = cardBackground,
            border = BorderStroke(1.dp, NovelPrimary.copy(alpha = 0.28f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1.35f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            tint = NovelPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Immersive Mode",
                            color = NovelTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Switch(
                        checked = immersiveMode,
                        onCheckedChange = onToggleImmersiveMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NovelOnPrimary,
                            checkedTrackColor = NovelPrimary,
                            uncheckedThumbColor = NovelTextSecondary,
                            uncheckedTrackColor = NovelSurfaceVariant
                        )
                    )
                }

                VerticalDivider(
                    color = dividerColor,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .weight(0.85f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = {}) // Placeholder
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fullscreen,
                        contentDescription = null,
                        tint = NovelPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Fullscreen",
                        color = NovelTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun EpubPrimaryNavActionItem(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) NovelPrimary else NovelTextTertiary,
            modifier = Modifier.size(21.dp)
        )
        Text(
            text = label,
            color = if (enabled) NovelTextPrimary else NovelTextTertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EpubToolGridItem(
    icon: ImageVector? = null,
    textBadge: String? = null,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier.height(22.dp),
            contentAlignment = Alignment.Center
        ) {
            if (textBadge != null) {
                Text(
                    text = textBadge,
                    color = NovelPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = NovelPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = label,
            color = NovelTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EpubToolVerticalDivider(color: Color) {
    VerticalDivider(
        color = color.copy(alpha = 0.65f),
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 6.dp)
    )
}
