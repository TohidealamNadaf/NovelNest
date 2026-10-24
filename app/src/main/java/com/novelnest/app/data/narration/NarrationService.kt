package com.novelnest.app.data.narration

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.*
import android.net.Uri
import androidx.core.content.ContextCompat
import com.novelnest.app.MainActivity
import com.novelnest.app.NovelNestApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID

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
    // The request identity survives a manual jump to its passage. Decoder epochs
    // still invalidate the old player's callbacks without restarting synthesis.
    private class PassageRequest(val index: Int) {
        var phase = NarrationPhase.CHECKING_CACHE
        lateinit var download: Deferred<File>
    }
    private var prefetch: PassageRequest? = null
    private var currentFile: File? = null
    private var checkpointJob: Job? = null
    private var idleJob: Job? = null
    private var epoch = 0
    private var wanted = false
    private var foreground = false
    private var stopping = false
    private var sessionId = UUID.randomUUID().toString()
    private var pendingSkip: Int? = null
    private var skipDeadline = 0L
    private var skipJob: Job? = null
    private var noisyRegistered = false
    private var cacheOwned = false
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
        override fun onReceive(context: Context?, intent: Intent?) { control { pause() } }
    }

    override fun onCreate() {
        super.onCreate()
        store = NarrationStore(this)
        audio = NarrationAudio(NarrationCache.directory(this), store)
        NarrationCache.playbackStarted()
        cacheOwned = true
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
        noisyRegistered = true
        activeService = WeakReference(this)
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
        // Every explicit startForegroundService call needs a fresh acknowledgement,
        // even when our local foreground flag was already true or Stop is pending.
        if (intent.action == START && !promote(force = true)) { stopSelf(); return START_NOT_STICKY }
        if (stopping) { stopSelf(); return START_NOT_STICKY }
        // Only an explicit Listen creates foreground playback. Old notification
        // controls cannot revive or change a replacement listening session.
        if (intent.action != START &&
            (intent.getStringExtra(SESSION) != sessionId || novelId.isBlank())) {
            if (novelId.isBlank()) stopSelf()
            return START_NOT_STICKY
        }
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
        if (stopping) return
        try { block() } catch (error: RuntimeException) {
            android.util.Log.w("NovelNestNarration", "Control failure: ${error.javaClass.simpleName} during ${phase.name}")
            runCatching {
                releasePlayer()
                fail("Narration controls were interrupted. Tap Play to retry.")
            }.onFailure { stopSession() }
        }
    }

    private fun promote(force: Boolean = false): Boolean {
        if (foreground && !force) return true
        return try {
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(ID, notification())
        foreground = true
        if (force) android.util.Log.d("NovelNestNarration", "Foreground start acknowledged")
        true
    } catch (error: RuntimeException) {
        android.util.Log.w("NovelNestNarration", "Foreground playback denied: ${error.javaClass.simpleName}")
        fail("Android could not start background playback. Open the app and tap Play again.")
        false
        }
    }

    private fun startChapter(intent: Intent) {
        if (wanted && novelId == intent.getStringExtra("novelId") &&
            chapterUrl == intent.getStringExtra("chapterUrl")) {
            resume()
            return
        }
        save()
        invalidate()
        sessionId = UUID.randomUUID().toString()
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
                control { when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> pause()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        val shouldResume = wanted
                        pause(abandonFocus = false)
                        resumeOnFocus = shouldResume
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocus) { resumeOnFocus = false; resume() }
                } }
            }.build()
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        focus = request
        return true
    }

    private fun resume() {
        if (stopping) return
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
                val pending = prefetch?.takeIf { it.index == index }
                if (pending != null && !pending.download.isCompleted) publish(loading = true, newPhase = pending.phase)
                val file = pending?.download?.await() ?: audio.get(passages[index]) { progress ->
                    scope.launch {
                        if (generation == epoch && wanted && player == null) publish(loading = true, newPhase = progress)
                    }
                }
                if (generation != epoch || !wanted) return@launch
                if (prefetch === pending) prefetch = null
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
                    if (stopping || generation != epoch || !wanted || player !== media) return@setOnPreparedListener
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
                    control {
                        if (generation == epoch && player === media && prepared) {
                            save()
                            publish()
                        }
                    }
                }
                media.setOnCompletionListener {
                    control {
                        if (generation == epoch && wanted && player === media) {
                            checkpointJob?.cancel(); releasePlayer(); offsetMs = 0
                            if (pendingSkip != null) {
                                // A queued manual destination wins over automatic advance.
                                publish(loading = true, newPhase = NarrationPhase.CHECKING_CACHE)
                            } else if (index < passages.lastIndex) { index++; save(); work = null; resume() }
                            else { save(); pause(); publish(message = "Chapter complete. Select the next chapter to continue.", newPhase = NarrationPhase.COMPLETED) }
                        }
                    }
                }
                media.setOnErrorListener { _, what, extra ->
                    control {
                        if (generation == epoch && player === media) {
                            val invalid = currentFile
                            releasePlayer()
                            scope.launch(Dispatchers.IO) { invalid?.delete() }
                            fail("Device could not decode cloud audio ($what/$extra). Invalid cache removed; tap Play to retry.")
                        }
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
        if (stopping || !wanted || index >= passages.lastIndex || prefetch != null) return
        val pending = PassageRequest(index + 1)
        val text = passages[pending.index]
        prefetch = pending
        // Assign the handle before starting, including when the audio is cached.
        pending.download = scope.async(start = CoroutineStart.LAZY) {
            val file = audio.get(text) { progress ->
                scope.launch {
                    if (!stopping && prefetch === pending) {
                        pending.phase = progress
                        if (wanted && index == pending.index && player == null) publish(loading = true, newPhase = progress)
                    }
                }
            }
            if (!stopping && prefetch === pending) {
                audio.trim(setOfNotNull(currentFile?.absolutePath, file.absolutePath))
            }
            file
        }
        pending.download.start()
        // async errors are consumed at transition, never crash the service or retry in a loop.
    }

    private fun pause(abandonFocus: Boolean = true) {
        if (stopping) return
        cancelPendingSkip()
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
        cancelPrefetch()
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
        // All entry points (app, PendingIntent and MediaSession) share this gate.
        // A second skip must not cancel accepted synthesis while the first loads.
        if (passages.isEmpty() || NarrationStatus.state.value.loading) return
        val targetIndex = ((pendingSkip ?: index) + delta).coerceIn(passages.indices)
        if (targetIndex == (pendingSkip ?: index)) return
        pendingSkip = targetIndex
        skipDeadline = SystemClock.elapsedRealtime() + 180L
        if (skipJob?.isActive == true) return
        // One timer per burst, not a service start/coroutine/network call per tap.
        skipJob = scope.launch {
            while (true) {
                val remaining = skipDeadline - SystemClock.elapsedRealtime()
                if (remaining <= 0L) break
                delay(remaining)
            }
            val target = pendingSkip
            pendingSkip = null; skipJob = null
            if (target != null && !stopping) selectPassage(target)
        }
    }

    private fun selectPassage(targetIndex: Int) {
        if (targetIndex == index) {
            // The burst may have returned to its starting passage at completion.
            if (wanted && player == null) resume()
            return
        }
        save()
        // A manual Next has the same destination as the existing prefetch. Keep
        // that request/result; cancelling it cannot reliably stop remote inference.
        invalidate(keepPrefetchFor = targetIndex)
        index = targetIndex; offsetMs = 0
        android.util.Log.d("NovelNestNarration", "Selected audio passage ${index + 1}")
        save(); resume()
    }

    private fun fail(message: String) {
        if (stopping) return
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
        cancelPendingSkip()
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
    private fun cancelPrefetch() {
        val pending = prefetch
        prefetch = null
        cleanup("prefetch cancellation") { pending?.download?.cancel() }
    }

    private fun invalidate(keepPrefetchFor: Int? = null) {
        cancelPendingSkip()
        epoch++; wanted = false; resumeOnFocus = false
        val previousWork = work; work = null
        cleanup("request cancellation") { previousWork?.cancel() }
        if (keepPrefetchFor == null || prefetch?.index != keepPrefetchFor) cancelPrefetch()
        checkpointJob?.cancel(); releasePlayer()
    }

    private fun cancelPendingSkip() {
        pendingSkip = null
        skipJob?.cancel(); skipJob = null
    }

    private fun publish(loading: Boolean = false, message: String = "", newPhase: NarrationPhase? = null) {
        if (stopping) return
        val playing = wanted && prepared && playbackStarted && !loading
        phase = newPhase ?: when { playing -> NarrationPhase.PLAYING; loading -> phase; else -> NarrationPhase.PAUSED }
        NarrationStatus.mutable.value = NarrationState(novelId, chapterTitle, playing, loading,
            if (passages.isEmpty()) 0 else index + 1, passages.size, message, chapterId, phase, sessionId)
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
            updatedAtMs = now, playing = playing, canSeek = ready, mediaId = currentMediaId(), sessionId = sessionId)
        session.setPlaybackState(PlaybackState.Builder().setActions(actions)
            .setBufferedPosition(if (ready) durationMs else 0L)
            .setState(if (loading) PlaybackState.STATE_BUFFERING else if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                position, if (playing) actualSpeed else 0f, now).build())
    }

    private fun currentMediaId() = "$novelId|$chapterId|$index|$fingerprint"

    private fun action(action: String): PendingIntent = PendingIntent.getService(this, action.hashCode(),
        Intent(this, NarrationService::class.java).setAction(action).putExtra(SESSION, sessionId)
            .setData(Uri.Builder().scheme("novelnest").authority("narration-control")
                .appendPath(sessionId).appendPath(action).build()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun notification(): Notification {
        val state = NarrationStatus.state.value
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(chapterTitle.ifBlank { "Novel Nest narration" })
            .setContentText(state.message.ifBlank { "${state.phase.caption} · Passage ${state.passage} of ${state.total}" })
            .setContentIntent(PendingIntent.getActivity(this, 9201,
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(wanted)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous passage", action(PREVIOUS)).build())
            .addAction(Notification.Action.Builder(if (wanted) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (wanted) "Pause" else "Play", action(if (wanted) PAUSE else PLAY)).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", action(STOP)).build())
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2)).build()
    }

    private fun stopSession() {
        if (stopping) { stopSelf(); return }
        stopping = true
        android.util.Log.d("NovelNestNarration", "Stopping narration session")
        cleanup("checkpoint") { save() }
        invalidate(); idleJob?.cancel()
        cleanup("focus") { focus?.let { audioManager.abandonAudioFocusRequest(it) } }; focus = null
        NarrationStatus.mutable.value = NarrationState()
        NarrationStatus.mutableTimeline.value = NarrationTimeline()
        cleanup("session") { session.isActive = false }
        cleanup("foreground") { stopForeground(STOP_FOREGROUND_REMOVE) }
        foreground = false; stopSelf()
    }
    override fun onTaskRemoved(rootIntent: Intent?) { if (!wanted) stopSession() }
    override fun onDestroy() {
        if (activeService.get() === this) activeService.clear()
        if (!stopping) cleanup("checkpoint") { save() }
        stopping = true
        cleanup("playback") { invalidate() }
        cleanup("idle") { idleJob?.cancel() }
        cleanup("scope") { scope.cancel() }
        // Schedules TLS cleanup on IO; never blocks this callback.
        if (::audio.isInitialized) cleanup("network") { audio.close() }
        cleanup("focus") { focus?.let { audioManager.abandonAudioFocusRequest(it) } }; focus = null
        if (noisyRegistered) cleanup("receiver") { unregisterReceiver(noisy) }
        noisyRegistered = false
        if (::session.isInitialized) cleanup("session") { session.release() }
        cleanup("notification") {
            stopForeground(STOP_FOREGROUND_REMOVE)
            // A notification detached after a long pause survives service destruction.
            getSystemService(NotificationManager::class.java).cancel(ID)
        }
        foreground = false
        if (cacheOwned) { NarrationCache.playbackStopped(); cacheOwned = false }
        NarrationStatus.mutable.value = NarrationState()
        NarrationStatus.mutableTimeline.value = NarrationTimeline()
        super.onDestroy()
    }

    private fun cleanup(step: String, block: () -> Unit) {
        try { block() } catch (error: Exception) {
            android.util.Log.w("NovelNestNarration", "Service cleanup $step failed: ${error.javaClass.simpleName}")
        }
    }

    companion object {
        @Volatile private var activeService = WeakReference<NarrationService>(null)
        private val main by lazy { Handler(Looper.getMainLooper()) }
        private const val SESSION = "narration_session"
        private const val CHANNEL = "narration_playback"
        private const val ID = 9201
        const val START = "narration.START"
        const val PLAY = "narration.PLAY"
        const val PAUSE = "narration.PAUSE"
        const val STOP = "narration.STOP"
        const val NEXT = "narration.NEXT"
        const val PREVIOUS = "narration.PREVIOUS"
        const val SEEK = "narration.SEEK"
        fun seek(context: Context, positionMs: Long, mediaId: String, expectedSession: String? = null) {
            dispatch(expectedSession) { service ->
                if (service.currentMediaId() == mediaId) service.seekWithinPassage(positionMs)
            }
        }
        fun command(context: Context, action: String, expectedSession: String? = null) {
            val accepted = dispatch(expectedSession) { service ->
                when (action) {
                    PLAY -> service.resume()
                    PAUSE -> service.pause()
                    STOP -> service.stopSession()
                    NEXT -> service.skip(1)
                    PREVIOUS -> service.skip(-1)
                }
            }
            if (!accepted && action == STOP &&
                (expectedSession == null || NarrationStatus.state.value.sessionId == expectedSession)) {
                // Clear stale UI without constructing a new service to stop it.
                if (activeService.get() == null) {
                    NarrationStatus.mutable.value = NarrationState()
                    NarrationStatus.mutableTimeline.value = NarrationTimeline()
                    context.getSystemService(NotificationManager::class.java).cancel(ID)
                }
            }
        }

        private fun dispatch(expectedSession: String?, action: (NarrationService) -> Unit): Boolean {
            val service = activeService.get() ?: return false
            val expected = expectedSession ?: NarrationStatus.state.value.sessionId
            if (expected.isBlank()) return false
            val command = Runnable {
                if (activeService.get() === service && !service.stopping && service.sessionId == expected) {
                    service.control { action(service) }
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) command.run() else main.post(command)
            return true
        }
        fun start(context: Context, novelId: String, chapterId: String, chapterUrl: String, title: String) {
            ContextCompat.startForegroundService(context, Intent(context, NarrationService::class.java).setAction(START)
                .putExtra("novelId", novelId).putExtra("chapterId", chapterId).putExtra("chapterUrl", chapterUrl).putExtra("chapterTitle", title))
        }
    }
}
