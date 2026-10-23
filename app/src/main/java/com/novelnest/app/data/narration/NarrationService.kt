package com.novelnest.app.data.narration

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.*
import androidx.core.content.ContextCompat
import com.novelnest.app.MainActivity
import com.novelnest.app.NovelNestApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import org.json.JSONObject
import java.io.File

/** User-started cloud audio session. No local synthesis and no restart after process death. */
class NarrationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate +
        CoroutineExceptionHandler { _, error ->
            // SupervisorJob isolates children; it does not handle uncaught launch failures.
            // Log only the type/stage, never chapter text, endpoint credentials or raw errors.
            android.util.Log.w("NovelNestNarration", "Background failure: ${error.javaClass.simpleName} during ${phase.name}")
            runCatching { releasePlayer(); fail("Narration was interrupted. Tap Play to retry.") }
                .onFailure { stopSelf() }
        })
    private lateinit var store: NarrationStore
    private lateinit var audio: NarrationAudio
    private lateinit var session: MediaSession
    private lateinit var audioManager: AudioManager
    private var focus: AudioFocusRequest? = null
    private var player: MediaPlayer? = null
    private var prepared = false
    private var playbackStarted = false
    private var phase = NarrationPhase.IDLE
    private var prepareTimeout: Job? = null
    private var requestedSpeed = 1f
    private var actualSpeed = 1f
    private var durationMs = 0L
    private var publishedMediaId = ""
    private var publishedDuration = -1L
    private var work: Job? = null
    private var prefetch: Deferred<File>? = null
    private var nextFile: File? = null
    private var currentFile: File? = null
    private var checkpointJob: Job? = null
    private var idleJob: Job? = null
    private var epoch = 0
    private var wanted = false
    private var foreground = false
    private var resumeOnFocus = false
    private var novelId = ""
    private var chapterUrl = ""
    private var chapterId = ""
    private var chapterTitle = ""
    private var fingerprint = ""
    private var passages = emptyList<String>()
    private var index = 0
    private var offsetMs = 0

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { pause() }
    }

    override fun onCreate() {
        super.onCreate()
        store = NarrationStore(this)
        audio = NarrationAudio(NarrationCache.directory(this), store)
        NarrationCache.playbackStarted()
        audioManager = getSystemService(AudioManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Chapter narration", NotificationManager.IMPORTANCE_LOW))
        session = MediaSession(this, "NovelNestNarration").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { control { resume() } }
                override fun onPause() { control { pause() } }
                override fun onStop() { control { stopSession() } }
                override fun onSkipToNext() { control { skip(1) } }
                override fun onSkipToPrevious() { control { skip(-1) } }
                override fun onSeekTo(pos: Long) { control { seekWithinPassage(pos) } }
            })
            isActive = true
        }
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            NarrationSpeed.load(applicationContext)
            NarrationSpeed.values.filterNotNull().collect { speed ->
                requestedSpeed = speed
                // setPlaybackParams can start a paused player: defer updates until explicit resume.
                if (prepared && wanted && runCatching { player?.isPlaying == true }.getOrDefault(false)) {
                    runCatching { applySpeed(checkNotNull(player)) }
                        .onSuccess { message -> publish(message = message.orEmpty()) }
                        .onFailure { fail("Playback speed could not be changed. Tap Play to retry.") }
                }
            }
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        // Promote before content/network work, including when starting from a media notification.
        if (!promote()) { stopSelf(); return START_NOT_STICKY }
        control { when (intent.action) {
            START -> startChapter(intent)
            PLAY -> resume()
            PAUSE -> pause()
            STOP -> stopSession()
            NEXT -> skip(1)
            PREVIOUS -> skip(-1)
            SEEK -> if (intent.getStringExtra("mediaId") == currentMediaId()) {
                seekWithinPassage(intent.getLongExtra("positionMs", 0L))
            }
            else -> stopSession()
        } }
        return START_NOT_STICKY
    }

    private fun control(block: () -> Unit) {
        try { block() } catch (error: RuntimeException) {
            android.util.Log.w("NovelNestNarration", "Control failure: ${error.javaClass.simpleName} during ${phase.name}")
            releasePlayer()
            fail("Narration controls were interrupted. Tap Play to retry.")
        }
    }

    private fun promote(): Boolean {
        if (foreground) return true
        return try {
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(ID, notification())
        foreground = true
        true
    } catch (error: RuntimeException) {
        android.util.Log.w("NovelNestNarration", "Foreground playback denied: ${error.javaClass.simpleName}")
        fail("Android could not start background playback. Open the app and tap Play again.")
        false
        }
    }

    private fun startChapter(intent: Intent) {
        save()
        invalidate()
        novelId = intent.getStringExtra("novelId").orEmpty()
        chapterUrl = intent.getStringExtra("chapterUrl").orEmpty()
        chapterId = intent.getStringExtra("chapterId").orEmpty()
        chapterTitle = intent.getStringExtra("chapterTitle").orEmpty()
        passages = emptyList(); index = 0; offsetMs = 0; fingerprint = ""
        if (novelId.isBlank() || chapterUrl.isBlank()) { fail("Chapter is not available for narration."); return }
        wanted = true; idleJob?.cancel(); publish(loading = true, newPhase = NarrationPhase.LOADING_CHAPTER)
        val generation = epoch
        work = scope.launch {
            try {
                NarrationSpeed.load(applicationContext)
                requestedSpeed = NarrationSpeed.values.value ?: 1f
                val repository = (application as NovelNestApplication).novelRepository
                val novel = repository.getNovelById(novelId) ?: error("Open the novel again before starting narration.")
                val chapter = repository.getChapterContent(novel.source, chapterUrl).getOrThrow()
                val prepared = withContext(Dispatchers.Default) {
                    narrationPassages(listOf(chapter.title) + chapter.paragraphs)
                }
                if (generation != epoch) return@launch
                if (chapter.paragraphs.none { it.isNotBlank() } || prepared.isEmpty()) error("Chapter text is unavailable.")
                passages = prepared
                fingerprint = withContext(Dispatchers.Default) { audioHash(prepared.joinToString("\n")) }
                if (generation != epoch) return@launch
                val saved = store.checkpoint(novelId)
                if (saved?.optString("chapterUrl") == chapterUrl && saved.optString("fingerprint") == fingerprint) {
                    index = saved.optInt("passage").coerceIn(passages.indices)
                    offsetMs = saved.optInt("offsetMs").coerceAtLeast(0)
                }
                work = null
                resume()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { if (generation == epoch) fail(e.message ?: "Could not load narration.") }
        }
    }

    private fun acquireFocus(): Boolean {
        focus?.let { return true }
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> pause()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        val shouldResume = wanted
                        pause(abandonFocus = false)
                        resumeOnFocus = shouldResume
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocus) { resumeOnFocus = false; resume() }
                }
            }.build()
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        focus = request
        return true
    }

    private fun resume() {
        // Repeated media Play events must not restart a pending chapter/audio request.
        if (wanted && (work?.isActive == true || (player != null && !prepared))) return
        if (passages.isEmpty()) {
            if (novelId.isBlank() || chapterUrl.isBlank()) { stopSession(); return }
            startChapter(Intent().putExtra("novelId", novelId).putExtra("chapterId", chapterId)
                .putExtra("chapterUrl", chapterUrl).putExtra("chapterTitle", chapterTitle))
            return
        }
        session.isActive = true
        idleJob?.cancel(); resumeOnFocus = false
        if (player != null && !prepared) return
        // MediaSession callbacks bypass onStartCommand. After the idle timeout,
        // Android 15 requires foreground promotion before requesting audio focus.
        if (!promote()) return
        if (!acquireFocus()) { fail("Another app is using audio. Tap Play to retry."); return }
        wanted = true
        player?.let {
            var message: String? = null
            runCatching { message = applySpeed(it); it.start(); playbackStarted = true }
                .onFailure { releasePlayer(); fail("Audio playback failed. Tap Play to retry.") }
            if (wanted) { publish(message = message.orEmpty()); schedulePrefetch(); checkpoints() }
            return
        }
        if (work?.isActive == true) return
        val generation = epoch
        publish(loading = true, newPhase = NarrationPhase.CHECKING_CACHE)
        work = scope.launch {
            try {
                val pending = prefetch
                if (pending != null && !pending.isCompleted) publish(loading = true, newPhase = NarrationPhase.WAITING_FOR_AUDIO)
                val file = nextFile ?: pending?.await() ?: audio.get(passages[index]) { progress ->
                    scope.launch {
                        if (generation == epoch && wanted && player == null) publish(loading = true, newPhase = progress)
                    }
                }
                prefetch = null; nextFile = null
                if (generation != epoch || !wanted) return@launch
                currentFile = file
                audio.trim(setOf(file.absolutePath))
                if (generation != epoch || !wanted) return@launch
                val media = MediaPlayer()
                player = media
                publish(loading = true, newPhase = NarrationPhase.PREPARING_PLAYER)
                media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                media.setWakeMode(this@NarrationService, PowerManager.PARTIAL_WAKE_LOCK)
                media.setOnPreparedListener {
                    if (generation != epoch || !wanted) return@setOnPreparedListener
                    prepareTimeout?.cancel(); prepareTimeout = null
                    prepared = true
                    runCatching {
                        durationMs = media.duration.toLong().coerceAtLeast(0L)
                        check(durationMs > 0L) { "Generated audio has no playable duration." }
                        if (offsetMs > 0 && durationMs > 0L) media.seekTo(
                            offsetMs.toLong().coerceAtMost(durationMs - 1L), MediaPlayer.SEEK_CLOSEST)
                        val message = applySpeed(media)
                        media.start(); playbackStarted = true
                        publish(message = message.orEmpty()); checkpoints(); schedulePrefetch()
                    }.onFailure { releasePlayer(); fail("Device could not start this audio. Tap Play to retry.") }
                }
                media.setOnSeekCompleteListener {
                    // Seek is asynchronous. Persist and publish the resulting position, not
                    // the old position returned immediately after calling seekTo().
                    if (generation == epoch && player === media && prepared) {
                        save()
                        publish()
                    }
                }
                media.setOnCompletionListener {
                    if (generation == epoch && wanted) {
                        checkpointJob?.cancel(); releasePlayer(); offsetMs = 0
                        if (index < passages.lastIndex) { index++; save(); work = null; resume() }
                        else { save(); pause(); publish(message = "Chapter complete. Select the next chapter to continue.", newPhase = NarrationPhase.COMPLETED) }
                    }
                }
                media.setOnErrorListener { _, what, extra ->
                    if (generation == epoch) {
                        val invalid = currentFile
                        releasePlayer()
                        scope.launch(Dispatchers.IO) { invalid?.delete() }
                        fail("Device could not decode cloud audio ($what/$extra). Invalid cache removed; tap Play to retry.")
                    }
                    true
                }
                media.setDataSource(file.absolutePath)
                media.prepareAsync()
                prepareTimeout = scope.launch {
                    delay(15_000)
                    if (generation == epoch && player === media && !prepared) {
                        releasePlayer()
                        fail("Audio download finished, but the device player timed out. Tap Play to retry.")
                    }
                }
                work = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { if (generation == epoch) fail(e.message ?: "Cloud narration unavailable.") }
        }
    }

    private fun schedulePrefetch() {
        if (!wanted || index >= passages.lastIndex || prefetch != null || nextFile != null) return
        val generation = epoch
        val text = passages[index + 1]
        prefetch = scope.async {
            val file = audio.get(text)
            if (generation == epoch) {
                audio.trim(setOfNotNull(currentFile?.absolutePath, file.absolutePath))
            }
            file
        }
        // async errors are consumed at transition, never crash the service or retry in a loop.
    }

    private fun pause(abandonFocus: Boolean = true) {
        wanted = false; resumeOnFocus = false
        playbackStarted = false
        checkpointJob?.cancel()
        if (prepared) player?.let {
            runCatching { if (it.isPlaying) it.pause(); offsetMs = it.currentPosition }
                .onFailure { releasePlayer() }
        }
        else releasePlayer()
        save()
        work?.cancel(); work = null
        prefetch?.cancel(); prefetch = null; nextFile = null
        if (player == null) epoch++
        if (abandonFocus) { focus?.let { audioManager.abandonAudioFocusRequest(it) }; focus = null }
        publish()
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(60_000)
            if (!wanted) {
                save()
                val previousState = NarrationStatus.state.value
                val lastDuration = durationMs
                releasePlayer()
                durationMs = lastDuration
                // Keep the paused session available in the app, but release the decoder
                // and foreground priority. A later explicit Play promotes us again.
                publish(message = previousState.message, newPhase = previousState.phase)
                stopForeground(STOP_FOREGROUND_DETACH)
                foreground = false
            }
        }
    }

    private fun skip(delta: Int) {
        if (passages.isEmpty()) return
        save(); invalidate(); index = (index + delta).coerceIn(passages.indices); offsetMs = 0
        save(); resume()
    }

    private fun fail(message: String) {
        android.util.Log.w("NovelNestNarration", "Narration stopped during ${phase.name}")
        pause(); publish(message = message.take(240), newPhase = NarrationPhase.FAILED)
    }

    private fun checkpoints() {
        checkpointJob?.cancel()
        checkpointJob = scope.launch {
            while (isActive && wanted) {
                delay(5_000)
                save()
                // Reuse the existing checkpoint cadence. Android extrapolates between
                // timestamps; no per-frame notifications or additional polling loop.
                publishPlaybackState()
            }
        }
    }

    private fun seekWithinPassage(positionMs: Long) {
        val media = player ?: return
        if (!prepared || durationMs <= 0L) return
        runCatching {
            media.seekTo(positionMs.coerceIn(0L, durationMs - 1L), MediaPlayer.SEEK_CLOSEST)
        }.onFailure { publish(message = "Could not seek this audio passage. Please try again.") }
    }

    private fun save() {
        if (novelId.isBlank() || passages.isEmpty()) return
        val position = if (prepared) runCatching { player?.currentPosition ?: offsetMs }.getOrDefault(offsetMs) else offsetMs
        offsetMs = position
        store.saveCheckpoint(JSONObject().put("novelId", novelId).put("chapterUrl", chapterUrl)
            .put("chapterId", chapterId).put("fingerprint", fingerprint).put("passage", index).put("offsetMs", position))
    }

    private fun applySpeed(media: MediaPlayer): String? {
        if (actualSpeed == requestedSpeed) return null
        val requested = requestedSpeed
        return try {
            media.playbackParams = PlaybackParams().allowDefaults().setPitch(1f).setSpeed(requested)
            actualSpeed = media.playbackParams.speed
            null
        } catch (_: RuntimeException) {
            media.playbackParams = PlaybackParams().allowDefaults().setPitch(1f).setSpeed(1f)
            actualSpeed = 1f
            requestedSpeed = 1f
            scope.launch { NarrationSpeed.fallback(applicationContext, requested) }
            "This device could not apply that speed. Playing at 1×."
        }
    }

    private fun releasePlayer() {
        prepareTimeout?.cancel(); prepareTimeout = null
        player?.let { runCatching { it.release() } }
        player = null; prepared = false; playbackStarted = false; actualSpeed = 1f; durationMs = 0L; currentFile = null
    }
    private fun invalidate() {
        epoch++; wanted = false; resumeOnFocus = false
        work?.cancel(); work = null; prefetch?.cancel(); prefetch = null; nextFile = null
        checkpointJob?.cancel(); releasePlayer()
    }

    private fun publish(loading: Boolean = false, message: String = "", newPhase: NarrationPhase? = null) {
        val playing = wanted && prepared && playbackStarted && !loading
        phase = newPhase ?: when { playing -> NarrationPhase.PLAYING; loading -> phase; else -> NarrationPhase.PAUSED }
        NarrationStatus.mutable.value = NarrationState(novelId, chapterTitle, playing, loading,
            if (passages.isEmpty()) 0 else index + 1, passages.size, message, chapterId, phase)
        val mediaId = "$novelId|$chapterId|$index|$chapterTitle"
        if (publishedMediaId != mediaId || publishedDuration != durationMs) {
            session.setMetadata(MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, mediaId)
                .putString(MediaMetadata.METADATA_KEY_TITLE, chapterTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Novel Nest · Passage ${index + 1} of ${passages.size}")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs).build())
            publishedMediaId = mediaId
            publishedDuration = durationMs
        }
        publishPlaybackState(loading)
        runCatching { getSystemService(NotificationManager::class.java).notify(ID, notification()) }
            .onFailure { android.util.Log.w("NovelNestNarration", "Media notification update failed: ${it.javaClass.simpleName}") }
    }

    private fun publishPlaybackState(loading: Boolean = NarrationStatus.state.value.loading) {
        val ready = prepared && player != null && durationMs > 0L
        val playing = wanted && prepared && playbackStarted && !loading
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        if (ready) actions = actions or PlaybackState.ACTION_SEEK_TO
        val position = if (ready) runCatching { player!!.currentPosition.toLong().coerceIn(0L, durationMs) }
            .getOrDefault(offsetMs.toLong().coerceIn(0L, durationMs))
            else if (durationMs > 0L) offsetMs.toLong().coerceIn(0L, durationMs) else PlaybackState.PLAYBACK_POSITION_UNKNOWN
        val now = SystemClock.elapsedRealtime()
        NarrationStatus.mutableTimeline.value = NarrationTimeline(
            positionMs = if (position >= 0L) position else offsetMs.toLong().coerceAtLeast(0L),
            durationMs = durationMs, speed = if (playing) actualSpeed else 0f,
            updatedAtMs = now, playing = playing, canSeek = ready, mediaId = currentMediaId())
        session.setPlaybackState(PlaybackState.Builder().setActions(actions)
            .setBufferedPosition(if (ready) durationMs else 0L)
            .setState(if (loading) PlaybackState.STATE_BUFFERING else if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                position, if (playing) actualSpeed else 0f, now).build())
    }

    private fun currentMediaId() = "$novelId|$chapterId|$index|$fingerprint"

    private fun action(action: String): PendingIntent = PendingIntent.getService(this, action.hashCode(),
        Intent(this, NarrationService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun notification(): Notification {
        val state = NarrationStatus.state.value
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(chapterTitle.ifBlank { "Novel Nest narration" })
            .setContentText(state.message.ifBlank { "${state.phase.caption} · Passage ${state.passage} of ${state.total}" })
            .setContentIntent(PendingIntent.getActivity(this, 9201, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(wanted)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous passage", action(PREVIOUS)).build())
            .addAction(Notification.Action.Builder(if (wanted) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (wanted) "Pause" else "Play", action(if (wanted) PAUSE else PLAY)).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", action(STOP)).build())
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2)).build()
    }

    private fun stopSession() {
        save(); invalidate(); idleJob?.cancel()
        focus?.let { audioManager.abandonAudioFocusRequest(it) }; focus = null
        NarrationStatus.mutable.value = NarrationState()
        NarrationStatus.mutableTimeline.value = NarrationTimeline()
        session.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; stopSelf()
    }
    override fun onTaskRemoved(rootIntent: Intent?) { if (!wanted) stopSession() }
    override fun onDestroy() {
        save(); invalidate(); scope.cancel()
        audio.close()
        focus?.let { audioManager.abandonAudioFocusRequest(it) }
        unregisterReceiver(noisy); session.release()
        NarrationCache.playbackStopped()
        NarrationStatus.mutable.value = NarrationState()
        NarrationStatus.mutableTimeline.value = NarrationTimeline()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "narration_playback"
        private const val ID = 9201
        const val START = "narration.START"
        const val PLAY = "narration.PLAY"
        const val PAUSE = "narration.PAUSE"
        const val STOP = "narration.STOP"
        const val NEXT = "narration.NEXT"
        const val PREVIOUS = "narration.PREVIOUS"
        const val SEEK = "narration.SEEK"
        fun seek(context: Context, positionMs: Long, mediaId: String) {
            ContextCompat.startForegroundService(context, Intent(context, NarrationService::class.java).setAction(SEEK)
                .putExtra("positionMs", positionMs).putExtra("mediaId", mediaId))
        }
        fun command(context: Context, action: String) {
            ContextCompat.startForegroundService(context, Intent(context, NarrationService::class.java).setAction(action))
        }
        fun start(context: Context, novelId: String, chapterId: String, chapterUrl: String, title: String) {
            ContextCompat.startForegroundService(context, Intent(context, NarrationService::class.java).setAction(START)
                .putExtra("novelId", novelId).putExtra("chapterId", chapterId).putExtra("chapterUrl", chapterUrl).putExtra("chapterTitle", title))
        }
    }
}
