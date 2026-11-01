package com.novelnest.app.data.narration

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * This scope outlives an individual service's cancelled playback scope. HTTPS
 * eviction closes TLS sockets and must never run from Service.onDestroy's main
 * thread. The job retains the client only, with no Service/Activity reference.
 */
internal object NarrationNetworkCleanup {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun release(client: OkHttpClient) {
        scope.launch {
            fun cleanup(step: String, action: () -> Unit) {
                try { action() } catch (error: Exception) {
                    Log.w("NovelNestNarration", "Network cleanup $step failed: ${error.javaClass.simpleName}")
                }
            }
            cleanup("cancel") { client.dispatcher.cancelAll() }
            cleanup("TLS") { client.connectionPool.evictAll() }
            cleanup("dispatcher") { client.dispatcher.executorService.shutdown() }
        }
    }
}
