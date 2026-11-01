package com.novelnest.app.feature.reader

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.util.WeakHashMap

private class ReaderScreenOnRequest(val previous: Boolean, var count: Int = 0)
// Destination transitions can temporarily share a ComposeView. Each effect releases only its own request.
// Values contain no View/Activity reference; all access is on the composition's main thread.
private val screenOnRequests = WeakHashMap<View, ReaderScreenOnRequest>()

@Composable
internal fun ReaderKeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        if (enabled) {
            val request = screenOnRequests.getOrPut(view) { ReaderScreenOnRequest(view.keepScreenOn) }
            request.count++
            view.keepScreenOn = true
        }
        onDispose {
            if (enabled) {
                val request = screenOnRequests[view]
                if (request != null && --request.count == 0) {
                    screenOnRequests.remove(view)
                    view.keepScreenOn = request.previous
                }
            }
        }
    }
}
