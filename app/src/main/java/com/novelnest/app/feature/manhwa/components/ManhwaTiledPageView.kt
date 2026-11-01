package com.novelnest.app.feature.manhwa.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.data.manhwa.ManhwaImageStore
import com.novelnest.app.data.manhwa.ManhwaPageLease
import com.novelnest.app.data.model.ManhwaPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

private const val TILE_MAX_HEIGHT_PX = 2048

private data class ImageTileSpec(
    val index: Int,
    val rect: Rect,
    val width: Int,
    val height: Int
) {
    val aspectRatio: Float get() = if (height > 0) width.toFloat() / height.toFloat() else 1f
}

/**
 * Tiled webtoon page renderer.
 * - Leases the original image from [ManhwaImageStore].
 * - Uses [BitmapRegionDecoder] to decode bounded visible slices off-main.
 * - Enforces lifecycle cancellation with [repeatOnLifecycle(Lifecycle.State.STARTED)].
 * - Releases decoders before closing leases, guarding disposal races.
 * - Recycles offscreen tile bitmaps to strictly bound memory.
 */
@Composable
fun ManhwaTiledPageView(
    page: ManhwaPage,
    chapterUrl: String,
    imageStore: ManhwaImageStore,
    onPageLoaded: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var retryTrigger by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var tileSpecs by remember { mutableStateOf<List<ImageTileSpec>>(emptyList()) }
    val decodeLock = remember { Any() }
    var activeDecoder by remember { mutableStateOf<BitmapRegionDecoder?>(null) }

    // Derive initial aspect ratio to prevent layout jumping
    val initialAspectRatio = remember(page.width, page.height) {
        val w = page.width ?: 800
        val h = page.height ?: 1200
        if (h > 0) (w.toFloat() / h.toFloat()).coerceIn(0.05f, 5f) else 0.67f
    }

    LaunchedEffect(page.url, chapterUrl, retryTrigger, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            isLoading = true
            errorMessage = null
            onPageLoaded(false)
            var lease: ManhwaPageLease? = null
            var decoder: BitmapRegionDecoder? = null
            try {
                val forceRefresh = retryTrigger > 0
                lease = imageStore.acquire(page, chapterUrl, forceRefresh)
                val acquiredFile = lease.file

                val createdDecoder = withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ParcelFileDescriptor.open(acquiredFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                            BitmapRegionDecoder.newInstance(pfd)
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        BitmapRegionDecoder.newInstance(acquiredFile.absolutePath, false)
                    }
                }

                decoder = createdDecoder
                synchronized(decodeLock) {
                    activeDecoder = createdDecoder
                }

                val imgW = createdDecoder.width
                val imgH = createdDecoder.height
                val numSlices = (imgH + TILE_MAX_HEIGHT_PX - 1) / TILE_MAX_HEIGHT_PX

                val specs = ArrayList<ImageTileSpec>(numSlices)
                for (i in 0 until numSlices) {
                    val yStart = i * TILE_MAX_HEIGHT_PX
                    val yEnd = minOf((i + 1) * TILE_MAX_HEIGHT_PX, imgH)
                    val sliceH = yEnd - yStart
                    val rect = Rect(0, yStart, imgW, yEnd)
                    specs.add(ImageTileSpec(index = i, rect = rect, width = imgW, height = sliceH))
                }

                tileSpecs = specs
                isLoading = false

                // Hold coroutine alive while in STARTED to maintain lease and decoder
                kotlinx.coroutines.awaitCancellation()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                isLoading = false
                errorMessage = error.message ?: "Failed to load page ${page.index + 1}"
                onPageLoaded(false)
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    synchronized(decodeLock) {
                        activeDecoder = null
                        decoder?.recycle()
                    }
                    lease?.close()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (tileSpecs.isEmpty()) Modifier.aspectRatio(initialAspectRatio)
                else Modifier.wrapContentHeight()
            )
            .background(NovelBackground),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(initialAspectRatio)
                    .background(NovelSurfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = NovelPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        text = "Page ${page.index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NovelTextSecondary
                    )
                }
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(initialAspectRatio)
                    .padding(16.dp)
                    .background(NovelSurface, shape = RoundedCornerShape(12.dp))
                    .border(1.dp, NovelSurfaceBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BrokenImage,
                        contentDescription = null,
                        tint = NovelTextTertiary,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Page ${page.index + 1} failed to load",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NovelTextPrimary
                    )
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = NovelTextSecondary,
                        maxLines = 2
                    )
                    Button(
                        onClick = { retryTrigger++ },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NovelPrimary,
                            contentColor = NovelOnPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = NovelOnPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Retry Page",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NovelOnPrimary
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    // Report readiness after the real image height has replaced its placeholder.
                    .onSizeChanged { if (it.height > 0) onPageLoaded(true) }
            ) {
                tileSpecs.forEach { spec ->
                    TiledSliceView(
                        spec = spec,
                        decodeLock = decodeLock,
                        getDecoder = { activeDecoder },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Individual bounded tile slice:
 * - Detects visibility relative to window + buffer using onGloballyPositioned.
 * - Decodes region off-main only while visible.
 * - Recycles bitmap when scrolled out of view to keep memory strictly bounded.
 */
@Composable
private fun TiledSliceView(
    spec: ImageTileSpec,
    decodeLock: Any,
    getDecoder: () -> BitmapRegionDecoder?,
    modifier: Modifier = Modifier
) {
    var isVisibleInWindow by remember { mutableStateOf(false) }
    var tileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val screenHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    val bufferPx = screenHeightPx * 0.75f // 75% buffer around window

    DisposableEffect(Unit) {
        onDispose {
            tileBitmap?.recycle()
            tileBitmap = null
        }
    }

    LaunchedEffect(isVisibleInWindow, spec.rect) {
        if (isVisibleInWindow) {
            val decoded = withContext(Dispatchers.IO) {
                synchronized(decodeLock) {
                    val decoder = getDecoder() ?: return@withContext null
                    if (decoder.isRecycled) return@withContext null
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    try {
                        decoder.decodeRegion(spec.rect, options)
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
            tileBitmap = decoded
        } else {
            tileBitmap?.recycle()
            tileBitmap = null
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(spec.aspectRatio)
            .background(NovelBackground)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                val isVisible = bounds.bottom >= -bufferPx && bounds.top <= (screenHeightPx + bufferPx)
                if (isVisibleInWindow != isVisible) {
                    isVisibleInWindow = isVisible
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val bitmap = tileBitmap
        if (bitmap != null && !bitmap.isRecycled) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Subtle placeholder matching dark background while tile prepares
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NovelBackground)
            )
        }
    }
}
