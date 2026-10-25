package com.rady.reelio.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.core.content.ContextCompat
import com.rady.reelio.AppGraph
import com.rady.reelio.data.MediaEntry
import com.rady.reelio.data.Subtitles
import com.rady.reelio.data.SubtitleTrack
import com.rady.reelio.data.Thumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.log10
import kotlin.math.roundToInt

data class PlayerState(
    val current: MediaEntry? = null,
    val playing: Boolean = false,
    val playWhenReady: Boolean = false,
    val buffering: Boolean = false,
    val ended: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val speed: Float = 1f,
    val engine: EngineKind? = null,
    val queueVersion: Int = 0,
    val error: String? = null,
    val sleepAtMs: Long? = null,
    val sleepAfterTrack: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val sourceId: String? = null,
)

data class VideoDims(val width: Int = 0, val height: Int = 0, val pixelRatio: Float = 1f) {
    val ratio: Float get() = if (width > 0 && height > 0) width * pixelRatio / height else 16f / 9f
}

/** Everything about playback lives here; the screens, the notification and the lock screen all talk to it. */
object PlayerHub {
    lateinit var app: Context; private set
    val scope: CoroutineScope = MainScope()
    val queue = PlayQueue()

    private var exo: ExoEngine? = null
    private var vlc: VlcEngine? = null
    var engine: PlaybackEngine? = null; private set
    var sessionId = 0; private set

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state
    private val _video = MutableStateFlow(VideoDims())
    val video: StateFlow<VideoDims> = _video
    private val _cue = MutableStateFlow<String?>(null)
    val embeddedCue: StateFlow<String?> = _cue
    private val _sidecar = MutableStateFlow<SubtitleTrack?>(null)
    val sidecar: StateFlow<SubtitleTrack?> = _sidecar
    private val _subDelay = MutableStateFlow(0L)
    val subtitleDelayMs: StateFlow<Long> = _subDelay
    private val _subsOn = MutableStateFlow(true)
    val subtitlesOn: StateFlow<Boolean> = _subsOn
    private val _tracks = MutableStateFlow(0)
    val tracksVersion: StateFlow<Int> = _tracks
    private val _boost = MutableStateFlow(100)
    val boost: StateFlow<Int> = _boost

    lateinit var sessionPlayer: ReelioSessionPlayer; private set
    var artwork: Pair<String, ByteArray?>? = null; private set

    private var playWhenReady = false
    private var ended = false
    private var error: String? = null
    private var speed = 1f
    private var resumeOnFocus = false
    private var hasFocus = false
    private var consecutiveErrors = 0
    private val mainFailed = HashSet<String>()
    private var enhancer: LoudnessEnhancer? = null
    var equalizer: Equalizer? = null; private set
    private var sleepJob: Job? = null
    private var sleepAt: Long? = null
    private var sleepAfterTrack = false
    private var saveJob: Job? = null
    private var restoredPosition = 0L

    var onVideoStart: ((MediaEntry) -> Unit)? = null

