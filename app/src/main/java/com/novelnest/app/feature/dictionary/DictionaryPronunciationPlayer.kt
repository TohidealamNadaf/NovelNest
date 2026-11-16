package com.novelnest.app.feature.dictionary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.novelnest.app.data.dictionary.DictionaryAudioException
import com.novelnest.app.data.dictionary.DictionaryAudioStore
import com.novelnest.app.data.dictionary.DictionaryRecording
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

internal data class DictionaryPronunciationState(
    val loading: Boolean = false,
    val playing: Boolean = false,
    val error: String? = null,
)

/** Activity-scoped, foreground-only playback. No service, local synthesis, or progress polling. */
internal class DictionaryPronunciationPlayer(
    context: Context,
    private val scope: CoroutineScope,
    private val store: DictionaryAudioStore,
    private val canPlay: () -> Boolean,
) {
    var state by mutableStateOf(DictionaryPronunciationState())
        private set
    private val app = context.applicationContext
    private val manager = app.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var focus: AudioFocusRequest? = null
    private var media: MediaPlayer? = null
    private var job: Job? = null
    private var epoch = 0L
    private var closed = false
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { stop() }
    }
    private var registered = false

    init {
        try {
            ContextCompat.registerReceiver(app, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        } catch (_: RuntimeException) { /* Audio focus and Activity pause still stop playback. */ }
    }

    fun toggle(recording: DictionaryRecording?) {
        if (closed || !canPlay() || recording == null) return
        if (state.loading || state.playing) { stop(); return }
        val id = ++epoch
        state = DictionaryPronunciationState(loading = true)
        job = scope.launch {
            try {
                store.withRecording(recording) { file ->
                    withContext(Dispatchers.Main.immediate) {
                        if (id == epoch && canPlay()) play(file)
                    }
                }
            } catch (_: TimeoutCancellationException) {
                if (id == epoch) state = DictionaryPronunciationState(error = "Pronunciation playback timed out. Please try again.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (id == epoch) state = DictionaryPronunciationState(error =
                    if (error is DictionaryAudioException) error.message else "Could not play this pronunciation. Please try again.")
            } finally {
                if (id == epoch) {
                    state = state.copy(loading = false, playing = false)
                    job = null
                }
            }
        }
    }

    fun stop() {
        ++epoch
        job?.cancel()
        job = null
        releasePlayer()
        state = DictionaryPronunciationState()
    }

    fun close() {
        closed = true
        stop()
        if (registered) {
            runCatching { app.unregisterReceiver(noisy) }
            registered = false
        }
    }

    private suspend fun play(file: File) {
        var owned: MediaPlayer? = null
        try {
            // Also bounds a broken decoder which never calls prepared/completion/error.
            withTimeout(35_000) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    fun complete(error: Exception? = null) {
                        if (continuation.isActive) continuation.resumeWith(
                            if (error == null) Result.success(Unit) else Result.failure(error))
                    }
                    try {
                        val player = MediaPlayer()
                        owned = player
                        media = player
                        player.setAudioAttributes(attributes)
                        player.setOnPreparedListener {
                            if (!continuation.isActive || media !== player) return@setOnPreparedListener
                            if (!canPlay()) {
                                continuation.cancel(CancellationException("The dictionary is no longer active."))
                                return@setOnPreparedListener
                            }
                            try {
                                if (player.duration !in 1..30_000) {
                                    complete(DictionaryAudioException("Unsupported pronunciation recording.", discardRecording = true))
                                    return@setOnPreparedListener
                                }
                                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                                    .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
                                    .setOnAudioFocusChangeListener({ change ->
                                        if (change < 0 && media === player && focus != null) stop()
                                    }, main).build()
                                focus = request // Also abandon failed/throwing requests to unregister their listener.
                                if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                                    complete(DictionaryAudioException("Audio is busy. Please try pronunciation again when ready."))
                                    return@setOnPreparedListener
                                }
                                player.start()
                                state = DictionaryPronunciationState(playing = true)
                            } catch (_: RuntimeException) {
                                complete(DictionaryAudioException("Could not play this pronunciation. Please try again."))
                            }
                        }
                        player.setOnCompletionListener { complete() }
                        player.setOnErrorListener { _, _, _ ->
                            complete(DictionaryAudioException("This pronunciation recording could not be decoded.", discardRecording = true))
                            true
                        }
                        player.setDataSource(file.absolutePath)
                        player.prepareAsync()
                        continuation.invokeOnCancellation {
                            main.post { if (media === player) releasePlayer() }
                        }
                    } catch (_: Exception) {
                        complete(DictionaryAudioException("Could not open this pronunciation recording. Please try again."))
                    }
                }
            }
        } finally { if (media === owned) releasePlayer() }
    }

    private fun releasePlayer() {
        val old = media
        media = null
        if (old != null) {
            runCatching { old.setOnPreparedListener(null) }
            runCatching { old.setOnCompletionListener(null) }
            runCatching { old.setOnErrorListener(null) }
            runCatching { old.release() }
        }
        val request = focus
        focus = null
        if (request != null) runCatching { manager.abandonAudioFocusRequest(request) }
    }
}
