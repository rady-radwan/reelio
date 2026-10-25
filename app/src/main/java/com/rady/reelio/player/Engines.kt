package com.rady.reelio.player

import android.content.Context
import android.os.ParcelFileDescriptor
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import com.rady.reelio.data.MediaEntry
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.roundToInt

enum class EngineKind { MAIN, VLC }

data class TrackOption(val label: String, val selected: Boolean, val ref: Any)

interface EngineListener {
    fun onStateChanged()
    fun onEnded()
    fun onError(message: String, canFallback: Boolean)
    fun onVideoSize(width: Int, height: Int, pixelRatio: Float)
    fun onCues(text: String?)
    fun onTracksChanged()
}

interface PlaybackEngine {
    val kind: EngineKind
    fun load(entry: MediaEntry, startMs: Long, play: Boolean)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(ms: Long)
    val positionMs: Long
    val durationMs: Long
    val isPlaying: Boolean
    val isBuffering: Boolean
    fun setSpeed(speed: Float)
    fun setVolume(v: Float)
    fun audioTracks(): List<TrackOption>
    fun textTracks(): List<TrackOption>
    fun selectAudio(t: TrackOption)
    fun selectText(t: TrackOption?)
    fun release()
}

/** Media3 ExoPlayer with FFmpeg audio decoders. Handles mp3, mp4, mov, mkv, webm, wav, aac, ogg, flac... */
@OptIn(UnstableApi::class)
class ExoEngine(ctx: Context, sessionId: Int, private val l: EngineListener) : PlaybackEngine {
    override val kind = EngineKind.MAIN
    val player: ExoPlayer
    private var lastTracksOk = true

    init {
        val renderers = DefaultRenderersFactory(ctx)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        player = ExoPlayer.Builder(ctx, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ctx, extractors))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                false,
            )
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.setAudioSessionId(sessionId)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) l.onEnded() else l.onStateChanged()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) = l.onStateChanged()

            override fun onPlayerError(error: PlaybackException) {
                val code = error.errorCode
                val fallback = code in 3000..4999 || code == PlaybackException.ERROR_CODE_UNSPECIFIED ||
                    code == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
                l.onError(error.errorCodeName.removePrefix("ERROR_CODE_").replace('_', ' ').lowercase(), fallback)
            }

            override fun onTracksChanged(tracks: Tracks) {
                val g = tracks.groups
                if (g.isEmpty()) return
                val hasVideo = g.any { it.type == C.TRACK_TYPE_VIDEO }
                val videoOk = g.any { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }
                val hasAudio = g.any { it.type == C.TRACK_TYPE_AUDIO }
                val audioOk = g.any { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
                val ok = (!hasVideo || videoOk) && (!hasAudio || audioOk) && (hasVideo || hasAudio)
                if (!ok && lastTracksOk) {
                    lastTracksOk = false
                    l.onError("unsupported codec", true)
                } else l.onTracksChanged()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0) l.onVideoSize(videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio)
            }

            override fun onCues(cueGroup: CueGroup) {
                val t = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n").trim()
                l.onCues(t.ifEmpty { null })
            }
        })
    }

    override fun load(entry: MediaEntry, startMs: Long, play: Boolean) {
        lastTracksOk = true
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverrides().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        player.setMediaItem(MediaItem.Builder().setUri(entry.toUri()).setMediaId(entry.uri).build(), startMs.coerceAtLeast(0))
        player.prepare()
        player.playWhenReady = play
    }

    override fun play() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }
    override fun pause() = player.pause()
    override fun stop() = player.stop()
    override fun seekTo(ms: Long) = player.seekTo(ms.coerceAtLeast(0))
    override val positionMs: Long get() = player.currentPosition
    override val durationMs: Long get() = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
    override val isPlaying: Boolean get() = player.isPlaying
    override val isBuffering: Boolean get() = player.playbackState == Player.STATE_BUFFERING
    override fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed)
    override fun setVolume(v: Float) { player.volume = v }
    fun setSkipSilence(on: Boolean) { player.skipSilenceEnabled = on }

    fun setVideoView(view: TextureView?) {
        if (view == null) player.clearVideoTextureView(null) else player.setVideoTextureView(view)
    }

    fun clearVideoView(view: TextureView) = player.clearVideoTextureView(view)

    private fun tracksOf(type: Int): List<TrackOption> {
        val out = ArrayList<TrackOption>()
        var n = 1
        for (g in player.currentTracks.groups) {
            if (g.type != type) continue
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                val f = g.getTrackFormat(i)
                val parts = listOfNotNull(
                    f.label,
                    f.language?.takeIf { it != "und" }?.let { java.util.Locale.forLanguageTag(it).displayLanguage.ifBlank { it } },
                    if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) when (f.channelCount) { 1 -> "mono"; 2 -> "stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> "${f.channelCount} ch" } else null,
                    f.sampleMimeType?.substringAfter('/')?.uppercase()?.takeIf { type == C.TRACK_TYPE_AUDIO },
                ).distinct()
                out += TrackOption("Track ${n++}" + if (parts.isEmpty()) "" else " — " + parts.joinToString(", "), g.isTrackSelected(i), g.mediaTrackGroup to i)
            }
        }
        return out
    }

    override fun audioTracks() = tracksOf(C.TRACK_TYPE_AUDIO)
    override fun textTracks(): List<TrackOption> =
        if (player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) tracksOf(C.TRACK_TYPE_TEXT).map { it.copy(selected = false) }
        else tracksOf(C.TRACK_TYPE_TEXT)

    @Suppress("UNCHECKED_CAST")
    private fun select(t: TrackOption, type: Int) {
        val (group, index) = t.ref as Pair<androidx.media3.common.TrackGroup, Int>
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group, index))
            .build()
    }

    override fun selectAudio(t: TrackOption) = select(t, C.TRACK_TYPE_AUDIO)

    override fun selectText(t: TrackOption?) {
        if (t == null) player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        else select(t, C.TRACK_TYPE_TEXT)
    }

    override fun release() = player.release()
}