    private val audioManager by lazy { app.getSystemService(AudioManager::class.java) }
    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            )
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { change -> onFocusChange(change) }
            .build()
    }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        sessionId = audioManager.generateAudioSessionId()
        speed = AppGraph.settings.speed.value
        _boost.value = AppGraph.settings.boost.value
        sessionPlayer = ReelioSessionPlayer()
        queue.onNewRound = { AppGraph.shuffleMemory.reset(queue.sourceId) }
        setupEffects()
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (playWhenReady) pause()   // headphones unplugged
            }
        }, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        restoreQueue()
        scope.launch {
            while (isActive) {
                delay(5000)
                if (playWhenReady) saveCurrentPosition()
            }
        }
    }

    // ---------- state ----------

    fun positionMs(): Long = engine?.positionMs ?: restoredPosition
    fun durationMs(): Long = engine?.durationMs?.takeIf { it > 0 } ?: queue.current?.durationMs ?: 0
    val isPlayWhenReady: Boolean get() = playWhenReady
    val isEnded: Boolean get() = ended
    val isBuffering: Boolean get() = engine?.isBuffering == true
    val exoPlayer: ExoEngine? get() = exo
    val vlcPlayer: VlcEngine? get() = vlc

    private fun publish() {
        val e = engine
        _state.value = PlayerState(
            current = queue.current,
            playing = e?.isPlaying == true,
            playWhenReady = playWhenReady,
            buffering = e?.isBuffering == true,
            ended = ended,
            durationMs = durationMs(),
            shuffle = queue.shuffle,
            repeat = queue.repeat,
            speed = speed,
            engine = e?.kind,
            queueVersion = queue.version,
            error = error,
            sleepAtMs = sleepAt,
            sleepAfterTrack = sleepAfterTrack,
            hasNext = queue.hasNext(),
            hasPrevious = queue.hasPrevious(),
            sourceId = queue.sourceId,
        )
        sessionPlayer.refresh()
    }

    private val listener = object : EngineListener {
        override fun onStateChanged() {
            if (engine?.isPlaying == true) consecutiveErrors = 0
            publish()
        }

        override fun onEnded() {
            val cur = queue.current ?: return
            AppGraph.positions.clear(cur.uri)
            if (sleepAfterTrack) {
                sleepAfterTrack = false
                pauseInternal(); ended = true; publish(); return
            }
            if (queue.repeat == RepeatMode.ONE) {
                loadCurrent(play = true, resume = false, startOverride = 0)
                return
            }
            if (queue.moveNext(manual = false)) loadCurrent(play = true, resume = false)
            else {
                ended = true
                playWhenReady = false
                abandonFocus()
                publish()
            }
        }

        override fun onError(message: String, canFallback: Boolean) {
            val cur = queue.current ?: return
            if (canFallback && engine is ExoEngine && cur.uri !in mainFailed) {
                // Main engine can't do this file: hand it to VLC from the same spot.
                mainFailed += cur.uri
                val pos = engine?.positionMs ?: 0
                loadCurrent(play = playWhenReady, resume = false, startOverride = pos)
                return
            }
            error = "Can't play \"${cur.title}\": $message"
            publish()
            consecutiveErrors++
            if (consecutiveErrors < queue.size.coerceAtMost(20)) {
                scope.launch {
                    delay(1800)
                    if (queue.current == cur && error != null && queue.moveNext(manual = false)) loadCurrent(play = true, resume = false)
                }
            }
        }

        override fun onVideoSize(width: Int, height: Int, pixelRatio: Float) {
            _video.value = VideoDims(width, height, pixelRatio)
        }

        override fun onCues(text: String?) { _cue.value = text }
        override fun onTracksChanged() { _tracks.value++ }
    }

    private fun exoEngine(): ExoEngine = exo ?: ExoEngine(app, sessionId, listener).also {
        exo = it
        it.setSkipSilence(AppGraph.settings.skipSilence.value)
    }

    private fun vlcEngine(): VlcEngine = vlc ?: VlcEngine(app, sessionId, listener).also { vlc = it }

    fun usesVlc(e: MediaEntry) = com.rady.reelio.data.MediaFormats.prefersVlc(e.extension) || e.uri in mainFailed

    private fun shouldRemember(e: MediaEntry) = e.isVideo || e.durationMs > 20 * 60_000

    // ---------- loading ----------

    /**
     * Play [list] starting at [start]. [start] = -1 with shuffle on picks a random song
     * that hasn't had its turn yet. [sourceId] identifies a playlist so smart-shuffle memory is kept.
     */
    fun playList(list: List<MediaEntry>, start: Int, shuffle: Boolean = false, sourceId: String? = null) {
        if (list.isEmpty()) return
        saveCurrentPosition()
        val src = sourceId ?: "list:" + list.size + ":" + (list.firstOrNull()?.uri?.hashCode() ?: 0)
        queue.set(list, start, shuffle, src, AppGraph.shuffleMemory.played(src))
        loadCurrent(play = true, resume = true)
    }

    fun playSingle(e: MediaEntry, siblings: List<MediaEntry>? = null) {
        val list = siblings ?: listOf(e)
        val idx = list.indexOfFirst { it.uri == e.uri }.coerceAtLeast(0)
        playList(list, idx, shuffle = false, sourceId = null)
    }

    private fun loadCurrent(play: Boolean, resume: Boolean, startOverride: Long? = null) {
        val e = queue.current
        if (e == null) {
            engine?.stop(); engine = null; publish(); return
        }
        ended = false
        error = null
        val settings = AppGraph.settings
        val start = startOverride ?: if (resume && settings.rememberPosition.value && shouldRemember(e)) AppGraph.positions.get(e.uri) else 0L
        val eng: PlaybackEngine = if (usesVlc(e)) vlcEngine() else exoEngine()
        if (engine !== eng) engine?.stop()
        engine = eng
        _cue.value = null
        _sidecar.value = null
        _subDelay.value = 0
        _subsOn.value = true
        _video.value = VideoDims()
        restoredPosition = 0
        eng.setSpeed(speed)
        eng.setVolume(1f)
        eng.load(e, start, play)
        playWhenReady = play
        if (play) requestFocus()
        if (queue.shuffle) AppGraph.shuffleMemory.markPlayed(queue.sourceId, e.uri)
        if (e.isVideo && eng is ExoEngine && e.isFile && settings.subAuto.value) autoLoadSubtitles(e)
        if (e.isVideo && play) onVideoStart?.invoke(e)
        loadArtwork(e)
        if (e.durationMs <= 0 || (e.mediaStoreId < 0 && e.artist.isEmpty())) probe(e)
        PlaybackService.instance?.setVideoMode(e.isVideo)
        reapplyEffects()
        publish()
        persistQueueSoon()
    }

    private fun probe(e: MediaEntry) {
        scope.launch {
            val p = withContext(Dispatchers.IO) { Thumbnails.probe(app, e) }
            if (p != e && queue.current?.uri == e.uri) {
                queue.replaceEntry(e, p)
                publish()
            }
        }
    }

    private fun loadArtwork(e: MediaEntry) {
        if (artwork?.first == e.uri) return
        artwork = e.uri to null
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { Thumbnails.artworkBytes(app, e) }
            if (queue.current?.uri == e.uri) {
                artwork = e.uri to bytes
                sessionPlayer.refresh()
            }
        }
    }

    // ---------- controls ----------

    fun play() {
        if (queue.current == null) return
        if (engine == null) {
            loadCurrent(play = true, resume = false, startOverride = restoredPosition)
            return
        }
        if (ended) {
            ended = false
            if (queue.repeat == RepeatMode.ALL && queue.moveNext(manual = false)) { loadCurrent(true, false); return }
            engine?.seekTo(0)
        }
        error = null
        requestFocus()
        engine?.play()
        playWhenReady = true
        publish()
    }

    fun pause() {
        resumeOnFocus = false
        pauseInternal()
    }

    private fun pauseInternal() {
        engine?.pause()
        playWhenReady = false
        saveCurrentPosition()
        publish()
    }

    fun toggle() = if (playWhenReady && !ended) pause() else play()

    fun seekTo(ms: Long) {
        if (engine == null) { restoredPosition = ms; publish(); return }
        if (ended) ended = false
        engine?.seekTo(ms.coerceIn(0, maxOf(0, durationMs())))
        publish()
    }

    fun seekBy(deltaMs: Long) = seekTo(positionMs() + deltaMs)

    fun next() {
        saveCurrentPosition()
        if (queue.moveNext(manual = true)) loadCurrent(play = true, resume = true)
    }

    /** Previous button: restart the song if we're past 3 seconds, otherwise go back. */
    fun previous() {
        if (positionMs() > 3000) { seekTo(0); return }
        previousItem()
    }

    fun previousItem() {
        saveCurrentPosition()
        if (queue.movePrevious()) loadCurrent(play = true, resume = true) else seekTo(0)
    }

    fun playOrderPos(p: Int) {
        saveCurrentPosition()
        queue.jumpToOrderPos(p)
        loadCurrent(play = true, resume = true)
    }

    fun setShuffle(on: Boolean) {
        queue.setShuffle(on, AppGraph.shuffleMemory.played(queue.sourceId))
        if (on) queue.current?.let { AppGraph.shuffleMemory.markPlayed(queue.sourceId, it.uri) }
        publish(); persistQueueSoon()
    }

    fun setRepeat(r: RepeatMode) {
        queue.repeat = r
        publish(); persistQueueSoon()
    }

    fun cycleRepeat() = setRepeat(
        when (queue.repeat) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF },
    )

    fun setSpeed(s: Float) {
        speed = s.coerceIn(0.25f, 4f)
        AppGraph.settings.speed.value = speed
        engine?.setSpeed(speed)
        publish()
    }

    fun enqueue(entries: List<MediaEntry>, playNext: Boolean) {
        if (entries.isEmpty()) return
        val wasEmpty = queue.add(entries, playNext)
        if (wasEmpty) loadCurrent(play = true, resume = true) else { publish(); persistQueueSoon() }
    }

    fun removeFromQueue(orderPos: Int) {
        val wasCurrent = queue.removeAtOrderPos(orderPos)
        if (wasCurrent) {
            if (queue.current != null) loadCurrent(play = playWhenReady, resume = true)
            else { engine?.stop(); engine = null; playWhenReady = false; abandonFocus() }
        }
        publish(); persistQueueSoon()
    }

    fun moveInQueue(from: Int, to: Int) {
        queue.moveUpcoming(from, to)
        publish(); persistQueueSoon()
    }

    fun clearQueue() {
        saveCurrentPosition()
        engine?.stop()
        engine = null
        playWhenReady = false
        queue.clear()
        abandonFocus()
        publish(); persistQueueSoon()
    }

    // ---------- volume boost & equalizer ----------

    private fun setupEffects() {
        runCatching { enhancer = LoudnessEnhancer(sessionId) }
        runCatching { equalizer = Equalizer(0, sessionId) }
        reapplyEffects()
    }

    private fun reapplyEffects() {
        if (enhancer == null) runCatching { enhancer = LoudnessEnhancer(sessionId) }
        applyBoost(_boost.value)
        applyEq()
    }

    /** 100 = normal, up to 300 = three times louder. */
    fun setBoost(percent: Int) {
        val p = percent.coerceIn(100, 300)
        _boost.value = p
        AppGraph.settings.boost.value = p
        applyBoost(p)
    }

    private fun applyBoost(p: Int) {
        val e = enhancer ?: return
        runCatching {
            val mB = (2000 * log10(p / 100.0)).roundToInt()
            e.setTargetGain(mB)
            e.enabled = p > 100
        }
    }

    fun applyEq() {
        val eq = equalizer ?: return
        val s = AppGraph.settings
        runCatching {
            eq.enabled = s.eqEnabled.value
            if (!s.eqEnabled.value) return
            val preset = s.eqPreset.value
            if (preset >= 0 && preset < eq.numberOfPresets) eq.usePreset(preset.toShort())
            else {
                val levels = s.eqBands.value.split(',').mapNotNull { it.toIntOrNull() }
                for (b in 0 until eq.numberOfBands) levels.getOrNull(b)?.let { eq.setBandLevel(b.toShort(), it.toShort()) }
            }
        }
    }

    // ---------- sleep timer ----------

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepAfterTrack = false
        sleepAt = null
        if (minutes != null && minutes > 0) {
            val at = System.currentTimeMillis() + minutes * 60_000L
            sleepAt = at
            sleepJob = scope.launch {
                delay(at - System.currentTimeMillis())
                // gentle fade-out over 5 s
                for (i in 10 downTo 1) { engine?.setVolume(i / 10f); delay(500) }
                pause()
                engine?.setVolume(1f)
                sleepAt = null
                publish()
            }
        }
        publish()
    }

    fun sleepAfterCurrent() {
        sleepJob?.cancel(); sleepAt = null
        sleepAfterTrack = true
        publish()
    }

    // ---------- subtitles (main engine; VLC finds same-name subtitles by itself) ----------

    private fun autoLoadSubtitles(e: MediaEntry) {
        scope.launch {
            val file = withContext(Dispatchers.IO) { runCatching { Subtitles.findFor(e.uri).firstOrNull() }.getOrNull() } ?: return@launch
            if (queue.current?.uri == e.uri) loadSubtitleFile(file)
        }
    }

    fun loadSubtitleFile(file: File) {
        val cur = queue.current ?: return
        val eng = engine
        if (eng is VlcEngine) { eng.addSubtitleFile(file.absolutePath); return }
        scope.launch {
            val s = AppGraph.settings
            val track = withContext(Dispatchers.IO) {
                runCatching { Subtitles.load(file, s.subCharset.value, s.subNonLatin.value) }.getOrNull()
            } ?: return@launch
            if (queue.current?.uri != cur.uri) return@launch
            _sidecar.value = track
            _subsOn.value = true
            (engine as? ExoEngine)?.selectText(null)   // don't show two subtitle tracks at once
            _tracks.value++
        }
    }

    fun reloadSubtitleEncoding() {
        _sidecar.value?.file?.let { loadSubtitleFile(it) }
    }

    fun setSubtitlesOn(on: Boolean) {
        _subsOn.value = on
        if (!on) engine?.selectText(null)
        _tracks.value++
    }

    fun selectEmbeddedSubtitle(t: TrackOption?) {
        _sidecar.value = null
        _subsOn.value = t != null
        engine?.selectText(t)
        _tracks.value++
    }

    fun setSubtitleDelay(ms: Long) {
        _subDelay.value = ms
        (engine as? VlcEngine)?.setSubtitleDelay(ms)
    }

    // ---------- audio focus ----------

    private fun requestFocus() {
        if (hasFocus) return
        hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        audioManager.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> { hasFocus = false; resumeOnFocus = false; pauseInternal() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (playWhenReady) { resumeOnFocus = true; pauseInternal() }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> engine?.setVolume(0.3f)
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                engine?.setVolume(1f)
                if (resumeOnFocus) { resumeOnFocus = false; play() }
            }
        }
    }

    // ---------- remembering positions & the queue ----------

    fun saveCurrentPosition() {
        val e = queue.current ?: return
        val eng = engine ?: return
        if (!shouldRemember(e) || !AppGraph.settings.rememberPosition.value) return
        val p = eng.positionMs
        val d = eng.durationMs.takeIf { it > 0 } ?: e.durationMs
        if (p > 5000 && (d <= 0 || d - p > 10_000)) AppGraph.positions.put(e.uri, p)
        else if (d > 0 && d - p <= 10_000) AppGraph.positions.clear(e.uri)
    }

    private val queueFile get() = File(app.filesDir, "queue.json")

    private fun persistQueueSoon() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1500)
            val o = JSONObject().apply {
                put("items", JSONArray().apply { queue.items.forEach { put(it.toJson()) } })
                put("order", JSONArray(queue.orderSnapshot))
                put("pos", queue.pos)
                put("shuffle", queue.shuffle)
                put("repeat", queue.repeat.name)
                put("source", queue.sourceId ?: "")
                put("position", positionMs())
            }
            withContext(Dispatchers.IO) { runCatching { queueFile.writeText(o.toString()) } }
        }
    }

    fun persistNow() {
        saveCurrentPosition()
        runCatching {
            val o = JSONObject().apply {
                put("items", JSONArray().apply { queue.items.forEach { put(it.toJson()) } })
                put("order", JSONArray(queue.orderSnapshot))
                put("pos", queue.pos)
                put("shuffle", queue.shuffle)
                put("repeat", queue.repeat.name)
                put("source", queue.sourceId ?: "")
                put("position", positionMs())
            }
            queueFile.writeText(o.toString())
        }
    }

    private fun restoreQueue() {
        runCatching {
            if (!queueFile.exists()) return
            val o = JSONObject(queueFile.readText())
            val arr = o.getJSONArray("items")
            val items = (0 until arr.length()).map { MediaEntry.fromJson(arr.getJSONObject(it)) }
            val ord = o.getJSONArray("order").let { a -> (0 until a.length()).map { a.getInt(it) } }
            queue.restore(
                items, ord, o.optInt("pos", 0), o.optBoolean("shuffle"),
                runCatching { RepeatMode.valueOf(o.optString("repeat")) }.getOrDefault(RepeatMode.OFF),
                o.optString("source").ifEmpty { null },
            )
            restoredPosition = o.optLong("position", 0)
            publish()
        }
    }
}
