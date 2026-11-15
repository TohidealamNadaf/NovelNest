package com.novelnest.app.feature.epub

import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.novelnest.app.data.local.epub.EpubImageAsset
import com.novelnest.app.data.preferences.*
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

private class EpubImageZoomControls {
    var zoom: ((Float) -> Unit)? = null
    var reset: (() -> Unit)? = null
}

// Transparent bar colors remain necessary on Android 8-14; Android 15 uses enforced edge-to-edge.
@Suppress("DEPRECATION")
@Composable
internal fun EpubImageViewer(
    state: EpubImageViewerState,
    settings: ReaderSettings,
    preferences: ReaderPreferencesManager,
    onSelect: (Int) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    var controls by rememberSaveable { mutableStateOf(true) }
    val zoom = remember { EpubImageZoomControls() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
    )) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val lightBackground = settings.epubImageBackground in setOf(EpubImageBackground.WHITE, EpubImageBackground.SEPIA)
        DisposableEffect(window, controls, lightBackground) {
            window?.let {
                if (Build.VERSION.SDK_INT < 35) {
                    it.statusBarColor = AndroidColor.TRANSPARENT
                    it.navigationBarColor = AndroidColor.TRANSPARENT
                }
                if (Build.VERSION.SDK_INT >= 29) it.isNavigationBarContrastEnforced = false
                val controller = WindowCompat.getInsetsController(it, it.decorView)
                controller.isAppearanceLightStatusBars = !controls && lightBackground
                controller.isAppearanceLightNavigationBars = !controls && lightBackground
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (controls) controller.show(WindowInsetsCompat.Type.systemBars())
                else controller.hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose { }
        }
        val background = Color(AndroidColor.parseColor(settings.epubImageBackground.cssColor))
        val surface = Color(0xFF171C23)
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(Modifier.fillMaxSize().background(background)) {
                Column(Modifier.fillMaxSize()) {
                    if (controls) {
                        Row(Modifier.fillMaxWidth().background(surface).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .statusBarsPadding().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close image viewer") }
                            Column(Modifier.weight(1f)) {
                                Text(state.images.getOrNull(state.index)?.title ?: "Illustrations", maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                if (state.images.isNotEmpty()) Text("Image " + (state.index + 1) + " of " + state.images.size,
                                    style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                            }
                            IconButton(onClick = { controls = false }) { Icon(Icons.Rounded.Fullscreen, "Hide controls for full-screen viewing") }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        when {
                            state.loading -> CircularProgressIndicator()
                            state.error != null -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.error, color = if (lightBackground) Color.Black else Color.White)
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = onRetry) { Text("Retry") }
                            }
                            state.asset != null -> key(state.asset) {
                                EpubZoomImage(state.asset, settings.epubImageFit, settings.epubImageBackground,
                                    zoom, onRetry, showControls = { controls = true })
                            }
                            else -> Text("There are no illustrations in this section.", modifier = Modifier.padding(24.dp),
                                color = if (lightBackground) Color.Black else Color.White)
                        }
                    }
                    if (controls) {
                        Column(Modifier.fillMaxWidth().background(surface).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .navigationBarsPadding().padding(horizontal = 8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                ViewerChoice(settings.epubImageFit.displayName, EpubImageFit.entries.toList(),
                                    { it.displayName }, { value ->
                                        preferences.updateEpubImageFit(value)
                                        zoom.reset?.invoke()
                                    }, Modifier.weight(1f))
                                ViewerChoice(settings.epubImageBackground.displayName, EpubImageBackground.entries.toList(),
                                    { it.displayName }, preferences::updateEpubImageBackground, Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                IconButton(onClick = { onSelect(state.index - 1) }, enabled = state.index > 0) {
                                    Icon(Icons.AutoMirrored.Rounded.NavigateBefore, "Previous illustration")
                                }
                                IconButton(onClick = { zoom.zoom?.invoke(1f / 1.25f) }, enabled = state.asset != null) {
                                    Icon(Icons.Rounded.ZoomOut, "Zoom out")
                                }
                                Text("Pinch or double-tap", style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f), maxLines = 2)
                                IconButton(onClick = { zoom.zoom?.invoke(1.25f) }, enabled = state.asset != null) {
                                    Icon(Icons.Rounded.ZoomIn, "Zoom in")
                                }
                                IconButton(onClick = { onSelect(state.index + 1) }, enabled = state.index + 1 < state.images.size) {
                                    Icon(Icons.AutoMirrored.Rounded.NavigateNext, "Next illustration")
                                }
                            }
                        }
                    }
                }
                if (!controls) {
                    FilledIconButton(onClick = { controls = true }, modifier = Modifier.align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp)) {
                        Icon(Icons.Rounded.FullscreenExit, "Show image controls")
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ViewerChoice(label: String, options: List<T>, name: (T) -> String,
    onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Rounded.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { value -> DropdownMenuItem(text = { Text(name(value)) },
                onClick = { expanded = false; onSelect(value) }) }
        }
    }
}

/** Only trusted HTML plus a single validated local image; every other resource request is denied. */
@Suppress("SetJavaScriptEnabled")
@Composable
private fun EpubZoomImage(asset: EpubImageAsset, fit: EpubImageFit, background: EpubImageBackground,
    controls: EpubImageZoomControls, onRetry: () -> Unit, showControls: () -> Unit) {
    val currentShowControls by rememberUpdatedState(showControls)
    val currentFit by rememberUpdatedState(fit)
    val currentBackground by rememberUpdatedState(background)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var browser by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val active = remember { AtomicBoolean(true) }
    LaunchedEffect(asset, ready, failed, lifecycle) {
        if (!ready && !failed) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(15_000)
            if (!ready) failed = true
        }
    }
    DisposableEffect(lifecycle, browser) {
        val web = browser
        controls.zoom = { factor ->
            if (active.get()) web?.evaluateJavascript("window.nnViewer && window.nnViewer.zoom(" + factor + ");", null)
        }
        controls.reset = { if (active.get()) web?.evaluateJavascript("window.nnViewer && window.nnViewer.reset();", null) }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> web?.onPause()
                Lifecycle.Event.ON_RESUME -> web?.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) web?.onResume() else web?.onPause()
        onDispose { lifecycle.removeObserver(observer); controls.zoom = null; controls.reset = null }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!failed) AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    browser = this
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    isHorizontalScrollBarEnabled = false
                    isVerticalScrollBarEnabled = false
                    settings.apply {
                        javaScriptEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        domStorageEnabled = false
                        blockNetworkLoads = true
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                    }
                    val web = this
                    addJavascriptInterface(object {
                        @JavascriptInterface fun ready() { web.post { if (active.get()) ready = true } }
                        @JavascriptInterface fun failed() { web.post { if (active.get()) failed = true } }
                        @JavascriptInterface fun showControls() { web.post { if (active.get()) currentShowControls() } }
                    }, "ImageViewerEvents")
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                            if (request.url.toString() == EpubImageViewerDocument.IMAGE_URL)
                                WebResourceResponse(asset.mimeType, null, 200, "OK", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(asset.bytes))
                            else WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                        override fun onPageFinished(view: WebView, url: String) {
                            view.evaluateJavascript("window.nnViewer && window.nnViewer.configure('" + currentFit.jsValue + "','" + currentBackground.cssColor + "');", null)
                        }
                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            if (active.get()) failed = true
                            return true // onRelease removes and destroys this viewer after a renderer failure.
                        }
                    }
                    loadDataWithBaseURL(EpubImageViewerDocument.ORIGIN, EpubImageViewerDocument.html, "text/html", "UTF-8", null)
                }
            },
            onRelease = { web ->
                active.set(false)
                controls.zoom = null
                controls.reset = null
                // A failed renderer may reject further calls. Cleanup must not escape onto Main.
                try {
                    web.stopLoading()
                    web.removeJavascriptInterface("ImageViewerEvents")
                    web.webViewClient = WebViewClient()
                    web.removeAllViews()
                } catch (_: RuntimeException) { }
                finally {
                    try { web.destroy() } catch (_: RuntimeException) { }
                    browser = null
                }
            },
            update = { web ->
                web.evaluateJavascript("window.nnViewer && window.nnViewer.configure('" + fit.jsValue + "','" + background.cssColor + "');", null)
            }
        )
        if (!ready && !failed) CircularProgressIndicator()
        if (failed) Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("This illustration could not be rendered.", color = if (background == EpubImageBackground.WHITE ||
                background == EpubImageBackground.SEPIA) Color.Black else Color.White)
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