/** LibVLC: plays almost anything (WMA, WMV, ASF, RMVB, APE, odd codecs). Used when the main engine can't. */
class VlcEngine(private val ctx: Context, sessionId: Int, private val l: EngineListener) : PlaybackEngine {
    override val kind = EngineKind.VLC
    private val libVlc: LibVLC = create(ctx, sessionId)
    val player = MediaPlayer(libVlc)
    private var pendingPause = false
    private var lengthMs = 0L
    private var ended = false
    private var pfd: ParcelFileDescriptor? = null
    private var current: MediaEntry? = null
    private var volume = 100

    init {
        player.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Playing -> {
                    ended = false
                    if (pendingPause) { pendingPause = false; player.pause() }
                    l.onStateChanged()
                }
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped, MediaPlayer.Event.Buffering -> l.onStateChanged()
                MediaPlayer.Event.EndReached -> { ended = true; l.onEnded() }
                MediaPlayer.Event.EncounteredError -> l.onError("this file couldn't be played", false)
                MediaPlayer.Event.LengthChanged -> { if (ev.lengthChanged > 0) lengthMs = ev.lengthChanged; l.onStateChanged() }
                MediaPlayer.Event.Vout -> if (ev.voutCount > 0) updateVideoSize()
                MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected -> {
                    updateVideoSize(); l.onTracksChanged()
                }
                else -> {}
            }
        }
    }

    private fun updateVideoSize() {
        val t = runCatching { player.currentVideoTrack }.getOrNull() ?: return
        if (t.width <= 0) return
        val ratio = if (t.sarDen > 0 && t.sarNum > 0) t.sarNum.toFloat() / t.sarDen else 1f
        l.onVideoSize(t.width, t.height, ratio)
    }

    override fun load(entry: MediaEntry, startMs: Long, play: Boolean) {
        ended = false
        current = entry
        lengthMs = entry.durationMs
        runCatching { pfd?.close() }
        pfd = null
        val media = if (entry.isFile) Media(libVlc, entry.uri)
        else {
            val fd = ctx.contentResolver.openFileDescriptor(entry.toUri(), "r")
            pfd = fd
            if (fd != null) Media(libVlc, fd.fileDescriptor) else Media(libVlc, entry.toUri())
        }
        media.setHWDecoderEnabled(true, false)
        if (startMs > 1000) media.addOption(":start-time=${startMs / 1000.0}")
        player.media = media
        media.release()
        pendingPause = !play
        player.play()
        player.setVolume(volume)
    }

    override fun play() {
        pendingPause = false
        if (ended) {
            ended = false
            current?.let { load(it, 0, true) }
            return
        }
        player.play()
    }

    override fun pause() {
        if (!player.isPlaying) pendingPause = true
        player.pause()
    }

    override fun stop() {
        pendingPause = false
        player.stop()
    }

    override fun seekTo(ms: Long) {
        if (ended) { ended = false; current?.let { load(it, ms, true) }; return }
        player.setTime(ms.coerceAtLeast(0))
    }

    override val positionMs: Long get() = if (ended) durationMs else player.time.coerceAtLeast(0)
    override val durationMs: Long get() = player.length.takeIf { it > 0 } ?: lengthMs
    override val isPlaying: Boolean get() = player.isPlaying
    override val isBuffering: Boolean get() = false
    override fun setSpeed(speed: Float) { player.rate = speed }
    override fun setVolume(v: Float) {
        volume = (v * 100).roundToInt().coerceIn(0, 200)
        player.setVolume(volume)
    }

    fun attach(layout: VLCVideoLayout) {
        runCatching { player.detachViews() }
        player.attachViews(layout, null, true, true)
    }

    fun detach() = runCatching { player.detachViews() }

    /** Aspect ratio for the VLC video view. */
    fun setAspect(mode: String) {
        when (mode) {
            "fill" -> { player.aspectRatio = null; player.videoScale = MediaPlayer.ScaleType.SURFACE_FILL }
            "crop" -> { player.aspectRatio = null; player.videoScale = MediaPlayer.ScaleType.SURFACE_FIT_SCREEN }
            "original" -> { player.aspectRatio = null; player.videoScale = MediaPlayer.ScaleType.SURFACE_ORIGINAL }
            "16:9", "4:3", "21:9", "1:1", "18:9", "5:4", "2.35:1" -> {
                player.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
                player.aspectRatio = mode
            }
            else -> { player.aspectRatio = null; player.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT }
        }
    }

    override fun audioTracks(): List<TrackOption> {
        val cur = player.audioTrack
        return (player.audioTracks ?: emptyArray()).filter { it.id >= 0 }
            .map { TrackOption(it.name ?: "Track ${it.id}", it.id == cur, it.id) }
    }

    override fun textTracks(): List<TrackOption> {
        val cur = player.spuTrack
        return (player.spuTracks ?: emptyArray()).filter { it.id >= 0 }
            .map { TrackOption(it.name ?: "Subtitle ${it.id}", it.id == cur, it.id) }
    }

    override fun selectAudio(t: TrackOption) { player.setAudioTrack(t.ref as Int) }
    override fun selectText(t: TrackOption?) { player.setSpuTrack((t?.ref as? Int) ?: -1) }

    fun addSubtitleFile(path: String) {
        player.addSlave(IMedia.Slave.Type.Subtitle, android.net.Uri.fromFile(java.io.File(path)), true)
    }

    fun setSubtitleDelay(ms: Long) { player.setSpuDelay(ms * 1000) }

    override fun release() {
        runCatching { player.setEventListener(null) }
        runCatching { player.stop() }
        runCatching { player.release() }
        runCatching { libVlc.release() }
        runCatching { pfd?.close() }
    }

    companion object {
        private fun create(ctx: Context, sessionId: Int): LibVLC {
            val base = arrayListOf("--audio-time-stretch", "--no-stats")
            return try {
                // Shares the app's audio session so the volume boost and equalizer also work here.
                LibVLC(ctx, ArrayList(base + "--audiotrack-session-id=$sessionId"))
            } catch (t: Throwable) {
                LibVLC(ctx, base)
            }
        }
    }
}
